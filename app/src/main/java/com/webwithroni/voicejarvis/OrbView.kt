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
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class OrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val PARTICLE_COUNT = 64

        private const val LISTEN_BREATH_MS = 2800L
        private const val HEARING_MOTION_MS = 1500L
        private const val SPEAKING_MOTION_MS = 1100L
        private const val THINKING_MOTION_MS = 1900L
        private const val ERROR_FLASH_MS = 850L

        private const val MIN_AUDIBLE_LEVEL = 0.08f
    }

    private val cyan =
        Color.parseColor("#5CE7FF")

    private val violet =
        Color.parseColor("#9B7CFF")

    private val errorColor =
        Color.parseColor("#FF7181")

    private val mutedColor =
        Color.parseColor("#414A58")

    private var state =
        JarvisState.LISTENING

    /*
     * Audio pipeline:
     *
     * raw amplitude
     *     ↓
     * noise gate
     *     ↓
     * attack/release smoothing
     *     ↓
     * visual amplitude
     */
    private var amplitude =
        0f

    private var targetAmplitude =
        0f

    private var breathScale =
        1f

    private var motionPhase =
        0f

    private var particleRotation =
        0f

    private var colorBlend =
        0f

    private var errorBlend =
        0f

    private var atmosphereAlpha =
        58

    private var coreAlpha =
        235

    private var ringAlpha =
        110

    private var animationRunning =
        false

    private val reducedMotion: Boolean
        get() =
            try {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                ) == 0f
            } catch (_: Exception) {
                false
            }

    private var motionAnimator:
        ValueAnimator? = null

    private var colorAnimator:
        ValueAnimator? = null

    private var errorAnimator:
        ValueAnimator? = null

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val particlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isFocusable = false
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES

        setLayerType(
            LAYER_TYPE_SOFTWARE,
            null
        )

        applyState(
            JarvisState.LISTENING,
            force = true
        )
    }

    fun setJarvisState(
        newState: JarvisState
    ) {
        if (
            state == newState &&
            animationRunning
        ) {
            return
        }

        state = newState

        applyState(
            newState
        )
    }

    fun setAmplitude(
        value: Float
    ) {
        /*
         * The service already performs its own RMS calculation.
         *
         * The Orb still applies another conservative gate and
         * smoothing layer so tiny room/fan/background noise does
         * not make the visual nervous system jitter.
         */
        val cleaned =
            value
                .coerceIn(0f, 1f)
                .let {
                    if (
                        it < MIN_AUDIBLE_LEVEL
                    ) {
                        0f
                    } else {
                        (
                            it - MIN_AUDIBLE_LEVEL
                        ) / (
                            1f - MIN_AUDIBLE_LEVEL
                        )
                    }
                }
                .coerceIn(0f, 1f)

        targetAmplitude =
            cleaned

        /*
         * Fast attack, slower release.
         *
         * Speech onset should feel immediate.
         * Speech decay should feel smooth.
         */
        val smoothing =
            if (
                cleaned >
                    amplitude
            ) {
                0.42f
            } else {
                0.14f
            }

        amplitude =
            (
                amplitude *
                    (1f - smoothing)
                    +
                    cleaned *
                        smoothing
            )
                .coerceIn(
                    0f,
                    1f
                )

        invalidate()
    }

    private fun applyState(
        newState: JarvisState,
        force: Boolean = false
    ) {
        motionAnimator?.cancel()
        colorAnimator?.cancel()
        errorAnimator?.cancel()

        motionAnimator = null
        colorAnimator = null
        errorAnimator = null

        animationRunning =
            false

        breathScale = 1f
        motionPhase = 0f
        particleRotation = 0f
        errorBlend = 0f

        animateColorTarget(
            when (newState) {
                JarvisState.THINKING ->
                    1f

                else ->
                    0f
            },
            immediate = force ||
                reducedMotion
        )

        when (newState) {

            JarvisState.LISTENING -> {
                atmosphereAlpha = 60
                coreAlpha = 235
                ringAlpha = 110

                if (reducedMotion) {
                    breathScale = 1f
                    invalidate()
                } else {
                    startListeningMotion()
                }
            }

            JarvisState.HEARING -> {
                atmosphereAlpha = 72
                coreAlpha = 245
                ringAlpha = 130

                if (reducedMotion) {
                    breathScale = 1f
                    invalidate()
                } else {
                    startReactiveMotion(
                        HEARING_MOTION_MS,
                        0.018f
                    )
                }
            }

            JarvisState.THINKING -> {
                atmosphereAlpha = 78
                coreAlpha = 238
                ringAlpha = 125

                if (reducedMotion) {
                    breathScale = 0.99f
                    particleRotation = 0f
                    invalidate()
                } else {
                    startThinkingMotion()
                }
            }

            JarvisState.SPEAKING -> {
                atmosphereAlpha = 76
                coreAlpha = 245
                ringAlpha = 128

                if (reducedMotion) {
                    breathScale = 1f
                    invalidate()
                } else {
                    startReactiveMotion(
                        SPEAKING_MOTION_MS,
                        0.025f
                    )
                }
            }

            JarvisState.ERROR -> {
                atmosphereAlpha = 68
                coreAlpha = 240
                ringAlpha = 125

                if (reducedMotion) {
                    errorBlend = 1f
                    invalidate()
                } else {
                    startErrorFlash()
                }
            }

            JarvisState.PAUSED -> {
                atmosphereAlpha = 12
                coreAlpha = 150
                ringAlpha = 24
                breathScale = 1f
                colorBlend = 0f
                invalidate()
            }
        }
    }

    private fun animateColorTarget(
        target: Float,
        immediate: Boolean
    ) {
        if (immediate) {
            colorBlend = target
            return
        }

        colorAnimator =
            ValueAnimator.ofFloat(
                colorBlend,
                target
            ).apply {

                duration = 420L

                interpolator =
                    DecelerateInterpolator()

                addUpdateListener {
                    colorBlend =
                        it.animatedValue
                            as Float

                    invalidate()
                }

                start()
            }
    }

    private fun startListeningMotion() {
        animationRunning = true

        motionAnimator =
            ValueAnimator.ofFloat(
                0f,
                1f,
                0f
            ).apply {

                duration =
                    LISTEN_BREATH_MS

                repeatCount =
                    ValueAnimator.INFINITE

                interpolator =
                    LinearInterpolator()

                addUpdateListener {
                    val t =
                        it.animatedValue
                            as Float

                    breathScale =
                        1f +
                            (
                                0.035f *
                                    t
                            )

                    motionPhase =
                        t

                    atmosphereAlpha =
                        (
                            58 +
                                22f *
                                    t
                        )
                            .toInt()

                    invalidate()
                }

                start()
            }
    }

    private fun startReactiveMotion(
        durationMs: Long,
        baselinePulse: Float
    ) {
        animationRunning = true

        motionAnimator =
            ValueAnimator.ofFloat(
                0f,
                1f,
                0f
            ).apply {

                duration =
                    durationMs

                repeatCount =
                    ValueAnimator.INFINITE

                interpolator =
                    LinearInterpolator()

                addUpdateListener {
                    val t =
                        it.animatedValue
                            as Float

                    motionPhase =
                        t

                    /*
                     * Baseline motion is tiny.
                     * The actual personality comes from
                     * the processed audio amplitude.
                     */
                    breathScale =
                        1f +
                            (
                                baselinePulse *
                                    t
                            )

                    /*
                     * Audio drives extra energy.
                     */
                    atmosphereAlpha =
                        (
                            62 +
                                (
                                    amplitude *
                                        70f
                                )
                            )
                                .toInt()
                                .coerceIn(
                                    35,
                                    145
                                )

                    invalidate()
                }

                start()
            }
    }

    private fun startThinkingMotion() {
        animationRunning = true

        motionAnimator =
            ValueAnimator.ofFloat(
                0f,
                1f
            ).apply {

                duration =
                    THINKING_MOTION_MS

                repeatCount =
                    ValueAnimator.INFINITE

                interpolator =
                    LinearInterpolator()

                addUpdateListener {
                    val t =
                        it.animatedValue
                            as Float

                    motionPhase =
                        t

                    particleRotation =
                        t * 360f

                    breathScale =
                        0.982f +
                            (
                                0.022f *
                                    (
                                        0.5f +
                                            0.5f *
                                                sin(
                                                    t *
                                                        Math.PI *
                                                        2.0
                                                )
                                                    .toFloat()
                                    )
                            )

                    atmosphereAlpha =
                        (
                            54 +
                                34f *
                                    (
                                        0.5f +
                                            0.5f *
                                                sin(
                                                    t *
                                                        Math.PI *
                                                        2.0
                                                )
                                                    .toFloat()
                                    )
                            )
                                .toInt()

                    invalidate()
                }

                start()
            }
    }

    private fun startErrorFlash() {
        animationRunning = true

        errorAnimator =
            ValueAnimator.ofFloat(
                0f,
                1f,
                0f
            ).apply {

                duration =
                    ERROR_FLASH_MS

                interpolator =
                    DecelerateInterpolator()

                addUpdateListener {
                    errorBlend =
                        it.animatedValue
                            as Float

                    invalidate()
                }

                start()
            }
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        val cx =
            width / 2f

        val cy =
            height / 2f

        val baseRadius =
            min(
                width,
                height
            ) / 2f

        val audioEnergy =
            when (state) {
                JarvisState.HEARING,
                JarvisState.SPEAKING ->
                    amplitude

                else ->
                    0f
            }

        val audioScale =
            when (state) {
                JarvisState.HEARING ->
                    audioEnergy *
                        0.075f

                JarvisState.SPEAKING ->
                    audioEnergy *
                        0.095f

                else ->
                    0f
            }

        val scale =
            (
                breathScale +
                    audioScale
            )
                .coerceIn(
                    0.94f,
                    1.12f
                )

        val baseStateColor =
            if (
                state ==
                    JarvisState.PAUSED
            ) {
                mutedColor
            } else {
                ColorUtils.blendARGB(
                    cyan,
                    violet,
                    colorBlend
                )
            }

        val coreColor =
            ColorUtils.blendARGB(
                baseStateColor,
                errorColor,
                errorBlend
            )

        /*
         * 01 — outer atmosphere
         */
        paint.style =
            Paint.Style.FILL

        paint.shader =
            RadialGradient(
                cx,
                cy,
                baseRadius *
                    1.08f *
                    scale,
                ColorUtils.setAlphaComponent(
                    coreColor,
                    if (
                        state ==
                            JarvisState.PAUSED
                    ) {
                        12
                    } else {
                        atmosphereAlpha
                    }
                ),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )

        canvas.drawCircle(
            cx,
            cy,
            baseRadius *
                1.08f *
                scale,
            paint
        )

        /*
         * 02 — outer reactive ring
         */
        paint.shader = null
        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth =
            if (
                state ==
                    JarvisState.THINKING
            ) {
                2.4f
            } else {
                2.2f
            }

        paint.color =
            ColorUtils.setAlphaComponent(
                coreColor,
                if (
                    state ==
                        JarvisState.PAUSED
                ) {
                    22
                } else {
                    ringAlpha
                }
            )

        canvas.drawCircle(
            cx,
            cy,
            baseRadius *
                0.74f *
                scale,
            paint
        )

        /*
         * 03 — particle / nervous-system ring
         */
        if (
            state !=
                JarvisState.PAUSED
        ) {
            drawParticleField(
                canvas,
                cx,
                cy,
                baseRadius,
                scale,
                coreColor
            )
        }

        /*
         * 04 — inner energy field
         */
        val fieldRadius =
            baseRadius *
                0.60f *
                scale

        val innerAlpha =
            if (
                state ==
                    JarvisState.PAUSED
            ) {
                28
            } else {
                (
                    115 +
                        audioEnergy *
                            55f
                )
                    .toInt()
                    .coerceIn(
                        90,
                        180
                    )
            }

        paint.shader =
            RadialGradient(
                cx,
                cy,
                fieldRadius,
                ColorUtils.setAlphaComponent(
                    coreColor,
                    innerAlpha
                ),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )

        canvas.drawCircle(
            cx,
            cy,
            fieldRadius,
            paint
        )

        /*
         * 05 — core
         *
         * ~110dp at the standard 240dp Orb size.
         */
        val coreRadius =
            baseRadius *
                0.46f *
                scale

        paint.shader = null
        paint.style =
            Paint.Style.FILL

        paint.color =
            ColorUtils.setAlphaComponent(
                coreColor,
                if (
                    state ==
                        JarvisState.PAUSED
                ) {
                    150
                } else {
                    coreAlpha
                }
            )

        canvas.drawCircle(
            cx,
            cy,
            coreRadius,
            paint
        )

        /*
         * 06 — subtle inner highlight
         */
        if (
            state !=
                JarvisState.PAUSED
        ) {
            paint.shader =
                RadialGradient(
                    cx -
                        coreRadius *
                            0.16f,
                    cy -
                        coreRadius *
                            0.18f,
                    coreRadius *
                        0.90f,
                    ColorUtils.setAlphaComponent(
                        Color.WHITE,
                        36
                    ),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )

            canvas.drawCircle(
                cx,
                cy,
                coreRadius,
                paint
            )
        }

        paint.shader = null
    }

    private fun drawParticleField(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        baseRadius: Float,
        scale: Float,
        color: Int
    ) {
        val ringRadius =
            baseRadius *
                0.67f *
                scale

        val rotation =
            Math.toRadians(
                particleRotation.toDouble()
            )

        val speed =
            when (state) {
                JarvisState.THINKING ->
                    1.0f

                JarvisState.SPEAKING ->
                    0.25f

                JarvisState.HEARING ->
                    0.18f

                else ->
                    0.06f
            }

        val effectiveRotation =
            rotation *
                speed

        for (
            i in 0 until PARTICLE_COUNT
        ) {
            val angle =
                (
                    2.0 *
                        Math.PI *
                        i /
                        PARTICLE_COUNT
                ) +
                    effectiveRotation

            val wave =
                sin(
                    (
                        motionPhase *
                            Math.PI *
                            2.0
                    ) +
                        i *
                            0.34
                )
                    .toFloat()

            val audioJitter =
                if (
                    state ==
                        JarvisState.HEARING ||
                    state ==
                        JarvisState.SPEAKING
                ) {
                    amplitude *
                        0.045f *
                        wave
                } else {
                    0f
                }

            val r =
                ringRadius *
                    (
                        1f +
                            audioJitter
                    )

            val alpha =
                when (state) {
                    JarvisState.THINKING ->
                        (
                            105 +
                                65 *
                                    (
                                        0.5f +
                                            0.5f *
                                                wave
                                    )
                            )
                                .toInt()

                    JarvisState.SPEAKING ->
                        (
                            95 +
                                amplitude *
                                    95f
                            )
                                .toInt()
                                .coerceIn(
                                    80,
                                    180
                                )

                    JarvisState.HEARING ->
                        (
                            90 +
                                amplitude *
                                    80f
                            )
                                .toInt()
                                .coerceIn(
                                    70,
                                    170
                                )

                    else ->
                        125
                }

            val size =
                when (state) {
                    JarvisState.THINKING ->
                        1.45f +
                            0.55f *
                                (
                                    0.5f +
                                        0.5f *
                                            wave
                                )

                    JarvisState.SPEAKING,
                    JarvisState.HEARING ->
                        1.35f +
                            amplitude *
                                1.25f

                    else ->
                        1.55f
                }

            particlePaint.style =
                Paint.Style.FILL

            particlePaint.color =
                ColorUtils.setAlphaComponent(
                    color,
                    alpha
                        .coerceIn(
                            0,
                            255
                        )
                )

            canvas.drawCircle(
                cx +
                    (
                        r *
                            cos(angle)
                    )
                        .toFloat(),
                cy +
                    (
                        r *
                            sin(angle)
                    )
                        .toFloat(),
                size,
                particlePaint
            )
        }
    }

    override fun onDetachedFromWindow() {
        motionAnimator?.cancel()
        colorAnimator?.cancel()
        errorAnimator?.cancel()

        motionAnimator = null
        colorAnimator = null
        errorAnimator = null

        super.onDetachedFromWindow()
    }
}
