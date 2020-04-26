package com.samsung.sel.cqe.nova.main.aware.interfaces

import android.net.wifi.aware.PeerHandle

interface IAwareServerSubscriber{
    fun onConnectionRequest(peerHandle: PeerHandle, senderId: String)
    fun onServerAcceptConnection(serverId : String)
    fun onServerRejectConnection(serverId : String)
}