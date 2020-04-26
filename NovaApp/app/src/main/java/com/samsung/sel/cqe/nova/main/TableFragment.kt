package com.samsung.sel.cqe.nova.main

import android.animation.AnimatorInflater
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.samsung.sel.cqe.nova.R
import com.samsung.sel.cqe.nova.main.utils.DistanceInfo


class TableFragment : Fragment() {
    private val table: TableLayout by lazy { view!!.findViewById<TableLayout>(R.id.tab_layout) }
    private var tableHeader: TableRow? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.table_fragment, container, false)
    }

    private fun generateTableHeader(): TableRow {
        val tableRow = TableRow(activity);
        tableRow.id = 0
        tableRow.setBackgroundColor(Color.LTGRAY)
        tableRow.layoutParams = TableRow.LayoutParams(
            TableRow.LayoutParams.MATCH_PARENT,
            TableRow.LayoutParams.WRAP_CONTENT
        )

        val nameLabel = TextView(activity)
        nameLabel.text = "PatientID"
        nameLabel.setPadding(5, 10, 5, 10)
        nameLabel.textAlignment = View.TEXT_ALIGNMENT_CENTER
        tableRow.addView(nameLabel)

        val pulseLabel = TextView(activity)
        pulseLabel.text = "Pulse"
        pulseLabel.setPadding(5, 10, 5, 10)
        pulseLabel.textAlignment = View.TEXT_ALIGNMENT_CENTER
        tableRow.addView(pulseLabel)

        val saturationLabel = TextView(activity)
        saturationLabel.text = "Saturation"
        saturationLabel.setPadding(5, 10, 5, 10)
        saturationLabel.textAlignment = View.TEXT_ALIGNMENT_CENTER
        tableRow.addView(saturationLabel)
        return tableRow
    }

    fun updateTable(rttAndPulse: MutableCollection<DistanceInfo>) {
        table.removeAllViews()
        table.clearDisappearingChildren()
        if (tableHeader == null) tableHeader = generateTableHeader()
        table.addView(tableHeader)
        var i = 0
        rttAndPulse.sortedBy { it.pulseOx }.forEach {
            val patientName = it.phoneName
            val tableRow = TableRow(activity);
            tableRow.id = i + 1
            tableRow.setBackgroundColor(Color.WHITE)
            tableRow.layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
            )

            val nameLabel = TextView(activity)
            nameLabel.text = patientName
            nameLabel.setPadding(5, 10, 5, 10)
            nameLabel.textAlignment = View.TEXT_ALIGNMENT_CENTER
            tableRow.addView(nameLabel)

            val pulseLabel = TextView(activity)
            pulseLabel.text = it.pulse.toString()
            pulseLabel.setPadding(5, 10, 5, 10)
            pulseLabel.textAlignment = View.TEXT_ALIGNMENT_CENTER
            tableRow.addView(pulseLabel)

            val saturationLabel = TextView(activity)
            saturationLabel.text = it.pulseOx.toString()
            saturationLabel.setPadding(5, 10, 5, 10)
            saturationLabel.textAlignment = View.TEXT_ALIGNMENT_CENTER
            tableRow.addView(saturationLabel)

            if (it.isAlarm) setAlarmOnRow(tableRow)
            table.addView(tableRow)
            i++
        }
    }

    fun setAlarmOnRow(tableRow: TableRow) {
        val anim =
            AnimatorInflater.loadAnimator(activity, R.animator.row_alarm) as ObjectAnimator
        anim.target = tableRow
        anim.setEvaluator(ArgbEvaluator())
        anim.start()
    }
}