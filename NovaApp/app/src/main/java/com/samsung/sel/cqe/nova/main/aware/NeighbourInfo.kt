package com.samsung.sel.cqe.nova.main.aware

import android.net.wifi.aware.PeerHandle
import com.samsung.sel.cqe.nova.main.utils.NovaMessageHeader

data class NeighbourInfo(
    val peer: PeerHandle, val masterRank: Int, val MAC: String,
    val phoneName: String, val status: PhoneStatus, val isAcceptsConnections: Boolean
) {
    constructor(peer: PeerHandle, header: NovaMessageHeader) : this(
        peer, header.masterRank, header.MAC,
        header.phoneName, header.status, header.acceptsConnection
    )
}

enum class PhoneStatus { MASTER, CLIENT, CLIENT_SERVER, CLIENT_OUT, CLIENT_IN, UNDECIDED, CLOSING }