package com.samsung.sel.cqe.nova.main.aware

import android.net.wifi.aware.PeerHandle
import com.samsung.sel.cqe.nova.main.controller.PhoneStatus
import com.samsung.sel.cqe.nova.main.utils.NovaMessageHeader

data class NeighbourInfo(
    val peer: PeerHandle,
    val masterRank: Int,
    val MAC: String,
    val phoneName: String,
    val status: PhoneStatus,
    val isAcceptsConnections: Boolean,
    val masterId: String,
    val isMaster: Boolean
) {
    constructor(peer: PeerHandle, header: NovaMessageHeader) : this(
        peer, header.masterRank, header.MAC, header.phoneName,
        header.status, header.acceptsConnection, header.masterId,
        header.isMaster
    )

    constructor(nI: NeighbourInfo, status: PhoneStatus) : this(
        nI.peer, nI.masterRank, nI.MAC, nI.phoneName, status, nI.isAcceptsConnections,
        nI.masterId, nI.isMaster
    )
}
