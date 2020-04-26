package com.samsung.sel.cqe.nova.main

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.rtt.WifiRttManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.samsung.sel.cqe.nova.R
import com.samsung.sel.cqe.nova.main.aware.PhoneStatus
import com.samsung.sel.cqe.nova.main.controller.NovaController
import java.util.*

class NovaActivity : AppCompatActivity() {

    private lateinit var novaController: NovaController

    private val input: TextView by lazy { findViewById<TextView>(R.id.input) }
    private val serverList: TextView by lazy { findViewById<TextView>(R.id.serversList) }
    private val statusText: TextView by lazy { findViewById<TextView>(R.id.status_view) }
    private val timeTextView: TextView by lazy { findViewById<TextView>(R.id.timeTextView) }
    private val phoneNameView: TextView by lazy { findViewById<TextView>(R.id.phoneName) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nova)
        val phoneID = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
        val phoneName = Settings.Global.getString(this.contentResolver, "device_name")
        novaController = NovaController(this,phoneID,phoneName)
        initStatusTextView()
    }

    private fun initStatusTextView() {
        statusText.gravity = Gravity.CENTER
        statusText.setBackgroundColor(Color.GREEN)
    }

    fun initPhoneNameView(phoneNameAndRank: String) {
        phoneNameView.text = phoneNameAndRank
        phoneNameView.gravity = Gravity.CENTER
        val lowerCaseName = phoneNameAndRank.toLowerCase(Locale.US)
        when {
            lowerCaseName.startsWith("grn") -> phoneNameView.setBackgroundColor(Color.GREEN)
            lowerCaseName.startsWith("red") -> {
                phoneNameView.setBackgroundColor(Color.RED)
                phoneNameView.setTextColor(Color.WHITE)
            }
            lowerCaseName.startsWith("blu") -> {
                phoneNameView.setBackgroundColor(Color.BLUE)
                phoneNameView.setTextColor(Color.WHITE)
            }
        }
    }

    fun appendToClientTextField(text: String) = runOnUiThread {
        input.text = "${input.text}\n$text"
    }

    fun setStatusOnTextView(status: PhoneStatus, masterName: String) = runOnUiThread {
        var text = "\t Status - $status."
        statusText.text = if(masterName.isEmpty()) text.plus(" \t") else text.plus(" My master - $masterName.\t")
        if (status == PhoneStatus.MASTER){
            statusText.setBackgroundColor(Color.RED)
            statusText.setTextColor(Color.WHITE)
        }else{
            statusText.setBackgroundColor(Color.GREEN)
        }
    }

    fun appendToServerTextField(text: String) {
        serverList.text = "${serverList.text}\n$text"
    }

    fun setTimeOnTextView(text: String) {
        runOnUiThread { timeTextView.text = text }
    }

    fun getConnectivityManager(): ConnectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun getWifiRttManager(): WifiRttManager =
        getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as WifiRttManager


    fun getWifiAwareManager(): WifiAwareManager =
        getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager


    fun showOnUiThread(message: String, duration: Int) {
        runOnUiThread {
            Toast.makeText(this, message, duration).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        novaController.close()
    }

}
