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

class NovaSocketClient(val socketService: IClientSocketService, val connectedPhoneId: String) :
    NovaSocket() {
    override var socket: Socket? = null
    override var printWriter: PrintWriter? = null
    lateinit var network: Network
    lateinit var networkCapabilities: NetworkCapabilities

    override fun onMessageReceived(message: String): Boolean {
        Log.w(TAG, "client received message: $message")
        return socketService.processReceivedMessage(message)
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
        Log.w(TAG, "Create Socket")
        Log.w(TAG, "PeerIpv6 $peerIpv6")
        Log.w(TAG, "PeerPort $peerPort")

        if (socket == null) {
            try {
                synchronized(lock) {
                    if (socket == null) {
                        peerIpv6?.let { createSocketAndInitIO(it, peerPort) }
                    }
                }
            } catch (ex: ConnectException) {
                ex.printStackTrace()
                socketService.onNetworkUnreachable(connectedPhoneId)
            }
        }
    }

    private fun createSocketAndInitIO(inet6Address: Inet6Address, port: Int) {
        socket = network.socketFactory.createSocket(inet6Address, port)
        socket?.let { socket ->
            Log.w(TAG, "Subscriber connection with ${socket.inetAddress}")
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
                Log.w(TAG, "SocketClient onUnavailable $connectedPhoneId")
                CoroutineScope(Dispatchers.Default).launch {
                    socketService.onNetworkUnreachable(connectedPhoneId)
                }
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "SocketClient onLost $connectedPhoneId")
                CoroutineScope(Dispatchers.Default).launch {
                    socketService.onServerConnectionLost(connectedPhoneId)
                }
            }
        }
}

