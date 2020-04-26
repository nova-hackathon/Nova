package com.samsung.sel.cqe.nova.main.socket

import android.net.ConnectivityManager
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeDiscoverySession
import android.util.Log
import com.samsung.sel.cqe.nova.main.COMPUTER_COMMUNICATION_TAG
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.controller.PhoneStatus
import com.samsung.sel.cqe.nova.main.structure.convertClusterInfoFromJson
import com.samsung.sel.cqe.nova.main.utils.MessageType
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import com.samsung.sel.cqe.nova.main.utils.convertMessageFromJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import com.samsung.sel.cqe.nova.main.socket.IClientSocketService as IClientSocketService

class ClientSocketService(
    private val phoneInfo: PhoneInfo,
    private val socketService: SocketService
) : IClientSocketService {
    private val isPingInProgress = AtomicBoolean(false)
    private val clientSocketsByServerId = ConcurrentHashMap<String, NovaSocketClient>()

    override fun processReceivedMessage(message: String): Boolean {
        val novaMessage: NovaMessage = convertMessageFromJson(message)
        when (novaMessage.type) {
            MessageType.SYNC_CLOCK ->
                socketService.adjustTimeToMaster(novaMessage.content)
            MessageType.STATUS_UPDATE ->
                onUpdateStatusRequest(novaMessage.header.phoneId, novaMessage.content)
            MessageType.MASTER_CLUSTER_INFO_UPDATE ->
                socketService.updateMasterClusterInfo(
                    convertClusterInfoFromJson(novaMessage.content)
                )
            MessageType.CLIENT_CLUSTER_INFO_UPDATE ->
                socketService.updateClientClusterInfo(
                    convertClusterInfoFromJson(novaMessage.content)
                )
            MessageType.CLUSTER_CONNECTION_LOST ->
                socketService.removeFromClusterConnectionMap(novaMessage.header.phoneId)
            MessageType.RTT_INIT ->
                socketService.onRttUpdate(
                    novaMessage.content, novaMessage.header.phoneId, novaMessage.header.status
                )
            MessageType.RTT_BROADCAST ->
                socketService.onRttBroadcast(
                    novaMessage.content, novaMessage.header.phoneId, novaMessage.header.status
                )
            MessageType.RTT_REQUEST -> {
                phoneInfo.clusterInfo.state.onRttRequest()
                return true
            }
            else -> Log.w(TAG, "Unexpected message type ${novaMessage.type}")
        }
        return false
    }

    private fun onUpdateStatusRequest(serverId: String, content: String) {
        val currentStatus = phoneInfo.status
        if (isCanSendChangeRoleMessage(serverId, currentStatus)) {
            val msg = NovaMessage(
                MessageType.CLIENT_ACCEPTS_CHANGE_ROLE, currentStatus.toString(), phoneInfo
            )
            Log.w(
                TAG,
                "Sending CLIENT_ACCEPTS_CHANGE_ROLE to ${socketService.getAwareInfoByID(serverId)?.phoneName}"
            )
            sendMessageToServer(serverId, msg)
        }
        socketService.updateStatus(PhoneStatus.valueOf(content))
    }

    private fun isCanSendChangeRoleMessage(serverId: String, currentStatus: PhoneStatus): Boolean =
        serverId == phoneInfo.masterId &&
                (currentStatus == PhoneStatus.CLIENT_OUT || currentStatus == PhoneStatus.CLIENT_IN)

    override fun requestSocketConnectionToServer(
        peerHandle: PeerHandle,
        serverPhoneId: String,
        subscribeSession: SubscribeDiscoverySession,
        connMgr: ConnectivityManager
    ) {
        val clientSocket = NovaSocketClient(this, serverPhoneId)
        clientSocket.requestNetwork(-1, peerHandle, subscribeSession, connMgr)
    }

    private fun startPingMessages(serverId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            while (isPingInProgress.get()) {
                sendPingMessage(serverId)
                delay(50_000)
            }
        }
    }

    override fun stopPingMessages() = isPingInProgress.set(false)

    private fun sendPingMessage(serverId: String) {
        val msg = NovaMessage(MessageType.PING, NovaMessage.EMPTY_CONTENT, phoneInfo)
        sendMessageToServer(serverId, msg)
    }

    override fun onNewServerConnected(serverId: String, clientSocket: NovaSocketClient) {
        Log.w(TAG, "Add new server $serverId")
        clientSocketsByServerId[serverId] = clientSocket
        if (phoneInfo.masterId.isEmpty() || phoneInfo.status == PhoneStatus.RTT_FINISHED) {
            socketService.changeMaster(serverId)
            requestStatusUpdateFromServer(serverId)
            socketService.syncTimeWithMaster(getFirstSocketToServer())
        }
        if (socketService.getAwareInfoByID(serverId)?.status == PhoneStatus.CLIENT_IN) {
            socketService.changeStatus(PhoneStatus.CLIENT_OUT)
        }
        sendRttUpdateToServer(serverId)
        isPingInProgress.set(true)
        startPingMessages(serverId)
    }

    private fun requestStatusUpdateFromServer(serverId: String) {
        val msg = NovaMessage(MessageType.REQUEST_STATUS, NovaMessage.EMPTY_CONTENT, phoneInfo)
        sendMessageToServer(serverId, msg)
    }

    private fun sendRttUpdateToServer(serverId: String) {
        val rttMessage =
            NovaMessage(MessageType.RTT_INIT, socketService.getRttResultsMap(), phoneInfo)
        sendMessageToServer(serverId, rttMessage)
    }

    override fun sendRttUpdateToServers(senderIdToOmit: String, rttMessage: NovaMessage) {
        clientSocketsByServerId.forEachKey(1) {
            if (it != senderIdToOmit) sendMessageToServer(it, rttMessage)
        }
    }

    private fun getFirstSocketToServer() = clientSocketsByServerId.entries.first().value

    override fun onServerConnectionLost(serverId: String) {
        stopPingMessages()
        Log.w(
            COMPUTER_COMMUNICATION_TAG,
            "Server ${socketService.getAwareInfoByID(serverId)?.phoneName} lost"
        )
        phoneInfo.clusterInfo.state.onServerLost()

    }


    override suspend fun onNetworkUnreachable(serverPhoneId: String) {
        socketService.onClientNetworkUnreachable(serverPhoneId)
    }


    override fun sendMessageToServer(senderId: String, msg: NovaMessage) {
        clientSocketsByServerId[senderId]?.sendMessage(msg)
    }


    override fun close() {
        clientSocketsByServerId.forEach { (_, u) -> u.onDestroy() }
    }

    override fun showOnUiThread(message: String, duration: Int) =
        socketService.showOnUiThread(message, duration)

    override fun getClientSockets(): ConcurrentHashMap<String, NovaSocket> =
        clientSocketsByServerId as ConcurrentHashMap<String, NovaSocket>

    override fun removeClientSocket(serverId: String) = clientSocketsByServerId.remove(serverId)

}