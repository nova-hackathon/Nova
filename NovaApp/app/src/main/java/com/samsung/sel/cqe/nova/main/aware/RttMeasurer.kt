package com.samsung.sel.cqe.nova.main.aware

import android.annotation.SuppressLint
import android.net.MacAddress
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.util.Log
import android.widget.Toast
import com.samsung.sel.cqe.nova.main.COMPUTER_COMMUNICATION_TAG
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.utils.*
import java.util.concurrent.Executors
import kotlin.math.min

class RttMeasurer(
    private val awareService: AwareService, private val wifiRttManager: WifiRttManager,
    private val phoneInfo: PhoneInfo
) {

    private var distancesMeasured: Int = 0
    fun measureDistanceToAll(macMap: HashMap<String, String>) {
        if (macMap.isEmpty()) return
        distancesMeasured = 0
        awareService.view.showOnUiThread("Sending to ${macMap.size}", Toast.LENGTH_LONG)
        Log.w(TAG, "Sending to ${macMap.size}")
        val reqList = createRttRequestsForPhonesFromMACs(macMap.keys)
        val distances = HashMap<String, ArrayList<Int>>()
        for (i in 0 until NUMBER_OF_REQUEST) {
            reqList.forEach { req -> requestRtt(req, wifiRttManager, distances, macMap) }
        }
        Log.w(TAG, "requested")
    }

    @SuppressLint("MissingPermission")
    private fun requestRtt(
        request: RangingRequest, manager: WifiRttManager,
        distances: HashMap<String, ArrayList<Int>>, macMap: HashMap<String, String>
    ) {
        manager.startRanging(request, Executors.newCachedThreadPool(),
            object : RangingResultCallback() {
                override fun onRangingResults(results: List<RangingResult>) {
                    processRangingResults(results, distances, macMap)
                }

                override fun onRangingFailure(code: Int) {
                    awareService.view.showOnUiThread("Fail", Toast.LENGTH_LONG)
                    sendRttResultsToServerSocket(distances)
                }
            })
    }

    private fun processRangingResults(
        request: List<RangingResult>,
        distances: HashMap<String, ArrayList<Int>>, macMap: HashMap<String, String>
    ) {
        for (res in request) {
            val currentPhoneName = macMap[res.macAddress.toString()] ?: "unknown"
            distances.putIfAbsent(currentPhoneName, ArrayList())
            distancesMeasured++
            if (res.status == RangingResult.STATUS_SUCCESS) {
                Log.w(TAG, "$currentPhoneName ${res.distanceMm}mm, ${res.rssi} rssi")
                distances[currentPhoneName]?.add(res.distanceMm)
            } else {
                Log.w(TAG, "status : ${res.status}")
            }
        }
        Log.w(TAG, "rtt length : ${distances.size}")
        if (distancesMeasured == macMap.size * NUMBER_OF_REQUEST) {
            sendRttResultsToServerSocket(distances)
        }
    }

    private fun sendRttResultsToServerSocket(distances: HashMap<String, ArrayList<Int>>) {
        val distanceList = ArrayList<DistanceElement>()
        distances.forEach { (k, v) ->
            var average = v.stream().mapToInt { it / 2 }.average().asDouble
            if (average == 0.0) {
                average = -1.0
            } else {
                awareService.view.appendToServerTextField("$k $average -------------------")
                average += (average * 20) / 100
            }
            val distanceElement = DistanceElement(k, average.toInt())
            awareService.view.appendToServerTextField("$k ${average}mm")
            distanceList.add(distanceElement)
        }
        val info = DistanceInfo(phoneInfo.phoneID, phoneInfo.phoneName, distanceList)
        val distanceToJsonString = convertDistanceToJsonString(info)
        val message = ComputerMessage(
            ComputerMessageType.DISTANCE_INFO,
            distanceToJsonString
        )
        awareService.lastRttResultsJson = convertComputerMessageToJsonString(message)
        Log.w(COMPUTER_COMMUNICATION_TAG, awareService.lastRttResultsJson)
    }

    private fun createRttRequestsForPhonesFromMACs(macSet: Set<String>): List<RangingRequest> {
        val reqList = ArrayList<RangingRequest>()
        val filteredSet = macSet.filterNot { it == "" }
        var currentState = 0
        var max = min(RangingRequest.getMaxPeers(), macSet.size - currentState)
        while (currentState != macSet.size) {
            Log.w(TAG, "$currentState $max")
            val req = RangingRequest.Builder().run {
                filteredSet.subList(currentState, currentState + max).forEach { mac ->
                    addWifiAwarePeer(MacAddress.fromString(mac))
                }
                build()
            }
            reqList.add(req)
            currentState += max
            max = min(RangingRequest.getMaxPeers(), macSet.size - currentState)
        }
        return reqList
    }

    companion object {
        private const val NUMBER_OF_REQUEST = 5
    }
}