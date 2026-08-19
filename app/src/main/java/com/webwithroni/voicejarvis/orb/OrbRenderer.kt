package com.webwithroni.voicejarvis.orb

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow

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
     * Fine internal energy-current paint.
     *
     * Used for subtle neural-core wisps without introducing
     * another heavy particle/path system.
     */
    private val coreWispPaint =
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

    /*
     * Reused spherical projection buffer.
     *
     * Avoids allocating FloatArray objects for every particle
     * on every rendered frame.
     */
    private val sphericalPositionBuffer =
        FloatArray(3)

    /*
     * Reusable render-order buffer.
     *
     * Stores particle indices rather than Particle references so
     * the frame renderer avoids creating temporary collections.
     */
    private val particleRenderOrder =
        IntArray(
            config.particleCount
        )

    /*
     * 3.3.2.14 — Allocation-free depth buckets.
     *
     * Fixed bucket count keeps render ordering bounded and avoids
     * per-frame sorting of the full particle list.
     */
    private companion object {
        /*
         * 3.3.2.16 — Higher-resolution depth quantization.
         *
         * 64 fixed buckets provide finer separation between particles
         * while preserving the allocation-free O(n) render ordering.
         */
        const val DEPTH_BUCKET_COUNT = 64
    }

    private val depthBucketCounts =
        IntArray(
            DEPTH_BUCKET_COUNT
        )

    private val depthBucketOffsets =
        IntArray(
            DEPTH_BUCKET_COUNT
        )

    private val particleDepthBuckets =
        IntArray(
            config.particleCount
        )

    /*
     * Reused transformed-Z values.
     *
     * The depth bucket pass computes these once per frame.
     * The draw pass reuses them instead of recalculating depth.
     */
    private val particleTransformedZ =
        FloatArray(
            config.particleCount
        )

    /*
     * Complete camera-space coordinates reused by the render pass.
     */
    private val particleTransformedX =
        FloatArray(
            config.particleCount
        )

    private val particleTransformedY =
        FloatArray(
            config.particleCount
        )

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
         * ------------------------------------------------------
         * 7. NEURAL CORE CURRENTS
         * ------------------------------------------------------
         *
         * Fine internal energy movement prevents the core from
         * reading as a flat colored disk.
         */
        drawNeuralCore(
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
    /**
     * Render the layered particle field as a lightweight 3D sphere.
     *
     * Pipeline:
     *
     * spherical coordinates
     *        ↓
     * orbital rotation
     *        ↓
     * perspective projection
     *        ↓
     * depth-aware size/alpha
     *        ↓
     * Canvas particle
     */
    private fun drawParticles(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        motion: OrbMotionController.Snapshot
    ) {

        val particleScale =
            motion.particleMultiplier

        val time =
            particles.elapsedSeconds()

        /*
         * State-driven orbital speed.
         */
        val rotationSpeed =
            when (state) {

                OrbState.LISTENING ->
                    0.22f

                OrbState.HEARING ->
                    0.48f

                OrbState.THINKING ->
                    0.82f

                OrbState.SPEAKING ->
                    0.58f

                OrbState.ERROR ->
                    0.30f

                OrbState.PAUSED ->
                    0.04f

                OrbState.PERMISSION_REQUIRED ->
                    0.10f
            }

        val yaw =
            time *
                rotationSpeed

        val pitch =
            sin(
                time * 0.38f
            ) * 0.10f

        val cosYaw =
            cos(yaw)

        val sinYaw =
            sin(yaw)

        val cosPitch =
            cos(pitch)

        val sinPitch =
            sin(pitch)

        /*
         * Virtual camera distance.
         */
        val cameraDistance =
            3.8f

        val renderParticles =
            particles.particles()

        /*
         * 3.3.2.14 — Fixed depth bucket ordering.
         *
         * Pipeline:
         *
         *   spherical position
         *          ↓
         *   yaw + pitch rotation
         *          ↓
         *   transformed Z
         *          ↓
         *   depth bucket
         *          ↓
         *   far → near render order
         *
         * This avoids the quadratic insertion-sort cost.
         */

        java.util.Arrays.fill(
            depthBucketCounts,
            0
        )

        val renderCount =
            minOf(
                renderParticles.size,
                particleRenderOrder.size
            )

        /*
         * First pass:
         * compute transformed depth and assign each particle
         * to one of the fixed buckets.
         */
        for (index in 0 until renderCount) {

            val particle =
                renderParticles[index]

            particles.sphericalPosition(
                particle,
                sphericalPositionBuffer
            )

            var x =
                sphericalPositionBuffer[0]

            var y =
                sphericalPositionBuffer[1]

            var z =
                sphericalPositionBuffer[2]

            /*
             * Y-axis rotation.
             */
            val rotatedX =
                x * cosYaw -
                    z * sinYaw

            val rotatedZ =
                x * sinYaw +
                    z * cosYaw

            x =
                rotatedX

            z =
                rotatedZ

            /*
             * X-axis pitch.
             */
            val pitchedY =
                y * cosPitch -
                    z * sinPitch

            val pitchedZ =
                y * sinPitch +
                    z * cosPitch

            y =
                pitchedY

            z =
                pitchedZ

            /*
             * Store the exact camera-space coordinates.
             */
            particleTransformedX[index] =
                x

            particleTransformedY[index] =
                y

            particleTransformedZ[index] =
                z

            /*
             * Convert transformed Z from roughly [-1.5, 1.5]
             * into a stable [0, bucketCount-1] range.
             */
            val normalizedDepth =
                (
                    (
                        z +
                            1.5f
                        ) /
                        3.0f
                    )
                    .coerceIn(
                        0f,
                        0.999999f
                    )

            val bucket =
                (
                    normalizedDepth *
                        DEPTH_BUCKET_COUNT
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        DEPTH_BUCKET_COUNT - 1
                    )

            particleDepthBuckets[index] =
                bucket

            depthBucketCounts[bucket]++
        }

        /*
         * Prefix offsets.
         *
         * Bucket 0 represents the farthest particles.
         * The final bucket represents the nearest particles.
         */
        var runningOffset =
            0

        for (
            bucket in
                0 until DEPTH_BUCKET_COUNT
        ) {

            depthBucketOffsets[bucket] =
                runningOffset

            runningOffset +=
                depthBucketCounts[bucket]
        }

        /*
         * Second pass:
         * place each particle index directly into its bucket range.
         */
        for (index in 0 until renderCount) {

            val bucket =
                particleDepthBuckets[index]

            val destination =
                depthBucketOffsets[bucket]

            particleRenderOrder[destination] =
                index

            depthBucketOffsets[bucket] =
                destination + 1
        }

        /*
         * Rebuild offsets because the renderer does not want the
         * working cursor values to persist into the next frame.
         */
        runningOffset =
            0

        for (
            bucket in
                0 until DEPTH_BUCKET_COUNT
        ) {

            val count =
                depthBucketCounts[bucket]

            depthBucketOffsets[bucket] =
                runningOffset

            runningOffset +=
                count
        }

        for (orderIndex in 0 until renderCount) {

            val particleIndex =
                particleRenderOrder[orderIndex]

            val particle =
                renderParticles[
                    particleIndex
                ]

            /*
             * Reuse the exact camera-space coordinates calculated
             * during the depth-bucket pass.
             *
             * This keeps sorting and projection mathematically
             * consistent and removes the second spherical transform.
             */
            var x =
                particleTransformedX[
                    particleIndex
                ]

            var y =
                particleTransformedY[
                    particleIndex
                ]

            var z =
                particleTransformedZ[
                    particleIndex
                ]

            /*
             * State-specific organic deformation.
             */
            val deformation =
                when (state) {

                    OrbState.THINKING ->
                        1f +
                            motion.fieldWarp *
                            0.040f *
                            sin(
                                particle.phase +
                                    time * 2.4f
                            )

                    OrbState.HEARING,
                    OrbState.SPEAKING ->
                        1f +
                            motion.audioEnergy *
                            0.055f *
                            sin(
                                particle.phase +
                                    time * 5.0f
                            )

                    else ->
                        1f +
                            motion.breath *
                            0.020f *
                            sin(
                                particle.phase +
                                    time
                            )
                }

            x *= deformation
            y *= deformation
            z *= deformation

            /*
             * Perspective projection.
             */
            val perspective =
                (
                    cameraDistance /
                        (
                            cameraDistance -
                                z
                            )
                    )
                    .coerceIn(
                        0.72f,
                        1.45f
                    )

            val depth01 =
                (
                    z /
                        cameraDistance +
                        1f
                    )
                    .coerceIn(
                        0.35f,
                        1.55f
                    )

            val projectedX =
                centerX +
                    x *
                    radius *
                    perspective

            val projectedY =
                centerY +
                    y *
                    radius *
                    perspective *
                    0.96f

            /*
             * 3.3.2.8 — Depth-aware particle rendering.
             *
             * The particle field should read as a volume rather
             * than a flat collection of dots.
             *
             * Back:
             *   smaller
             *   dimmer
             *   softer
             *
             * Middle:
             *   balanced visibility
             *
             * Front:
             *   slightly larger
             *   brighter
             *   more visually dominant
             *
             * The response remains deliberately restrained so the
             * Orb never turns into a noisy star field.
             */

            val depthVisibility =
                depth01.coerceIn(
                    0.35f,
                    1.55f
                )

            /*
             * 3.3.2.17 — Controlled volumetric depth falloff.
             *
             * Preserve the full particle field, but give the rear
             * hemisphere a softer visual contribution and the front
             * hemisphere a slightly stronger presence.
             *
             * This affects only visual weighting:
             * position and perspective remain unchanged.
             */
            val normalizedDepth =
                (
                    (
                        depthVisibility - 0.35f
                    ) / 1.20f
                )
                .coerceIn(
                    0f,
                    1f
                )

            val depthCurve =
                normalizedDepth.pow(
                    1.22f
                )

            val volumetricFalloff =
                0.72f +
                    depthCurve *
                    0.28f

            /*
             * Stable per-particle micro variation.
             *
             * phase is already deterministic, so this does not
             * introduce frame-to-frame randomness or allocations.
             */
            val microVariation =
                0.92f +
                    (
                        sin(
                            particle.phase
                        ) *
                        0.08f
                    )

            val size =
                particle.size *
                    (
                        0.44f +
                            depthCurve *
                            0.66f
                        ) *
                    microVariation *
                    particleScale *
                    perspective *
                    volumetricFalloff

            /*
             * Rear particles remain visible.
             *
             * Do not let depth reduce alpha to zero because that
             * would create obvious popping as particles rotate
             * through the rear hemisphere.
             */
            val depthAlpha =
                (
                    0.48f +
                        depthCurve *
                        0.52f
                    ) *
                    volumetricFalloff

            val alpha =
                (
                    particle.alpha *
                        depthAlpha *
                        microVariation *
                        particleScale *
                        perspective *
                        255f
                    )
                    .toInt()
                    .coerceIn(
                        6,
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

            /*
             * 3.3.2.9 — Particle visual hierarchy.
             *
             * Depth determines how strongly a particle contributes
             * to the Orb's perceived volume.
             *
             * Rear particles:
             *   restrained
             *
             * Middle particles:
             *   normal field visibility
             *
             * Front particles:
             *   brighter and more prominent
             *
             * Layer identity is preserved through particleColor().
             * This stage only adjusts luminance/alpha behavior.
             */

            val frontLight =
                (
                    (
                        depthCurve -
                            0.75f
                        ) /
                        0.80f
                    )
                    .coerceIn(
                        0f,
                        1f
                    )

            val rearFade =
                (
                    1f -
                        (
                            0.55f -
                                depthCurve
                            )
                            .coerceIn(
                                0f,
                                0.55f
                            ) *
                        0.32f
                    )
                    .coerceIn(
                        0.72f,
                        1f
                    )

            /*
             * Large particles receive slightly stronger depth
             * separation because they act as volumetric anchors.
             */
            val layerBoost =
                when (particle.layer) {

                    OrbParticleSystem.Layer.MICRO ->
                        0.92f

                    OrbParticleSystem.Layer.ORBIT ->
                        1.00f

                    OrbParticleSystem.Layer.LARGE ->
                        1.08f
                }

            /*
             * State-aware luminance response.
             *
             * THINKING / SPEAKING can expose the front hemisphere
             * slightly more strongly, while idle states remain calm.
             */
            val stateLight =
                when (state) {

                    OrbState.THINKING ->
                        1f +
                            frontLight *
                            0.10f

                    OrbState.SPEAKING ->
                        1f +
                            frontLight *
                            0.14f

                    OrbState.HEARING ->
                        1f +
                            frontLight *
                            0.08f

                    OrbState.ERROR ->
                        1f +
                            frontLight *
                            0.04f

                    else ->
                        1f +
                            frontLight *
                            0.05f
                }

            val finalAlpha =
                (
                    alpha *
                        rearFade *
                        layerBoost *
                        stateLight
                    )
                    .toInt()
                    .coerceIn(
                        4,
                        255
                    )

            particlePaint.color =
                particleColor(
                    state,
                    particle.layer
                )

            particlePaint.alpha =
                finalAlpha

            canvas.drawCircle(
                projectedX,
                projectedY,
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
                    motion.breath * 0.60f +
                        motion.pulse * 0.28f +
                        stateEnergy * 0.62f +
                        motion.fieldWarp * 0.46f
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
                            0.060f *
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
     * 3.3.2.18 — Shared energy lighting envelope.
     *
     * Keeps the shell, membrane, inner shell and core visually
     * connected instead of letting every layer brighten independently.
     *
     * audioEnergy:
     *   transient voice energy
     *
     * pulse:
     *   rhythmic system pulse
     *
     * glowMultiplier:
     *   global state-driven glow strength
     *
     * Returns a restrained [0, 1.35] visual intensity.
     */
    private fun energyLightEnvelope(
        motion: OrbMotionController.Snapshot
    ): Float {

        val signal =
            (
                0.62f +
                    motion.audioEnergy * 0.24f +
                    motion.pulse * 0.14f
                )
                .coerceIn(
                    0.45f,
                    1.10f
                )

        return (
            signal *
                motion.glowMultiplier
            )
            .coerceIn(
                0.40f,
                1.35f
            )
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

        val lightEnvelope =
            energyLightEnvelope(
                motion
            )

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
                58f *
                    lightEnvelope
                )
                .toInt()
                .coerceIn(
                    18,
                    110
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
                72f *
                    lightEnvelope *
                    (
                        0.92f +
                            motion.audioEnergy *
                            0.32f
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

        val lightEnvelope =
            energyLightEnvelope(
                motion
            )

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
                44f *
                    lightEnvelope *
                    (
                        0.78f +
                            motion.audioEnergy * 0.36f +
                            motion.pulse * 0.14f
                    )
                )
                .toInt()
                .coerceIn(
                    18,
                    105
                )

        canvas.drawCircle(
            centerX,
            centerY,
            innerRadius,
            innerShellPaint
        )

        /*
         * Secondary internal energy bloom.
         *
         * This keeps the core alive without turning it into
         * an opaque colored disk.
         */
        if (!reducedMotion) {

            val bloomRadius =
                innerRadius *
                    (
                        0.40f +
                            motion.audioEnergy * 0.08f +
                            motion.pulse * 0.045f
                    )

            val bloomAlpha =
                (
                    27f *
                        lightEnvelope *
                        (
                            0.75f +
                                motion.audioEnergy * 0.38f
                        )
                    )
                    .toInt()
                    .coerceIn(
                        12,
                        90
                    )

            innerShellPaint.shader =
                RadialGradient(
                    centerX -
                        innerRadius * 0.08f,
                    centerY -
                        innerRadius * 0.10f,
                    bloomRadius,
                    highlightColor(
                        color
                    ),
                    transparentColor(
                        color
                    ),
                    Shader.TileMode.CLAMP
                )

            innerShellPaint.alpha =
                bloomAlpha

            canvas.drawCircle(
                centerX,
                centerY,
                bloomRadius,
                innerShellPaint
            )
        }

        innerShellPaint.shader =
            null

        /*
         * Internal particle field.
         *
         * Only a subset of particles enters the core so the
         * center feels volumetric rather than filled.
         */
        if (!reducedMotion) {

            val internalRadius =
                innerRadius * 0.92f

            val internalEnergy =
                (
                    0.22f +
                        motion.audioEnergy * 0.72f +
                        motion.pulse * 0.20f
                )
                .coerceIn(
                    0.18f,
                    1f
                )

            var index = 0

            for (
                particle in particles.particles()
            ) {

                if (
                    particle.layer !=
                        OrbParticleSystem.Layer.MICRO
                ) {
                    continue
                }

                /*
                 * Deterministic filtering keeps the field stable.
                 */
                if (
                    index % 3 != 0
                ) {
                    index++
                    continue
                }

                val orbit =
                    particle.orbitRadius *
                        0.58f *
                        innerRadius

                val angle =
                    particle.angle +
                        particle.phase *
                        0.14f

                val x =
                    centerX +
                        kotlin.math.cos(angle) *
                        orbit

                val y =
                    centerY +
                        kotlin.math.sin(angle) *
                        orbit *
                        0.88f

                val depth =
                    particle.depth
                        .coerceIn(
                            0.15f,
                            1f
                        )

                val size =
                    (
                        particle.size *
                            0.72f *
                            (
                                0.55f +
                                    depth * 0.55f
                            )
                        )
                        .coerceIn(
                            0.55f,
                            2.4f
                        )

                val alpha =
                    (
                        78f *
                            particle.alpha *
                            internalEnergy *
                            (
                                0.45f +
                                    depth * 0.55f
                            )
                        )
                        .toInt()
                        .coerceIn(
                            8,
                            120
                        )

                if (
                    alpha <= 0
                ) {
                    index++
                    continue
                }

                particlePaint.style =
                    Paint.Style.FILL

                particlePaint.color =
                    particleColor(
                        state,
                        OrbParticleSystem.Layer.MICRO
                    )

                /*
                 * 3.3.2.10 — Core / energy hierarchy.
                 *
                 * The innermost MICRO particles form the Orb's
                 * energetic nucleus.
                 *
                 * The nucleus should feel:
                 *
                 *   dense
                 *   luminous
                 *   alive
                 *   restrained
                 *
                 * It must remain visually subordinate to the main
                 * membrane glow while clearly reading as the source
                 * of the Orb's energy.
                 */

                val coreTime =
                    particles.elapsedSeconds()

                /*
                 * Slow breathing keeps the nucleus alive even when
                 * there is no audio input.
                 */
                val coreBreath =
                    1f +
                        sin(
                            coreTime *
                                0.95f +
                                particle.phase
                        ) *
                        0.045f

                /*
                 * Audio states create a stronger but controlled
                 * nucleus response.
                 */
                val coreAudio =
                    when (state) {

                        OrbState.HEARING ->
                            1f +
                                motion.audioEnergy *
                                0.16f

                        OrbState.SPEAKING ->
                            1f +
                                motion.audioEnergy *
                                0.22f

                        OrbState.THINKING ->
                            1f +
                                motion.fieldWarp *
                                0.10f

                        OrbState.ERROR ->
                            1f +
                                motion.fieldWarp *
                                0.05f

                        else ->
                            1f
                    }

                /*
                 * Depth still matters inside the nucleus, but with
                 * a much smaller range than the outer particle field.
                 */
                val coreDepth =
                    0.90f +
                        particle.depth *
                        0.18f

                val coreScale =
                    coreBreath *
                        coreAudio *
                        coreDepth

                val coreSize =
                    size *
                        coreScale

                /*
                 * The nucleus receives a modest alpha lift.
                 *
                 * Avoiding 255 keeps the underlying glow and membrane
                 * visible through the particle field.
                 */
                val coreAlpha =
                    (
                        alpha *
                            (
                                0.88f +
                                    coreAudio *
                                    0.12f
                                )
                        )
                        .toInt()
                        .coerceIn(
                            8,
                            255
                        )

                particlePaint.alpha =
                    coreAlpha

                canvas.drawCircle(
                    x,
                    y,
                    coreSize,
                    particlePaint
                )

                index++
            }
        }

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

        /*
         * 3.3.2.18C — Inner membrane lighting coherence.
         *
         * The inner membrane sits between the energy shell and
         * neural core, so it follows the shared envelope rather
         * than using an independent glow multiplier.
         */
        val membraneLight =
            energyLightEnvelope(
                motion
            )

        ringPaint.alpha =
            (
                40f *
                    membraneLight *
                    (
                        0.90f +
                            motion.pulse *
                            0.22f
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
     * Render three restrained internal energy currents.
     *
     * These are intentionally thin and translucent. The goal is
     * to make the core feel volumetric and alive without turning
     * it into a solid glowing disk.
     */
    private fun drawNeuralCore(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        motion: OrbMotionController.Snapshot
    ) {

        if (reducedMotion) {
            return
        }

        val color =
            stateColor(
                state
            )

        val stateEnergy =
            when (state) {

                OrbState.THINKING ->
                    1.00f

                OrbState.HEARING,
                OrbState.SPEAKING ->
                    motion.audioEnergy

                OrbState.ERROR ->
                    motion.errorProgress

                else ->
                    0.18f
            }
            .coerceIn(
                0.12f,
                1f
            )

        val coreRadius =
            radius *
                motion.innerCoreScale *
                0.92f

        /*
         * 3.3.2.18B — Shared neural-core lighting.
         *
         * The wisps live inside the inner shell, so they inherit
         * the same energy envelope but at a deliberately reduced
         * intensity.
         */
        val lightEnvelope =
            energyLightEnvelope(
                motion
            )

        val coreLight =
            (
                0.58f +
                    lightEnvelope * 0.32f
                )
                .coerceIn(
                    0.58f,
                    1.02f
                )

        val movement =
            motion.breath * 55f +
                motion.pulse * 35f +
                motion.fieldWarp * 24f +
                motion.audioEnergy * 80f

        coreWispPaint.style =
            Paint.Style.STROKE

        coreWispPaint.strokeWidth =
            radius *
                (
                    0.0045f +
                        stateEnergy * 0.0035f
                    )

        coreWispPaint.strokeCap =
            Paint.Cap.ROUND

        coreWispPaint.color =
            highlightColor(
                color
            )

        coreWispPaint.alpha =
            (
                42f *
                    coreLight *
                    (
                        0.62f +
                            stateEnergy * 0.52f
                        )
                )
                .toInt()
                .coerceIn(
                    10,
                    65
                )

        /*
         * Current 1
         */
        canvas.save()

        canvas.rotate(
            movement,
            centerX,
            centerY
        )

        canvas.drawArc(
            centerX - coreRadius,
            centerY - coreRadius * 0.82f,
            centerX + coreRadius,
            centerY + coreRadius * 0.82f,
            205f,
            78f + stateEnergy * 22f,
            false,
            coreWispPaint
        )

        canvas.restore()

        /*
         * Current 2
         */
        canvas.save()

        canvas.rotate(
            -movement * 0.72f,
            centerX,
            centerY
        )

        coreWispPaint.alpha =
            (
                31f *
                    coreLight *
                    (
                        0.62f +
                            stateEnergy * 0.48f
                        )
                )
                .toInt()
                .coerceIn(
                    8,
                    52
                )

        canvas.drawArc(
            centerX - coreRadius * 0.86f,
            centerY - coreRadius,
            centerX + coreRadius * 0.86f,
            centerY + coreRadius,
            25f,
            62f + stateEnergy * 18f,
            false,
            coreWispPaint
        )

        canvas.restore()

        /*
         * Current 3
         */
        canvas.save()

        canvas.rotate(
            movement * 0.43f,
            centerX,
            centerY
        )

        coreWispPaint.alpha =
            (
                25f *
                    coreLight *
                    (
                        0.62f +
                            stateEnergy * 0.44f
                        )
                )
                .toInt()
                .coerceIn(
                    7,
                    42
                )

        canvas.drawArc(
            centerX - coreRadius * 0.72f,
            centerY - coreRadius * 0.72f,
            centerX + coreRadius * 0.72f,
            centerY + coreRadius * 0.72f,
            125f,
            58f + stateEnergy * 16f,
            false,
            coreWispPaint
        )

        canvas.restore()

        coreWispPaint.alpha =
            0
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
