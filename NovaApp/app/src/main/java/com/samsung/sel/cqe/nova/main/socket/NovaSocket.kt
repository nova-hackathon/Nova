package com.samsung.sel.cqe.nova.main.socket

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.util.Log
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import com.samsung.sel.cqe.nova.main.utils.convertMessageToJsonString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketException

abstract class NovaSocket {
    abstract var socket: Socket?
    abstract var printWriter: PrintWriter?
    val lock: Any = Any()
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var connectivityManager: ConnectivityManager? = null
    private var port: Int? = null


    abstract fun onMessageReceived(message: String): Boolean
    abstract fun createNetworkCallback(discoverySession: DiscoverySession): ConnectivityManager.NetworkCallback
    abstract fun createNetworkSpecifier(
        port: Int,
        peerHandle: PeerHandle,
        discoverySession: DiscoverySession
    ): WifiAwareNetworkSpecifier

    abstract suspend fun createSocket()

    fun requestNetwork(
        port: Int,
        peerHandle: PeerHandle,
        discoverySession: DiscoverySession,
        connectivityManager: ConnectivityManager
    ) {
        Log.w(TAG, "NovaSocket: Request Network Info")
        Log.w(TAG, "NovaSocket: PeerHandle $peerHandle")
        Log.w(TAG, "NovaSocket: DiscoverySession $discoverySession")
        Log.w(TAG, "NovaSocket: Port $port")
        val networkSpecifier = createNetworkSpecifier(port, peerHandle, discoverySession)
        val myNetworkRequest = createNetworkRequest(networkSpecifier)
        this.port = port
        this.connectivityManager = connectivityManager
        callback = createNetworkCallback(discoverySession)
        callback?.let { connectivityManager.requestNetwork(myNetworkRequest, it)}
    }

    private fun createNetworkRequest(networkSpecifier: WifiAwareNetworkSpecifier) =
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()

    fun sendMessage(message: NovaMessage) = CoroutineScope(Dispatchers.IO).launch {
        printWriter?.let { printWriter ->
            val messageJson =
                convertMessageToJsonString(
                    message
                )
            printWriter.println(messageJson)
            Log.w(TAG, "NovaMessage written successfully: $message")
        }
    }

    fun initSocketListener() = CoroutineScope(Dispatchers.IO).launch {
        try {
            socket?.let { socket ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { bufferedReader ->
                    var tmp: String? = bufferedReader.readLine()
                    while (tmp != null) {
                        val ifStopReader= onMessageReceived(tmp)
                        tmp = if (ifStopReader) null else bufferedReader.readLine()
                    }
                }
            }
        } catch (e: SocketException) {
            Log.w(TAG, "SOCKET EXC CAUGHT.")
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun closeNetwork() {
        callback?.let {
            Log.w(TAG, "Unregister Network Callback on port $port")
            connectivityManager?.unregisterNetworkCallback(it)
        }
    }

    open fun close() {
        closeNetwork()
        socket?.close()
        printWriter?.close()
    }

    open fun onDestroy() {
        printWriter?.close()
        socket?.close()
    }


}