package com.samsung.sel.cqe.nova.main.controller

import android.net.MacAddress
import com.samsung.sel.cqe.nova.main.structure.ClusterInfo
import com.samsung.sel.cqe.nova.main.structure.ClusterState

data class PhoneInfo(
    var phoneID: String = "",
    var phoneName: String = "",
    var macAddress: MacAddress? = null,
    var masterRank: Int = -1,
    @Volatile var masterId: String = "",
    @Volatile var isMaster: Boolean = false,
    @Volatile var acceptsConnection: Boolean = false,
    @Volatile var status: PhoneStatus = PhoneStatus.UNDECIDED
){
    lateinit var clusterInfo: ClusterInfo

    fun assignClusterInfo(clusterState: ClusterState){
        clusterInfo = ClusterInfo(clusterState, phoneID)
    }

}

enum class PhoneStatus { MASTER, CLIENT, CLIENT_SERVER, CLIENT_OUT, CLIENT_IN, UNDECIDED, CLIENT_SERVER_AWAITS_RECONNECT, RTT_IN_PROGRESS, RTT_FINISHED, CLOSING }