package com.samsung.sel.cqe.nova.main.aware

import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.WifiAwareSession
import android.util.Log
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.interfaces.IAwareServer
import com.samsung.sel.cqe.nova.main.aware.interfaces.IServerDiscoverySubscriber
import com.samsung.sel.cqe.nova.main.utils.MessageType
import com.samsung.sel.cqe.nova.main.utils.NovaMessage

class AwareServer(
    private val awareServiceName: String,
    private val session: WifiAwareSession,
    private val service: AwareService
) : IServerDiscoverySubscriber, IAwareServer {
    override var publishDiscoverySession: PublishDiscoverySession? = null

    private fun createPublisherConfig(specificInfo: ByteArray): PublishConfig =
        PublishConfig.Builder()
            .setServiceName(awareServiceName)
            .setServiceSpecificInfo(specificInfo)
            .build()

    override fun publish(headerBytes: ByteArray) {
        val config = createPublisherConfig(headerBytes)
        val callback = ServerDiscoverySessionCallback(this)
        session.publish(config, callback, null)
    }

    override fun setPublishSession(publishSession: PublishDiscoverySession) {
        publishDiscoverySession = publishSession
    }

    override suspend fun processServerMessage(novaMessage: NovaMessage, peerHandle: PeerHandle) {
        Log.w(TAG, "Processing $novaMessage")
        when (novaMessage.type) {
            MessageType.REQUEST_SOCKET -> service.onConnectionRequest(peerHandle,novaMessage.header.phoneId)
            MessageType.ACCEPT_CONNECTION -> service.onServerAcceptConnection(novaMessage.header.phoneId)
            MessageType.ACCEPT_CLUSTER_CONNECTION -> service.onServerAcceptExternalConnection(novaMessage.header.phoneId)
            MessageType.REJECT_CONNECTION -> service.onServerRejectConnection(novaMessage.header.phoneId, novaMessage.content)
            else -> Log.w(TAG, "Unknown message type ${novaMessage.type}")
        }
    }

    override fun updateConfig(configBytes: ByteArray) {
        val publishConfig = createPublisherConfig(configBytes)
        publishDiscoverySession?.updatePublish(publishConfig)
    }

    override fun close() {
        publishDiscoverySession?.close()
    }
}