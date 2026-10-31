package ru.gs8triple

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private var left = 400
    private var center = 760

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences("gs8triple_v2", MODE_PRIVATE)
        left = prefs.getInt("left", 400)
        center = prefs.getInt("center", 760)
        showPanel()
    }

    private fun showPanel() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(0xDD202B38.toInt())
        }
        box.addView(TextView(this).apply {
            text = "GS8 TRIPLE • НАСТРОЙКА РАЗМЕРОВ"
            setTextColor(-1); textSize = 12f
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val leftButton = Button(this).apply { text = "◀ ЛЕВАЯ ГРАНИЦА ▶" }
        val centerButton = Button(this).apply { text = "◀ ЦЕНТР ▶" }
        row.addView(leftButton, LinearLayout.LayoutParams(0, 64, 1f))
        row.addView(centerButton, LinearLayout.LayoutParams(0, 64, 1f))
        box.addView(row)
        val summary = TextView(this).apply { setTextColor(-1); text = "Лево $left • Центр $center" }
        box.addView(summary)
        fun update() { summary.text = "Лево $left • Центр $center" }
        leftButton.setOnTouchListener(Drag { dx -> left = (left + dx).coerceIn(200, 1500); update() })
        centerButton.setOnTouchListener(Drag { dx -> center = (center + dx).coerceIn(200, 1400); update() })
        box.addView(Button(this).apply {
            text = "СОХРАНИТЬ РАЗМЕРЫ"
            setOnClickListener {
                getSharedPreferences("gs8triple_v2", MODE_PRIVATE).edit()
                    .putInt("left", left).putInt("center", center).apply()
                Toast.makeText(this@OverlayService, "Размеры сохранены. Открой GS8 Triple и нажми «Разложить окна».", Toast.LENGTH_LONG).show()
            }
        })
        box.addView(Button(this).apply { text = "ЗАКРЫТЬ"; setOnClickListener { stopSelf() } })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 70 }
        wm.addView(box, params)
        panel = box
    }

    private inner class Drag(private val onDelta: (Int) -> Unit) : View.OnTouchListener {
        private var lastX = 0f
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastX = event.rawX; return true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ((event.rawX - lastX) / resources.displayMetrics.density).toInt()
                    if (dx != 0) { onDelta(dx * 2); lastX = event.rawX }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
            }
            return false
        }
    }

    override fun onDestroy() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
