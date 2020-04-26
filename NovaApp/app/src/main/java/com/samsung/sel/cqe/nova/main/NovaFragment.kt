package com.samsung.sel.cqe.nova.main

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.rtt.WifiRttManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.fragment.app.Fragment
import com.samsung.sel.cqe.nova.R
import com.samsung.sel.cqe.nova.main.controller.NovaController
import com.samsung.sel.cqe.nova.main.controller.PhoneStatus
import com.samsung.sel.cqe.nova.main.map.MapFragment
import com.samsung.sel.cqe.nova.main.utils.DistanceElement

class NovaFragment(
    val tableFragment: TabsActivity
) : Fragment() {

//    private lateinit var novaController: NovaController

    private val distanceHeader: TextView by lazy { activity!!.findViewById<TextView>(R.id.distanceHeader) }
    private val pulseHeader: TextView by lazy { activity!!.findViewById<TextView>(R.id.pulseHeader) }

    private val pulseTitle: TextView by lazy { activity!!.findViewById<TextView>(R.id.pulseTitle) }
    private val pulseValueView: TextView by lazy { activity!!.findViewById<TextView>(R.id.pulseValue) }

    private val pulseOxTitle: TextView by lazy { activity!!.findViewById<TextView>(R.id.pulseOxTitle) }
    private val pulseOxValueView: TextView by lazy { activity!!.findViewById<TextView>(R.id.pulseOxValue) }

    private val phoneName1: TextView by lazy { activity!!.findViewById<TextView>(R.id.phoneName1) }
    private val phoneName2: TextView by lazy { activity!!.findViewById<TextView>(R.id.phoneName2) }
    private val phoneDistance1: TextView by lazy { activity!!.findViewById<TextView>(R.id.phoneDistance1) }
    private val phoneDistance2: TextView by lazy { activity!!.findViewById<TextView>(R.id.phoneDistance2) }


    private val statusText: TextView by lazy { activity!!.findViewById<TextView>(R.id.status_view) }
    private val phoneNameView: TextView by lazy { activity!!.findViewById<TextView>(R.id.phoneName) }
    private val alarmButton: TextView by lazy { activity!!.findViewById<TextView>(R.id.alarmButton) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_nova, container, false)
    }

    override fun onStart() {
        super.onStart()
        val phoneID =
            Settings.Secure.getString(activity?.contentResolver, Settings.Secure.ANDROID_ID)
        val phoneName = Settings.Global.getString(activity?.contentResolver, "device_name")
//        novaController = NovaController(this, phoneID, phoneName,tableFragment,mapFragment)
        initStatusTextView()
        initDistanceAndPulseViews()
        initAlarmButton()
        setPulseInfoView(
            tableFragment.pulseMeasurer.getPulse(),
            tableFragment.pulseMeasurer.getPulseOx()
        )
    }

    private fun initAlarmButton() {
        alarmButton.text = "ALARM"
        alarmButton.textSize = SMALL_TEXT_SIZE
        alarmButton.setTextColor(Color.WHITE)
        alarmButton.setBackgroundColor(Color.rgb(156, 255, 153))
        alarmButton.setOnClickListener {
//            novaController.startAlarm()
//            tableFragment.setAlarm()
            alarmButton.setBackgroundColor(Color.RED)
            val fadeAnim = AnimationUtils.loadAnimation(context, R.anim.fade)
            it.startAnimation(fadeAnim)
        }
    }

    private fun initStatusTextView() {
        statusText.gravity = Gravity.CENTER
        statusText.setBackgroundColor(Color.GREEN)
        statusText.textSize = SMALL_TEXT_SIZE
    }

    private fun initDistanceAndPulseViews() {
        distanceHeader.setTypeface(Typeface.DEFAULT_BOLD)
        distanceHeader.gravity = Gravity.CENTER
        distanceHeader.textSize = MEDIUM_TEXT_SIZE

        pulseHeader.setTypeface(Typeface.DEFAULT_BOLD)
        pulseHeader.gravity = Gravity.CENTER
        pulseHeader.textSize = MEDIUM_TEXT_SIZE

        pulseTitle.text = "Pulse: "
        pulseTitle.textSize = SMALL_TEXT_SIZE
        pulseValueView.textSize = SMALL_TEXT_SIZE

        pulseOxTitle.text = "Pulse Ox (SpO2): "
        pulseOxTitle.textSize = SMALL_TEXT_SIZE
        pulseOxValueView.textSize = SMALL_TEXT_SIZE

        setTextViewStyle(phoneName1)
        setTextViewStyle(phoneDistance1)
        setTextViewStyle(phoneName2)
        setTextViewStyle(phoneDistance2)

    }

    fun setTextViewStyle(textView: TextView) {
        textView.textSize = SMALL_TEXT_SIZE
    }

    fun initPhoneNameView(phoneName: String) {
        phoneNameView.setTypeface(Typeface.DEFAULT_BOLD)
        phoneNameView.textSize = LARGE_TEXT_SIZE
        phoneNameView.text = phoneName
        phoneNameView.gravity = Gravity.CENTER
    }

    fun setDistanceInfoView(list: List<DistanceElement>) = activity?.runOnUiThread {
        if (list.isNotEmpty()) {
            phoneName1.text = "${list[0].phoneName}:"
            phoneDistance1.text = "${list[0].distance / 1000.0} m"
            phoneName2.text = ""
            phoneDistance2.text = ""
        }
        if (list.size > 1) {
            phoneName2.text = "${list[1].phoneName}:"
            phoneDistance2.text = "${list[1].distance / 1000.0} m"
        }
    }

    fun setInProgressDistanceInfoView(list: List<String>) = activity?.runOnUiThread {
        val text = "Measuring"
        if (list.isNotEmpty()) {
            phoneName1.text = "${list[0]}:"
            phoneDistance1.text = text
            phoneName2.text = ""
            phoneDistance2.text = ""
        }
        if (list.size > 1) {
            phoneName2.text = "${list[1]}:"
            phoneDistance2.text = text
        }
    }


    @SuppressLint("ResourceAsColor")
    fun setStatusOnTextView(status: PhoneStatus, masterName: String) = activity?.runOnUiThread {

        var visibleStatus =
            when (status) {
                PhoneStatus.MASTER -> "MASTER"
                PhoneStatus.RTT_IN_PROGRESS -> return@runOnUiThread
                PhoneStatus.RTT_FINISHED -> return@runOnUiThread
                PhoneStatus.UNDECIDED -> "UNDECIDED"
                else -> "CLIENT"
            }

        val text = "Role - $visibleStatus"
        statusText.text = text
        statusText.setTextColor(Color.DKGRAY)
        if (visibleStatus == "MASTER") {
            statusText.setBackgroundColor(Color.YELLOW)
        } else if (visibleStatus == "CLIENT") {
            statusText.setBackgroundColor(Color.rgb(49, 196, 245))
        } else {
            statusText.setBackgroundColor(Color.GREEN)
        }
    }

    fun setPulseInfoView(pulseValue: Int, pulseOxValue: Int) = activity?.runOnUiThread {
        pulseValueView.text = "$pulseValue bpm"
        pulseOxValueView.text = "$pulseOxValue %"
    }

    fun generateDistanceTable(sortedDistanceList: List<DistanceElement>) {
        val table = TableLayout(activity)
        val lp = TableLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        table.apply {
            layoutParams = lp
            isShrinkAllColumns = true
        }

        for (distanceEl in sortedDistanceList) {

            val row = TableRow(activity)
            row.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val phoneNameColumn = generateTableColumn(distanceEl.phoneName)
            val phoneDistanceColumn = generateTableColumn("${distanceEl.distance} mm")

            row.addView(phoneNameColumn)
            row.addView(phoneDistanceColumn)

            table.addView(row)
        }
        //linearLayout.removeAllViews()
        //linearLayout.addView(table)
        //Log.w(TAG, "Table VIEW ADDED + ${linearLayout.visibility} + ${sortedDistanceList.size}")
    }

    private fun generateTableColumn(columnValue: String): TextView {
        val column = TextView(activity)
        column.apply {
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT
            )
            text = columnValue
        }
        return column
    }

    fun getConnectivityManager(): ConnectivityManager =
        activity?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun getWifiRttManager() =
        activity?.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as WifiRttManager

    fun getWifiAwareManager(): WifiAwareManager =
        activity?.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager

    fun showOnUiThread(message: String, duration: Int) {
        activity?.runOnUiThread {
            Toast.makeText(activity, message, duration).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "onDESTROY")
    }

    override fun onStop() {
        super.onStop()
        Log.w(TAG, "onSTOP")
//        novaController.close()

    }

    companion object {
        val SMALL_TEXT_SIZE = 16F
        val MEDIUM_TEXT_SIZE = 18F
        val LARGE_TEXT_SIZE = 24F
    }

}
