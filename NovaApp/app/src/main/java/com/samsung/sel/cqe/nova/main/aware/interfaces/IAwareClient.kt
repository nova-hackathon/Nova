package com.samsung.sel.cqe.nova.main.aware.interfaces

import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeDiscoverySession
import com.samsung.sel.cqe.nova.main.aware.NeighbourInfo
import com.samsung.sel.cqe.nova.main.controller.PhoneStatus
import com.samsung.sel.cqe.nova.main.utils.NovaMessage

interface IAwareClient {
    var subscribeSession: SubscribeDiscoverySession?
    fun sendMessage(peerHandle: PeerHandle, message: NovaMessage, tryCount: Int = 0)
    fun sendMessageToClient(phoneId: String, message: NovaMessage)
    fun getInfoByPhoneId(phoneId: String): NeighbourInfo?
    fun getPeerIdByClusterIdAndPhoneStatus(clusterId: String, phoneStatus: PhoneStatus): String?
    fun updateConfig(configBytes: ByteArray)
    fun close()
    fun getMastersIds(): Set<String>
    fun getMacIdMap(): HashMap<String, String>
    fun getMacIdMap(clusterId: String): HashMap<String, String>
    fun removePhone(phoneId: String): NeighbourInfo?
    fun getFilteredMap(phoneStatus: PhoneStatus): Map<String, NeighbourInfo>
    fun subscribe(specificInfo : ByteArray)
    fun logPeersMap()
    fun isPeerStillAvailable(phoneId: String): Boolean
    fun getAvailableMastersIds(): Set<String>
    fun setPeerStatusToRttInProgress(phoneId: String)

}