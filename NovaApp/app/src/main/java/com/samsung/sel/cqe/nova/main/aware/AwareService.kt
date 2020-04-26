package com.samsung.sel.cqe.nova.main.aware

import android.net.MacAddress
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.IdentityChangedListener
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.WifiAwareSession
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.samsung.sel.cqe.nova.R
import com.samsung.sel.cqe.nova.main.NovaActivity
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.interfaces.IAwareClientSubscriber
import com.samsung.sel.cqe.nova.main.aware.interfaces.IAwareServerSubscriber
import com.samsung.sel.cqe.nova.main.controller.NovaController
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.utils.MessageType
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import com.samsung.sel.cqe.nova.main.utils.NovaMessageHeader
import com.samsung.sel.cqe.nova.main.utils.convertMessageHeaderToJsonBytes
import kotlinx.coroutines.*

class AwareService(
    private val novaController: NovaController,
    val view: NovaActivity, private val phoneInfo: PhoneInfo
) : IAwareServerSubscriber,
    IAwareClientSubscriber {
    companion object {
        const val MAX_SERVER_RESPONSE_TIME = 4_000L
    }

    lateinit var session: WifiAwareSession
    private val handlerThread = HandlerThread("CallbackThread")
    private lateinit var client: AwareClient
    private lateinit var server: AwareServer
    var serverResponseJob: Job? = null
    var currPhoneId: String = ""
    internal var lastRttResultsJson = ""
    private val rttMeasurer by lazy {
        RttMeasurer(
            this, novaController.wifiRttManager,
            phoneInfo
        )
    }

    init {
        handlerThread.start()
        val handler = Handler(handlerThread.looper)
        val wifiAwareManager = novaController.wifiAwareManager
        wifiAwareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                super.onAttached(session)
                this@AwareService.session = session
            }
        }, object : IdentityChangedListener() {
            override fun onIdentityChanged(mac: ByteArray) {
                super.onIdentityChanged(mac)
                if (phoneInfo.macAddress == null) {
                    phoneInfo.macAddress = MacAddress.fromBytes(mac)
                    initAwarePublisherAndSubscriber()
                }
            }
        }, handler)
    }

    private fun initAwarePublisherAndSubscriber() {
        val awareServiceName = view.resources.getString(R.string.awareServiceName)
        val header = NovaMessageHeader(phoneInfo)
        val headerBytes = convertMessageHeaderToJsonBytes(header)
        client = AwareClient(awareServiceName, session, this)
        server = AwareServer(awareServiceName, session, this)
        server.publishService(headerBytes)
        Thread.sleep(200)//new service must be discovered first
        client.subscribeService(headerBytes)
    }

    private fun sendSubscribeMessage(
        peerHandle: PeerHandle, type: MessageType,
        content: String = NovaMessage.EMPTY_CONTENT
    ) {
        val message = createNovaMessage(type, content)
        client.sendMessage(peerHandle, message)
    }

    fun sendSubscribeMessageToClient(
        clientId: String, type: MessageType,
        content: String = NovaMessage.EMPTY_CONTENT
    ) {
        val message = createNovaMessage(type, content)
        client.sendMessage(clientId, message)
    }

    private fun createNovaMessage(type: MessageType, content: String) =
        NovaMessage(type, content, phoneInfo)

    fun chooseMasterFromPeers() {
        if (phoneInfo.status == PhoneStatus.UNDECIDED) {
            val peerWithMaxMasterRank: NeighbourInfo? = client.getUndecidedByMaxRank()
            Log.w(
                TAG,
                "Peer Master Rank: ${peerWithMaxMasterRank?.masterRank} :: Own Master Rank: ${phoneInfo.masterRank}"
            )
            if (peerWithMaxMasterRank?.masterRank ?: -1 < phoneInfo.masterRank) {
                novaController.becomeMaster()
            } else {
                client.runAnalyze()
            }
        }
    }


    private fun handleConnectionRequest(peerHandle: PeerHandle, senderId: String) {
        server.publishDiscoverySession?.let { publishDiscoverySession ->
            novaController.onClientSocketRequest(peerHandle, senderId, publishDiscoverySession)
        }
    }

    fun requestNewClientConnection(peerHandle: PeerHandle, phoneId: String) {
        sendSubscribeMessage(peerHandle, MessageType.REQUEST_SOCKET)
        currPhoneId = phoneId
        serverResponseJob = CoroutineScope(Dispatchers.IO).launch {
            delay(MAX_SERVER_RESPONSE_TIME)
            if (currPhoneId == phoneId) {
                currPhoneId = ""
                novaController.requestNextConnection()
            }
        }
    }

    private fun createNewClientConnection(serverId: String) {
        if (currPhoneId != serverId || phoneInfo.status != PhoneStatus.UNDECIDED) return
        serverResponseJob?.cancel()
        currPhoneId = ""
        client.getInfo(serverId)?.let { info ->
            client.subscribeDiscoveredSession?.let { subscribeSession ->
                novaController.onServerAcceptSocketConnection(info.peer, subscribeSession, serverId)
            }
        }
    }

    fun updateConfigs() {
        val header = NovaMessageHeader(phoneInfo)
        Log.w(TAG, "Config Updated $header")
        val headerBytes = convertMessageHeaderToJsonBytes(header)
        client.updateConfig(headerBytes)
        server.updateConfig(headerBytes)
    }

    fun close() {
        CoroutineScope(Dispatchers.IO).launch {
            novaController.changeStatus(PhoneStatus.CLOSING)
            delay(1000)
            handlerThread.quitSafely()
            client.close()
            server.close()
            session.close()
        }
    }

    fun measureRttDistanceToAll() {
        val macMap = client.getIdMacMap()
        rttMeasurer.measureDistanceToAll(macMap)

    }

    fun getMastersIds() = client.getMastersIds()
    fun removeServer(serverId: String) = client.removeFromPeersMap(serverId)
    fun getInfoByID(serverId: String): NeighbourInfo? = client.getInfo(serverId)
    fun getMasterPhoneName() = client.getInfo(phoneInfo.masterId)?.phoneName ?: ""

    override fun onConnectionRequest(peerHandle: PeerHandle, senderId: String) =
        handleConnectionRequest(peerHandle, senderId)

    override fun onServerRejectConnection(serverId: String) =
        novaController.onServerRejectSocketConnection(serverId)

    override fun onServerAcceptConnection(serverId: String) = createNewClientConnection(serverId)

    override fun runAnalyze() {
        CoroutineScope(Dispatchers.IO).launch {
            delay(1000)
            novaController.requestNextConnection()
        }
    }

    override fun onNewServerDiscovered(phoneId: String) {
        novaController.addPhoneIdToServersQueue(phoneId)
    }

    override fun onServerDisconnected(phoneId: String) {
        novaController.removePhoneIdFromServersQueue(phoneId)
    }
}