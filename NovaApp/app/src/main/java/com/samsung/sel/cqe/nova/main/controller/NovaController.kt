package com.samsung.sel.cqe.nova.main.controller

import android.net.ConnectivityManager
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.rtt.WifiRttManager
import android.util.Log
import android.widget.Toast
import com.samsung.sel.cqe.nova.main.NovaFragment
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.TableFragment
import com.samsung.sel.cqe.nova.main.aware.AwareService
import com.samsung.sel.cqe.nova.main.map.MapFragment
import com.samsung.sel.cqe.nova.main.pulse.PulseMeasurer
import com.samsung.sel.cqe.nova.main.rtt.RttMeasurer
import com.samsung.sel.cqe.nova.main.rtt.VirtualDevices
import com.samsung.sel.cqe.nova.main.socket.NovaSocketClient
import com.samsung.sel.cqe.nova.main.socket.SocketService
import com.samsung.sel.cqe.nova.main.structure.ActiveIndependentCluster
import com.samsung.sel.cqe.nova.main.structure.ClusterInfo
import com.samsung.sel.cqe.nova.main.structure.ClusterState
import com.samsung.sel.cqe.nova.main.sync.NovaSync
import com.samsung.sel.cqe.nova.main.utils.DistanceInfo
import com.samsung.sel.cqe.nova.main.utils.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class NovaController(
    private val view: NovaFragment,
    private val phoneID: String,
    val phoneName: String,
    private val tableFragment: TableFragment,
    val mapFragment: MapFragment
) {
    private val awareService: AwareService
    private val novaSync: NovaSync
    private val socketService: SocketService
    private val phoneInfo = createPhoneInfo()
    private val rttMeasurer: RttMeasurer
    val pulseMeasurer: PulseMeasurer
    private val virtualDevices: VirtualDevices by lazy { VirtualDevices(PulseMeasurer(null)) }
    private val wifiRttManager: WifiRttManager by lazy { view.getWifiRttManager() }

    val connectivityManager: ConnectivityManager by lazy { view.getConnectivityManager() }
    val wifiAwareManager: WifiAwareManager by lazy { view.getWifiAwareManager() }

    private val activeIndependentCluster: ClusterState by lazy {
        ActiveIndependentCluster(
            this,
            phoneInfo
        )
    }

    init {
        awareService = AwareService(this, view, phoneInfo)
        novaSync = NovaSync(this, phoneInfo)
        rttMeasurer = RttMeasurer(view, wifiRttManager, phoneInfo, this)
        pulseMeasurer = PulseMeasurer(this)
        socketService = SocketService(phoneInfo, this, view)
        view.setStatusOnTextView(phoneInfo.status, phoneInfo.masterId)
        view.initPhoneNameView("$phoneName")

        phoneInfo.assignClusterInfo(activeIndependentCluster)
    }

    fun onServerAcceptSocketConnection(
        peerHandle: PeerHandle,
        subscribeSession: SubscribeDiscoverySession,
        serverPhoneId: String
    ) {
        socketService.requestClientSocketConnectionToServer(
            peerHandle,
            serverPhoneId,
            subscribeSession
        )
    }

    suspend fun onServerRejectSocketConnection(
        phoneID: String,
        messageContent: String
    ) {
        val serverPhoneStatus: PhoneStatus? =
            PhoneStatus.values().firstOrNull { it.name == messageContent }
        Log.w(TAG, "Reject connection with serverStatus $serverPhoneStatus")
        if (phoneInfo.status == PhoneStatus.UNDECIDED && serverPhoneStatus == PhoneStatus.MASTER) {
            if (awareService.requestedServerIds.contains(phoneID)) {
                awareService.serverResponseJobs[phoneID]?.cancel()
                chooseNewServer()
            }
        } else if (phoneInfo.status == PhoneStatus.UNDECIDED && serverPhoneStatus == PhoneStatus.RTT_IN_PROGRESS) {
            if (awareService.requestedServerIds.contains(phoneID)) {
                awareService.serverResponseJobs[phoneID]?.cancel()
                masterConnectionLost()
            }
        } else if (phoneInfo.status == PhoneStatus.RTT_FINISHED) {
            if (awareService.requestedServerIds.contains(phoneID)) {
                awareService.serverResponseJobs[phoneID]?.cancel()
                reconnectToMaster(phoneID)
            }
        }
    }

    fun onClientSocketRequest(
        peerHandle: PeerHandle, clientId: String,
        publishDiscoverySession: PublishDiscoverySession
    ) {
        Log.w(TAG, "peer : $peerHandle")
        if (socketService.isServerAcceptClient() && phoneInfo.acceptsConnection) {
            Log.w(TAG, "accepting $clientId")
            socketService.checkQueueAndCreateConnectionWithClient(
                peerHandle, clientId, publishDiscoverySession,
                connectivityManager
            )
            updateAcceptConnections()
            Log.w(TAG, "AcceptConnection: ${phoneInfo.acceptsConnection}")
        } else if (phoneInfo.status != PhoneStatus.CLIENT_SERVER) {
            Log.w(TAG, "rejecting $clientId ")
            val messageContent = phoneInfo.status.toString()
            awareService.sendSubscribeMessageToClient(
                clientId,
                MessageType.REJECT_CONNECTION,
                messageContent
            )
        }
    }


    fun updateClock(stopWatchStartTime: Long) {
        val serverTime = System.currentTimeMillis() - stopWatchStartTime
        //view.setTimeOnTextView("$serverTime")
    }

    fun close() {
        Log.w(TAG, "on CLOSE")
        changeStatus(PhoneStatus.CLOSING)

        CoroutineScope(Dispatchers.IO).launch {
            delay(20_000)
            awareService.close()
            socketService.close()
            novaSync.close()
            rttMeasurer.close()
            pulseMeasurer.close()
            phoneInfo.clusterInfo.state.close()
            Log.w(TAG, "FINISH on CLOSE")
        }
    }

    private fun onStatusChanged() {
        updateConfigs()
        val masterName = awareService.getMasterPhoneName()
        view.setStatusOnTextView(phoneInfo.status, masterName)
    }

    fun becomeMaster() {
        Log.w(TAG, "BECOMING MASTER")
        phoneInfo.status = PhoneStatus.MASTER
        socketService.clearServersQueue()
        phoneInfo.isMaster = true
        phoneInfo.clusterInfo.clusterId = phoneID
        phoneInfo.masterId = phoneID
        phoneInfo.acceptsConnection = true
        setMasterToClusterConnectionStatus(false)
        onStatusChanged()
        startRttMeasureTimer()
    }

    fun startRttMeasureTimer() = socketService.startRttMeasureTimer()

    fun setMasterToClusterConnectionStatus(ifConnectionCreated: Boolean) {
        awareService.setMasterToClusterConnectionCreationStatus(ifConnectionCreated)
    }

    private fun createPhoneInfo() = PhoneInfo(
        phoneID = phoneID, phoneName = phoneName,
        masterRank = Random.nextInt(0, Integer.MAX_VALUE - 1)
        //masterRank = Integer.MAX_VALUE
    )

    fun changeStatus(status: PhoneStatus) {
        Log.w(TAG, "Changing status from ${phoneInfo.status} to $status")
        when (status) {
            PhoneStatus.CLIENT_IN ->
                phoneInfo.status = status
            PhoneStatus.CLIENT_SERVER -> {
                phoneInfo.status = status
                updateAcceptConnections()
            }
            else -> phoneInfo.status = status
        }
        onStatusChanged()
    }

    fun logAddNewClusterConnection(clientId: String, clusterId: String) {
        Log.w(
            TAG,
            "Adding New Cluster [${awareService.getNameByID(clusterId)}] Connection from ${awareService.getNameByID(
                clientId
            )}"
        )
    }

    fun logRemoveClusterConnection(clientId: String) {
        Log.w(TAG, "Removing Cluster Connection from ${awareService.getNameByID(clientId)}")
    }

    fun changeClusterNeighbours(sentClusterInfo: ClusterInfo) {
        phoneInfo.clusterInfo.apply {
            closeNeighbourId = sentClusterInfo.clusterId
            fartherNeighbourId = sentClusterInfo.closeNeighbourId
        }
        onStatusChanged()
    }

    fun updateClientClusterInfo(masterClusterInfo: ClusterInfo) {
        phoneInfo.clusterInfo.apply {
            clusterId = masterClusterInfo.clusterId
            closeNeighbourId = masterClusterInfo.closeNeighbourId
            fartherNeighbourId = masterClusterInfo.fartherNeighbourId
        }
        onStatusChanged()
    }

    fun resetClusterInfo() {
        phoneInfo.clusterInfo.apply {
            closeNeighbourId = ""
            fartherNeighbourId = ""
        }
        onStatusChanged()
    }

    fun resetClientClusterInfo() {
        phoneInfo.clusterInfo.apply {
            clusterId = ""
            closeNeighbourId = ""
            fartherNeighbourId = ""
        }
        onStatusChanged()
    }

    fun syncTimeWithMaster(firstSocketToServer: NovaSocketClient) =
        novaSync.sync(firstSocketToServer)

    fun adjustTimeToMaster(serverMessage: String) = novaSync.adjustStartTimeUsingMsg(serverMessage)

    fun changeMaster(serverId: String) {
        phoneInfo.masterId = serverId
        phoneInfo.clusterInfo.clusterId = serverId
        phoneInfo.isMaster = false
        changeStatus(PhoneStatus.CLIENT)
    }


    fun onMasterLost() {
        phoneInfo.masterId = ""
        phoneInfo.clusterInfo.clusterId = ""
        changeStatus(PhoneStatus.UNDECIDED)
    }

    fun updateAcceptConnections() {
        phoneInfo.acceptsConnection =
            if (phoneInfo.status == PhoneStatus.CLIENT_SERVER || phoneInfo.status == PhoneStatus.CLIENT_SERVER_AWAITS_RECONNECT) socketService.isServerAcceptMaster()
            else socketService.isServerAcceptClient()
        Log.w(TAG, "AcceptConnection update = ${phoneInfo.acceptsConnection}")
        updateConfigs()
    }

    fun unableAcceptConnections() {
        phoneInfo.acceptsConnection = false
        updateConfigs()
    }

    private fun updateConfigs() {
        awareService.updateConfigs()
    }

    suspend fun requestNextConnection() {
        Log.w(TAG, "Delay in requestNextConnection")
        delay(500)
        if (socketService.isServersQueueNotEmpty()) {
            chooseNewServer()
        } else {
            awareService.chooseMasterFromPeers()
        }
    }

    suspend fun chooseNewServer() {
        socketService.chooseNewServer()
    }


    fun requestNewClientConnection(peerHandle: PeerHandle, serverId: String) {
        awareService.requestNewClientConnection(peerHandle, serverId)
    }

    suspend fun startAnalysis() {
        requestNextConnection()
    }

    fun reconnectToMaster(serverId: String) {
        val serverPeer = getAwareInfoByID(serverId)?.peer

        if (serverPeer != null) {
            requestNewClientConnection(serverPeer, serverId)
        } else {
            masterConnectionLost()
        }
    }

    fun addPhoneIdToServersQueue(phoneId: String) {
        socketService.addToServersQueue(phoneId)
    }

    fun removePhoneIdFromServersQueue(phoneId: String) {
        socketService.removeFromServersQueue(phoneId)
    }

    private fun connectToCI() {
        try {
            val ciInfo = getFilteredPeersMap(PhoneStatus.CLIENT_IN).entries
                .first { it.value.masterId == phoneInfo.clusterInfo.fartherNeighbourId }
            Log.w(TAG, "CI connection requested")
            awareService.requestNewClientConnection(ciInfo.value.peer, ciInfo.key)
        } catch (ex: NoSuchElementException) {
            changeStatus(PhoneStatus.CLIENT_OUT)
            Log.w(TAG, "Can't find CI by id")
        }
    }


    fun getAwareInfoByID(phoneId: String) = awareService.getInfoByID(phoneId)
    fun getPeerIdByClusterIdAndPhoneStatus(clusterId: String, phoneStatus: PhoneStatus) =
        awareService.getPeerIdByClusterIdAndPhoneStatus(clusterId, phoneStatus)

    fun getMastersIds() = awareService.getMastersIds()
    fun getAvailableMastersIds() = awareService.getAvailableMastersIds()
    fun getMacIdMap() = awareService.getMacIdMap()
    fun getMacIdMapForCluster() = awareService.getMacIdMap(phoneInfo.masterId)
    fun getFilteredPeersMap(phoneStatus: PhoneStatus) = awareService.getFilteredMap(phoneStatus)
    fun sendSubscribeMessageToClient(type: MessageType, clientId: String) =
        awareService.sendSubscribeMessageToClient(clientId, type)

    fun setPeerStatusToRttInProgress(phoneId: String) =
        awareService.setPeerStatusToRttInProgress(phoneId)

    fun getSyncStopWatchStartTime() = novaSync.stopWatchStartTime

    fun getClusterIds(): ArrayList<String> {
        val clusterInfo = ArrayList<String>()
        if (phoneInfo.clusterInfo.clusterId == phoneID)
            clusterInfo.add(phoneName)
        else
            clusterInfo.add(awareService.getNameByID(phoneInfo.clusterInfo.clusterId) ?: "")

        clusterInfo.add(awareService.getNameByID(phoneInfo.clusterInfo.closeNeighbourId) ?: "")
        clusterInfo.add(awareService.getNameByID(phoneInfo.clusterInfo.fartherNeighbourId) ?: "")

        return clusterInfo
    }

    fun removePhoneInfo(phoneId: String) = awareService.removePhoneInfo(phoneId)
    fun updateStatus(status: PhoneStatus) {
        if (phoneInfo.status == PhoneStatus.CLIENT_OUT || phoneInfo.status == PhoneStatus.CLIENT_IN || phoneInfo.status == PhoneStatus.CLIENT_SERVER) socketService.disconnectSocketsWithoutMasterSocket()
        if (status == PhoneStatus.CLIENT_OUT) connectToCI()
        else changeStatus(status)
    }

    fun checkIfClusterConnected(clusterId: String): Boolean =
        socketService.checkIfClusterConnected(clusterId)

    fun setPhoneStatusForClusterConnection(status: PhoneStatus) =
        awareService.setPhoneStatusForClusterConnection(status)

    fun setMasterToClusterReconnectionId(clusterId: String = "") {
        awareService.setMasterToClusterReconnectionId(clusterId)
    }

    suspend fun blockUntilServerIsAvailable(peerId: String) =
        socketService.blockUntilServerIsAvailable(peerId)

    fun isPeerStillAvailable(phoneId: String) = awareService.isPeerStillAvailable(phoneId)

    fun logClusterConnectionMap() = socketService.logClusterConnectionMap()

    fun getRttResultsMap() = rttMeasurer.getRttResultsMap()
    fun addToRttResultsMap(distanceInfo: DistanceInfo) =
        rttMeasurer.addToRttResultsMap(distanceInfo)

    fun addToRttResultsMap(receivedRttJsonMap: String, senderId: String) =
        rttMeasurer.addToRttResultsMap(receivedRttJsonMap, senderId)

    fun getDistanceInfoByPhoneId(phoneId: String) = rttMeasurer.getDistanceInfoByPhoneId(phoneId)

    fun forwardRttRequest(): Int = socketService.forwardRttRequest()
    fun disconnectAllClients(): Set<String> = socketService.disconnectAllClients()
    fun disconnectAllServers(): Set<String> = socketService.disconnectAllServers()
    fun isClientConnected(clientId: String): Boolean = socketService.isClientConnected(clientId)
    fun resetServerSocketParameters() = socketService.resetServerSocketParameters()
    fun masterConnectionLost() = socketService.masterConnectionLost()

    internal fun measureRttDistanceToAll(macMap: HashMap<String, String>) {
        if (!phoneInfo.isMaster) resetClientClusterInfo()

        Log.w(TAG, "MacMap $macMap")
        if (macMap.isEmpty()) onRttMeasureFinished() else rttMeasurer.measureDistanceToAll(macMap)
    }

    fun onRttMeasureFinished() {
        resetServerSocketParameters()
        updateAcceptConnections()
        changeStatus(PhoneStatus.RTT_FINISHED)
        phoneInfo.clusterInfo.state.onRttMeasureFinished()
    }

    fun setMeasuringStatusOnDistanceInfoView() {
        val clusterPhoneNames = getMacIdMapForCluster().values.sorted()
        view.setInProgressDistanceInfoView(clusterPhoneNames)
    }

    fun setDistanceInfoView(distanceInfo: DistanceInfo) {
        val distanceText = StringBuffer()
        distanceInfo.distanceList.sortedBy { it.phoneName }
            .forEach { distanceText.append("${it.phoneName}: \t ${it.distance} mm\n\n") }

        view.setDistanceInfoView(distanceInfo.distanceList.sortedBy { it.phoneName })

    }

    fun setPulseInfoView(pulseValue: Int, pulseOxValue: Int) {
        view.setPulseInfoView(pulseValue, pulseOxValue)
    }

    fun startAlarm() {
        pulseMeasurer.startAlarm()
        val alarmDistanceInfo = rttMeasurer.setAlarm(true)
        alarmDistanceInfo?.let {
            socketService.broadcastRttInfo(alarmDistanceInfo)
        }
    }

    fun getPulse() = pulseMeasurer.getPulse()
    fun getPulseOx() = pulseMeasurer.getPulseOx()

    fun onAlarmDiscovered(sentDistanceInfo: DistanceInfo) {
        Log.w(
            "ALARM",
            "ALARM on ${sentDistanceInfo.phoneName} + ${sentDistanceInfo.pulse} + ${sentDistanceInfo.pulseOx}"
        )
        view.showOnUiThread("ALARM ${sentDistanceInfo.phoneName}", Toast.LENGTH_LONG)
    }

    fun updatePulseTable(rttAndPulse: ConcurrentHashMap<String, DistanceInfo>) {
        view.activity?.runOnUiThread {
            tableFragment.updateTable(rttAndPulse.values)
        }
        mapFragment.updateMap(rttAndPulse)
    }

    fun getVirtualDevices(actualDevicesMeasure: DistanceInfo?) =
        virtualDevices.getVirtualDevices(actualDevicesMeasure)

    fun generateNormalPulse() = pulseMeasurer.generateNormalPulse()
    fun generateNormalPulseOx() = pulseMeasurer.generateNormalPulseOx()
    fun generateAlarmPulse() = pulseMeasurer.generateAlarmPulse()
    fun generateAlarmPulseOx() = pulseMeasurer.generateAlarmPulseOx()
}