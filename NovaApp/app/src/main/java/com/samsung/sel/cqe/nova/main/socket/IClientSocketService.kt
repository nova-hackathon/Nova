package com.samsung.sel.cqe.nova.main.socket

import android.net.ConnectivityManager
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeDiscoverySession
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import java.util.concurrent.ConcurrentHashMap

interface IClientSocketService {

    fun processReceivedMessage(message: String): Boolean
    fun requestSocketConnectionToServer(
        peerHandle: PeerHandle, serverPhoneId: String,
        subscribeSession: SubscribeDiscoverySession,
        connMgr: ConnectivityManager
    )
    fun sendMessageToServer(senderId: String, msg: NovaMessage)
    fun onNewServerConnected(serverId: String, clientSocket: NovaSocketClient)
    fun onServerConnectionLost(serverId: String)
    suspend fun onNetworkUnreachable(serverPhoneId: String)
    fun close()
    fun removeClientSocket(serverId: String): NovaSocketClient?
    fun getClientSockets() : ConcurrentHashMap<String, NovaSocket>

    fun showOnUiThread(message: String, duration: Int)
    fun sendRttUpdateToServers(senderIdToOmit: String, rttMessage: NovaMessage)
    fun stopPingMessages()
}