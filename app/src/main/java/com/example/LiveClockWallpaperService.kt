package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class LiveClockWallpaperService : WallpaperService() {

  override fun onCreateEngine(): Engine {
    return LiveClockEngine()
  }

  inner class LiveClockEngine : Engine() {
    private val handler = Handler(Looper.getMainLooper())
    private var visible = false
    private var width = 0
    private var height = 0

    // Drawing paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(180, 255, 255, 255)
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(30, 255, 255, 255)
      style = Paint.Style.FILL
    }
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(45, 255, 255, 255)
      style = Paint.Style.STROKE
      strokeWidth = 2f
    }
    private val handHourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
    }
    private val handMinutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
    }
    private val handSecondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
    }
    private val dialMajorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
    }
    private val dialMinorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(70, 255, 255, 255)
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
    }
    private val dialNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(220, 255, 255, 255)
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Wallpaper Particles
    private val particles = ArrayList<EngineParticle>()

    // Battery status
    private var batteryPct = 100
    private var isCharging = false

    private val batteryReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
          val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
          val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
          val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
          isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
              status == BatteryManager.BATTERY_STATUS_FULL
          batteryPct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 100
        }
      }
    }

    private val drawRunnable = object : Runnable {
      override fun run() {
        drawFrame()
        if (visible) {
          handler.postDelayed(this, 16) // ~60fps smooth loop
        }
      }
    }

    override fun onCreate(surfaceHolder: SurfaceHolder?) {
      super.onCreate(surfaceHolder)
      setTouchEventsEnabled(true)
      val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
      ContextCompat.registerReceiver(
        this@LiveClockWallpaperService,
        batteryReceiver,
        filter,
        ContextCompat.RECEIVER_EXPORTED
      )
    }

    override fun onDestroy() {
      super.onDestroy()
      handler.removeCallbacks(drawRunnable)
      try {
        unregisterReceiver(batteryReceiver)
      } catch (_: Exception) {}
    }

    override fun onVisibilityChanged(visible: Boolean) {
      this.visible = visible
      if (visible) {
        handler.post(drawRunnable)
      } else {
        handler.removeCallbacks(drawRunnable)
      }
    }

    override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, w: Int, h: Int) {
      super.onSurfaceChanged(holder, format, w, h)
      width = w
      height = h
      initParticles(w, h)
      drawFrame()
    }

    override fun onTouchEvent(event: MotionEvent?) {
      if (event != null && (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE)) {
        // Add interactive touch particles
        val touchX = event.x
        val touchY = event.y
        for (i in 0..2) {
          particles.add(
            EngineParticle(
              x = touchX + (Math.random().toFloat() - 0.5f) * 40f,
              y = touchY + (Math.random().toFloat() - 0.5f) * 40f,
              size = Math.random().toFloat() * 6f + 2f,
              vx = (Math.random().toFloat() - 0.5f) * 3f,
              vy = (Math.random().toFloat() - 0.5f) * 3f,
              alpha = 0.9f
            )
          )
          if (particles.size > 80) {
            particles.removeAt(0)
          }
        }
      }
      super.onTouchEvent(event)
    }

    private fun initParticles(w: Int, h: Int) {
      particles.clear()
      if (w <= 0 || h <= 0) return
      val count = 40
      for (i in 0 until count) {
        particles.add(
          EngineParticle(
            x = Math.random().toFloat() * w,
            y = Math.random().toFloat() * h,
            size = Math.random().toFloat() * 4f + 1f,
            vx = (Math.random().toFloat() - 0.5f) * 0.6f,
            vy = (Math.random().toFloat() - 0.5f) * 0.6f,
            alpha = Math.random().toFloat() * 0.7f + 0.2f
          )
        )
      }
    }

    private fun drawFrame() {
      val holder = surfaceHolder ?: return
      var canvas: Canvas? = null
      try {
        canvas = holder.lockCanvas()
        if (canvas != null && width > 0 && height > 0) {
          renderScene(canvas)
        }
      } finally {
        if (canvas != null) {
          try {
            holder.unlockCanvasAndPost(canvas)
          } catch (_: Exception) {}
        }
      }
    }

    private fun renderScene(canvas: Canvas) {
      val prefs = getSharedPreferences("live_clock_prefs", Context.MODE_PRIVATE)
      val themeName = prefs.getString("theme", "midnight") ?: "midnight"
      val isAnalog = prefs.getBoolean("is_analog", true)
      val is24H = prefs.getBoolean("is_24h", false)
      val showSeconds = prefs.getBoolean("show_seconds", true)
      val smoothSweep = prefs.getBoolean("smooth_sweep", true)

      // Theme Colors
      val (bgTop, bgMid, bgBot, accentColor) = when (themeName) {
        "aurora" -> listOf(0xFF041B1D.toInt(), 0xFF0C352F.toInt(), 0xFF03141F.toInt(), 0xFF69F0AE.toInt())
        "cyberpunk" -> listOf(0xFF120324.toInt(), 0xFF290838.toInt(), 0xFF080D21.toInt(), 0xFFFF4081.toInt())
        "sunset" -> listOf(0xFF240B15.toInt(), 0xFF441724.toInt(), 0xFF1B091F.toInt(), 0xFFFFB74D.toInt())
        "emerald" -> listOf(0xFF061912.toInt(), 0xFF0D2E21.toInt(), 0xFF04140D.toInt(), 0xFF81C784.toInt())
        "minimal" -> listOf(0xFF000000.toInt(), 0xFF080808.toInt(), 0xFF000000.toInt(), 0xFFE0E0E0.toInt())
        else -> listOf(0xFF090B14.toInt(), 0xFF151A30.toInt(), 0xFF0D1120.toInt(), 0xFF64B5F6.toInt())
      }

      // 1. Draw Background Gradient
      val bgShader = LinearGradient(
        0f, 0f, width.toFloat(), height.toFloat(),
        intArrayOf(bgTop, bgMid, bgBot),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
      )
      bgPaint.shader = bgShader
      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

      // 2. Draw Floating Wallpaper Particles
      for (p in particles) {
        p.update(width, height)
        particlePaint.color = accentColor
        particlePaint.alpha = (p.alpha * 255).toInt().coerceIn(10, 255)
        canvas.drawCircle(p.x, p.y, p.size, particlePaint)
      }

      val now = Calendar.getInstance()
      val timeMillis = System.currentTimeMillis()
      val ms = (timeMillis % 1000).toInt()
      val sec = now.get(Calendar.SECOND)
      val min = now.get(Calendar.MINUTE)
      val hr = now.get(Calendar.HOUR_OF_DAY)

      val dateStr = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
      datePaint.color = accentColor
      datePaint.textSize = (width * 0.042f).coerceIn(32f, 52f)

      // Top Status (Date + Battery Badge)
      val topMargin = (height * 0.11f)
      canvas.drawText(dateStr.uppercase(Locale.getDefault()), width / 2f, topMargin, datePaint)

      // Battery Pill Indicator
      val battCardW = 200f
      val battCardH = 50f
      val battLeft = (width - battCardW) / 2f
      val battTop = topMargin + 16f
      val cardRect = RectF(battLeft, battTop, battLeft + battCardW, battTop + battCardH)
      canvas.drawRoundRect(cardRect, 25f, 25f, cardPaint)
      canvas.drawRoundRect(cardRect, 25f, 25f, cardStrokePaint)

      val boltText = if (isCharging) "⚡ " else ""
      subTextPaint.textSize = 28f
      canvas.drawText("$boltText$batteryPct% Battery", width / 2f, battTop + 34f, subTextPaint)

      val centerY = height * 0.46f

      if (isAnalog) {
        // Draw Analog Clock Dial
        val clockRadius = (width * 0.36f).coerceIn(160f, 320f)

        // Clock Face Background
        val dialShader = RadialGradient(
          width / 2f, centerY, clockRadius,
          intArrayOf(Color.argb(35, 255, 255, 255), Color.argb(120, 0, 0, 0)),
          floatArrayOf(0.4f, 1f),
          Shader.TileMode.CLAMP
        )
        val dialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = dialShader }
        canvas.drawCircle(width / 2f, centerY, clockRadius, dialBgPaint)
        canvas.drawCircle(width / 2f, centerY, clockRadius, cardStrokePaint)

        // Draw Dial Ticks & Numerals
        dialMajorTickPaint.color = accentColor
        dialMajorTickPaint.strokeWidth = 5f
        dialMinorTickPaint.strokeWidth = 2.5f
        dialNumPaint.textSize = clockRadius * 0.15f

        for (i in 0 until 60) {
          val angle = Math.toRadians((i * 6).toDouble())
          val isMajor = i % 5 === 0
          val r1 = clockRadius - 12f
          val r2 = if (isMajor) r1 - 20f else r1 - 10f
          val x1 = (width / 2f + r1 * sin(angle)).toFloat()
          val y1 = (centerY - r1 * cos(angle)).toFloat()
          val x2 = (width / 2f + r2 * sin(angle)).toFloat()
          val y2 = (centerY - r2 * cos(angle)).toFloat()

          canvas.drawLine(x1, y1, x2, y2, if (isMajor) dialMajorTickPaint else dialMinorTickPaint)
        }

        for (num in 1..12) {
          val angle = Math.toRadians((num * 30).toDouble())
          val numR = clockRadius - 46f
          val x = (width / 2f + numR * sin(angle)).toFloat()
          val y = (centerY - numR * cos(angle)).toFloat() + dialNumPaint.textSize / 3f
          canvas.drawText(num.toString(), x, y, dialNumPaint)
        }

        // Calculate Hands
        val exactSec = if (smoothSweep) sec + (ms / 1000f) else sec.toFloat()
        val exactMin = min + exactSec / 60f
        val exactHr = (hr % 12) + exactMin / 60f

        val hrAngle = Math.toRadians((exactHr * 30).toDouble())
        val minAngle = Math.toRadians((exactMin * 6).toDouble())
        val secAngle = Math.toRadians((exactSec * 6).toDouble())

        // Hour Hand
        handHourPaint.strokeWidth = 10f
        val hrLen = clockRadius * 0.52f
        canvas.drawLine(
          width / 2f, centerY,
          (width / 2f + hrLen * sin(hrAngle)).toFloat(),
          (centerY - hrLen * cos(hrAngle)).toFloat(),
          handHourPaint
        )

        // Minute Hand
        handMinutePaint.strokeWidth = 7f
        val minLen = clockRadius * 0.76f
        canvas.drawLine(
          width / 2f, centerY,
          (width / 2f + minLen * sin(minAngle)).toFloat(),
          (centerY - minLen * cos(minAngle)).toFloat(),
          handMinutePaint
        )

        // Second Hand
        if (showSeconds) {
          handSecondPaint.color = accentColor
          handSecondPaint.strokeWidth = 3.5f
          val secLen = clockRadius * 0.86f
          canvas.drawLine(
            width / 2f, centerY,
            (width / 2f + secLen * sin(secAngle)).toFloat(),
            (centerY - secLen * cos(secAngle)).toFloat(),
            handSecondPaint
          )
        }

        // Center Pin
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = accentColor
          style = Paint.Style.FILL
        }
        canvas.drawCircle(width / 2f, centerY, 12f, pinPaint)
        val pinCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          style = Paint.Style.FILL
        }
        canvas.drawCircle(width / 2f, centerY, 5f, pinCenter)

      } else {
        // Draw Digital Clock
        val hourVal = if (is24H) hr else (if (hr % 12 == 0) 12 else hr % 12)
        val hourStr = if (is24H) String.format("%02d", hourVal) else hourVal.toString()
        val minStr = String.format("%02d", min)
        val timeStr = "$hourStr:$minStr"

        timePaint.textSize = (width * 0.22f).coerceIn(120f, 220f)
        val textBounds = Rect()
        timePaint.getTextBounds(timeStr, 0, timeStr.length, textBounds)
        canvas.drawText(timeStr, width / 2f, centerY + textBounds.height() / 2f, timePaint)

        if (!is24H) {
          val ampm = if (hr >= 12) "PM" else "AM"
          datePaint.textSize = 40f
          canvas.drawText(ampm, width / 2f, centerY + textBounds.height() + 40f, datePaint)
        }
      }

      // 4. Draw Weather Bottom Summary Card
      val weatherCardW = (width * 0.82f).coerceIn(280f, 480f)
      val weatherCardH = 110f
      val weatherLeft = (width - weatherCardW) / 2f
      val weatherTop = height * 0.72f
      val wCardRect = RectF(weatherLeft, weatherTop, weatherLeft + weatherCardW, weatherTop + weatherCardH)

      canvas.drawRoundRect(wCardRect, 30f, 30f, cardPaint)
      canvas.drawRoundRect(wCardRect, 30f, 30f, cardStrokePaint)

      val weatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
      canvas.drawText("🌤️ Live Weather", weatherLeft + 30f, weatherTop + 50f, weatherPaint)

      subTextPaint.textAlign = Paint.Align.LEFT
      subTextPaint.textSize = 26f
      canvas.drawText("Tap wallpaper in app to refresh", weatherLeft + 30f, weatherTop + 85f, subTextPaint)
    }
  }

  data class EngineParticle(
    var x: Float,
    var y: Float,
    var size: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float
  ) {
    fun update(w: Int, h: Int) {
      x += vx
      y += vy
      if (x < 0) x = w.toFloat()
      if (x > w) x = 0f
      if (y < 0) y = h.toFloat()
      if (y > h) y = 0f
    }
  }
}
