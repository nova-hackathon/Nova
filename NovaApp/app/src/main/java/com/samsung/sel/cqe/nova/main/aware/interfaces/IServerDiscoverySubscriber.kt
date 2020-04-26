package com.samsung.sel.cqe.nova.main.aware.interfaces

import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import com.samsung.sel.cqe.nova.main.utils.NovaMessage

interface IServerDiscoverySubscriber {
    fun setPublishSession(publishSession: PublishDiscoverySession)
    fun processServerMessage(novaMessage: NovaMessage, peerHandle: PeerHandle)
}