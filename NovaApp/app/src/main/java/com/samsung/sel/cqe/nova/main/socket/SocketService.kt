package com.samsung.sel.cqe.nova.main.socket

import android.net.ConnectivityManager
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeDiscoverySession
import android.util.Log
import com.samsung.sel.cqe.nova.main.COMPUTER_COMMUNICATION_TAG
import com.samsung.sel.cqe.nova.main.NovaFragment
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.NeighbourInfo
import com.samsung.sel.cqe.nova.main.controller.NovaController
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.controller.PhoneStatus
import com.samsung.sel.cqe.nova.main.structure.ClusterInfo
import com.samsung.sel.cqe.nova.main.utils.*
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import kotlin.coroutines.coroutineContext

class SocketService(
    private val phoneInfo: PhoneInfo,
    private val novaController: NovaController,
    private val view: NovaFragment
) {

    @Volatile
    private var rttMeasureTimerJob: Job? = null
    private val serversQueue = LinkedBlockingDeque<String>()
    private val clusterConnectionMap = ConcurrentHashMap<String, String>()
    private val clientSocketService: IClientSocketService = ClientSocketService(phoneInfo, this)
    private val serverSocketService: IServerSocketService = ServerSocketService(phoneInfo, this)
    private var clientServerResponseJob: Job? = null

    suspend fun chooseNewServer() {
        var serverId: String = serversQueue.poll() ?: "-1"
        var serverInfo: NeighbourInfo? = novaController.getAwareInfoByID(serverId)
        while ((serverInfo?.isAcceptsConnections == false && serversQueue.isNotEmpty())) {
            Log.w(
                TAG, "While Server accepts connection status: " +
                        "${serverInfo.isAcceptsConnections} ${serverInfo.phoneName}"
            )
            serverId = serversQueue.poll() ?: "-1"
            serverInfo = novaController.getAwareInfoByID(serverId)
        }
        Log.w(
            TAG, "Server accepts connection status: ${serverInfo?.isAcceptsConnections} " +
                    "${serverInfo?.phoneName}"
        )
        if (serverInfo?.isAcceptsConnections == true) {
            novaController.requestNewClientConnection(serverInfo.peer, serverId)
        } else {
            novaController.requestNextConnection()
        }
    }

    fun requestClientSocketConnectionToServer(
        peerHandle: PeerHandle,
        serverPhoneId: String,
        subscribeSession: SubscribeDiscoverySession
    ) {
        Log.w(TAG, "creating client socket $peerHandle")
        val connMgr = novaController.connectivityManager
        clientSocketService.requestSocketConnectionToServer(
            peerHandle, serverPhoneId, subscribeSession, connMgr
        )
    }

    fun updateMasterClusterInfo(sentClusterInfo: ClusterInfo) {
        clientServerResponseJob?.cancel()
        novaController.logAddNewClusterConnection(phoneInfo.phoneID, sentClusterInfo.clusterId)
        addToClusterConnectionMap(phoneInfo.phoneID, sentClusterInfo.clusterId)
        novaController.changeClusterNeighbours(sentClusterInfo)
        serverSocketService.sendClusterUpdateToClients()
    }

    fun updateClientClusterInfo(masterClusterInfo: ClusterInfo) =
        novaController.updateClientClusterInfo(masterClusterInfo)

    fun sendNewClusterConnectionInfoToMaster(connectedClusterId: String) {
        val message = NovaMessage(
            MessageType.NEW_CLUSTER_CONNECTION_ESTABLISHED,
            connectedClusterId, phoneInfo
        )
        clientSocketService.sendMessageToServer(phoneInfo.masterId, message)
    }

    fun onRttUpdate(rttMessage: String, senderId: String, senderStatus: PhoneStatus) {
        val senderDistanceInfo = novaController.addToRttResultsMap(rttMessage, senderId)
        Log.w(COMPUTER_COMMUNICATION_TAG, "on Update from ${senderDistanceInfo?.phoneName}")
        senderDistanceInfo?.let { broadcastRttInfo(senderDistanceInfo, senderId, senderStatus) }
    }

    fun onRttBroadcast(rttBroadcastInfo: String, senderId: String, senderStatus: PhoneStatus) {
        val sentDistanceInfo = convertDistanceInfoFromJson(rttBroadcastInfo)
        if(sentDistanceInfo.isAlarm){
            novaController.onAlarmDiscovered(sentDistanceInfo)
        }
        novaController.addToRttResultsMap(sentDistanceInfo)
        Log.w(
            COMPUTER_COMMUNICATION_TAG,
            "on Broadcast from ${getAwareInfoByID(senderId)?.phoneName}}"
        )
        broadcastRttInfo(sentDistanceInfo, senderId, senderStatus)
    }

    fun broadcastRttInfo(
        broadcastDistanceInfo: DistanceInfo,
        senderId: String = "",
        senderStatus: PhoneStatus = PhoneStatus.UNDECIDED
    ) {
        val jsonContent = convertDistanceInfoToJsonString(broadcastDistanceInfo)
        val rttMessage = NovaMessage(MessageType.RTT_BROADCAST, jsonContent, phoneInfo)
        // Blocking possibility of broadcasting RTT_UPDATE after C0 - CI rtt update was executed (avoiding looping in)
        if (canBroadcastInfo(senderStatus)) {
            Log.w(COMPUTER_COMMUNICATION_TAG, "Broadcasting $broadcastDistanceInfo")
            clientSocketService.sendRttUpdateToServers(senderId, rttMessage)
            serverSocketService.sendRttUpdateToClients(senderId, rttMessage)
        }
    }

    private fun canBroadcastInfo(senderStatus: PhoneStatus): Boolean =
        !((senderStatus == PhoneStatus.CLIENT_OUT || senderStatus == PhoneStatus.CLIENT_IN)
                && !phoneInfo.isMaster)

    fun onNewClusterConnected(clientId: String, clusterId: String) {
        novaController.setMasterToClusterConnectionStatus(true)
        novaController.logAddNewClusterConnection(clientId, clusterId)
        addToClusterConnectionMap(clientId, clusterId)
    }

    fun onSyncRequest(content: String, senderId: String) {
        Log.w(TAG, "${System.currentTimeMillis()} obtain sync response:${LocalDateTime.now()}")
        val msg = NovaMessage(
            MessageType.SYNC_CLOCK,
            "$content${System.currentTimeMillis() - novaController.getSyncStopWatchStartTime()}",
            phoneInfo
        )
        serverSocketService.sendMessageToClient(senderId, msg)
    }

    fun masterConnectionLost() {
        CoroutineScope(Dispatchers.Default).launch {
            Log.w(TAG, "Started onMasterConnectionLost Task")
            novaController.onMasterLost()
            serversQueue.clear()
            val availableMasterKeys = novaController.getAvailableMastersIds()
            val masterKeys = novaController.getMastersIds()
            Log.w(COMPUTER_COMMUNICATION_TAG, "Available Master Keys $availableMasterKeys")
            Log.w(COMPUTER_COMMUNICATION_TAG, "Master Keys $masterKeys")
            when {
                availableMasterKeys.isNotEmpty() -> addMastersAndAnalyze(availableMasterKeys)
                masterKeys.isNotEmpty() -> waitForAvailableMaster(masterKeys)
                else -> novaController.startAnalysis()
            }
        }
    }

    private suspend fun addMastersAndAnalyze(availableMasterIds: Set<String>) {
        Log.w(COMPUTER_COMMUNICATION_TAG, "Available Masters")
        serversQueue.addAll(availableMasterIds)
        novaController.startAnalysis()
    }

    private suspend fun waitForAvailableMaster(initMasterKeys: Set<String>) {
        var masterKeys = initMasterKeys
        Log.w(
            COMPUTER_COMMUNICATION_TAG, "Non available Master, awaiting ${masterKeys.first()}"
        )
        withContext(Dispatchers.IO) {
            do {
                Log.w(
                    COMPUTER_COMMUNICATION_TAG,
                    "Non available Master, awaiting ${masterKeys.first()}"
                )
                val masterAvailability = blockUntilServerIsAvailable(masterKeys.first())
                masterKeys = novaController.getMastersIds()
            } while (masterKeys.isNotEmpty() && !masterAvailability)
            novaController.startAnalysis()
        }
    }

    suspend fun blockUntilServerIsAvailable(peerId: String): Boolean {
        withContext(Dispatchers.IO) {
            Log.w(
                TAG, "Blocking Server: ${serversQueue.isEmpty()} && " +
                        "${novaController.isPeerStillAvailable(peerId)}"
            )
            while (serversQueue.isEmpty() && novaController.isPeerStillAvailable(peerId)) {
                Log.w(
                    TAG, "Blocking Server: ${serversQueue.isEmpty()} && " +
                            "${novaController.isPeerStillAvailable(peerId)}"
                )
                Log.w(
                    TAG, "Blocking Server, awaiting for: " +
                            "${novaController.getAwareInfoByID(peerId)?.phoneName}"
                )
                delay(1000)
            }
        }
        Log.w(TAG, "Blocking Server outside of withContext: $coroutineContext")
        return novaController.isPeerStillAvailable(peerId)
    }

    fun disconnectSocketsWithoutMasterSocket() {
        val clients = clientSocketService.getClientSockets()
            .filterKeys { it != phoneInfo.masterId }.entries
        val servers = serverSocketService.getServerSockets()
            .filterKeys { it != phoneInfo.masterId }.entries
        disconnectAll(clients)
        disconnectAll(servers)
    }

    fun disconnectAllClients(): Set<String> {
        val serverSocketSet = serverSocketService.getServerSockets()
        val clientIds = HashSet<String>(serverSocketSet.keys)
        Log.w(COMPUTER_COMMUNICATION_TAG, "Disconnecting sockets with $clientIds" )
        disconnectAll(serverSocketSet.entries)
        Log.w(COMPUTER_COMMUNICATION_TAG, "Disconnecting sockets with $clientIds" )
        return clientIds
    }

    fun disconnectAllServers(): Set<String> {
        val clientSocketSet = clientSocketService.getClientSockets()
        disconnectAll(clientSocketSet.entries)
        return clientSocketSet.keys
    }

    private fun disconnectAll(socketSet: Set<Map.Entry<String, NovaSocket>>) {
        socketSet.forEach { (phoneId, socket) ->
            Log.w(TAG, "Disconnecting socket with ${getAwareInfoByID(phoneId)?.phoneName}")
            clientSocketService.removeClientSocket(phoneId)
            serverSocketService.removeServerSocket(phoneId)
            socket.close()
        }
    }

    fun resetServerSocketParameters(){
        serverSocketService.resetSocketCount()
        serverSocketService.resetAvailableRolesArray()
    }

    fun startRttMeasureTimer() {
        rttMeasureTimerJob = CoroutineScope(Dispatchers.Default).launch {
            val timeout = 10_000L
            delay(timeout)
            Log.w(COMPUTER_COMMUNICATION_TAG, "RTT $timeout timeout finished!")
            clientSocketService.stopPingMessages()
            if (phoneInfo.status != PhoneStatus.RTT_IN_PROGRESS) phoneInfo.clusterInfo.state.initializeRttMeasure()
        }
    }


    fun forwardRttRequest(): Int {
        val message = NovaMessage(
            MessageType.RTT_REQUEST,
            NovaMessage.EMPTY_CONTENT,
            phoneInfo
        )

         return serverSocketService.sendRttRequestToClients(message)
    }

    suspend fun onClientNetworkUnreachable(serverPhoneId: String) {
        Log.w(TAG, "Called Client Network Unreachable")
        if (!phoneInfo.isMaster) {
            addToServersQueue(serverPhoneId)
            chooseNewServer()
        } else {
            novaController.setMasterToClusterConnectionStatus(false)
        }
    }

    fun close() {
        clientSocketService.close()
        serverSocketService.close()
    }

    fun changeMaster(serverId: String) = novaController.changeMaster(serverId)
    fun changeStatus(status: PhoneStatus) = novaController.changeStatus(status)

    fun sendSubscribeMessageToClient(type: MessageType, clientId: String) =
        novaController.sendSubscribeMessageToClient(type, clientId)

    fun getAwareInfoByID(phoneId: String) = novaController.getAwareInfoByID(phoneId)

    fun updateAcceptConnections() = novaController.updateAcceptConnections()

    fun syncTimeWithMaster(firstSocketToServer: NovaSocketClient) =
        novaController.syncTimeWithMaster(firstSocketToServer)

    fun adjustTimeToMaster(serverMessage: String) = novaController.adjustTimeToMaster(serverMessage)

    fun updateStatus(status: PhoneStatus) = novaController.updateStatus(status)
    fun isServerAcceptClient(): Boolean = serverSocketService.isAcceptClient()
    fun isServerAcceptMaster(): Boolean = serverSocketService.isAcceptMaster()


    fun checkQueueAndCreateConnectionWithClient(
        peerHandle: PeerHandle, clientId: String,
        publishDiscoverySession: PublishDiscoverySession, connectivityManager: ConnectivityManager
    ) = serverSocketService.checkQueueAndCreateConnectionWithClient(
        peerHandle, clientId, publishDiscoverySession, connectivityManager
    )

    fun isServersQueueNotEmpty() = serversQueue.isNotEmpty()

    fun clearServersQueue() = serversQueue.clear()

    fun addToServersQueue(phoneId: String) {
        serversQueue.add(phoneId)
    }

    fun removeFromServersQueue(phoneId: String) {
        serversQueue.remove(phoneId)
    }

    fun removePhoneInfo(phoneId: String) = novaController.removePhoneInfo(phoneId)
    fun setPeerStatusToRttInProgress(phoneId: String) =
        novaController.setPeerStatusToRttInProgress(phoneId)

    fun addToClusterConnectionMap(clientId: String, clusterId: String) {
        novaController.logAddNewClusterConnection(clientId, clusterId)
        clusterConnectionMap.putIfAbsent(clientId, clusterId)
    }

    fun removeFromClusterConnectionMap(clientId: String) {
        novaController.logRemoveClusterConnection(clientId)
        clusterConnectionMap.remove(clientId)
    }

    fun isClientConnected(clientId: String): Boolean = serverSocketService.isClientConnected(clientId)

    fun checkIfClusterConnected(clusterId: String): Boolean =
        clusterConnectionMap.values.contains(clusterId)

    fun getRttResultsMap() = novaController.getRttResultsMap()

    fun logClusterConnectionMap() = Log.d(TAG, "clusterConnectionMap $clusterConnectionMap")

    fun showOnUiThread(message: String, duration: Int) =
        view.showOnUiThread(message, duration)

}

