package com.samsung.sel.cqe.nova.main.controller

import android.net.MacAddress
import com.samsung.sel.cqe.nova.main.aware.PhoneStatus

data class PhoneInfo(
    var phoneID: String = "",
    var phoneName: String = "",
    var macAddress: MacAddress? = null,
    var masterRank: Int = -1,
    var masterId: String = "",
    @Volatile var isMaster: Boolean = false,
    @Volatile var acceptsConnection: Boolean = false,
    var status: PhoneStatus = PhoneStatus.UNDECIDED
)