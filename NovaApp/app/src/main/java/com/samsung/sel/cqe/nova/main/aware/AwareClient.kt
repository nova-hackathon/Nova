package com.samsung.sel.cqe.nova.main.aware

import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareSession
import android.util.Log
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.interfaces.IAwareClient
import com.samsung.sel.cqe.nova.main.aware.interfaces.IClientDiscoverySubscriber
import com.samsung.sel.cqe.nova.main.controller.PhoneStatus
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import com.samsung.sel.cqe.nova.main.utils.NovaMessageHeader
import com.samsung.sel.cqe.nova.main.utils.convertMessageToJsonBytes
import kotlinx.coroutines.*
import java.lang.StringBuilder
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class AwareClient(
    private val awareServiceName: String,
    private val wifiAwareSession: WifiAwareSession,
    private val service: AwareService
) : IClientDiscoverySubscriber, IAwareClient {

    override var subscribeSession: SubscribeDiscoverySession? = null
    private val nextMessageId = AtomicInteger(0)
    private val peersMap = ConcurrentHashMap<String, NeighbourInfo>()
    private val messageSentIds = ConcurrentHashMap<Int, AwareMessage>()

    private val discoveryContext = Executors.newCachedThreadPool().asCoroutineDispatcher()

    private fun createSubscriberConfig(specificInfo: ByteArray): SubscribeConfig =
        SubscribeConfig.Builder()
            .setServiceName(awareServiceName)
            .setServiceSpecificInfo(specificInfo)
            .build()

    override fun subscribe(specificInfo: ByteArray) {
        if (subscribeSession == null) {

            val config = createSubscriberConfig(specificInfo)
            val callback =
                ClientDiscoverySessionCallback(
                    this
                )
            wifiAwareSession.subscribe(config, callback, null)
        }
    }

    override fun setDiscoveredSession(discoverySession: SubscribeDiscoverySession) {
        subscribeSession = discoverySession
    }


    override suspend fun onAwareServiceDiscovered(
        header: NovaMessageHeader, peerHandle: PeerHandle
    ) {
        withContext(discoveryContext) {
            val neighbourInfo = NeighbourInfo(peerHandle, header)
            Log.w(TAG, "Discovered $neighbourInfo")
            if ((peersMap.putIfAbsent(header.phoneId, neighbourInfo) == null
                        || peersMap[header.phoneId]?.status != header.status) && header.status == PhoneStatus.MASTER
            ) {
                Log.w(TAG, "Server Queue: added ${header.phoneName}")
                service.onNewServerDiscovered(header.phoneId)
            } else if (!service.isMasterClusterConnectionCreated() && header.status == service.getPhoneStatusForClusterConnection() && header.acceptsConnection
                && !service.checkIfClusterConnected(neighbourInfo.masterId) && service.isClusterReconnecting(
                    neighbourInfo.masterId
                )
            ) {
                service.logClusterConnectionMap()
                service.createMasterConnectionWithClientServer(
                    peerHandle,
                    header.phoneId,
                    header.masterId,
                    header.status
                )
            }

            if (header.status == PhoneStatus.CLOSING && !service.isPeerMasterPhone(header.phoneId)) {
                Log.w(TAG, "Removing ${peersMap[header.phoneId]?.phoneName}")
                removePhone(header.phoneId)
            }
            if ((peersMap[header.phoneId]?.status != header.status || peersMap[header.phoneId]?.isAcceptsConnections != header.acceptsConnection) && header.status != PhoneStatus.CLOSING) {
                peersMap[header.phoneId] = neighbourInfo
            }
//            service.setDeviceCountView(peersMap.size)
        }

    }

    override fun updateConfig(configBytes: ByteArray) {
        val subscribeConfig = createSubscriberConfig(configBytes)
        subscribeSession?.updateSubscribe(subscribeConfig)
    }

    override fun close() {
        subscribeSession?.close()
    }

    override fun sendMessage(peerHandle: PeerHandle, message: NovaMessage, tryCount: Int) {
        subscribeSession?.let {
            val messageId = nextMessageId.incrementAndGet()
            it.sendMessage(
                peerHandle, messageId,
                convertMessageToJsonBytes(message)
            )
            messageSentIds[messageId] = AwareMessage(peerHandle, message, tryCount)
            Log.w(TAG, "Sending id: ${messageId}, $NovaMessage ")
        }
    }

    override fun sendMessageToClient(phoneId: String, message: NovaMessage) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!peersMap.contains(phoneId)) {
                delay(100)
            }
            peersMap[phoneId]?.let { info ->
                sendMessage(info.peer, message)
            }
        }
    }

    override fun onAwareMessageSendFailed(messageId: Int) {
        if (messageSentIds.contains(messageId)) {
            Log.w(TAG, "Message remains unsent MessageId: ${messageId}, $NovaMessage ")
            val (peerHandle, awareMessage, tryCount) = messageSentIds.remove(messageId)!!
            tryCount.inc()
            if (tryCount < AwareMessage.TRY_LIMIT) sendMessage(peerHandle, awareMessage, tryCount)

        }
    }

    override fun onAwareMessageSendSuccess(messageId: Int) {
        if (messageSentIds.contains(messageId)) {
            Log.w(TAG, "Removing from list MessageId: ${messageId}, $NovaMessage ")
            messageSentIds.remove(messageId)
        }
    }

    override fun getInfoByPhoneId(phoneId: String): NeighbourInfo? = peersMap[phoneId]

    override fun getPeerIdByClusterIdAndPhoneStatus(clusterId: String, phoneStatus: PhoneStatus): String?
        = peersMap.filterValues { it.masterId == clusterId && it.status == phoneStatus }.keys.firstOrNull()

    override fun isPeerStillAvailable(phoneId: String) = peersMap.containsKey(phoneId)

    override fun getMacIdMap(): HashMap<String, String> {
        val macMap = HashMap<String, String>()
        peersMap.entries.forEach { macMap[it.value.MAC] = it.value.phoneName }
        return macMap
    }

    override fun getMacIdMap(clusterId: String): HashMap<String, String> {
        val macMap = HashMap<String, String>()
        peersMap.filterValues { nI -> nI.masterId == clusterId  }.entries.forEach { macMap[it.value.MAC] = it.value.phoneName }
        return macMap
    }

    override fun removePhone(phoneId: String): NeighbourInfo? {
        val peer = peersMap.remove(phoneId)
//        service.setDeviceCountView(peersMap.size)
        return peer
    }

    override fun setPeerStatusToRttInProgress(phoneId: String) {
        peersMap[phoneId]?.let { neighbourInfo ->
            peersMap[phoneId] = NeighbourInfo(neighbourInfo, PhoneStatus.RTT_IN_PROGRESS)
        }
    }

    override fun getMastersIds() = peersMap.filter { it.value.isMaster && it.value.status != PhoneStatus.MASTER }.keys

    override fun getAvailableMastersIds() =
        peersMap.filter { it.value.isMaster && it.value.status == PhoneStatus.MASTER }.keys

    override fun getFilteredMap(phoneStatus: PhoneStatus) =
        peersMap.filter { it.value.status == phoneStatus }

    override fun getDiscoveryCoroutineContext() = discoveryContext

    override fun logPeersMap() {
        val s = StringBuilder()
        peersMap.forEachKey(Long.MAX_VALUE) { s.appendln(getInfoByPhoneId(it)?.phoneName) }
        Log.w(TAG, "Peers Map contains: $s")
    }

    private data class AwareMessage(
        val peerHandle: PeerHandle,
        val message: NovaMessage,
        var tryCount: Int
    ) {
        companion object {
            const val TRY_LIMIT = 3
        }
    }
}