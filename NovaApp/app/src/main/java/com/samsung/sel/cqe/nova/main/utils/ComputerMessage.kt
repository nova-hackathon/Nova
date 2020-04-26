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
    val distanceList: List<DistanceElement>
)

@Throws(JsonSyntaxException::class)
fun convertComputerMessageFromJson(message: String): ComputerMessage {
    return Gson().fromJson(message, ComputerMessage::class.java)
}

fun convertComputerMessageToJsonString(message: ComputerMessage): String {
    return Gson().toJson(message)
}

@Throws(JsonSyntaxException::class)
fun convertDistanceFromJson(message: String): DistanceInfo {
    return Gson().fromJson(message, DistanceInfo::class.java)
}

fun convertDistanceToJsonString(message: DistanceInfo): String {
    return Gson().toJson(message)
}