package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import org.json.JSONObject

class BatteryBridge(private val context: Context) {

  private var batteryLevel: Int = 100
  private var isCharging: Boolean = false

  init {
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val batteryStatus: Intent? = ContextCompat.registerReceiver(
      context,
      null,
      filter,
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
    updateBatteryStatus(batteryStatus)
  }

  private fun updateBatteryStatus(intent: Intent?) {
    if (intent != null) {
      val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
      val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
      val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
      
      isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                   status == BatteryManager.BATTERY_STATUS_FULL
      
      batteryLevel = if (level >= 0 && scale > 0) {
        ((level.toFloat() / scale.toFloat()) * 100).toInt()
      } else {
        100
      }
    }
  }

  @JavascriptInterface
  fun getBatteryStatus(): String {
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val intent = ContextCompat.registerReceiver(
      context,
      null,
      filter,
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
    updateBatteryStatus(intent)

    val json = JSONObject()
    json.put("level", batteryLevel)
    json.put("isCharging", isCharging)
    return json.toString()
  }

  @JavascriptInterface
  fun getBatteryLevel(): Int {
    return batteryLevel
  }

  @JavascriptInterface
  fun isCharging(): Boolean {
    return isCharging
  }
}
