package com.example

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.widget.Toast

class WallpaperBridge(private val activity: Activity) {

  @JavascriptInterface
  fun setAsLiveWallpaper() {
    activity.runOnUiThread {
      try {
        val component = ComponentName(activity, LiveClockWallpaperService::class.java)
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
          putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        Toast.makeText(activity, "Select Home screen / Lock screen to apply", Toast.LENGTH_LONG).show()
      } catch (e: Exception) {
        try {
          val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          activity.startActivity(fallbackIntent)
        } catch (e2: Exception) {
          Toast.makeText(activity, "Please select Live Clock in your system wallpaper settings", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  @JavascriptInterface
  fun syncWallpaperSettings(
    theme: String,
    isAnalog: Boolean,
    is24H: Boolean,
    showSeconds: Boolean,
    smoothSweep: Boolean
  ) {
    val prefs = activity.getSharedPreferences("live_clock_prefs", Context.MODE_PRIVATE)
    prefs.edit()
      .putString("theme", theme)
      .putBoolean("is_analog", isAnalog)
      .putBoolean("is_24h", is24H)
      .putBoolean("show_seconds", showSeconds)
      .putBoolean("smooth_sweep", smoothSweep)
      .apply()
  }

  @JavascriptInterface
  fun setKeepScreenOn(keepOn: Boolean) {
    activity.runOnUiThread {
      if (keepOn) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }

  @JavascriptInterface
  fun setShowOnLockScreen(showWhenLocked: Boolean) {
    activity.runOnUiThread {
      activity.setShowWhenLocked(showWhenLocked)
      activity.setTurnScreenOn(showWhenLocked)
    }
  }
}
