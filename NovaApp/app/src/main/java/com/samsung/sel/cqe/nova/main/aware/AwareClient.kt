package com.samsung.sel.cqe.nova.main.aware

import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareSession
import android.util.Log
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.interfaces.IAwareClientSubscriber
import com.samsung.sel.cqe.nova.main.aware.interfaces.IClientDiscoverySubscriber
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import com.samsung.sel.cqe.nova.main.utils.NovaMessageHeader
import com.samsung.sel.cqe.nova.main.utils.convertMessageToJsonBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AwareClient(
    private val awareServiceName: String,
    private val wifiAwareSession: WifiAwareSession,
    private val subscriber: IAwareClientSubscriber
) : IClientDiscoverySubscriber {

    var subscribeDiscoveredSession: SubscribeDiscoverySession? = null
    private val nextMessageId = AtomicInteger(0)
    private val peersMap = ConcurrentHashMap<String, NeighbourInfo>()

    private fun createSubscriberConfig(specificInfo: ByteArray): SubscribeConfig =
        SubscribeConfig.Builder()
            .setServiceName(awareServiceName)
            .setServiceSpecificInfo(specificInfo)
            .build()


    fun subscribeService(headerBytes: ByteArray) {
        if (subscribeDiscoveredSession == null) {

            val config = createSubscriberConfig(headerBytes)
            val callback =
                ClientDiscoverySessionCallback(
                    this
                )
            wifiAwareSession.subscribe(config, callback, null)
        }
    }

    override fun setDiscoveredSession(discoverySession: SubscribeDiscoverySession) {
        subscribeDiscoveredSession = discoverySession
    }


    override fun onAwareServiceDiscovered(header: NovaMessageHeader, peerHandle: PeerHandle) {
        val neighbourInfo = NeighbourInfo(peerHandle, header)
        if ((peersMap.putIfAbsent(header.phoneId, neighbourInfo) == null
                    || peersMap[header.phoneId]?.status != header.status) && header.status == PhoneStatus.MASTER
        ) {
            Log.w(TAG, "Server Queue: added ${header.phoneName}")
            subscriber.onNewServerDiscovered(header.phoneId)
        }

        if (header.status == PhoneStatus.CLOSING) {
            peersMap.remove(header.phoneId)
            subscriber.onServerDisconnected(header.phoneId)
            Log.w(TAG, "Client closing -> removing ${header.phoneName}")
        } else if (peersMap[header.phoneId]?.status != header.status || peersMap[header.phoneId]?.isAcceptsConnections != header.acceptsConnection) {
            peersMap[header.phoneId] = neighbourInfo
        }
    }

    override fun runAnalyze() {
        subscriber.runAnalyze()
    }


    fun updateConfig(headerBytes: ByteArray) {
        val subscribeConfig = createSubscriberConfig(headerBytes)
        subscribeDiscoveredSession?.updateSubscribe(subscribeConfig)
    }

    fun close() {
        subscribeDiscoveredSession?.close()
    }

    fun sendMessage(peerHandle: PeerHandle, novaMessage: NovaMessage) {
        subscribeDiscoveredSession?.let {
            it.sendMessage(
                peerHandle, nextMessageId.incrementAndGet(),
                convertMessageToJsonBytes(novaMessage)
            )
            Log.w(TAG, "id: ${nextMessageId.get()}, $NovaMessage ")
        }
    }

    fun sendMessage(clientId: String, novaMessage: NovaMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!peersMap.contains(clientId)) {
                delay(100)
            }
            peersMap[clientId]?.let { info ->
                sendMessage(info.peer, novaMessage)
            }
        }
    }

    fun getIdMacMap(): HashMap<String, String> {
        val macMap = HashMap<String, String>()
        peersMap.entries.forEach { macMap[it.key] = it.value.MAC }
        return macMap
    }

    fun getInfo(phoneId: String): NeighbourInfo? = peersMap[phoneId]

    fun getMastersIds() = peersMap.filter { it.value.status == PhoneStatus.MASTER }.keys

    fun removeFromPeersMap(phoneId: String) = peersMap.remove(phoneId)

    fun getUndecidedByMaxRank(): NeighbourInfo? =
        peersMap.values.filter { it.status == PhoneStatus.UNDECIDED }.maxBy { it.masterRank }

}