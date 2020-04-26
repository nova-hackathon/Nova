package com.samsung.sel.cqe.nova.main.socket

import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.util.Log
import android.widget.Toast
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.utils.MessageType
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import com.samsung.sel.cqe.nova.main.utils.convertMessageFromJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class NovaServerSocket(
    val socketService: ServerSocketService,
    val port: Int,
    val connectedPhoneId: String
) : AwareSocket() {
    override var socket: Socket? = null
    override var printWriter: PrintWriter? = null
    val isAlreadyFinishedRequest: AtomicBoolean = AtomicBoolean(false)
    private val serverSocket: ServerSocket = ServerSocket(port)

    override fun createNetworkSpecifier(
        port: Int,
        peerHandle: PeerHandle,
        discoverySession: DiscoverySession
    ) = WifiAwareNetworkSpecifier.Builder(discoverySession, peerHandle)
        .setPskPassphrase("somePassword")
        .setPort(port)
        .build()

    override suspend fun createSocket() {
        socket = serverSocket.accept()
        socket?.let { socket ->
            socketService.showOnUiThread(
                "Publisher connection with ${socket.inetAddress}",
                Toast.LENGTH_LONG
            )
            initSocketListener()
            printWriter = PrintWriter(socket.getOutputStream(), true)
            socketService.onNewClientConnected(connectedPhoneId, this)
        }
    }

    override fun onMessageReceived(message: String) {
        Log.w(TAG, "server received message: $message")
        socketService.processReceivedMessage(message)
    }

    override fun createNetworkCallback(discoverySession: DiscoverySession) =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!isAlreadyFinishedRequest.get()) {
                    socketService.onRequestFinished()
                    isAlreadyFinishedRequest.set(true)
                }
                if (socket == null) {
                    synchronized(lock) {
                        CoroutineScope(Dispatchers.IO).launch {
                            if (socket == null) {
                                createSocket()
                            }
                        }
                    }
                }
            }

            override fun onUnavailable() {
                Log.w(TAG, "Network unavailable $connectedPhoneId")
                if (!isAlreadyFinishedRequest.get()) {
                    socketService.onRequestFinished()
                    isAlreadyFinishedRequest.set(true)
                }
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost $connectedPhoneId")
                socketService.onClientConnectionLost()
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        serverSocket.close()
    }


}





