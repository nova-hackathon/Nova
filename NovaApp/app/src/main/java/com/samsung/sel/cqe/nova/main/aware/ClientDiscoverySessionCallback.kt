package com.samsung.sel.cqe.nova.main.aware

import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeDiscoverySession
import android.util.Log
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.interfaces.IClientDiscoverySubscriber
import com.samsung.sel.cqe.nova.main.utils.convertMessageHeaderFromJson

class ClientDiscoverySessionCallback(private val subscriberI: IClientDiscoverySubscriber) :
    DiscoverySessionCallback() {
    override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
        subscriberI.setDiscoveredSession(session)
        Log.w(TAG, "Service discovered $session")
        subscriberI.runAnalyze()
    }

    //CAUTION if will be more than 3 servers in network - this method will be called  continuously( like while(true))
    override fun onServiceDiscovered(
        peerHandle: PeerHandle,
        serviceSpecificInfo: ByteArray, matchFilter: List<ByteArray>
    ) {
        val specificString = String(serviceSpecificInfo)
        val header = convertMessageHeaderFromJson(specificString)
        subscriberI.onAwareServiceDiscovered(header, peerHandle)
    }

    override fun onSessionConfigUpdated() {
        super.onSessionConfigUpdated()
        Log.w(TAG, "Subscribe config updated successfully")
    }

    override fun onMessageSendFailed(messageId: Int) {
        super.onMessageSendFailed(messageId)
        Log.w(TAG, "Message send failed: $messageId")
    }
}