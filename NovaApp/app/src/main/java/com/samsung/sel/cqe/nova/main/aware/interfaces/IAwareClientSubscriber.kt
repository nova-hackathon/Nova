package com.samsung.sel.cqe.nova.main.aware.interfaces

interface IAwareClientSubscriber {
    fun onNewServerDiscovered(phoneId: String)
    fun onServerDisconnected(phoneId: String)
    fun runAnalyze()
}