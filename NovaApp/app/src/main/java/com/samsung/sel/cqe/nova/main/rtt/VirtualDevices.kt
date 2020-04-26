package com.samsung.sel.cqe.nova.main.rtt

import com.samsung.sel.cqe.nova.main.controller.NovaController
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.utils.DistanceElement
import com.samsung.sel.cqe.nova.main.utils.DistanceInfo
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

class VirtualDevices(
    val nc: NovaController,
    val phoneInfo: PhoneInfo
) {

    private val mapGenerated = LinkedHashMap<String, DistanceInfo>()
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(3)

    init {
          executor.scheduleAtFixedRate({ (generateVirtualDevices()) }, 1, 10, TimeUnit.SECONDS)
    }

    fun generateVirtualDevices(){
        val generatedMap = getVirtualDevices()
        nc.updateVirtualDevicesMeasures(generatedMap)
    }

    private fun generateInitialList(actualDevicesMeasure: DistanceInfo?) {
        val currentMeasure =
            actualDevicesMeasure ?: nc.getCurrentDistanceInfo()

        mapGenerated.clear()
        for (i in 1..DEVICE_COUNT_LOCAL) {
            val info =
                DistanceInfo(
                    "${nc.phoneName} $i",
                    "${nc.phoneName} $i",
                    ArrayList(),
                    false,
                    nc.generateNormalPulse(),
                    nc.generateNormalPulseOx()
                )
            mapGenerated[info.phoneName] = info
        }

        currentMeasure?.let {
            mapGenerated[currentMeasure.phoneName] = currentMeasure
        }
        for (first in mapGenerated) {
            val distanceList = ArrayList<DistanceElement>()
            for (second in mapGenerated) {
                if (first.key != second.key) {
                    var distance = getDistanceToPhoneByNameOrDefault(second.value, first.key)
                    if (distance == null) {
                        distance = ThreadLocalRandom.current().nextInt(1000, 20_000)
                    }
                    distanceList.add(DistanceElement(second.key, distance))
                }
            }
            first.value.distanceList = distanceList
        }
    }

    private fun getDistanceToPhoneByNameOrDefault(distanceInfo: DistanceInfo, phoneName: String) =
        distanceInfo.distanceList.firstOrNull { it.phoneName == phoneName }?.distance


    private fun updateValues() {
        mapGenerated.forEach { (_, map) ->
            val list = ArrayList<DistanceElement>()
            map.distanceList.forEach { (key, value) ->
                val maxChange = (value * .05).toInt()
                val change = ThreadLocalRandom.current().nextInt(0, maxChange + 1)
                val action = ThreadLocalRandom.current().nextInt(0, 100)
                val distance =
                    if (action > 50) value + change else value - change
                list.add(DistanceElement(key, distance))
            }
            map.distanceList = list
        }
    }

    fun getVirtualDevices(actualDevicesMeasure: DistanceInfo? = null): LinkedHashMap<String, DistanceInfo> {
        generateInitialList(actualDevicesMeasure)
        updateValues()
        return mapGenerated
    }

    fun close() {
        executor.awaitTermination(1, TimeUnit.SECONDS)
        executor.shutdownNow()
    }

    companion object {
        const val DEVICE_COUNT_LOCAL = 15
    }
}