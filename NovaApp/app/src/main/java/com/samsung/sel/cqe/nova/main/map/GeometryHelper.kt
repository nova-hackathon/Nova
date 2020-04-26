package map

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

fun Int.pow(pow: Int) = toDouble().pow(pow).toInt()

/*
        Calculate position of 3rd device when only 2 are present.

        Details about math involved can be found at:
        http://paulbourke.net/geometry/circlesphere/
        under 'Intersection of two circles' section

        We discard coordinate that is below x axis,
        because it's a mirror image, which we can simulate
        in different ways.*/
fun calculateIntersectionOfTwoCircles(
    firstDevice: DevicePosition,
    secondDevice: DevicePosition,
    firstDeviceDistance: Int,
    secondDeviceDistance: Int
): Pair<Int, Int> {
    val x0 = firstDevice.xCoordinate.toDouble()
    val y0 = firstDevice.yCoordinate.toDouble()
    val x1 = secondDevice.xCoordinate.toDouble()
    val y1 = secondDevice.yCoordinate.toDouble()

    val d = sqrt((x1 - x0).pow(2) + (y1 - y0).pow(2))

    val (r0, r1) = observationalErrorHandler(d, firstDeviceDistance, secondDeviceDistance)
    val a = (r0.pow(2) - r1.pow(2) + d.pow(2)) / (2 * d)
    val h = sqrt(r0.pow(2) - a.pow(2))

    val x2 = x0 + a * (x1 - x0) / d
    val y2 = y0 + a * (y1 - y0) / d

    val x3 = (x2 + h * (y1 - y0) / d)
    val y3 = (y2 - h * (x1 - x0) / d)

    val x4 = (x2 - h * (y1 - y0) / d)
    val y4 = (y2 + h * (x1 - x0) / d)

    if (y3 < 0)
        return Pair(x4.toInt(), y4.toInt())
    return Pair(x3.toInt(), y3.toInt())
}

/*
     Increase or decrease radius if circles
       don't intersect, stop at maximum
       observational error value specified
   */
fun observationalErrorHandler(
    distanceBetweenNodes: Double,
    firstDeviceRadius: Int,
    secondDeviceRadius: Int
): Pair<Int, Int> {
    val observationError = 5000
    var iterator = 0
    var rn = firstDeviceRadius
    var rm = secondDeviceRadius
    while (distanceBetweenNodes > rn + rm) {
        if (iterator <= 5000) {
            rn += 1
            rm += 1
            iterator += 1
        } else {
//            println("Circles are separate")
            return Pair(-1, -1)
        }
    }

    while (distanceBetweenNodes < abs(rn - rm)) {
        if (iterator <= observationError) {
            rn += 1
            rm -= 1
            iterator += 1
        }
        if (iterator >= observationError) {
            rn -= 1
            rm += 1
            iterator += 1
        }
        if (iterator >= 3 * observationError) {
//            println("Circles are contained within each other")
            return Pair(-1, -1)
        }
    }

    if (distanceBetweenNodes.toInt() == 0 && rn == rm) {
//        println("Coincident circle, possible duplicate reading")
        return Pair(-1, -1)
    }
    return Pair(rn, rm)
}

/*Calculate centroid of three coordinates
  Details about math involved can be found at:
  https://www.mathopenref.com/coordcentroid.html*/
fun centroid(pos_1: Pair<Int, Int>, pos_2: Pair<Int, Int>, pos_3: Pair<Int, Int>): Pair<Int, Int> {
    val x1 = pos_1.first
    val y1 = pos_1.second
    val x2 = pos_2.first
    val y2 = pos_2.second
    val x3 = pos_3.first
    val y3 = pos_3.second
    val x = (x1 + x2 + x3) / 3
    val y = (y1 + y2 + y3) / 3
    return Pair(x, y)
}