package com.samsung.sel.cqe.nova.main.structure

import android.util.Log
import com.samsung.sel.cqe.nova.main.COMPUTER_COMMUNICATION_TAG
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.controller.NovaController
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.controller.PhoneStatus
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ActiveIndependentCluster(
    private val novaController: NovaController,
    private val phoneInfo: PhoneInfo
) : ClusterState {

    private val disconnectClientsIds = CopyOnWriteArrayList<String>()
    private val isRttInProgress = AtomicBoolean(false)
    private var blockingCoroutine: Job? = null

    override fun initializeRttMeasure() {
        novaController.unableAcceptConnections()
        novaController.changeStatus(PhoneStatus.RTT_IN_PROGRESS)
        Log.w(COMPUTER_COMMUNICATION_TAG, "RTT INITIALIZE STARTED")
        novaController.setMeasuringStatusOnDistanceInfoView()

        isRttInProgress.set(true)
        novaController.forwardRttRequest()
        measureRtt()
    }

    override fun onRttRequest() {
        Log.w(COMPUTER_COMMUNICATION_TAG, "ON RTT REQUEST")
        novaController.setMeasuringStatusOnDistanceInfoView()
        isRttInProgress.set(true)
        blockingCoroutine = CoroutineScope(Dispatchers.Default).launch {
            when (phoneInfo.status) {
                PhoneStatus.CLIENT_SERVER -> {
                    blockUntilMasterFinishedRttMeasure()
                    measureRtt()
                }
                PhoneStatus.CLIENT_OUT -> {
                    blockUntilPeerFinishedRttMeasure(PhoneStatus.CLIENT_SERVER)
                    measureRtt()
                }
                PhoneStatus.CLIENT_IN -> {
                    blockUntilPeerFinishedRttMeasure(PhoneStatus.CLIENT_OUT)
                    measureRtt()
                }
            }
        }
    }

    private fun measureRtt() {
        Log.w(COMPUTER_COMMUNICATION_TAG, "RTT MEASURE STARTED")
        novaController.unableAcceptConnections()

        val macMap = novaController.getMacIdMapForCluster()

        novaController.disconnectAllServers()

        disconnectClientsIds.clear()
        val clientsIdsSet = novaController.disconnectAllClients()
        disconnectClientsIds.addAll(clientsIdsSet)
        Log.w(COMPUTER_COMMUNICATION_TAG, "Disconnect Clients Set: $disconnectClientsIds")

        CoroutineScope(Dispatchers.Default).launch {
            delay(300)
            novaController.measureRttDistanceToAll(macMap)
        }
    }

    private suspend fun blockUntilFinishedRttMeasure(phoneId: String) {
        withContext(Dispatchers.IO) {
            Log.w(
                TAG,
                "Blocking Peer: ${novaController.getAwareInfoByID(phoneId)?.status} && " +
                        "${novaController.isPeerStillAvailable(phoneId)}"
            )
            while (novaController.getAwareInfoByID(phoneId)?.status != PhoneStatus.RTT_FINISHED && novaController.isPeerStillAvailable(
                    phoneId
                )
            ) {
                Log.w(
                    TAG,
                    "Blocking Peer: ${novaController.getAwareInfoByID(phoneId)?.status} "
                )
                Log.w(
                    TAG, "Blocking Peer, awaiting for: " +
                            "${novaController.getAwareInfoByID(phoneId)?.phoneName} "
                )
                delay(500)
            }
        }
    }

    private suspend fun blockUntilPeerFinishedRttMeasure(phoneStatus: PhoneStatus) {
        val peerId =
            novaController.getPeerIdByClusterIdAndPhoneStatus(phoneInfo.masterId, phoneStatus)
        peerId?.let { blockUntilFinishedRttMeasure(peerId) }
    }

    private suspend fun blockUntilMasterFinishedRttMeasure() =
        blockUntilFinishedRttMeasure(phoneInfo.masterId)

    override fun onRttMeasureFinished() {
        if (!phoneInfo.isMaster) {
            novaController.reconnectToMaster(phoneInfo.masterId)
        } else {
            blockingCoroutine = CoroutineScope(Dispatchers.Default).launch {
                blockUntilClientsReconnect()
                novaController.startRttMeasureTimer()
                novaController.changeStatus(PhoneStatus.MASTER)
            }
        }

        isRttInProgress.set(false)
    }

    private suspend fun blockUntilClientsReconnect() {
        withContext(Dispatchers.IO) {
            while (disconnectClientsIds.isNotEmpty()) {
                Log.w(
                    COMPUTER_COMMUNICATION_TAG,
                    "Non available Master, awaiting ${disconnectClientsIds.first()}"
                )
                blockUntilClientIsConnected(disconnectClientsIds.first())
            }
        }
    }

    private suspend fun blockUntilClientIsConnected(clientId: String) {
        withContext(Dispatchers.IO) {
            Log.w(
                TAG, "Blocking Client: ${novaController.getAwareInfoByID(clientId)?.phoneName}"
            )
            while (!novaController.isClientConnected(clientId) && novaController.isPeerStillAvailable(
                    clientId
                )
            ) {
                Log.w(
                    TAG, "Blocking Client: ${novaController.isClientConnected(clientId)} && " +
                            "${novaController.isPeerStillAvailable(clientId)}"
                )
                Log.w(
                    TAG, "Blocking Client, awaiting for: " +
                            "${novaController.getAwareInfoByID(clientId)?.phoneName}"
                )
                delay(500)
            }
            disconnectClientsIds.remove(clientId)
        }
    }

    override fun onServerLost() {
        if (!isRttInProgress.get()) onRttRequest()
    }

    override fun close(){
        blockingCoroutine?.cancel()
    }

}