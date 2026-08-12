package com.webwithroni.voicejarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class OrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val cyan = Color.parseColor("#5CE7FF")
    private val violet = Color.parseColor("#9B7CFF")
    private val errorColor = Color.parseColor("#FF7181")
    private val mutedColor = Color.parseColor("#414A58")

    private var state: JarvisState = JarvisState.LISTENING
    private var amplitude = 0f

    private var breathScale = 1f
    private var colorBlend = 0f
    private var errorBlend = 0f
    private var atmosphereAlpha = 60

    private val reducedMotion: Boolean
        get() = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) { false }

    private var breatheAnimator: ValueAnimator? = null
    private var thinkAnimator: ValueAnimator? = null
    private var errorAnimator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particleCount = 60

    init { applyState(JarvisState.LISTENING) }

    fun setJarvisState(newState: JarvisState) {
        if (state == newState) return
        state = newState
        applyState(newState)
    }

    fun setAmplitude(value: Float) {
        val gated = if (value < 0.15f) 0f else value
        amplitude = (amplitude * 0.7f + gated * 0.3f).coerceIn(0f, 1f)
        invalidate()
    }

    private fun applyState(newState: JarvisState) {
        breatheAnimator?.cancel(); thinkAnimator?.cancel(); errorAnimator?.cancel()
        when (newState) {
            JarvisState.LISTENING -> startBreathing(false)
            JarvisState.HEARING -> startBreathing(true)
            JarvisState.SPEAKING -> startBreathing(true)
            JarvisState.THINKING -> startThinking()
            JarvisState.ERROR -> startErrorFlash()
            JarvisState.PAUSED -> {
                breathScale = 1f; colorBlend = 0f; atmosphereAlpha = 10
                invalidate()
            }
        }
    }

    private fun startBreathing(fast: Boolean) {
        if (reducedMotion) { breathScale = 1f; colorBlend = 0f; invalidate(); return }
        breatheAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = if (fast) 900L else 2800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                breathScale = 1f + (0.035f * t)
                colorBlend = 0f
                atmosphereAlpha = (60 + t * 40).toInt()
                invalidate()
            }
            start()
        }
    }

    private fun startThinking() {
        if (reducedMotion) { colorBlend = 1f; breathScale = 0.97f; invalidate(); return }
        thinkAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                colorBlend = 0.55f + (t * 0.45f)
                breathScale = 0.97f + (t * 0.05f)
                atmosphereAlpha = (50 + t * 50).toInt()
                invalidate()
            }
            start()
        }
    }

    private fun startErrorFlash() {
        errorAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 850L
            interpolator = LinearInterpolator()
            addUpdateListener { errorBlend = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = min(width, height) / 2f

        val ampBoost = if (state == JarvisState.HEARING || state == JarvisState.SPEAKING) amplitude * 0.25f else 0f
        val scale = breathScale + ampBoost

        val coreColor = if (state == JarvisState.PAUSED) mutedColor
            else ColorUtils.blendARGB(ColorUtils.blendARGB(cyan, violet, colorBlend), errorColor, errorBlend)

        val alphaAtmo = if (state == JarvisState.PAUSED) 10 else atmosphereAlpha

        paint.shader = RadialGradient(cx, cy, baseRadius * scale,
            ColorUtils.setAlphaComponent(coreColor, alphaAtmo), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, baseRadius * scale, paint)

        val ringRadius = baseRadius * 0.73f * scale
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = ColorUtils.setAlphaComponent(coreColor, if (state == JarvisState.PAUSED) 20 else 110)
        canvas.drawCircle(cx, cy, ringRadius, paint)
        paint.style = Paint.Style.FILL

        if (state != JarvisState.PAUSED) {
            drawParticleField(canvas, cx, cy, baseRadius, scale, coreColor)
        }

        val fieldRadius = baseRadius * 0.55f * scale
        paint.shader = RadialGradient(cx, cy, fieldRadius,
            ColorUtils.setAlphaComponent(coreColor, if (state == JarvisState.PAUSED) 25 else 150),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, fieldRadius, paint)

        val coreRadius = baseRadius * 0.32f * scale
        paint.shader = null
        paint.color = coreColor
        canvas.drawCircle(cx, cy, coreRadius, paint)
    }

    private fun drawParticleField(canvas: Canvas, cx: Float, cy: Float, baseRadius: Float, scale: Float, color: Int) {
        particlePaint.color = ColorUtils.setAlphaComponent(color, 140)
        val ringRadius = baseRadius * 0.66f * scale
        val t = System.currentTimeMillis() / 320.0
        for (i in 0 until particleCount) {
            val angle = 2 * Math.PI * i / particleCount
            val jitter = 0.05f * sin(t + i * 0.35).toFloat() + 0.03f * sin(t * 1.7 + i).toFloat()
            val r = ringRadius * (1f + jitter * (0.6f + amplitude))
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            canvas.drawCircle(x, y, 1.6f, particlePaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breatheAnimator?.cancel(); thinkAnimator?.cancel(); errorAnimator?.cancel()
    }
}
