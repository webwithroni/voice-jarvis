package com.webwithroni.voicejarvis.orb

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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
 * 7. abstract energy field
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

    private val ripplePaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Reusable paths for the deformable neural membrane.
     *
     * Reusing these objects avoids allocating a new Path every frame.
     */
    private val outerMembranePath =
        Path()

    private val innerMembranePath =
        Path()

    private val ripplePath =
        Path()

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

        /*
         * Feed the organic motion envelope into the particle field.
         *
         * MotionController owns the meaning of the motion.
         * ParticleSystem owns how individual particles react.
         */
        particles.setMotionEnvelope(
            breath =
                motion.breath,
            pulse =
                motion.pulse,
            fieldWarp =
                motion.fieldWarp,
            audioEnergy =
                motion.audioEnergy
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
         * Base visual radius from the available viewport.
         *
         * Dynamic breathing and audio scaling are owned by the
         * individual core/shell/particle motion layers. Keeping the
         * master radius static prevents double amplification.
         */
        val visualRadius =
            minOf(
                width,
                height
            ) *
                0.34f

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
         * 4.5. VOICE ENERGY RIPPLE
         * ------------------------------------------------------
         *
         * The ripple sits between the particle field and the
         * energy membrane so it visually propagates outward.
         */
        drawVoiceRipple(
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
         * Final initialization marker.
         *
         * The Orb is intentionally abstract:
         * particles + energy + motion only.
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
             * particle field organic rather than perfectly
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
     * Build an organic, deformable neural membrane.
     *
     * Multiple low-frequency harmonics create controlled asymmetry.
     * The membrane remains coherent and circular at rest, but subtly
     * deforms with breathing, audio energy and contextual activity.
     */
    private fun buildMembranePath(
        path: Path,
        centerX: Float,
        centerY: Float,
        baseRadius: Float,
        motion: OrbMotionController.Snapshot,
        inner: Boolean
    ) {

        path.reset()

        val points =
            if (inner) 56 else 72

        val stateEnergy =
            when (state) {

                OrbState.THINKING ->
                    1.00f

                OrbState.SPEAKING ->
                    motion.audioEnergy

                OrbState.HEARING ->
                    motion.audioEnergy * 0.70f

                OrbState.ERROR ->
                    motion.errorProgress

                else ->
                    0.20f
            }

        val deformation =
            if (reducedMotion) {
                0f
            } else {
                (
                    motion.breath * 0.55f +
                        motion.pulse * 0.20f +
                        stateEnergy * 0.55f +
                        motion.fieldWarp * 0.35f
                    )
                    .coerceIn(
                        0f,
                        1.5f
                    )
            }

        val time =
            motion.breath +
                motion.audioEnergy * 1.7f +
                motion.fieldWarp * 0.8f

        for (index in 0 until points) {

            val normalized =
                index.toFloat() /
                    points.toFloat()

            val angle =
                normalized *
                    (2f * Math.PI.toFloat())

            /*
             * Three harmonics at different frequencies prevent the
             * membrane from becoming a synchronized "wobbling circle".
             */
            val harmonic1 =
                sin(
                    angle * 3f +
                        time * 1.8f
                ) *
                    0.45f

            val harmonic2 =
                sin(
                    angle * 5f -
                        time * 1.15f +
                        1.3f
                ) *
                    0.30f

            val harmonic3 =
                sin(
                    angle * 7f +
                        time * 0.72f -
                        0.8f
                ) *
                    0.15f

            val radialNoise =
                (
                    harmonic1 +
                        harmonic2 +
                        harmonic3
                    ) *
                    deformation

            val directionBias =
                if (inner) {
                    0.45f
                } else {
                    1f
                }

            val radius =
                baseRadius *
                    (
                        1f +
                            radialNoise *
                            0.045f *
                            directionBias
                        )

            val x =
                centerX +
                    cos(angle) *
                    radius

            val y =
                centerY +
                    sin(angle) *
                    radius *
                    0.94f

            if (index == 0) {
                path.moveTo(
                    x,
                    y
                )
            } else {
                path.lineTo(
                    x,
                    y
                )
            }
        }

        path.close()
    }

    /**
     * Render a soft voice-energy ripple around the neural core.
     *
     * This is deliberately restrained:
     * strong speech produces a visible propagation ring,
     * while quiet speech remains subtle.
     */
    private fun drawVoiceRipple(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        motion: OrbMotionController.Snapshot
    ) {

        if (
            reducedMotion ||
            (
                state != OrbState.HEARING &&
                    state != OrbState.SPEAKING
                )
        ) {
            return
        }

        val energy =
            motion.audioEnergy

        if (energy < 0.05f) {
            return
        }

        /*
         * Build a normalized ripple phase from elapsed motion.
         *
         * The pulse repeatedly propagates outward, but the amplitude
         * remains tied to current audio energy so silence removes it.
         */
        val phase =
            (
                motion.pulse *
                    0.72f +
                    motion.audioEnergy *
                    0.28f
                )
                .coerceIn(
                    0f,
                    1f
                )

        val rippleProgress =
            (
                phase +
                    motion.audioEnergy *
                    0.35f
                )
                .coerceIn(
                    0f,
                    1f
                )

        val startRadius =
            radius *
                motion.innerCoreScale *
                1.12f

        val endRadius =
            radius *
                motion.shellScale *
                (
                    1.18f +
                        energy *
                        0.38f
                    )

        val rippleRadius =
            startRadius +
                (
                    endRadius -
                        startRadius
                    ) *
                    rippleProgress

        val fade =
            (
                1f -
                    rippleProgress
                )
                .coerceIn(
                    0f,
                    1f
                )

        val alpha =
            (
                90f *
                    energy *
                    fade *
                    motion.glowMultiplier
                )
                .toInt()
                .coerceIn(
                    0,
                    120
                )

        if (alpha <= 0) {
            return
        }

        val color =
            stateColor(
                state
            )

        ripplePaint.style =
            Paint.Style.STROKE

        ripplePaint.strokeWidth =
            radius *
                (
                    0.006f +
                        energy *
                        0.010f
                    )

        ripplePaint.color =
            highlightColor(
                color
            )

        ripplePaint.alpha =
            alpha

        /*
         * Use the same organic membrane geometry so the ripple feels
         * like energy propagating through the Orb rather than a HUD circle.
         */
        buildMembranePath(
            path =
                ripplePath,
            centerX =
                centerX,
            centerY =
                centerY,
            baseRadius =
                rippleRadius,
            motion =
                motion,
            inner =
                false
        )

        canvas.drawPath(
            ripplePath,
            ripplePaint
        )

        ripplePaint.alpha =
            0
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
                motion.shellScale

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
         * Deformable neural membrane.
         *
         * Unlike the old static oval, this boundary subtly reacts
         * to breathing, voice energy and cognitive activity.
         */
        ringPaint.style =
            Paint.Style.STROKE

        ringPaint.strokeWidth =
            radius *
                (
                    0.008f +
                        motion.audioEnergy *
                        0.006f
                    )

        ringPaint.color =
            highlightColor(
                color
            )

        ringPaint.alpha =
            (
                75f *
                    motion.glowMultiplier *
                    (
                        0.92f +
                            motion.audioEnergy *
                            0.40f
                        )
                )
                .toInt()
                .coerceIn(
                    20,
                    175
                )

        buildMembranePath(
            path =
                outerMembranePath,
            centerX =
                centerX,
            centerY =
                centerY,
            baseRadius =
                shellRadius,
            motion =
                motion,
            inner =
                false
        )

        canvas.drawPath(
            outerMembranePath,
            ringPaint
        )
    }

    /**
     * Inner shell establishes the darker inner energy volume.
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
                motion.innerCoreScale *
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

        /*
         * Inner neural membrane.
         *
         * This is deliberately weaker than the outer membrane,
         * creating layered depth around the inner energy core.
         */
        ringPaint.style =
            Paint.Style.STROKE

        ringPaint.strokeWidth =
            radius *
                (
                    0.006f +
                        motion.audioEnergy *
                        0.003f
                    )

        ringPaint.color =
            highlightColor(
                color
            )

        ringPaint.alpha =
            (
                42f *
                    motion.glowMultiplier *
                    (
                        0.90f +
                            motion.pulse *
                            0.25f
                        )
                )
                .toInt()
                .coerceIn(
                    12,
                    95
                )

        buildMembranePath(
            path =
                innerMembranePath,
            centerX =
                centerX,
            centerY =
                centerY,
            baseRadius =
                innerRadius,
            motion =
                motion,
            inner =
                true
        )

        canvas.drawPath(
            innerMembranePath,
            ringPaint
        )
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
