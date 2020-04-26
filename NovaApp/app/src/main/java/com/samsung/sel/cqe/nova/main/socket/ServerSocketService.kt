package com.samsung.sel.cqe.nova.main.socket

import android.net.ConnectivityManager
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import android.util.Log
import com.samsung.sel.cqe.nova.main.NovaActivity
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.controller.NovaController
import com.samsung.sel.cqe.nova.main.utils.MessageType
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import com.samsung.sel.cqe.nova.main.utils.convertMessageFromJson
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ServerSocketService(
    private val view: NovaActivity,
    private val novaController: NovaController
) {
    companion object {
        const val MAX_CLIENT_COUNT = 3
    }

    private val nextSocketPort = AtomicInteger(9134)
    private val socketClientCount = AtomicInteger(0)
    private val isRequestInProgress = AtomicBoolean(false)
    private val serverSocketsByClientId = ConcurrentHashMap<String, NovaServerSocket>()
    private val clientSocketQueue = LinkedBlockingQueue<SocketQueueElement>()

    fun processReceivedMessage(message: String) {
        val novaMessage: NovaMessage = convertMessageFromJson(message)
        when (novaMessage.type) {
            MessageType.SYNC_REQUEST -> novaController.onSyncRequest(
                novaMessage.content, novaMessage.senderId
            )
        }
    }

    fun isAcceptClient(): Boolean = socketClientCount.get() < MAX_CLIENT_COUNT

    fun onNewClientConnected(clientId: String, clientSocket: NovaServerSocket) {
        Log.w(TAG, "Add new client $clientId")
        serverSocketsByClientId[clientId] = clientSocket
        view.appendToClientTextField("client $clientId ${novaController.getAwareInfoByID(clientId)?.phoneName}")
    }

    private fun createConnectionWithClient(
        peerHandle: PeerHandle, clientId: String,
        publishDiscoverySession: PublishDiscoverySession,
        connectivityManager: ConnectivityManager
    ) {
        isRequestInProgress.set(true)
        Log.w(TAG, "Server create new socket")
        novaController.sendSubscribeMessageToClient(MessageType.ACCEPT_CONNECTION, clientId)
        val port = nextSocketPort.incrementAndGet()
        val serverSocket =
            NovaServerSocket(this, port, clientId)
        serverSocket.requestNetwork(port, peerHandle, publishDiscoverySession, connectivityManager)
    }

    fun checkQueueAndCreateConnectionWithClient(
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

    fun onRequestFinished() {
        isRequestInProgress.set(false)
        if (clientSocketQueue.isNotEmpty()) {
            val nextClient = clientSocketQueue.poll()
            nextClient?.apply {
                createConnectionWithClient(
                    peerHandle, clientId,
                    publishDiscoverySession, connectivityManager
                )
            }
        }
    }

    fun onClientConnectionLost() {
        socketClientCount.decrementAndGet()
        novaController.updateAcceptConnections()
    }

    fun close() {
        serverSocketsByClientId.forEach { (_, u) -> u.onDestroy() }
    }

    fun showOnUiThread(message: String, duration: Int) =
        view.showOnUiThread(message, duration)

    fun sendMessageToId(senderId: String, msg: NovaMessage) {
        serverSocketsByClientId[senderId]?.sendMessage(msg)
    }

    private data class SocketQueueElement(
        val peerHandle: PeerHandle, val clientId: String,
        val publishDiscoverySession: PublishDiscoverySession,
        val connectivityManager: ConnectivityManager
    )
}