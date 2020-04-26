package com.samsung.sel.cqe.nova.main

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.samsung.sel.cqe.nova.R
import com.samsung.sel.cqe.nova.main.pulse.PulseMeasurer
import com.samsung.sel.cqe.nova.main.rtt.VirtualDevices
import kotlinx.coroutines.*

class TabsActivity : AppCompatActivity() {

    var pulseMeasurer = PulseMeasurer(null)
    val virtualDevices = VirtualDevices(pulseMeasurer).apply { getVirtualDevices(null) }
    private var alarmIndex = -1

    private var updateJob: Job? = null
    lateinit var adapter: PagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_pager)
        val tabLayout: TabLayout = findViewById<View>(R.id.tab_layout) as TabLayout
        tabLayout.addTab(tabLayout.newTab().setText("Info"))
        tabLayout.addTab(tabLayout.newTab().setText("Table"))
        tabLayout.addTab(tabLayout.newTab().setText("Map"))
        tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        val viewPager = findViewById<View>(R.id.pager) as ViewPager
        adapter = PagerAdapter(supportFragmentManager, tabLayout.tabCount, this)
        viewPager.adapter = adapter
        viewPager.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.setOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewPager.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        runUpdate()
    }

    private fun runUpdate() {
        updateJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                virtualDevices.updateValues(alarmIndex)
                val virtualDevices = virtualDevices.mapGenerated
                Log.d("JSON_RTT", "StartJson")
                virtualDevices.values.forEach {
                    Log.d("JSON_RTT", Gson().toJson(it))
                }
                pulseMeasurer.generatePulseMeasures()
                if (adapter.mapFragment.isVisible) adapter.mapFragment.updateMap(virtualDevices)
                if (adapter.novaFragment.isVisible) adapter.novaFragment.setPulseInfoView(
                    pulseMeasurer.getPulse(),
                    pulseMeasurer.getPulseOx()
                )
                if (adapter.tableFragment.isVisible) runOnUiThread {
                    adapter.tableFragment.updateTable(
                        virtualDevices.values
                    )
                }
                delay(5_000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
    }

    fun setAlarm() {
        alarmIndex = 22
    }
}