package com.samsung.sel.cqe.nova.main.map

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.samsung.sel.cqe.nova.R
import com.samsung.sel.cqe.nova.main.TabsActivity
import com.samsung.sel.cqe.nova.main.utils.DistanceInfo
import map.MapCalculationHelper
import java.util.*
import kotlin.collections.HashMap

class MapFragment(val tabsActivity: TabsActivity) : Fragment() {

    private val lineChart: LineChart by lazy { activity!!.findViewById<LineChart>(R.id.lineChart) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.map_fragment, container, false)
    }

    override fun onStart() {
        super.onStart()
        val mapGenerated = tabsActivity.virtualDevices.mapGenerated
        if (mapGenerated.isNotEmpty()) {
            updateMap(mapGenerated)
        }
    }

    private val colorsMap = HashMap<String, Int>()

    fun updateMap(distances: Map<String, DistanceInfo>) {
        val helper = MapCalculationHelper(distances)
        val devicesCoordinates = helper.calculate()

        val lineData = mutableListOf<LineDataSet>()
        val customLegend = mutableListOf<LegendEntry>()
        devicesCoordinates.forEach {
            val entry = Entry(it.xCoordinate / 1000f, it.yCoordinate / 1000f, it.device)
            val vl = LineDataSet(listOf(entry), it.device.phoneName)
            vl.setDrawValues(false)
            vl.setDrawFilled(true)
            if (it.device.isAlarm) {
                vl.circleRadius = 10F
                colorsMap[it.device.phoneName] = Color.RED
            } else {
                vl.circleRadius = 5F
            }
            vl.setCircleColor(colorsMap.getOrAddDefault(it.device.phoneId, generateRandomColor()))

            lineData.add(vl)
            customLegend.add(LegendEntry().apply {
                label = it.device.phoneName
                formColor = colorsMap[label] ?: Color.RED
            })
        }
        lineChart.data = LineData(lineData as List<ILineDataSet>)
        lineChart.description = Description().apply { text = "" }
        lineChart.legend.setCustom(customLegend)
        lineChart.legend.isWordWrapEnabled = true
        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(true)

        if (context != null) {
            val markerView = CustomMarker(this.context!!, R.layout.marker_view)
            lineChart.marker = markerView
        }
        activity?.runOnUiThread {
            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
        }
    }

    private fun generateRandomColor(): Int {
        val rnd = Random()
        return Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
    }

    fun <K, V> HashMap<K, V>.getOrAddDefault(key: K, default: V): V {
        val orDefault = getOrDefault(key, default)
        if (orDefault == default) {
            put(key, orDefault)
        }
        return orDefault
    }
}