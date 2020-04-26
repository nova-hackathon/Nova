package com.samsung.sel.cqe.nova.main

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.samsung.sel.cqe.nova.R

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val aware = findViewById<Button>(R.id.launch_btn)
        aware.setOnClickListener {
            startActivity(Intent(this, NovaActivity::class.java))
        }
        hasWifiAware = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
        androidId = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
    }

    companion object {
        @JvmStatic
        var hasWifiAware = false

        @JvmStatic
        var hasRtt: Boolean = false

        @JvmStatic
        var androidId = ""

    }
}

val TAG = "AwareTag"
val SYNC_MSG_SEP = ":"
const val COMPUTER_COMMUNICATION_TAG = "COMMUNICATION"
