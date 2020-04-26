package com.samsung.sel.cqe.nova.main.utils

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.controller.PhoneStatus

data class NovaMessage(
    val type: MessageType,
    val content: String,
    val header: NovaMessageHeader
) {

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
    val masterId: String,
    val acceptsConnection: Boolean,
    val masterRank: Int,
    val MAC: String,
    val phoneName: String,
    val status: PhoneStatus,
    val isMaster: Boolean
) {
    constructor(phoneInfo: PhoneInfo) : this(
        phoneInfo.phoneID, phoneInfo.masterId, phoneInfo.acceptsConnection, phoneInfo.masterRank,
        phoneInfo.macAddress.toString(), phoneInfo.phoneName, phoneInfo.status, phoneInfo.isMaster
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
    PING, REQUEST_SOCKET, REJECT_CONNECTION, ACCEPT_CONNECTION, SYNC_CLOCK, SYNC_REQUEST, STATUS_UPDATE, ACCEPT_CLUSTER_CONNECTION, REQUEST_CLUSTER_SOCKET, MASTER_CLUSTER_INFO_UPDATE, REQUEST_STATUS, CLIENT_CLUSTER_INFO_UPDATE, NEW_CLUSTER_CONNECTION_ESTABLISHED, CLUSTER_CONNECTION_LOST, CLIENT_ACCEPTS_CHANGE_ROLE, RTT_INIT, RTT_BROADCAST, RTT_REQUEST
}
