package com.samsung.sel.cqe.nova.main.aware.interfaces

import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeDiscoverySession
import com.samsung.sel.cqe.nova.main.utils.NovaMessageHeader
import kotlinx.coroutines.ExecutorCoroutineDispatcher

interface IClientDiscoverySubscriber {
    fun setDiscoveredSession(discoverySession: SubscribeDiscoverySession)
    suspend fun onAwareServiceDiscovered(header: NovaMessageHeader, peerHandle: PeerHandle)
    fun getDiscoveryCoroutineContext(): ExecutorCoroutineDispatcher
    fun onAwareMessageSendFailed(messageId: Int)
    fun onAwareMessageSendSuccess(messageId: Int)
}