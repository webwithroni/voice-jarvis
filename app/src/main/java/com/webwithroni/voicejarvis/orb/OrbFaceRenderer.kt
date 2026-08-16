package com.webwithroni.voicejarvis.orb

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the subtle humanoid personality layer of the Orb.
 *
 * This class draws only the face:
 *
 * - face cavity
 * - luminous eyes
 * - reactive mouth
 * - subtle neural energy details
 *
 * It intentionally knows nothing about:
 *
 * - Android services
 * - Gemini
 * - microphone capture
 * - TTS
 * - JarvisService
 *
 * OrbRenderer owns the overall rendering composition.
 */
class OrbFaceRenderer {

    private val cavityPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val eyePaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val mouthPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val neuralPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val highlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val cavityRect =
        RectF()

    /**
     * Render the humanoid face.
     *
     * @param canvas target Canvas
     * @param centerX horizontal center
     * @param centerY vertical center
     * @param radius face/core radius
     * @param state current Orb state
     * @param activity contextual Orb activity
     * @param amplitude cleaned audio amplitude [0,1]
     * @param timeSeconds continuous animation time
     */
    fun draw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        state: OrbState,
        activity: OrbActivity,
        amplitude: Float,
        timeSeconds: Float,
        breath: Float = 0f,
        pulse: Float = 0f,
        audioEnergy: Float = 0f,
        fieldWarp: Float = 0f
    ) {

        val safeAmplitude =
            amplitude
                .coerceIn(
                    0f,
                    1f
                )

        /*
         * Unified neural energy signal.
         *
         * Breath keeps the face alive at rest.
         * Pulse adds subtle internal rhythm.
         * Audio makes speech/hearing expressive.
         * Field warp makes thinking feel more computational.
         */
        val neuralEnergy =
            (
                breath * 0.25f +
                    pulse * 0.15f +
                    audioEnergy * 0.45f +
                    fieldWarp * 0.15f +
                    safeAmplitude * 0.20f
                )
                .coerceIn(
                    0f,
                    1f
                )

        val stateColor =
            stateColor(
                state
            )

        /*
         * ------------------------------------------------------
         * FACE CAVITY
         * ------------------------------------------------------
         *
         * A soft dark inner volume.
         *
         * This is deliberately not a literal human head shape.
         * It should feel like a digital consciousness cavity.
         */
        cavityPaint.style =
            Paint.Style.FILL

        cavityPaint.color =
            darkenedColor(
                stateColor,
                0.18f
            )

        cavityPaint.alpha =
            cavityAlpha(
                state
            )

        val cavityWidth =
            radius * 1.25f

        val cavityHeight =
            radius * 1.42f

        cavityRect.set(
            centerX - cavityWidth * 0.5f,
            centerY - cavityHeight * 0.5f,
            centerX + cavityWidth * 0.5f,
            centerY + cavityHeight * 0.5f
        )

        canvas.drawOval(
            cavityRect,
            cavityPaint
        )

        /*
         * ------------------------------------------------------
         * NEURAL ENERGY DETAILS
         * ------------------------------------------------------
         */
        drawNeuralDetails(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            radius = radius,
            state = state,
            activity = activity,
            timeSeconds = timeSeconds,
            color = stateColor,
            breath = breath,
            pulse = pulse,
            audioEnergy = audioEnergy,
            fieldWarp = fieldWarp
        )

        /*
         * ------------------------------------------------------
         * EYES
         * ------------------------------------------------------
         *
         * Luminous apertures rather than cartoon eyeballs.
         */
        val eyeY =
            centerY -
                radius * 0.17f

        val eyeSpacing =
            radius * 0.34f

        val eyeEnergy =
            (
                eyeEnergy(
                    state,
                    safeAmplitude
                ) +
                    neuralEnergy * 0.34f
                )
                .coerceIn(
                    0f,
                    1f
                )

        drawEye(
            canvas,
            centerX - eyeSpacing,
            eyeY,
            radius,
            eyeEnergy,
            stateColor
        )

        drawEye(
            canvas,
            centerX + eyeSpacing,
            eyeY,
            radius,
            eyeEnergy,
            stateColor
        )

        /*
         * ------------------------------------------------------
         * MOUTH
         * ------------------------------------------------------
         */
        drawMouth(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            radius = radius,
            state = state,
            amplitude = safeAmplitude,
            color = stateColor
        )

        /*
         * ------------------------------------------------------
         * CENTRAL HIGHLIGHT
         * ------------------------------------------------------
         */
        if (
            state !=
                OrbState.PAUSED
        ) {

            highlightPaint.style =
                Paint.Style.FILL

            highlightPaint.color =
                OrbColors.WHITE

            val highlightEnergy =
                (
                    neuralEnergy * 0.70f +
                        safeAmplitude * 0.30f
                    )
                    .coerceIn(
                        0f,
                        1f
                    )

            highlightPaint.alpha =
                (
                    20f +
                        highlightEnergy * 75f
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        255
                    )

            val highlightScale =
                1f +
                    (
                        breath * 0.10f +
                            pulse * 0.08f +
                            audioEnergy * 0.14f
                        )

            canvas.drawCircle(
                centerX,
                centerY -
                    radius * 0.46f,
                radius *
                    0.025f *
                    highlightScale,
                highlightPaint
            )
        }
    }

    /**
     * Draw one luminous energy aperture.
     */
    private fun drawEye(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        energy: Float,
        color: Int
    ) {

        eyePaint.style =
            Paint.Style.STROKE

        eyePaint.strokeWidth =
            radius * 0.035f

        eyePaint.strokeCap =
            Paint.Cap.ROUND

        eyePaint.color =
            color

        eyePaint.alpha =
            (
                110f +
                    energy * 120f
            )
                .toInt()
                .coerceIn(
                    0,
                    255
                )

        val eyeWidth =
            radius *
                (
                    0.085f +
                        energy * 0.055f
                    )

        val eyeHeight =
            radius *
                (
                    0.045f +
                        energy * 0.035f
                    )

        val rect =
            RectF(
                x - eyeWidth,
                y - eyeHeight,
                x + eyeWidth,
                y + eyeHeight
            )

        canvas.drawOval(
            rect,
            eyePaint
        )

        /*
         * Tiny central energy point.
         */
        eyePaint.style =
            Paint.Style.FILL

        eyePaint.alpha =
            (
                80f +
                    energy * 145f
            )
                .toInt()
                .coerceIn(
                    0,
                    255
                )

        canvas.drawCircle(
            x,
            y,
            radius * (
                0.012f +
                    energy * 0.014f
                ),
            eyePaint
        )
    }

    /**
     * Draw the mouth according to the current state.
     *
     * Speaking uses amplitude as a tiny waveform.
     * Thinking uses a restrained horizontal pulse.
     */
    private fun drawMouth(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        state: OrbState,
        amplitude: Float,
        color: Int
    ) {

        mouthPaint.style =
            Paint.Style.STROKE

        mouthPaint.strokeCap =
            Paint.Cap.ROUND

        mouthPaint.color =
            color

        mouthPaint.strokeWidth =
            radius * 0.028f

        mouthPaint.alpha =
            when (state) {

                OrbState.PAUSED ->
                    65

                OrbState.ERROR ->
                    210

                else ->
                    175
            }

        val mouthY =
            centerY +
                radius * 0.19f

        when (state) {

            OrbState.SPEAKING -> {

                val width =
                    radius * 0.23f

                val segments =
                    5

                val segmentWidth =
                    width /
                        segments

                var previousX =
                    centerX - width

                var previousY =
                    mouthY

                for (
                    i in 0 until segments
                ) {

                    val x =
                        centerX -
                            width +
                            segmentWidth *
                                (i + 1)

                    val wave =
                        sin(
                            i *
                                1.55f +
                                amplitude *
                                6f
                        ) *
                            (
                                radius *
                                    (
                                        0.025f +
                                            amplitude *
                                                0.065f
                                        )
                            )

                    val y =
                        mouthY +
                            wave

                    canvas.drawLine(
                        previousX,
                        previousY,
                        x,
                        y,
                        mouthPaint
                    )

                    previousX =
                        x

                    previousY =
                        y
                }
            }

            OrbState.THINKING -> {

                val pulse =
                    0.45f +
                        sin(
                            amplitude *
                                2f
                        ) *
                        0.10f

                val width =
                    radius *
                        0.18f *
                        pulse

                canvas.drawLine(
                    centerX - width,
                    mouthY,
                    centerX + width,
                    mouthY,
                    mouthPaint
                )
            }

            OrbState.ERROR -> {

                val width =
                    radius *
                        0.19f

                canvas.drawLine(
                    centerX - width,
                    mouthY,
                    centerX + width,
                    mouthY,
                    mouthPaint
                )
            }

            OrbState.PAUSED -> {

                val width =
                    radius *
                        0.12f

                canvas.drawLine(
                    centerX - width,
                    mouthY,
                    centerX + width,
                    mouthY,
                    mouthPaint
                )
            }

            else -> {

                /*
                 * Neutral tiny energy aperture.
                 */
                val width =
                    radius *
                        (
                            0.10f +
                                amplitude *
                                    0.05f
                            )

                canvas.drawLine(
                    centerX - width,
                    mouthY,
                    centerX + width,
                    mouthY,
                    mouthPaint
                )
            }
        }
    }

    /**
     * Draw subtle internal neural arcs.
     */
    private fun drawNeuralDetails(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        state: OrbState,
        activity: OrbActivity,
        timeSeconds: Float,
        color: Int,
        breath: Float,
        pulse: Float,
        audioEnergy: Float,
        fieldWarp: Float
    ) {

        if (
            state ==
                OrbState.PAUSED
        ) {
            return
        }

        neuralPaint.style =
            Paint.Style.STROKE

        neuralPaint.strokeCap =
            Paint.Cap.ROUND

        neuralPaint.strokeWidth =
            radius * 0.012f

        neuralPaint.color =
            color

        val activityBoost =
            when (activity) {

                OrbActivity.RESEARCHING ->
                    1.35f

                OrbActivity.EXECUTING_TOOL ->
                    1.20f

                OrbActivity.CONTROLLING_DEVICE ->
                    1.15f

                OrbActivity.WAITING_CONFIRMATION ->
                    1.05f

                else ->
                    1f
            }

        val neuralMotion =
            (
                breath * 0.35f +
                    pulse * 0.20f +
                    audioEnergy * 0.30f +
                    fieldWarp * 0.45f
                )
                .coerceIn(
                    0f,
                    1.5f
                )

        neuralPaint.alpha =
            when (state) {

                OrbState.THINKING ->
                    (
                        70f *
                            activityBoost *
                            (
                                1f +
                                    neuralMotion *
                                    0.75f
                                )
                        )
                            .toInt()
                            .coerceIn(
                                0,
                                175
                            )

                OrbState.SPEAKING ->
                    (
                        45f +
                            audioEnergy * 70f +
                            neuralMotion * 20f
                        )
                            .toInt()
                            .coerceIn(
                                20,
                                155
                            )

                OrbState.HEARING ->
                    (
                        38f +
                            audioEnergy * 48f +
                            breath * 12f
                        )
                            .toInt()
                            .coerceIn(
                                18,
                                120
                            )

                else ->
                    (
                        24f +
                            breath * 12f +
                            pulse * 8f
                        )
                            .toInt()
                            .coerceIn(
                                12,
                                70
                            )
            }

        val arcRadius =
            radius *
                0.40f

        val arcPulse =
            (
                1f +
                    sin(
                        timeSeconds *
                            (
                                1.8f +
                                    fieldWarp * 1.20f +
                                    audioEnergy * 0.80f
                                )
                    ) *
                    (
                        0.05f +
                            breath * 0.05f +
                            audioEnergy * 0.08f
                        )
                )

        val left =
            RectF(
                centerX -
                    arcRadius * arcPulse,
                centerY -
                    arcRadius,
                centerX +
                    arcRadius * arcPulse,
                centerY +
                    arcRadius
            )

        canvas.drawArc(
            left,
            210f,
            75f,
            false,
            neuralPaint
        )

        canvas.drawArc(
            left,
            -105f,
            75f,
            false,
            neuralPaint
        )

        /*
         * Tiny vertical neural spine.
         */
        val spineHeight =
            radius *
                0.55f

        val spineX =
            centerX

        val spineTop =
            centerY -
                spineHeight * 0.5f

        val spineBottom =
            centerY +
                spineHeight * 0.5f

        val drift =
            sin(
                timeSeconds *
                    (
                        2.2f +
                            fieldWarp * 1.60f +
                            audioEnergy * 0.90f
                        )
            ) *
                radius *
                (
                    0.012f +
                        breath * 0.010f +
                        pulse * 0.006f +
                        audioEnergy * 0.016f +
                        fieldWarp * 0.010f
                    )

        canvas.drawLine(
            spineX + drift,
            spineTop,
            spineX - drift,
            spineBottom,
            neuralPaint
        )
    }

    private fun stateColor(
        state: OrbState
    ): Int {

        return when (state) {

            OrbState.LISTENING,
            OrbState.HEARING,
            OrbState.SPEAKING ->
                OrbColors.CYAN

            OrbState.THINKING ->
                OrbColors.VIOLET

            OrbState.ERROR ->
                OrbColors.ERROR

            OrbState.PAUSED ->
                OrbColors.PAUSED

            OrbState.PERMISSION_REQUIRED ->
                OrbColors.PERMISSION
        }
    }

    private fun eyeEnergy(
        state: OrbState,
        amplitude: Float
    ): Float {

        return when (state) {

            OrbState.HEARING ->
                (
                    0.55f +
                        amplitude * 0.45f
                    )

            OrbState.SPEAKING ->
                (
                    0.65f +
                        amplitude * 0.35f
                    )

            OrbState.THINKING ->
                0.70f

            OrbState.ERROR ->
                0.85f

            OrbState.PAUSED ->
                0.15f

            OrbState.PERMISSION_REQUIRED ->
                0.50f

            OrbState.LISTENING ->
                0.50f
        }
            .coerceIn(
                0f,
                1f
            )
    }

    private fun cavityAlpha(
        state: OrbState
    ): Int {

        return when (state) {

            OrbState.PAUSED ->
                115

            OrbState.ERROR ->
                165

            OrbState.PERMISSION_REQUIRED ->
                150

            else ->
                135
        }
    }

    private fun darkenedColor(
        color: Int,
        factor: Float
    ): Int {

        val safeFactor =
            factor.coerceIn(
                0f,
                1f
            )

        val r =
            (android.graphics.Color.red(color) *
                safeFactor)
                .toInt()

        val g =
            (android.graphics.Color.green(color) *
                safeFactor)
                .toInt()

        val b =
            (android.graphics.Color.blue(color) *
                safeFactor)
                .toInt()

        return android.graphics.Color.argb(
            255,
            r,
            g,
            b
        )
    }
}
