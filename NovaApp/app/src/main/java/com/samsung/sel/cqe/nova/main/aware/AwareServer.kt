package com.samsung.sel.cqe.nova.main.aware

import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.WifiAwareSession
import android.util.Log
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.interfaces.IAwareServerSubscriber
import com.samsung.sel.cqe.nova.main.aware.interfaces.IServerDiscoverySubscriber
import com.samsung.sel.cqe.nova.main.utils.MessageType
import com.samsung.sel.cqe.nova.main.utils.NovaMessage

class AwareServer(
    private val awareServiceName: String,
    private val session: WifiAwareSession,
    private val subscriber: IAwareServerSubscriber
) : IServerDiscoverySubscriber {
    var publishDiscoverySession: PublishDiscoverySession? = null

    private fun createPublisherConfig(specificInfo: ByteArray): PublishConfig =
        PublishConfig.Builder()
            .setServiceName(awareServiceName)
            .setServiceSpecificInfo(specificInfo)
            .build()

    fun publishService(headerBytes: ByteArray) {
        val config = createPublisherConfig(headerBytes)
        val callback = ServerDiscoverySessionCallback(this)
        session.publish(config, callback, null)
    }

    override fun setPublishSession(publishSession: PublishDiscoverySession) {
        publishDiscoverySession = publishSession
    }

    override fun processServerMessage(novaMessage: NovaMessage, peerHandle: PeerHandle) {
        Log.w(TAG, "Processing $novaMessage")
        when (novaMessage.type) {
            MessageType.REQUEST_SOCKET -> subscriber.onConnectionRequest(peerHandle,novaMessage.senderId)
            MessageType.ACCEPT_CONNECTION -> subscriber.onServerAcceptConnection(novaMessage.senderId)
            MessageType.REJECT_CONNECTION -> subscriber.onServerRejectConnection(novaMessage.senderId)
            else -> Log.w(TAG, "Unknown message type ${novaMessage.type}")
        }
    }

    fun updateConfig(headerBytes: ByteArray) {
        val publishConfig = createPublisherConfig(headerBytes)
        publishDiscoverySession?.updatePublish(publishConfig)
    }

    fun close() {
        publishDiscoverySession?.close()
    }
}