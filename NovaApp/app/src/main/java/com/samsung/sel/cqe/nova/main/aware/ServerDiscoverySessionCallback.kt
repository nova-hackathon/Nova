package com.samsung.sel.cqe.nova.main.aware

import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import android.util.Log
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.interfaces.IServerDiscoverySubscriber
import com.samsung.sel.cqe.nova.main.utils.convertMessageFromJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ServerDiscoverySessionCallback(
    private val subscriberI: IServerDiscoverySubscriber
) : DiscoverySessionCallback() {
    override fun onPublishStarted(session: PublishDiscoverySession) {
        subscriberI.setPublishSession(session)
        Log.w(TAG, "service published")
    }

    override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
        val messageString = String(message)
        val gridMessage = convertMessageFromJson(messageString)
        CoroutineScope(Dispatchers.IO).launch {
            subscriberI.processServerMessage(gridMessage, peerHandle)
        }
    }


    override fun onSessionConfigUpdated() {
        super.onSessionConfigUpdated()
        Log.w(TAG, "Publish config updated successfully")
    }
}