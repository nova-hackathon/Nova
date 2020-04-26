package com.samsung.sel.cqe.nova.main.rtt

import com.samsung.sel.cqe.nova.main.controller.NovaController
import com.samsung.sel.cqe.nova.main.pulse.PulseMeasurer
import com.samsung.sel.cqe.nova.main.utils.DistanceElement
import com.samsung.sel.cqe.nova.main.utils.DistanceInfo
import java.util.concurrent.ThreadLocalRandom

class VirtualDevices(val pulseMeasurer: PulseMeasurer) {

    val mapGenerated = LinkedHashMap<String, DistanceInfo>()

    private fun generateInitialList(actualDevicesMeasure: DistanceInfo?) {
        mapGenerated.clear()
        for (i in 3..50) {
            val info = if (i != 45)
                DistanceInfo(
                    "${Companion.NAME_PREFIX} $i",
                    "${Companion.NAME_PREFIX} $i",
                    ArrayList(),
                    false,
                    pulseMeasurer.generateNormalPulse(),
                    pulseMeasurer.generateNormalPulseOx()
                )
            else
                DistanceInfo(
                    "${Companion.NAME_PREFIX} $i",
                    "${Companion.NAME_PREFIX} $i",
                    ArrayList(),
                    true,
                    pulseMeasurer.generateAlarmPulse(),
                    pulseMeasurer.generateAlarmPulseOx()
                )
            mapGenerated[info.phoneName] = info
        }

        actualDevicesMeasure?.let {
            mapGenerated[actualDevicesMeasure.phoneName] = actualDevicesMeasure
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


    fun updateValues(alarmIndex: Int = -1) {
        mapGenerated.forEach { (_, map) ->
            val list = ArrayList<DistanceElement>()
            if (map.phoneName.split(" ").equals(alarmIndex.toString())) {
                map.isAlarm = true
            }
            map.pulse = pulseMeasurer.generateNormalPulse()
            map.pulseOx = pulseMeasurer.generateNormalPulseOx()
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

    fun getVirtualDevices(actualDevicesMeasure: DistanceInfo?): LinkedHashMap<String, DistanceInfo> {
        generateInitialList(actualDevicesMeasure)
        updateValues()
        return mapGenerated
    }

    companion object {
        private const val NAME_PREFIX = "Patient"
        const val DEVICE_COUNT_LOCAL = 15
    }
}