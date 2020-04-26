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
import com.samsung.sel.cqe.nova.main.NovaFragment
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.interfaces.IAwareClient
import com.samsung.sel.cqe.nova.main.aware.interfaces.IAwareServer
import com.samsung.sel.cqe.nova.main.controller.NovaController
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.controller.PhoneStatus
import com.samsung.sel.cqe.nova.main.utils.MessageType
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import com.samsung.sel.cqe.nova.main.utils.NovaMessageHeader
import com.samsung.sel.cqe.nova.main.utils.convertMessageHeaderToJsonBytes
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class AwareService(
    private val novaController: NovaController,
    private val view: NovaFragment, private val phoneInfo: PhoneInfo
) {
    companion object {
        const val MAX_SERVER_RESPONSE_TIME = 4_000L
    }

    lateinit var session: WifiAwareSession
    private val handlerThread = HandlerThread("CallbackThread")
    private lateinit var client: IAwareClient
    private lateinit var server: IAwareServer
    val serverResponseJobs = ConcurrentHashMap<String, Job>()
    val requestedServerIds = HashSet<String>()

    private val isClusterConnectionCreated = AtomicBoolean(true)
    private var phoneStatusForClusterConnection = PhoneStatus.CLIENT_SERVER
    private val clusterIdToReconnect = StringBuffer("")

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
                Log.w(TAG,"MAC Changed : ${MacAddress.fromBytes(mac)}")
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
        server.publish(headerBytes)
        Thread.sleep(200)//new service must be discovered first
        client.subscribe(headerBytes)

        CoroutineScope(Dispatchers.Default).launch {
            delay(1_000)
            novaController.masterConnectionLost()
        }
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
        client.sendMessageToClient(clientId, message)
    }

    private fun createNovaMessage(type: MessageType, content: String) =
        NovaMessage(type, content, phoneInfo)

    suspend fun chooseMasterFromPeers() {
        if (phoneInfo.status == PhoneStatus.UNDECIDED) {
            var peerId: String? = null
            val peerWithMaxMasterRank: NeighbourInfo? =
                client.getFilteredMap(PhoneStatus.UNDECIDED).maxBy { it.value.masterRank }?.run {
                    peerId = key
                    value
                }
            Log.w(
                TAG,
                "Peer Master Rank: ${peerWithMaxMasterRank?.masterRank} :: Own Master Rank: ${phoneInfo.masterRank}"
            )
            client.logPeersMap()
            if (peerWithMaxMasterRank?.masterRank ?: -1 < phoneInfo.masterRank) {
                novaController.becomeMaster()
            } else {
                withContext(Dispatchers.IO) {
                    Log.w(TAG, "chooseMasterFromPeers ${kotlin.coroutines.coroutineContext}")
                    novaController.blockUntilServerIsAvailable(peerId?:"")
                    novaController.chooseNewServer()
                }
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
        requestedServerIds.add(phoneId)
        val serverResponseJob = CoroutineScope(Dispatchers.IO).launch {
            delay(MAX_SERVER_RESPONSE_TIME)
            if (requestedServerIds.contains(phoneId)) {
                Log.w(TAG, "Lack response from server - ${getNameByID(phoneId)}")
                requestedServerIds.remove(phoneId)
                when (phoneInfo.status) {
                    PhoneStatus.RTT_FINISHED -> novaController.reconnectToMaster(phoneId)
                    PhoneStatus.UNDECIDED -> novaController.masterConnectionLost()
                    PhoneStatus.MASTER -> isClusterConnectionCreated.set(false)
                    PhoneStatus.CLIENT -> {
                        novaController.changeStatus(PhoneStatus.CLIENT_OUT)
                        requestNewClientConnection(peerHandle, phoneId)
                    }
                    else -> Log.w(TAG, "Response from unrequested server $phoneId")
                }
            }
        }
        serverResponseJobs[phoneId] = serverResponseJob
    }

    fun onServerAcceptConnection(serverId: String) {
        if (phoneInfo.status == PhoneStatus.CLIENT_OUT || phoneInfo.status == PhoneStatus.CLIENT || phoneInfo.status == PhoneStatus.UNDECIDED
            || phoneInfo.status == PhoneStatus.RTT_FINISHED) {
            acceptConnection(serverId)
        }
    }

    private fun acceptConnection(phoneId: String) {
        if (phoneInfo.status == PhoneStatus.CLIENT) {
            novaController.changeStatus(PhoneStatus.CLIENT_OUT)
        }
        if (requestedServerIds.contains(phoneId)) {
            serverResponseJobs[phoneId]?.cancel()
            requestedServerIds.remove(phoneId)
            client.getInfoByPhoneId(phoneId)?.let { info ->
                client.subscribeSession?.let { subscribeSession ->
                    novaController.onServerAcceptSocketConnection(
                        info.peer, subscribeSession, phoneId
                    )
                }
            }
        }
    }

    fun onServerAcceptExternalConnection(phoneId: String) = acceptConnection(phoneId)

    fun updateConfigs() {
        val header = NovaMessageHeader(phoneInfo)
        val headerBytes = convertMessageHeaderToJsonBytes(header)
        client.updateConfig(headerBytes)
        server.updateConfig(headerBytes)
    }

    fun close() {
        CoroutineScope(Dispatchers.IO).launch {
            handlerThread.quitSafely()
            client.close()
            server.close()
            session.close()
        }
    }

    fun getMastersIds() = client.getMastersIds()
    fun getAvailableMastersIds() = client.getAvailableMastersIds()
    fun getInfoByID(serverId: String): NeighbourInfo? = client.getInfoByPhoneId(serverId)
    fun getPeerIdByClusterIdAndPhoneStatus(clusterId: String, phoneStatus: PhoneStatus): String? = client.getPeerIdByClusterIdAndPhoneStatus(clusterId, phoneStatus)
    fun getNameByID(phoneId: String): String? = client.getInfoByPhoneId(phoneId)?.phoneName
    fun getMasterPhoneName() = client.getInfoByPhoneId(phoneInfo.masterId)?.phoneName ?: ""

    fun onConnectionRequest(peerHandle: PeerHandle, senderId: String) =
        handleConnectionRequest(peerHandle, senderId)


    suspend fun onServerRejectConnection(serverId: String, messageContent: String) =
        novaController.onServerRejectSocketConnection(serverId, messageContent)


    fun createMasterConnectionWithClientServer(
        peerHandle: PeerHandle,
        phoneId: String,
        masterId: String,
        status: PhoneStatus
    ) {
        if (phoneInfo.phoneID == masterId) return
        Log.w(
            TAG,
            "Cluster: Connection request creation to $status and clusterId ${novaController.getAwareInfoByID(
                masterId
            )?.phoneName}"
        )
        isClusterConnectionCreated.set(true)
        requestNewClientConnection(peerHandle, phoneId)

    }

    fun isMasterClusterConnectionCreated() = isClusterConnectionCreated.get()

    fun setMasterToClusterConnectionCreationStatus(ifConnectionCreated: Boolean) {
        isClusterConnectionCreated.set(ifConnectionCreated)
    }

    fun getPhoneStatusForClusterConnection() = phoneStatusForClusterConnection

    fun setPhoneStatusForClusterConnection(status: PhoneStatus) {
        phoneStatusForClusterConnection = status
    }

    fun setMasterToClusterReconnectionId(clusterId: String) {
        clusterIdToReconnect.replace(0, clusterIdToReconnect.capacity(), clusterId)
        Log.w(
            TAG,
            "Reconnect ClusterId set to: '${novaController.getAwareInfoByID(clusterIdToReconnect.toString())?.phoneName}'"
        )
    }

    fun isClusterReconnecting(clusterId: String): Boolean {
        val reconnectClusterId = clusterIdToReconnect.toString()
        if (reconnectClusterId.isEmpty()) return true
        return reconnectClusterId == clusterId
    }

    fun onNewServerDiscovered(phoneId: String) {
        novaController.addPhoneIdToServersQueue(phoneId)
    }

    fun isPeerMasterPhone(peerId: String) = phoneInfo.isMaster && novaController.isClientConnected(peerId)
    fun getFilteredMap(phoneStatus: PhoneStatus) = client.getFilteredMap(phoneStatus)
    fun getMacIdMap() = client.getMacIdMap()
    fun getMacIdMap(clusterId: String) = client.getMacIdMap(clusterId)

    fun removePhoneInfo(phoneId: String) = client.removePhone(phoneId)
    fun isPeerStillAvailable(phoneId: String) = client.isPeerStillAvailable(phoneId)
    fun setPeerStatusToRttInProgress(phoneId: String) =  client.setPeerStatusToRttInProgress(phoneId)

    fun checkIfClusterConnected(clusterId: String): Boolean =
        novaController.checkIfClusterConnected(clusterId)

    fun logClusterConnectionMap() {
        novaController.logClusterConnectionMap()
    }

//    fun setDeviceCountView(deviceCount: Int) = novaController.setDeviceCountView(deviceCount)
}