package com.samsung.sel.cqe.nova.main.socket

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.WifiAwareNetworkInfo
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.util.Log
import android.widget.Toast
import com.samsung.sel.cqe.nova.main.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Inet6Address
import java.net.Socket

class NovaSocketClient(val socketService: ClientSocketService, val connectedPhoneId: String) :
    AwareSocket() {
    override var socket: Socket? = null
    override var printWriter: PrintWriter? = null
    lateinit var network: Network
    lateinit var networkCapabilities: NetworkCapabilities

    override fun onMessageReceived(message: String) {
        Log.w(TAG, "client received message: $message")
        socketService.processReceivedMessage(message)
    }

    override fun createNetworkSpecifier(
        port: Int,
        peerHandle: PeerHandle,
        discoverySession: DiscoverySession
    ) = WifiAwareNetworkSpecifier.Builder(discoverySession, peerHandle)
        .setPskPassphrase("somePassword")
        .build()

    override suspend fun createSocket() {
        val peerAwareInfo = networkCapabilities.transportInfo as WifiAwareNetworkInfo
        val peerIpv6 = peerAwareInfo.peerIpv6Addr
        val peerPort = peerAwareInfo.port
        if (socket == null) {
            synchronized(lock) {
                if (socket == null) {
                    try {
                        peerIpv6?.let { createSocketAndInitIO(it, peerPort) }
                    } catch (ex: ConnectException) {
                        socketService.onNetworkUnreachable()
                    }
                }
            }
        }
    }

    private fun createSocketAndInitIO(inet6Address: Inet6Address, port: Int) {
        socket = network.socketFactory.createSocket(inet6Address, port)
        socket?.let { socket ->
            socketService.showOnUiThread(
                "Subscriber connection with ${socket.inetAddress}",
                Toast.LENGTH_LONG
            )
            try {
                printWriter = PrintWriter(socket.getOutputStream(), true)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            initSocketListener()
            socketService.onNewServerConnected(connectedPhoneId, this)
        }
    }

    override fun createNetworkCallback(discoverySession: DiscoverySession) =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                this@NovaSocketClient.network = network
                this@NovaSocketClient.networkCapabilities = networkCapabilities
                CoroutineScope(Dispatchers.IO).launch {
                    createSocket()
                }
            }

            override fun onUnavailable() {
                Log.w(TAG, "Network unavailable $connectedPhoneId")
                socketService.onNetworkUnreachable()
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost $connectedPhoneId")
                socketService.onServerConnectionLost(connectedPhoneId)
                socketService.stopPingMessages()
            }
        }
}

