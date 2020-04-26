package com.samsung.sel.cqe.nova.main.aware.interfaces

import android.net.wifi.aware.PublishDiscoverySession

interface IAwareServer {
    var publishDiscoverySession: PublishDiscoverySession?

    fun updateConfig(configBytes: ByteArray)
    fun close()
    fun publish(headerBytes: ByteArray)
}