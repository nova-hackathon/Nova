package com.samsung.sel.cqe.nova.main.utils

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

data class ComputerMessage(val type: ComputerMessageType, val content: String)
enum class ComputerMessageType {
    DISTANCE_INFO
}

data class DistanceElement(val phoneName: String, val distance: Int)

data class DistanceInfo(
    val phoneId: String,
    val phoneName: String,
    var distanceList: List<DistanceElement>,
    var isAlarm: Boolean,
    var pulse: Int,
    var pulseOx: Int
) {

    constructor(distanceInfo: DistanceInfo, isAlarm: Boolean, pulse: Int, pulseOx: Int) : this(
        distanceInfo.phoneId,
        distanceInfo.phoneName,
        distanceInfo.distanceList,
        isAlarm, pulse, pulseOx
    )

    fun getDistanceToPhoneByNameOrDefault(phoneName: String, default: Int = -1) =
        distanceList.find { it.phoneName == phoneName }?.distance ?: default
}

@Throws(JsonSyntaxException::class)
fun convertComputerMessageFromJson(message: String): ComputerMessage {
    return Gson().fromJson(message, ComputerMessage::class.java)
}

fun convertComputerMessageToJsonString(message: ComputerMessage): String {
    return Gson().toJson(message)
}

@Throws(JsonSyntaxException::class)
fun convertDistanceInfoFromJson(message: String): DistanceInfo {
    return Gson().fromJson(message, DistanceInfo::class.java)
}

fun convertDistanceInfoToJsonString(message: DistanceInfo): String {
    return Gson().toJson(message)
}