package com.samsung.sel.cqe.nova.main.socket

import android.net.ConnectivityManager
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import java.util.concurrent.ConcurrentHashMap

interface IServerSocketService {
    fun processReceivedMessage(message: String): Boolean
    fun onNewClientConnected(clientId: String, clientSocket: NovaServerSocket)
    fun checkQueueAndCreateConnectionWithClient(
        peerHandle: PeerHandle, clientId: String,
        publishDiscoverySession: PublishDiscoverySession, connectivityManager: ConnectivityManager
    )
    fun onRequestFinished()
    fun onNetworkUnavailable(connectedPhoneId: String)
    fun onClientConnectionLost(phoneId: String)
    fun sendClusterUpdateToClients(clientId: String = "")
    fun sendMessageToClient(senderId: String, msg: NovaMessage)
    fun close()

    fun getServerSockets(): ConcurrentHashMap<String, NovaSocket>
    fun removeServerSocket(clientId: String): NovaServerSocket?
    fun isAcceptClient() : Boolean

    fun isAcceptMaster() : Boolean

    fun showOnUiThread(message: String, duration: Int)
    fun sendRttUpdateToClients(senderIdToOmit: String, rttMessage: NovaMessage)
    fun sendRttRequestToClients(rttRequestMessage: NovaMessage): Int
    fun resetAvailableRolesArray()
    fun resetSocketCount()
    fun isClientConnected(clientId: String): Boolean
}