package com.samsung.sel.cqe.nova.main.rtt

import android.annotation.SuppressLint
import android.net.MacAddress
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.samsung.sel.cqe.nova.main.COMPUTER_COMMUNICATION_TAG
import com.samsung.sel.cqe.nova.main.NovaFragment
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.controller.NovaController
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.utils.DistanceElement
import com.samsung.sel.cqe.nova.main.utils.DistanceInfo
import com.samsung.sel.cqe.nova.main.utils.convertDistanceInfoToJsonString
import java.lang.reflect.Type
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min


class RttMeasurer(
    private val view: NovaFragment,
    private val wifiRttManager: WifiRttManager,
    private val phoneInfo: PhoneInfo,
    private val novaController: NovaController
) {

    private companion object{
        private const val LIMIT_OF_FAILED_REQUEST = 1
        private const val RTT_TAG = "JSON_RTT"
    }

    @Volatile private var startTime = 1L
    private val isAlarm = AtomicBoolean()

    @Volatile private var lastRttResultsJson = ""
    private val rttResultsMap = ConcurrentHashMap<String, DistanceInfo>()
    private val rttMapType: Type = object : TypeToken<Map<String?, DistanceInfo?>?>() {}.type
    private val numberOfSucceedRangingRequests: AtomicInteger = AtomicInteger(0)
    private val numberOfFailedRangingRequests: AtomicInteger = AtomicInteger(0)
    private val rttRequestQueue = ConcurrentLinkedQueue<RangingRequestElement>()

    private val executor = Executors.newCachedThreadPool()
    @Volatile private var numberOfRequests: AtomicInteger = AtomicInteger(0)

    internal fun getRttResultsMap(): String = Gson().toJson(rttResultsMap)

    @Synchronized
    internal fun addToRttResultsMap(distanceInfo: DistanceInfo) {
        rttResultsMap[distanceInfo.phoneId] = distanceInfo
        Log.w(COMPUTER_COMMUNICATION_TAG, "Added to rttResultsMap: $distanceInfo")
        Log.w(COMPUTER_COMMUNICATION_TAG, "$rttResultsMap")
        novaController.updatePulseTable(rttResultsMap)
        logAllDistances()
    }

    @Synchronized
    internal fun addToRttResultsMap(receivedRttJsonMap: String, senderId: String): DistanceInfo? {
        val rttMap: Map<String, DistanceInfo> = Gson().fromJson(receivedRttJsonMap, rttMapType)
        rttMap.filterKeys { it != phoneInfo.phoneID }.forEach { rttResultsMap[it.key] = it.value }
        Log.w(COMPUTER_COMMUNICATION_TAG, "RttResultsMap: $rttResultsMap")
        logAllDistances()
        novaController.updatePulseTable(rttResultsMap)
        return rttResultsMap[senderId]
    }

    @Synchronized
    private fun addVirtualDevicesMeasures(info: DistanceInfo) = novaController.getVirtualDevices(info).forEach { rttResultsMap[it.value.phoneId] = it.value }

    private fun logDistance(distanceInfo: DistanceInfo){
        val jsonRtt = convertDistanceInfoToJsonString(distanceInfo)
        Log.w(RTT_TAG, jsonRtt)
    }

    private fun logAllDistances(){
        Log.w(RTT_TAG, "StartJson")
        rttResultsMap.forEach{ logDistance(it.value) }
    }

    fun setAlarm(value: Boolean): DistanceInfo? {
        synchronized(this) {
            isAlarm.set(value)
            val distanceInfo = rttResultsMap[phoneInfo.phoneID]
            if (distanceInfo != null) {
                val alarmDistanceInfo = DistanceInfo(
                    distanceInfo,
                    isAlarm.get(),
                    novaController.getPulse(),
                    novaController.getPulseOx()
                )
                addToRttResultsMap(alarmDistanceInfo)
                return alarmDistanceInfo
            }
            return null
        }
    }

    internal fun getDistanceInfoByPhoneId(phoneId: String) = rttResultsMap[phoneId]

    fun measureDistanceToAll(macMap: HashMap<String, String>) {
        startTime = System.currentTimeMillis()
        numberOfSucceedRangingRequests.set(0)
        numberOfFailedRangingRequests.set(0)
        Log.w(TAG, "Sending to ${macMap.size}")

        rttRequestQueue.clear()
        val distances = HashMap<String, ArrayList<Int>>()
        val numberOfProcessingRequests = prepareAndExecuteRttRequests(macMap, distances)
        numberOfRequests.set(numberOfProcessingRequests)
    }

    private fun prepareAndExecuteRttRequests(macMap: HashMap<String, String>, distances: HashMap<String, ArrayList<Int>>): Int {
        val reqList = createRttRequestsForPhonesFromMACs(macMap.keys)
        reqList.forEach { rttRequestQueue.add(RangingRequestElement(it, macMap)) }
        executeNextRttRequest(distances)
        return reqList.size
    }

    private fun executeNextRttRequest(distances: HashMap<String, ArrayList<Int>>){
        rttRequestQueue.poll()?.let { reqEl -> startRttRanging(reqEl.request, wifiRttManager, distances, reqEl.macMap)
            Log.w(COMPUTER_COMMUNICATION_TAG, "requested")}
    }


    @SuppressLint("MissingPermission")
    private fun startRttRanging(
        request: RangingRequest,
        manager: WifiRttManager,
        distances: HashMap<String, ArrayList<Int>>,
        macMap: HashMap<String, String>
    ) {

        manager.startRanging(request, executor,
            object : RangingResultCallback() {
                override fun onRangingResults(results: List<RangingResult>) {
                    numberOfSucceedRangingRequests.incrementAndGet()
                    Log.w(COMPUTER_COMMUNICATION_TAG, "Succeed request ${numberOfFailedRangingRequests.get()}")
                    processRangingResults(results, distances, macMap)
                }

                override fun onRangingFailure(code: Int) {
                    Log.w(COMPUTER_COMMUNICATION_TAG, "Failed request ${numberOfFailedRangingRequests.get()}")

                    numberOfFailedRangingRequests.incrementAndGet()
                    when {
                        ifRttMeasurementFinished() -> sendRttResultsToServerSocket(distances)
                        else -> {
                            rttRequestQueue.add(RangingRequestElement(request, macMap))
                            executeNextRttRequest(distances)
                        }
                    }

                }
            })
    }

    private fun processRangingResults(
        request: List<RangingResult>,
        distances: HashMap<String, ArrayList<Int>>,
        macMap: HashMap<String, String>
    ) {
        val failedRequestsMacMap = HashMap<String, String>()
        for (res in request) {
            val currentMacAddress = res.macAddress.toString()
            val currentPhoneName = macMap[currentMacAddress] ?: "unknown"
            distances.putIfAbsent(currentPhoneName, ArrayList())
            if (res.status == RangingResult.STATUS_SUCCESS) {
                Log.w(TAG, "$currentPhoneName ${res.distanceMm}mm, ${res.rssi} rssi")
                distances[currentPhoneName]?.add(res.distanceMm)
            } else {
                failedRequestsMacMap[currentMacAddress] = currentPhoneName
                Log.w(TAG, "status : ${res.status}")
            }
        }
        Log.w(TAG, "rtt length : ${distances.size}")
        if(failedRequestsMacMap.isNotEmpty()){
            numberOfFailedRangingRequests.incrementAndGet()
            numberOfSucceedRangingRequests.decrementAndGet()
        }

        when {
            ifRttMeasurementFinished() -> sendRttResultsToServerSocket(distances)
            failedRequestsMacMap.isNotEmpty() ->
                prepareAndExecuteRttRequests(failedRequestsMacMap, distances)
            else ->
                executeNextRttRequest(distances)
        }

    }

    private fun ifRttMeasurementFinished(): Boolean =
        numberOfSucceedRangingRequests.get() + numberOfFailedRangingRequests.get()  == numberOfRequests.get() +
                LIMIT_OF_FAILED_REQUEST * if(numberOfFailedRangingRequests.get() < numberOfRequests.get())
                numberOfFailedRangingRequests.get() else numberOfRequests.get()

    private fun sendRttResultsToServerSocket(distances: HashMap<String, ArrayList<Int>>) {
        val distanceList = ArrayList<DistanceElement>()
        distances.filter { it.value.isNotEmpty() }.forEach { (k, v) ->
            var average = v.stream().mapToInt { it / 2 }.average().asDouble
            if (average == 0.0) {
                average = 0.0
            } else {
                //view.appendToServerTextField("$k $average -------------------")
                average += (average * 20) / 10
            }
            val distanceElement = DistanceElement(k, average.toInt())
            //view.appendToServerTextField("$k ${average}mm")
            distanceList.add(distanceElement)
        }
        val info = DistanceInfo(phoneInfo.phoneID, phoneInfo.phoneName, distanceList, isAlarm.get(), novaController.getPulse(), novaController.getPulseOx())
        novaController.setDistanceInfoView(info)
        //rttResultsMap[info.phoneId] = info
        lastRttResultsJson = convertDistanceInfoToJsonString(info)
        addVirtualDevicesMeasures(info)

        Log.w(COMPUTER_COMMUNICATION_TAG, "RTT Measure finished ${rttResultsMap[info.phoneId]}")
        Log.w(COMPUTER_COMMUNICATION_TAG, "RTT Time  ${(System.currentTimeMillis() - startTime)}")

        novaController.onRttMeasureFinished()
    }

    private fun createRttRequestsForPhonesFromMACs(macs: Set<String>): List<RangingRequest> {
        val reqList = ArrayList<RangingRequest>()
        val filteredSet = macs.filterNot { it == "" }
        var currentState = 0
        var max = min(RangingRequest.getMaxPeers(), macs.size - currentState)
        while (currentState != macs.size) {
            Log.w(TAG, "$currentState $max")
            val req = RangingRequest.Builder().run {
                filteredSet.subList(currentState, currentState + max).forEach { mac ->
                    Log.w(
                        COMPUTER_COMMUNICATION_TAG,
                        "processing mac ${MacAddress.fromString(mac)} || $mac"
                    )
                    addWifiAwarePeer(MacAddress.fromString(mac))
                }
                build()
            }
            reqList.add(req)
            currentState += max
            max = min(RangingRequest.getMaxPeers(), macs.size - currentState)
        }
        return reqList
    }

    fun close() {
       executor.shutdownNow()
    }

    private data class RangingRequestElement(val request: RangingRequest, val macMap: HashMap<String, String>)
}