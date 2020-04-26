package com.samsung.sel.cqe.nova.main.socket

import android.net.ConnectivityManager
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import android.util.Log
import com.samsung.sel.cqe.nova.main.COMPUTER_COMMUNICATION_TAG
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.NeighbourInfo
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.controller.PhoneStatus
import com.samsung.sel.cqe.nova.main.structure.convertClusterInfoToJsonString
import com.samsung.sel.cqe.nova.main.utils.MessageType
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import com.samsung.sel.cqe.nova.main.utils.convertMessageFromJson
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ServerSocketService(
    private val phoneInfo: PhoneInfo,
    private val socketService: SocketService
) : IServerSocketService {
    companion object {
        const val MAX_CLIENT_COUNT = 3
        const val MAX_MASTER_COUNT = 1
        val ROLES_ARRAY: Collection<PhoneStatus> = Collections.unmodifiableList(
            arrayListOf(
                PhoneStatus.CLIENT_SERVER,
                PhoneStatus.CLIENT_OUT,
                PhoneStatus.CLIENT_IN
            )
        )
    }

    private val nextSocketPort = AtomicInteger(9132)
    private val socketClientCount = AtomicInteger(0)
    private val isRequestInProgress = AtomicBoolean(false)
    private val serverSocketsByClientId = ConcurrentHashMap<String, NovaServerSocket>()
    private val clientSocketQueue = LinkedBlockingQueue<SocketQueueElement>()
    private val changingClientRoleJobs = ConcurrentHashMap<String, Job>()
    private val availableRoles = Collections.synchronizedList(
        arrayListOf(
            PhoneStatus.CLIENT_SERVER,
            PhoneStatus.CLIENT_OUT,
            PhoneStatus.CLIENT_IN
        )
    )

    override fun processReceivedMessage(message: String): Boolean {
        val novaMessage: NovaMessage = convertMessageFromJson(message)
        when (novaMessage.type) {
            MessageType.SYNC_REQUEST ->
                socketService.onSyncRequest(novaMessage.content, novaMessage.header.phoneId)
            MessageType.REQUEST_STATUS ->
                onRequestStatusUpdateFromClient(novaMessage.header.phoneId)
            MessageType.CLIENT_ACCEPTS_CHANGE_ROLE ->
                onAcceptChangeRoleRequestFromClient(
                    novaMessage.header.phoneId, PhoneStatus.valueOf(novaMessage.content)
                )
            MessageType.NEW_CLUSTER_CONNECTION_ESTABLISHED ->
                socketService.onNewClusterConnected(novaMessage.header.phoneId, novaMessage.content)
            MessageType.RTT_INIT ->
                socketService.onRttUpdate(
                    novaMessage.content, novaMessage.header.phoneId, novaMessage.header.status
                )
            MessageType.RTT_BROADCAST ->
                socketService.onRttBroadcast(
                    novaMessage.content, novaMessage.header.phoneId, novaMessage.header.status
                )
            else -> Log.w(TAG, "Unknown message type ${novaMessage.type}")
        }
        return false
    }

    override fun isAcceptClient(): Boolean = socketClientCount.get() < MAX_CLIENT_COUNT
    override fun isAcceptMaster(): Boolean = socketClientCount.get() < MAX_MASTER_COUNT

    override fun onNewClientConnected(clientId: String, clientSocket: NovaServerSocket) {
        Log.w(TAG, "Add new client $clientId")
        serverSocketsByClientId[clientId] = clientSocket
        if (phoneInfo.status == PhoneStatus.CLIENT_SERVER_AWAITS_RECONNECT)
            socketService.changeStatus(PhoneStatus.CLIENT_SERVER)
        if (phoneInfo.status == PhoneStatus.CLIENT_SERVER) {
            sendClusterInfoToConnectedClientMaster(clientId)
            socketService.sendNewClusterConnectionInfoToMaster(clientId)
        } else if (phoneInfo.isMaster) {
            sendClusterUpdateToClients(clientId)
        }
        sendRttUpdateToClient(clientId)
    }

    private fun sendClusterInfoToConnectedClientMaster(clientId: String) {
        val message = NovaMessage(
            MessageType.MASTER_CLUSTER_INFO_UPDATE,
            convertClusterInfoToJsonString(phoneInfo.clusterInfo),
            phoneInfo
        )
        sendMessageToClient(clientId, message)
    }


    private fun sendRttUpdateToClient(clientId: String) {
        val rttMessage = NovaMessage(
            MessageType.RTT_INIT,
            socketService.getRttResultsMap(),
            phoneInfo
        )
        sendMessageToClient(clientId, rttMessage)
    }

    override fun sendRttUpdateToClients(senderIdToOmit: String, rttMessage: NovaMessage) {
        serverSocketsByClientId.forEachKey(1) {
            if (it != senderIdToOmit)
                sendMessageToClient(it, rttMessage)
        }
    }

    override fun sendClusterUpdateToClients(clientId: String) {
        val contentJson = convertClusterInfoToJsonString(phoneInfo.clusterInfo)
        val message = NovaMessage(MessageType.CLIENT_CLUSTER_INFO_UPDATE, contentJson, phoneInfo)
        if (clientId.isNotEmpty()) {
            sendMessageToClient(clientId, message)
        } else {
            serverSocketsByClientId.forEachKey(1) { sendMessageToClient(it, message) }
        }
    }

    private fun onRequestStatusUpdateFromClient(clientId: String): PhoneStatus {
        Log.w(TAG, "Status update request from $clientId")
        val nextClientRole = getNextClientRole()
        Log.w(TAG, "Chosen next role: $nextClientRole")
        val message =
            NovaMessage(MessageType.STATUS_UPDATE, nextClientRole.toString(), phoneInfo)
        sendMessageToClient(clientId, message)
        return nextClientRole
    }

    private fun onAcceptChangeRoleRequestFromClient(
        clientId: String,
        changedClientRole: PhoneStatus
    ) {
        Log.w(
            TAG, "On CLIENT_ACCEPTS_CHANGE_ROLE from " +
                    "${socketService.getAwareInfoByID(clientId)?.phoneName}"
        )
        if (changingClientRoleJobs.containsKey(clientId)) {
            changingClientRoleJobs[clientId]?.cancel()
            availableRoles.add(changedClientRole)
            Log.w(TAG, "Changed role: $changedClientRole")
            availableRoles.sort()
            Log.w(TAG, "Changed role. Available roles: $availableRoles")
        }

    }

    private fun createConnectionWithClient(
        peerHandle: PeerHandle, clientId: String,
        publishDiscoverySession: PublishDiscoverySession,
        connectivityManager: ConnectivityManager
    ) {
        isRequestInProgress.set(true)
        val port = nextSocketPort.incrementAndGet()
        Log.w(TAG, "Server create new socket")
        if (isStatusNotClientInAnsAwaitsReconnect())
            socketService.sendSubscribeMessageToClient(MessageType.ACCEPT_CONNECTION, clientId)
        else
            socketService.sendSubscribeMessageToClient(
                MessageType.ACCEPT_CLUSTER_CONNECTION, clientId
            )

        val serverSocket =
            NovaServerSocket(this, port, clientId)
        serverSocket.requestNetwork(port, peerHandle, publishDiscoverySession, connectivityManager)
    }

    private fun isStatusNotClientInAnsAwaitsReconnect() =
        phoneInfo.status != PhoneStatus.CLIENT_SERVER &&
                phoneInfo.status != PhoneStatus.CLIENT_SERVER_AWAITS_RECONNECT

    override fun checkQueueAndCreateConnectionWithClient(
        peerHandle: PeerHandle, clientId: String,
        publishDiscoverySession: PublishDiscoverySession, connectivityManager: ConnectivityManager
    ) {
        socketClientCount.incrementAndGet()
        if (!isRequestInProgress.get()) {
            createConnectionWithClient(
                peerHandle, clientId,
                publishDiscoverySession, connectivityManager
            )
        } else {
            val el = SocketQueueElement(
                peerHandle, clientId,
                publishDiscoverySession, connectivityManager
            )
            clientSocketQueue.add(el)
        }
    }

    override fun onNetworkUnavailable(connectedPhoneId: String) {
        removeServerSocket(connectedPhoneId)
        socketClientCount.decrementAndGet()
        socketService.updateAcceptConnections()
    }

    override fun onRequestFinished() {
        Log.w(TAG, "onRequestFinished, clientsQueueIsEmpty: ${clientSocketQueue.isEmpty()}")
        isRequestInProgress.set(false)
        if (clientSocketQueue.isNotEmpty()) {
            val nextClient = clientSocketQueue.poll()
            nextClient?.apply {
                createConnectionWithClient(
                    peerHandle, clientId, publishDiscoverySession, connectivityManager
                )
            }
        }
    }

    private fun getNextClientRole(): PhoneStatus =
        if (availableRoles.isNotEmpty()) availableRoles.removeAt(0) else PhoneStatus.CLIENT


    override fun onClientConnectionLost(phoneId: String) {
        val info = socketService.getAwareInfoByID(phoneId)
        socketService.removePhoneInfo(phoneId)
        Log.w(
            COMPUTER_COMMUNICATION_TAG, "Client ${info?.phoneName} lost with status ${info?.status}"
        )
        if (phoneInfo.status == PhoneStatus.RTT_IN_PROGRESS) return

        removeServerSocket(phoneId)
        socketClientCount.decrementAndGet()
        socketService.updateAcceptConnections()
        Log.w(
            TAG,
            "Client ${info?.phoneName} lost with status ${info?.status} ifIsMaster: ${phoneInfo.isMaster}"
        )
        if (info != null && phoneInfo.isMaster && info.status != PhoneStatus.CLIENT) {
            addLostRole(info)
        } else if (phoneInfo.status == PhoneStatus.CLIENT_SERVER) {
            awaitForReconnection()
        }

    }

    private fun awaitForReconnection() {
        socketService.changeStatus(PhoneStatus.CLIENT_SERVER_AWAITS_RECONNECT)
        CoroutineScope(Dispatchers.IO).launch {
            delay(4000)
            if (phoneInfo.status == PhoneStatus.CLIENT_SERVER_AWAITS_RECONNECT)
                socketService.changeStatus(PhoneStatus.CLIENT_SERVER)
        }
    }

    private fun addLostRole(info: NeighbourInfo) {
        val disconnectedPhoneStatus = info.status
        availableRoles.add(disconnectedPhoneStatus)
        Log.w(TAG, "Added role: $disconnectedPhoneStatus")
        availableRoles.sort()
        Log.w(TAG, "Available roles: $availableRoles")
        if (availableRoles.size != MAX_CLIENT_COUNT && disconnectedPhoneStatus != PhoneStatus.CLIENT_IN) {
            changeClientRole(disconnectedPhoneStatus)
        }
    }

    private fun changeClientRole(lostStatus: PhoneStatus) {
        var roleToBeChanged = PhoneStatus.CLIENT_IN
        if (lostStatus == PhoneStatus.CLIENT_SERVER) {
            findClientByPhoneStatus(roleToBeChanged)?.apply {
                sendUpdateRoleToClient(roleToBeChanged, this)
            } ?: run {
                roleToBeChanged = PhoneStatus.CLIENT_OUT
                findClientByPhoneStatus(roleToBeChanged)?.apply {
                    sendUpdateRoleToClient(roleToBeChanged, this)
                }
            }
        } else if (lostStatus == PhoneStatus.CLIENT_OUT) {
            findClientByPhoneStatus(roleToBeChanged)?.apply {
                sendUpdateRoleToClient(roleToBeChanged, this)
            }
        }
    }

    private fun sendUpdateRoleToClient(roleToBeChanged: PhoneStatus, clientIdToBeChanged: String) {
        val changingToRole = onRequestStatusUpdateFromClient(clientIdToBeChanged)
        val changingClientRoleJob = CoroutineScope(Dispatchers.IO).launch {
            delay(2000)
            if (changingClientRoleJobs.containsKey(clientIdToBeChanged)) {
                changingClientRoleJobs.remove(clientIdToBeChanged)
                availableRoles.add(changingToRole)
                Log.w(TAG, "Failed change role: $changingToRole")
                availableRoles.sort()
                Log.w(TAG, "Failed change role. Available roles: $availableRoles")
            }
        }
        changingClientRoleJobs[clientIdToBeChanged] = changingClientRoleJob

    }


    override fun sendRttRequestToClients(rttRequestMessage: NovaMessage): Int {
        serverSocketsByClientId.forEachKey(1) {
                sendMessageToClient(it, rttRequestMessage)
                val info = socketService.getAwareInfoByID(it)
                Log.w(
                    COMPUTER_COMMUNICATION_TAG,
                    "Sending RTT request message to Client ${info?.status} ${info?.phoneName}"
                )
        }
        return serverSocketsByClientId.size
    }

    private fun findClientByPhoneStatus(status: PhoneStatus): String? =
        serverSocketsByClientId
            .filterKeys { clientId -> socketService.getAwareInfoByID(clientId)?.status == status }
            .keys.firstOrNull()

    override fun close() {
        serverSocketsByClientId.forEach { (_, u) -> u.onDestroy() }
    }

    override fun showOnUiThread(message: String, duration: Int) =
        socketService.showOnUiThread(message, duration)

    override fun sendMessageToClient(senderId: String, msg: NovaMessage) {
        serverSocketsByClientId[senderId]?.let {
            it.sendMessage(msg)
            Log.w(COMPUTER_COMMUNICATION_TAG, "Sent message to $senderId, ${msg.type}")
        }
    }

    override fun getServerSockets(): ConcurrentHashMap<String, NovaSocket> =
        serverSocketsByClientId as ConcurrentHashMap<String, NovaSocket>

    override fun removeServerSocket(clientId: String) = serverSocketsByClientId.remove(clientId)

    override fun resetAvailableRolesArray() {
        availableRoles.clear()
        availableRoles.addAll(ROLES_ARRAY)
        Log.w(TAG, "After Reset Available Roles Array = $availableRoles")
    }

    override fun resetSocketCount() {
        socketClientCount.set(0)
    }

    override fun isClientConnected(clientId: String): Boolean = serverSocketsByClientId.containsKey(clientId)

    private data class SocketQueueElement(
        val peerHandle: PeerHandle, val clientId: String,
        val publishDiscoverySession: PublishDiscoverySession,
        val connectivityManager: ConnectivityManager
    )
}