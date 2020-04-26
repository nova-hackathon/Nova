package com.samsung.sel.cqe.nova.main.aware.interfaces

import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeDiscoverySession
import com.samsung.sel.cqe.nova.main.utils.NovaMessageHeader

interface IClientDiscoverySubscriber {
    fun setDiscoveredSession(discoverySession: SubscribeDiscoverySession)
    fun onAwareServiceDiscovered(header: NovaMessageHeader, peerHandle: PeerHandle)
    fun runAnalyze()
}