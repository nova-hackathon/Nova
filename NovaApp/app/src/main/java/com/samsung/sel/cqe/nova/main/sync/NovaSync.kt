package com.samsung.sel.cqe.nova.main.sync

import android.util.Log
import com.samsung.sel.cqe.nova.main.SYNC_MSG_SEP
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.aware.AwareService
import com.samsung.sel.cqe.nova.main.controller.NovaController
import com.samsung.sel.cqe.nova.main.controller.PhoneInfo
import com.samsung.sel.cqe.nova.main.socket.NovaSocketClient
import com.samsung.sel.cqe.nova.main.utils.MessageType
import com.samsung.sel.cqe.nova.main.utils.NovaMessage
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class NovaSync(
    private val novaController: NovaController,
    private val phoneInfo: PhoneInfo
) {

    var stopWatchStartTime = System.currentTimeMillis()
    var timerScheduledExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()


    init {
        writeTimesToLogs()
        timerScheduledExecutor.scheduleAtFixedRate(
            {
                novaController.updateClock(stopWatchStartTime)
            },
            5, 1, TimeUnit.MILLISECONDS
        )

    }

    private fun writeTimesToLogs() {
        Log.w(
            TAG,
            "start:$stopWatchStartTime, milis:${System.currentTimeMillis()}, dt:${System.currentTimeMillis() - stopWatchStartTime}, date:${LocalDateTime.now()}"
        )
    }

    fun sync(clientSocket: NovaSocketClient) {
        Log.w(TAG, "synchronize")
        Log.w(
            TAG,
            "${System.currentTimeMillis()} send sync request date:${LocalDateTime.now()}"
        )
        clientSocket.sendMessage(
            NovaMessage(
                MessageType.SYNC_REQUEST,
                System.currentTimeMillis().toString() + SYNC_MSG_SEP,
                phoneInfo
            )
        )
    }

fun adjustStartTimeUsingMsg(msg: String) {
    Log.w(
        TAG,
        "${System.currentTimeMillis()} obtain sync response:${LocalDateTime.now()}"
    )
    try {
        val contentSplit = msg.split(SYNC_MSG_SEP)
        val sendRequestTime = contentSplit[0].toLong()
        val timeValue = contentSplit[1].toLong()
        val oneWaySendinDuration = (System.currentTimeMillis() - sendRequestTime) / 2
        stopWatchStartTime = System.currentTimeMillis() - timeValue - oneWaySendinDuration
        Log.w(TAG, "adjustStartTimeUsingMsg(): $msg")
        writeTimesToLogs()
    } catch (e: Exception) {
        Log.e(TAG, "failed to adjust time with msg: $msg", e)
    }
}

fun close() {
    try {
        timerScheduledExecutor.awaitTermination(10, TimeUnit.MILLISECONDS)
        timerScheduledExecutor.shutdownNow()
    } catch (e: Exception) {
    }
}
}