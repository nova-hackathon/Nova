package com.samsung.sel.cqe.nova.main.pulse

import android.util.Log
import com.samsung.sel.cqe.nova.main.TAG
import com.samsung.sel.cqe.nova.main.controller.NovaController
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class PulseMeasurer(val novaController: NovaController?) {

    companion object {
        const val PULSE_MAX = 80
        const val PULSE_MIN = 70
        const val PULSEOX_MAX = 100
        const val PULSEOX_MIN = 95

        const val ALARM_PULSE_MAX = 170
        const val ALARM_PULSE_MIN = 160
        const val ALARM_PULSEOX_MAX = 90
        const val ALARM_PULSEOX_MIN = 87
    }

    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(3)
    private var scheduledFuture: ScheduledFuture<*>? = null
    var pulse = AtomicInteger()
    var pulseOx = AtomicInteger()

    init {
      if (novaController != null) scheduledFuture = executor.scheduleAtFixedRate({ generatePulseMeasures() }, 1, 3, TimeUnit.SECONDS)
   }

    fun generateNormalPulse() = Random.nextInt(PULSE_MIN, PULSE_MAX + 1)
    fun generateNormalPulseOx() = Random.nextInt(PULSEOX_MIN, PULSEOX_MAX + 1)
    fun generateAlarmPulse() = Random.nextInt(ALARM_PULSE_MIN, ALARM_PULSE_MAX + 1)
    fun generateAlarmPulseOx() = Random.nextInt(ALARM_PULSEOX_MIN, ALARM_PULSEOX_MAX + 1)

    fun generatePulseMeasures() {
        val pulseValue = generateNormalPulse()
        val pulseOxValue = generateNormalPulseOx()
        setPulseMeasures(pulseValue, pulseOxValue)
    }

    private fun generateAlarmPulseMeasures() {
        val pulseValue = generateAlarmPulse()
        val pulseOxValue = generateAlarmPulseOx()
        setPulseMeasures(pulseValue, pulseOxValue)
    }

    private fun setPulseMeasures(pulseValue: Int, pulseOxValue: Int) {
        pulse.set(pulseValue)
        pulseOx.set(pulseOxValue)
        novaController?.setPulseInfoView(pulseValue, pulseOxValue)
    }

    fun startAlarm(){
        scheduledFuture?.cancel(true)
        executor.scheduleAtFixedRate({ generateAlarmPulseMeasures() }, 0, 2, TimeUnit.SECONDS)
    }

    fun getPulse() = pulse.get()
    fun getPulseOx() = pulseOx.get()

    fun close() {
        executor.awaitTermination(1, TimeUnit.SECONDS)
        executor.shutdownNow()
    }

}