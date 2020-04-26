package com.samsung.sel.cqe.nova.main.controller

import android.net.ConnectivityManager
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.rtt.WifiRttManager
import android.util.Log
import android.widget.Toast
import com.samsung.sel.cqe.nova.main.NovaActivity
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.AwareService
import com.samsung.sel.cqe.nova.main.aware.PhoneStatus
import com.samsung.sel.cqe.nova.main.socket.ClientSocketService
import com.samsung.sel.cqe.nova.main.socket.ServerSocketService
import com.samsung.sel.cqe.nova.main.sync.NovaSync
import com.samsung.sel.cqe.nova.main.utils.MessageType
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import java.time.LocalDateTime
import kotlin.random.Random

class NovaController(
    private val view: NovaActivity,
    private val phoneID: String,
    val phoneName: String
) {
    private val awareService: AwareService
    private val novaSync: NovaSync
    internal val clientSocketService: ClientSocketService
    private val serverSocketService: ServerSocketService

    val connectivityManager: ConnectivityManager by lazy { view.getConnectivityManager() }
    val wifiRttManager: WifiRttManager by lazy { view.getWifiRttManager() }
    val wifiAwareManager: WifiAwareManager by lazy { view.getWifiAwareManager() }
    private val phoneInfo = createPhoneInfo()


    init {
        awareService = AwareService(this, view, phoneInfo)
        novaSync = NovaSync(this, phoneInfo)
        clientSocketService = ClientSocketService(phoneInfo, view, this)
        serverSocketService = ServerSocketService(view, this)
        view.setStatusOnTextView(phoneInfo.status, phoneInfo.masterId)
        view.initPhoneNameView("$phoneName RANK : ${phoneInfo.masterRank}")
    }

    fun onServerAcceptSocketConnection(
        peerHandle: PeerHandle,
        subscribeSession: SubscribeDiscoverySession,
        serverPhoneId: String
    ) {
        clientSocketService.requestSocketConnectionToServer(
            peerHandle,
            serverPhoneId,
            subscribeSession
        )
    }

    fun onServerRejectSocketConnection(phoneID: String) {
        if (phoneInfo.status == PhoneStatus.UNDECIDED) {
            if (phoneID == awareService.currPhoneId) {
                awareService.serverResponseJob?.cancel()
                clientSocketService.chooseNewServer()
            }
        }
    }

    fun onClientSocketRequest(
        peerHandle: PeerHandle, clientId: String,
        publishDiscoverySession: PublishDiscoverySession
    ) {
        Log.w(TAG, "peer : $peerHandle")
        if (serverSocketService.isAcceptClient()) {
            Log.w(TAG, "accepting $clientId")
            serverSocketService.checkQueueAndCreateConnectionWithClient(
                peerHandle, clientId, publishDiscoverySession,
                connectivityManager
            )
            updateAcceptConnections()
            Log.w(TAG, "AcceptConnection: ${phoneInfo.acceptsConnection}")
            awareService.updateConfigs()
        } else {
            Log.w(TAG, "rejecting $clientId ")
            view.showOnUiThread("rejecting $clientId", Toast.LENGTH_SHORT)
            awareService.sendSubscribeMessageToClient(clientId, MessageType.REJECT_CONNECTION)
        }
    }


    fun updateClock(stopWatchStartTime: Long) {
        val serverTime = System.currentTimeMillis() - stopWatchStartTime
        view.setTimeOnTextView("$serverTime")
    }

    fun close() {
        awareService.close()
        clientSocketService.close()
        serverSocketService.close()
        novaSync.close()
    }

    fun onStatusChanged() {
        awareService.updateConfigs()
        val masterName = awareService.getMasterPhoneName()
        view.setStatusOnTextView(phoneInfo.status, masterName)
    }

    fun becomeMaster() {
        phoneInfo.status = PhoneStatus.MASTER
        clientSocketService.clearServersQueue()
        phoneInfo.isMaster = true
        phoneInfo.acceptsConnection = true
        onStatusChanged()
    }

    private fun createPhoneInfo() = PhoneInfo(
        phoneID = phoneID, phoneName = phoneName,
        masterRank = Random.nextInt(0, Integer.MAX_VALUE)
    )

    fun changeStatus(status: PhoneStatus) {
        phoneInfo.status = status
        onStatusChanged()
    }

    fun syncTimeWithMaster() {
        val serverSocket = clientSocketService.getFirstMapValue()
        novaSync.sync(serverSocket)
    }

    fun adjustTimeToMaster(serverMessage: String){
        novaSync.adjustStartTimeUsingMsg(serverMessage)
    }


    fun changeMaster(serverId: String) {
        phoneInfo.masterId = serverId
        phoneInfo.isMaster = false
        changeStatus(PhoneStatus.CLIENT)
    }

    fun onMasterLost() {
        phoneInfo.masterId = ""
        changeStatus(PhoneStatus.UNDECIDED)
    }

    fun updateAcceptConnections() {
        phoneInfo.acceptsConnection = serverSocketService.isAcceptClient()
    }

    fun requestNextConnection() {
        if (clientSocketService.isQueueNotEmpty()) {
            clientSocketService.chooseNewServer()
        } else {
            awareService.chooseMasterFromPeers()
        }
    }

    fun requestNewClientConnection(peerHandle: PeerHandle, serverId: String){
        awareService.requestNewClientConnection(peerHandle, serverId)
    }

    fun startAnalysis() {
        awareService.runAnalyze()
    }

    fun addPhoneIdToServersQueue(phoneId: String){
        clientSocketService.addToServersQueue(phoneId)
    }

    fun removePhoneIdFromServersQueue(phoneId: String){
        clientSocketService.removeFromServersQueue(phoneId)
    }

    fun getAwareInfoByID(phoneID: String) = awareService.getInfoByID(phoneID)
    fun removeServerByID(phoneID: String) = awareService.removeServer(phoneID)
    fun getMastersIds() = awareService.getMastersIds()
    fun sendSubscribeMessageToClient(type: MessageType, clientId: String) =
        awareService.sendSubscribeMessageToClient(clientId, type)

    fun onSyncRequest(content: String, senderId: String) {
        Log.w(TAG, "${System.currentTimeMillis()} obtain sync response:${LocalDateTime.now()}")
        val msg = NovaMessage(
            MessageType.SYNC_CLOCK,
            "$content${System.currentTimeMillis() - novaSync.stopWatchStartTime}", phoneInfo
        )
        serverSocketService.sendMessageToId(senderId, msg)
    }
}