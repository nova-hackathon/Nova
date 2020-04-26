package com.samsung.sel.cqe.nova.main.utils

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.samsung.sel.cqe.nova.main.aware.PhoneStatus
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo

data class NovaMessage(
    val type: MessageType,
    val content: String,
    val header: NovaMessageHeader
) {
    val senderId = header.phoneId

    companion object {
        const val EMPTY_CONTENT = ""
    }

    constructor(type: MessageType, content: String, phoneInfo: PhoneInfo) : this(
        type, content,
        NovaMessageHeader(phoneInfo)
    )
}

data class NovaMessageHeader(
    val phoneId: String,
    val acceptsConnection: Boolean,
    val masterRank: Int,
    val MAC: String,
    val phoneName: String,
    val status: PhoneStatus
) {
    constructor(phoneInfo: PhoneInfo) : this(
        phoneInfo.phoneID, phoneInfo.acceptsConnection, phoneInfo.masterRank,
        phoneInfo.macAddress.toString(), phoneInfo.phoneName, phoneInfo.status
    )
}

@Throws(JsonSyntaxException::class)
fun convertMessageFromJson(message: String): NovaMessage {
    return Gson().fromJson(message, NovaMessage::class.java)
}

fun convertMessageToJsonBytes(message: NovaMessage): ByteArray {
    return Gson().toJson(message).toByteArray()
}

fun convertMessageToJsonString(message: NovaMessage): String {
    return Gson().toJson(message)
}

@Throws(JsonSyntaxException::class)
fun convertMessageHeaderFromJson(message: String): NovaMessageHeader {
    return Gson().fromJson(message, NovaMessageHeader::class.java)
}

fun convertMessageHeaderToJsonBytes(message: NovaMessageHeader): ByteArray {
    return Gson().toJson(message).toByteArray()
}

fun convertMessageHeaderToJsonString(message: NovaMessageHeader): String {
    return Gson().toJson(message)
}

enum class MessageType {
    PING, REQUEST_SOCKET, REJECT_CONNECTION, ACCEPT_CONNECTION, SYNC_CLOCK, SYNC_REQUEST
}
