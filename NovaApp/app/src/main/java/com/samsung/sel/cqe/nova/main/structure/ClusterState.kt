package com.samsung.sel.cqe.nova.main.structure

interface ClusterState {
    fun initializeRttMeasure()
    fun onRttRequest()
    fun onRttMeasureFinished()
    fun onServerLost() {}
    fun close()
}