package com.samsung.sel.cqe.nova.main.map

import android.content.Context
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.samsung.sel.cqe.nova.main.utils.DistanceInfo
import kotlinx.android.synthetic.main.marker_view.view.*

class CustomMarker(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {

    override fun refreshContent(entry: Entry, highlight: Highlight?) {
        val distanceInfo = entry.data as DistanceInfo
        deviceMapInfo.text =
            "${distanceInfo.phoneName}\nPulse: ${distanceInfo.pulse}\nPulseOx: ${distanceInfo.pulseOx}"
        super.refreshContent(entry, highlight)
    }

    override fun getOffsetForDrawingAtPoint(xpos: Float, ypos: Float): MPPointF {
        return MPPointF(-width / 2f, -height - 10f)
    }
}
