package map

import com.samsung.sel.cqe.nova.main.utils.DistanceInfo
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue

class MapCalculationHelper(private val distances: Map<String, DistanceInfo>) {

    private val unknownPositions: ArrayBlockingQueue<DistanceInfo> =
        ArrayBlockingQueue(distances.keys.size)
    private val knownPositions = ArrayList<DevicePosition>()
    private var currentDevice: DistanceInfo =
        DistanceInfo("unknown", "unknown", emptyList(), false, 0, 0)

    /*
      It is used to calculate coordinates of all
      devices, based on it's distances.
      Stars by locating first device on map,
      on location (0, 0). Then second device
      on location (-1 *distanceA->B, 0).
      Then third by using geometry circle intersection
      function. And each consecutive after third one
      by using three separate equations of two-circle
      intersections and using mean approximation
      of three coordinate estimates (centroid), which
      results in one, most probable location of a device.
       */
    fun calculate(): java.util.ArrayList<DevicePosition> {
        unknownPositions.addAll(distances.values)

        val startDevice = unknownPositions.poll()
        val secondStartDevice =
            distances[startDevice.phoneName]?.distanceList?.first()?.phoneName ?: ""
        val secondStartDevicePosition =
            Pair(getDistanceBetweenDevices(startDevice.phoneName, secondStartDevice), 0)
        knownPositions.add(DevicePosition(startDevice, Pair(0, 0)))
        knownPositions.add(
            DevicePosition(
                distances[secondStartDevice]!!,
                secondStartDevicePosition
            )
        )
        unknownPositions.remove(distances[secondStartDevice])

        while (unknownPositions.isNotEmpty()) {
            currentDevice = unknownPositions.poll()
            val calculatedPosition = calculateDevicePosition()
            knownPositions.add(calculatedPosition)
        }
        return knownPositions
    }

    private fun calculateDevicePosition(
    ): DevicePosition {
        val firstDevice = knownPositions.getFromTheEnd(2)
        val firstDeviceName = knownPositions.getFromTheEnd(2).device.phoneName
        val firstDeviceDistance: Int = getDistanceBetweenDeviceAndCurrent(firstDeviceName)

        val secondDevice = knownPositions.getFromTheEnd(1)
        val secondDeviceName = knownPositions.getFromTheEnd(1).device.phoneName
        val secondDeviceDistance: Int = getDistanceBetweenDeviceAndCurrent(secondDeviceName)

        val positionCoordinates = if (knownPositions.size <= 2) {
            calculateCoordinatesByDistanceBetweenTwoDevices(
                firstDevice, secondDevice, firstDeviceDistance, secondDeviceDistance
            )
        } else {
            calculateCoordinatesByDistanceBetweenThreeDevices(
                firstDevice, secondDevice, firstDeviceDistance, secondDeviceDistance
            )
        }
        val absPosition =
            Pair(positionCoordinates.first.absoluteValue, positionCoordinates.second.absoluteValue)
        return DevicePosition(currentDevice, absPosition)
    }

    private fun calculateCoordinatesByDistanceBetweenThreeDevices(
        firstDevicePosition: DevicePosition,
        secondDevicePosition: DevicePosition,
        firstDeviceDistance: Int,
        secondDeviceDistance: Int
    ): Pair<Int, Int> {
        val thirdDevice = knownPositions.getFromTheEnd(3).device.phoneName
        val thirdDevicePosition = knownPositions.getFromTheEnd(3)
        val thirdDeviceDistance = getDistanceBetweenDeviceAndCurrent(thirdDevice)
        val firstIntersection = calculateIntersectionOfTwoCircles(
            firstDevicePosition, secondDevicePosition,
            firstDeviceDistance, secondDeviceDistance
        )
        val secondIntersection = calculateIntersectionOfTwoCircles(
            firstDevicePosition, thirdDevicePosition,
            firstDeviceDistance, thirdDeviceDistance
        )
        val thirdIntersection = calculateIntersectionOfTwoCircles(
            secondDevicePosition, thirdDevicePosition,
            secondDeviceDistance, thirdDeviceDistance
        )
        return centroid(firstIntersection, secondIntersection, thirdIntersection)
    }

    private fun calculateCoordinatesByDistanceBetweenTwoDevices(
        firstDevice: DevicePosition,
        secondDevice: DevicePosition,
        firstDeviceDistance: Int,
        secondDeviceDistance: Int
    ): Pair<Int, Int> {
        return calculateIntersectionOfTwoCircles(
            firstDevice, secondDevice, firstDeviceDistance, secondDeviceDistance
        )
    }

    private fun getDistanceBetweenDeviceAndCurrent(deviceFrom: String): Int {
        return getDistanceBetweenDevices(deviceFrom, currentDevice.phoneName)
    }

    private fun getDistanceBetweenDevices(deviceFrom: String, deviceTo: String): Int {
        val distanceInfo = distances[deviceFrom]
        return if (distanceInfo != null) {
            distanceInfo.getDistanceToPhoneByNameOrDefault(deviceTo)
        } else {
            println("Cannot find distance map for $deviceFrom")
            -1
        }
    }
}

fun <T> List<T>.getFromTheEnd(indexFromTheEnd: Int): T = get(size - indexFromTheEnd)