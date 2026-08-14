package com.webwithroni.voicejarvis.orb

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Main Canvas composition renderer for the Jarvis Orb.
 *
 * Rendering order:
 *
 * 1. atmosphere
 * 2. outer glow
 * 3. distant particles
 * 4. orbit particles
 * 5. energy shell
 * 6. inner shell
 * 7. humanoid face
 *
 * This class owns visual composition only.
 *
 * It does not know about:
 *
 * - Gemini
 * - JarvisService
 * - AudioEngine
 * - Android permissions
 */
class OrbRenderer(
    private val config: OrbConfig = OrbConfig()
) {

    private val atmospherePaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val outerGlowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val particlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val shellPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val innerShellPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val ringPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val highlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val tempRect =
        RectF()

    private val faceRenderer =
        OrbFaceRenderer()

    private val particles =
        OrbParticleSystem(
            config = config
        )

    private val motionController =
        OrbMotionController(
            config = config
        )

    private var state =
        OrbState.LISTENING

    private var activity =
        OrbActivity.NONE

    private var audioAmplitude =
        0f

    private var reducedMotion =
        false

    private var lastFrameNanos =
        0L

    private var initialized =
        false

    /**
     * Change base state.
     */
    fun setState(
        value: OrbState
    ) {

        state =
            value

        motionController.setState(
            value
        )
    }

    /**
     * Change contextual activity.
     */
    fun setActivity(
        value: OrbActivity
    ) {

        activity =
            value

        motionController.setActivity(
            value
        )
    }

    /**
     * Supply cleaned audio amplitude.
     */
    fun setAudioAmplitude(
        value: Float
    ) {

        audioAmplitude =
            value
                .coerceIn(
                    0f,
                    1f
                )

        motionController.setAudioAmplitude(
            audioAmplitude
        )

        particles.setAudioAmplitude(
            audioAmplitude
        )
    }

    /**
     * Enable reduced motion.
     */
    fun setReducedMotion(
        enabled: Boolean
    ) {

        reducedMotion =
            enabled

        motionController.setReducedMotion(
            enabled
        )
    }

    /**
     * Reset the full visual simulation.
     */
    fun reset() {

        particles.reset()

        motionController.reset()

        motionController.setState(
            state
        )

        motionController.setActivity(
            activity
        )

        motionController.setAudioAmplitude(
            audioAmplitude
        )

        lastFrameNanos =
            0L

        initialized =
            false
    }

    /**
     * Render one frame.
     *
     * The View calls this continuously.
     */
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        if (
            width <= 0f ||
            height <= 0f
        ) {
            return
        }

        val nowNanos =
            System.nanoTime()

        val deltaSeconds =
            if (
                lastFrameNanos == 0L
            ) {
                0f
            } else {
                (
                    nowNanos -
                        lastFrameNanos
                    ) / 1_000_000_000f
            }
                .coerceIn(
                    0f,
                    0.05f
                )

        lastFrameNanos =
            nowNanos

        val motion =
            motionController.update(
                deltaSeconds
            )

        particles.update(
            deltaSeconds =
                deltaSeconds,
            state =
                state,
            activity =
                activity
        )

        val centerX =
            width * 0.5f

        val centerY =
            height * 0.5f

        /*
         * The actual visible orb scales from available width.
         *
         * The View itself can remain larger so the glow and
         * particles have breathing room.
         */
        val visualRadius =
            minOf(
                width,
                height
            ) *
                0.34f *
                motion.scale

        /*
         * ------------------------------------------------------
         * 1. ATMOSPHERE
         * ------------------------------------------------------
         */
        drawAtmosphere(
            canvas =
                canvas,
            centerX =
                centerX,
            centerY =
                centerY,
            radius =
                visualRadius,
            motion =
                motion
        )

        /*
         * ------------------------------------------------------
         * 2. OUTER GLOW
         * ------------------------------------------------------
         */
        drawOuterGlow(
            canvas =
                canvas,
            centerX =
                centerX,
            centerY =
                centerY,
            radius =
                visualRadius,
            motion =
                motion
        )

        /*
         * ------------------------------------------------------
         * 3 + 4. PARTICLE FIELD
         * ------------------------------------------------------
         */
        drawParticles(
            canvas =
                canvas,
            centerX =
                centerX,
            centerY =
                centerY,
            radius =
                visualRadius,
            motion =
                motion
        )

        /*
         * ------------------------------------------------------
         * 5. ENERGY SHELL
         * ------------------------------------------------------
         */
        drawEnergyShell(
            canvas =
                canvas,
            centerX =
                centerX,
            centerY =
                centerY,
            radius =
                visualRadius,
            motion =
                motion
        )

        /*
         * ------------------------------------------------------
         * 6. INNER SHELL
         * ------------------------------------------------------
         */
        drawInnerShell(
            canvas =
                canvas,
            centerX =
                centerX,
            centerY =
                centerY,
            radius =
                visualRadius,
            motion =
                motion
        )

        /*
         * ------------------------------------------------------
         * 7. HUMANOID FACE
         * ------------------------------------------------------
         */
        faceRenderer.draw(
            canvas =
                canvas,
            centerX =
                centerX,
            centerY =
                centerY,
            radius =
                visualRadius *
                    config.coreScale,
            state =
                state,
            activity =
                activity,
            amplitude =
                motion.faceEnergy *
                    audioAmplitude,
            timeSeconds =
                if (initialized) {
                    nowNanos /
                        1_000_000_000f
                } else {
                    0f
                }
        )

        /*
         * Final initialization marker.
         */
        initialized =
            true
    }

    /**
     * Soft environmental energy around the Orb.
     */
    private fun drawAtmosphere(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        motion: OrbMotionController.Snapshot
    ) {

        val color =
            stateColor(
                state
            )

        val atmosphereRadius =
            radius *
                1.75f

        atmospherePaint.style =
            Paint.Style.FILL

        atmospherePaint.shader =
            RadialGradient(
                centerX,
                centerY,
                atmosphereRadius,
                darkenedColor(
                    color,
                    0.20f
                ),
                transparentColor(
                    color
                ),
                Shader.TileMode.CLAMP
            )

        atmospherePaint.alpha =
            (
                65f *
                    motion.glowMultiplier
                )
                .toInt()
                .coerceIn(
                    0,
                    120
                )

        canvas.drawCircle(
            centerX,
            centerY,
            atmosphereRadius,
            atmospherePaint
        )

        atmospherePaint.shader =
            null
    }

    /**
     * Large outer glow layer.
     */
    private fun drawOuterGlow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        motion: OrbMotionController.Snapshot
    ) {

        val color =
            stateColor(
                state
            )

        val glowRadius =
            radius *
                (
                    config.shellScale *
                        1.34f
                    )

        outerGlowPaint.style =
            Paint.Style.FILL

        outerGlowPaint.shader =
            RadialGradient(
                centerX,
                centerY,
                glowRadius,
                translucentColor(
                    color,
                    (
                        125f *
                            motion.glowMultiplier
                        )
                            .toInt()
                            .coerceIn(
                                30,
                                180
                            )
                ),
                transparentColor(
                    color
                ),
                Shader.TileMode.CLAMP
            )

        canvas.drawCircle(
            centerX,
            centerY,
            glowRadius,
            outerGlowPaint
        )

        outerGlowPaint.shader =
            null
    }

    /**
     * Render the layered particle field.
     */
    private fun drawParticles(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        motion: OrbMotionController.Snapshot
    ) {

        val halfWidth =
            radius *
                config.shellScale

        val halfHeight =
            radius *
                config.shellScale *
                0.88f

        val fieldCenterY =
            centerY

        val particleScale =
            motion.particleMultiplier

        for (
            particle in particles.particles()
        ) {

            val depth =
                particle.depth

            val orbitalRadius =
                particle.orbitRadius *
                    radius

            val angle =
                particle.angle

            val x =
                centerX +
                    cos(angle) *
                    orbitalRadius

            val y =
                fieldCenterY +
                    sin(angle) *
                    orbitalRadius *
                    0.70f

            /*
             * Slight elliptical compression keeps the
             * particle field humanoid rather than perfectly
             * spherical.
             */
            val clampedX =
                x.coerceIn(
                    centerX - halfWidth,
                    centerX + halfWidth
                )

            val clampedY =
                y.coerceIn(
                    fieldCenterY - halfHeight,
                    fieldCenterY + halfHeight
                )

            val size =
                particle.size *
                    (
                        0.65f +
                            depth *
                            0.55f
                        ) *
                    particleScale

            val alpha =
                (
                    particle.alpha *
                        (
                            0.55f +
                                depth *
                                0.45f
                            ) *
                        particleScale *
                        255f
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        255
                    )

            if (
                size <= 0f ||
                alpha <= 0
            ) {
                continue
            }

            particlePaint.style =
                Paint.Style.FILL

            particlePaint.color =
                particleColor(
                    state,
                    particle.layer
                )

            particlePaint.alpha =
                alpha

            canvas.drawCircle(
                clampedX,
                clampedY,
                size,
                particlePaint
            )
        }
    }

    /**
     * Main translucent energy shell.
     */
    private fun drawEnergyShell(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        motion: OrbMotionController.Snapshot
    ) {

        val color =
            stateColor(
                state
            )

        val shellRadius =
            radius *
                config.shellScale

        shellPaint.style =
            Paint.Style.FILL

        shellPaint.shader =
            RadialGradient(
                centerX -
                    shellRadius *
                    0.18f,
                centerY -
                    shellRadius *
                    0.24f,
                shellRadius *
                    1.35f,
                highlightColor(
                    color
                ),
                transparentColor(
                    color
                ),
                Shader.TileMode.CLAMP
            )

        shellPaint.alpha =
            (
                95f *
                    motion.glowMultiplier
                )
                .toInt()
                .coerceIn(
                    25,
                    170
                )

        canvas.drawCircle(
            centerX,
            centerY,
            shellRadius,
            shellPaint
        )

        shellPaint.shader =
            null

        /*
         * Fine outer ring.
         */
        ringPaint.style =
            Paint.Style.STROKE

        ringPaint.strokeWidth =
            radius *
                0.010f

        ringPaint.color =
            highlightColor(
                color
            )

        ringPaint.alpha =
            (
                75f *
                    motion.glowMultiplier
                )
                .toInt()
                .coerceIn(
                    20,
                    150
                )

        tempRect.set(
            centerX -
                shellRadius,
            centerY -
                shellRadius,
            centerX +
                shellRadius,
            centerY +
                shellRadius
        )

        canvas.drawOval(
            tempRect,
            ringPaint
        )
    }

    /**
     * Inner shell establishes the darker humanoid volume.
     */
    private fun drawInnerShell(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        motion: OrbMotionController.Snapshot
    ) {

        val color =
            stateColor(
                state
            )

        val innerRadius =
            radius *
                config.coreScale *
                1.25f

        innerShellPaint.style =
            Paint.Style.FILL

        innerShellPaint.shader =
            RadialGradient(
                centerX -
                    innerRadius *
                    0.15f,
                centerY -
                    innerRadius *
                    0.20f,
                innerRadius *
                    1.20f,
                darkenedColor(
                    color,
                    0.28f
                ),
                transparentColor(
                    color
                ),
                Shader.TileMode.CLAMP
            )

        innerShellPaint.alpha =
            (
                195f *
                    motion.glowMultiplier
                )
                .toInt()
                .coerceIn(
                    60,
                    230
                )

        canvas.drawCircle(
            centerX,
            centerY,
            innerRadius,
            innerShellPaint
        )

        innerShellPaint.shader =
            null
    }

    /**
     * Semantic state palette.
     */
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

    /**
     * Particle color varies subtly by depth layer.
     */
    private fun particleColor(
        state: OrbState,
        layer: OrbParticleSystem.Layer
    ): Int {

        val color =
            stateColor(
                state
            )

        return when (layer) {

            OrbParticleSystem.Layer.MICRO ->
                color

            OrbParticleSystem.Layer.ORBIT ->
                highlightColor(
                    color
                )

            OrbParticleSystem.Layer.LARGE ->
                OrbColors.WHITE
        }
    }

    private fun highlightColor(
        color: Int
    ): Int {

        val r =
            ((color shr 16) and 0xFF)

        val g =
            ((color shr 8) and 0xFF)

        val b =
            (color and 0xFF)

        val boostedR =
            (
                r +
                    (255 - r) *
                    0.28f
                )
                .toInt()
                .coerceIn(
                    0,
                    255
                )

        val boostedG =
            (
                g +
                    (255 - g) *
                    0.28f
                )
                .toInt()
                .coerceIn(
                    0,
                    255
                )

        val boostedB =
            (
                b +
                    (255 - b) *
                    0.28f
                )
                .toInt()
                .coerceIn(
                    0,
                    255
                )

        return (
            0xFF000000.toInt() or
                (boostedR shl 16) or
                (boostedG shl 8) or
                boostedB
            )
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
            (
                ((color shr 16) and 0xFF) *
                    safeFactor
                )
                .toInt()

        val g =
            (
                ((color shr 8) and 0xFF) *
                    safeFactor
                )
                .toInt()

        val b =
            (
                (color and 0xFF) *
                    safeFactor
                )
                .toInt()

        return (
            0xFF000000.toInt() or
                (r shl 16) or
                (g shl 8) or
                b
            )
    }

    private fun translucentColor(
        color: Int,
        alpha: Int
    ): Int {

        return (
            (alpha.coerceIn(0, 255) shl 24) or
                (color and 0x00FFFFFF)
            )
    }

    private fun transparentColor(
        color: Int
    ): Int {

        return (
            color and
                0x00FFFFFF
            )
    }
}
