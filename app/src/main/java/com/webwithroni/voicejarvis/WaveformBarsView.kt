package com.webwithroni.voicejarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

class WaveformBarsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barCount = 5
    private var currentLevel = 0.08f
    private var color = Color.parseColor("#5CE7FF")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@WaveformBarsView.color }
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (running) handler.postDelayed(this, 80)
        }
    }

    fun setBarColor(c: Int) { color = c; paint.color = c; invalidate() }
    fun setLevel(level: Float) { currentLevel = level.coerceIn(0.05f, 1f) }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); running = true; handler.post(tick) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); running = false; handler.removeCallbacks(tick) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = width / (barCount * 2.2f)
        val gap = barWidth * 1.2f
        val cy = height / 2f
        val t = System.currentTimeMillis() / 140.0
        for (i in 0 until barCount) {
            val variance = 0.4f + 0.6f * abs(sin(t + i * 1.1))
            val level = (currentLevel * variance.toFloat()).coerceIn(0.06f, 1f)
            val h = height * level
            val left = i * (barWidth + gap)
            val top = cy - h / 2f
            canvas.drawRoundRect(left, top, left + barWidth, top + h, barWidth / 2f, barWidth / 2f, paint)
        }
    }
}
