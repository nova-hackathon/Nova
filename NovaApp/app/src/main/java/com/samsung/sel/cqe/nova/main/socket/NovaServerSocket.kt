package com.samsung.sel.cqe.nova.main.socket

import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.util.Log
import android.widget.Toast
import com.samsung.sel.cqe.nova.main.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class NovaServerSocket(
    val socketService: IServerSocketService,
    port: Int,
    val connectedPhoneId: String
) : NovaSocket() {
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
        Log.w(TAG, "Creating Server Socket")
        socket = serverSocket.accept()
        Log.w(TAG, "Server Socket Created")
        socket?.let { socket ->
            initSocketListener()
            printWriter = PrintWriter(socket.getOutputStream(), true)
            socketService.onNewClientConnected(connectedPhoneId, this)
        }
    }

    override fun close() {
        super.close()
        serverSocket.close()
    }

    override fun onMessageReceived(message: String): Boolean {
        Log.w(TAG, "server received message: $message")
        return socketService.processReceivedMessage(message)
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
                Log.w(TAG, "Network unavailable on server side $connectedPhoneId")
                socketService.onNetworkUnavailable(connectedPhoneId)
                if (!isAlreadyFinishedRequest.get()) {
                    socketService.onRequestFinished()
                    isAlreadyFinishedRequest.set(true)
                }
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "ServerSocket onLost $connectedPhoneId")
                socketService.onClientConnectionLost(connectedPhoneId)
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        serverSocket.close()
    }


}





