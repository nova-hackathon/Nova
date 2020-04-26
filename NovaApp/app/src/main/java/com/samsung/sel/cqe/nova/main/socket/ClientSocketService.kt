package com.samsung.sel.cqe.nova.main.socket

import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeDiscoverySession
import android.util.Log
import android.widget.Toast
import com.samsung.sel.cqe.nova.main.NovaActivity
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.*
import com.samsung.sel.cqe.nova.main.controller.NovaController
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.utils.MessageType
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import com.samsung.sel.cqe.nova.main.utils.convertMessageFromJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class ClientSocketService(
    val phoneInfo: PhoneInfo,
    val view: NovaActivity,
    val novaController: NovaController
) {
    private val isPingInProgress = AtomicBoolean(false)
    private val clientSocketsByServerId = ConcurrentHashMap<String, NovaSocketClient>()
    private val serversQueue = LinkedBlockingQueue<String>()

    fun processReceivedMessage(message: String) {
        val novaMessage: NovaMessage = convertMessageFromJson(message)
        when (novaMessage.type) {
            MessageType.SYNC_CLOCK -> novaController.adjustTimeToMaster(novaMessage.content)
        }
    }

    fun requestSocketConnectionToServer(
        peerHandle: PeerHandle,
        serverPhoneId: String,
        subscribeSession: SubscribeDiscoverySession
    ) {
        Log.w(TAG, "creating client socket $peerHandle")
        val connMgr = novaController.connectivityManager
        val clientSocket = NovaSocketClient(this, serverPhoneId)
        clientSocket.requestNetwork(-1, peerHandle, subscribeSession, connMgr)
    }

    private fun startPingMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            while (isPingInProgress.get()) {
                sendPingMessage()
                delay(50_000)
            }
        }
    }

    fun stopPingMessages() = isPingInProgress.set(false)

    private fun sendPingMessage() {
        val serverSocket = clientSocketsByServerId[phoneInfo.masterId]
        val msg = NovaMessage(
            MessageType.PING,
            NovaMessage.EMPTY_CONTENT,
            phoneInfo
        )
        serverSocket?.sendMessage(msg)
    }

    fun onNewServerConnected(serverId: String, serverSocket: NovaSocketClient) {
        Log.w(TAG, "Add new server $serverId")
        clientSocketsByServerId[serverId] = serverSocket
        view.appendToServerTextField("server $serverId ${novaController.getAwareInfoByID(serverId)?.phoneName}")
        if (phoneInfo.masterId == "") {
            novaController.changeMaster(serverId)
            novaController.syncTimeWithMaster()
            isPingInProgress.set(true)
            startPingMessages()
        }
    }

    fun getFirstMapValue() = clientSocketsByServerId.entries.first().value

    fun onServerConnectionLost(serverId: String) {
        novaController.onMasterLost()
        serversQueue.clear()
        novaController.removeServerByID(serverId)
        serversQueue.addAll(novaController.getMastersIds())
        novaController.startAnalysis()
    }

    fun onNetworkUnreachable() {
        view.showOnUiThread("Network is unreachable", Toast.LENGTH_LONG)
        chooseNewServer()
    }

    fun chooseNewServer() {
        var serverId: String = serversQueue.poll() ?: "-1"
        var serverInfo: NeighbourInfo? = novaController.getAwareInfoByID(serverId)
        while ((serverInfo?.isAcceptsConnections == false && serversQueue.isNotEmpty())) {
            Log.w(
                TAG,
                "While Server accepts connection status: ${serverInfo?.isAcceptsConnections} ${serverInfo?.phoneName}"
            )
            serverId = serversQueue.poll() ?: "-1"
            serverInfo = novaController.getAwareInfoByID(serverId)
        }

        Log.w(
            TAG,
            "Server accepts connection status: ${serverInfo?.isAcceptsConnections} ${serverInfo?.phoneName}"
        )
        if (serverInfo?.isAcceptsConnections == true) {
            novaController.requestNewClientConnection(serverInfo.peer, serverId)
        } else {
            novaController.requestNextConnection()
        }

    }

    fun isQueueNotEmpty() = serversQueue.isNotEmpty()
    fun clearServersQueue() = serversQueue.clear()

    fun addToServersQueue(phoneId: String){
        serversQueue.add(phoneId)
    }

    fun removeFromServersQueue(phoneId: String){
        serversQueue.remove(phoneId)
    }

    fun close() {
        clientSocketsByServerId.forEach { (_, u) -> u.onDestroy() }
    }

    fun showOnUiThread(message: String, duration: Int) =
        view.showOnUiThread(message, duration)

}