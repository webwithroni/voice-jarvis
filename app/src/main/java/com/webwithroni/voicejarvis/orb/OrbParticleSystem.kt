package com.webwithroni.voicejarvis.orb

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Lightweight particle simulation for the Jarvis Orb.
 *
 * Responsibilities:
 *
 * - Maintain particle data
 * - Update particle positions
 * - Maintain orbital depth
 * - Apply state/activity intensity
 * - Provide particle data to OrbRenderer
 *
 * This class intentionally knows nothing about Canvas,
 * Paint, View, Android UI state, or audio capture.
 */
class OrbParticleSystem(
    private val config: OrbConfig = OrbConfig(),
    private val random: Random = Random.Default
) {

    /**
     * Visual particle layers.
     */
    enum class Layer {
        MICRO,
        ORBIT,
        LARGE
    }

    /**
     * Mutable particle record.
     */
    data class Particle(
        var angle: Float,
        var orbitRadius: Float,
        var depth: Float,
        var radialVelocity: Float,
        var angularVelocity: Float,
        var size: Float,
        var alpha: Float,
        var phase: Float,
        val layer: Layer
    )

    private val particles =
        ArrayList<Particle>(
            config.particleCount
        )

    private var elapsedSeconds =
        0f

    private var activityIntensity =
        1f

    private var audioAmplitude =
        0f

    /**
     * Motion envelope supplied by OrbMotionController.
     *
     * These values let the particle field breathe, expand and
     * distort with the Orb instead of behaving as an independent
     * animation.
     */
    private var breath =
        0f

    private var pulse =
        0f

    private var fieldWarp =
        0f

    private var audioEnergy =
        0f

    private var rotationDirection =
        1f

    init {
        reset()
    }

    /**
     * Recreate the deterministic particle field.
     */
    fun reset() {

        particles.clear()

        elapsedSeconds = 0f
        activityIntensity = 1f
        audioAmplitude = 0f
        breath = 0f
        pulse = 0f
        fieldWarp = 0f
        audioEnergy = 0f
        rotationDirection = 1f

        repeat(config.particleCount) {

            particles += createParticle()
        }
    }

    /**
     * Update the particle simulation.
     *
     * @param deltaSeconds elapsed frame time
     * @param state current Orb base state
     * @param activity current contextual activity
     */
    fun update(
        deltaSeconds: Float,
        state: OrbState,
        activity: OrbActivity = OrbActivity.NONE
    ) {

        val dt =
            deltaSeconds
                .coerceIn(
                    0f,
                    0.05f
                )

        elapsedSeconds += dt

        activityIntensity =
            activityMultiplier(
                state,
                activity
            )

        val stateSpeed =
            stateRotationMultiplier(
                state
            )

        val audioBoost =
            (
                audioAmplitude *
                    config.audioResponse
            )
                .coerceIn(
                    0f,
                    1.5f
                )

        /*
         * Organic field motion.
         *
         * Breath affects the entire field.
         * Audio adds perceptual expansion.
         * Pulse adds tiny rhythmic movement.
         * Field warp becomes more noticeable during thinking,
         * speaking and error states.
         */
        val breathingExpansion =
            1f +
                breath *
                    config.breathDepth *
                    1.35f

        val pulseExpansion =
            1f +
                pulse *
                    config.idlePulseDepth *
                    0.75f

        val audioExpansion =
            1f +
                audioEnergy *
                    config.particleAudioScale

        val fieldExpansion =
            (
                breathingExpansion *
                    pulseExpansion *
                    audioExpansion
                )
                .coerceIn(
                    0.96f,
                    1.22f
                )

        val turbulence =
            fieldWarp.coerceIn(
                0f,
                1.5f
            )

        val globalAngularSpeed =
            config.baseRotationSpeed *
                stateSpeed *
                activityIntensity *
                (
                    1f +
                        audioBoost * 0.35f +
                        turbulence * 0.12f
                    ) *
                rotationDirection

        for (particle in particles) {

            particle.angle +=
                particle.angularVelocity *
                    globalAngularSpeed *
                    dt

            /*
             * Base radial movement plus the new organic field.
             */
            particle.orbitRadius +=
                particle.radialVelocity *
                    activityIntensity *
                    dt

            particle.orbitRadius =
                (
                    particle.orbitRadius *
                        (
                            1f +
                                (
                                    fieldExpansion -
                                        1f
                                ) *
                                0.020f *
                                dt
                        )
                    )


            /*
             * Keep particles inside a bounded orbital region.
             */
            if (
                particle.orbitRadius < minOrbitRadius(
                    particle.layer
                )
            ) {

                particle.orbitRadius =
                    maxOrbitRadius(
                        particle.layer
                    )
            }

            if (
                particle.orbitRadius > maxOrbitRadius(
                    particle.layer
                )
            ) {

                particle.orbitRadius =
                    minOrbitRadius(
                        particle.layer
                    )
            }

            /*
             * Subtle depth oscillation.
             *
             * This gives the field a living 3D feeling without
             * introducing a full 3D engine.
             */
            particle.depth =
                (
                    particle.depth +
                        sin(
                            elapsedSeconds *
                                0.45f +
                                particle.phase
                        ) *
                        0.0025f *
                        dt
                )
                    .coerceIn(
                        0.15f,
                        1f
                    )

            /*
             * Audio adds a restrained radial pulse.
             */
            if (
                state ==
                    OrbState.HEARING ||
                state ==
                    OrbState.SPEAKING
            ) {

                /*
                 * Strong speech creates an outward energy impulse.
                 */
                val speechImpulse =
                    audioEnergy *
                        config.particleAudioScale *
                        when (state) {

                            OrbState.SPEAKING ->
                                1.45f

                            else ->
                                0.85f
                        }

                particle.orbitRadius *=
                    1f +
                        speechImpulse *
                        dt
            }

            /*
             * Thinking creates controlled turbulence.
             *
             * Each particle gets a different phase so the field
             * never looks like one synchronized sine wave.
             */
            if (
                state ==
                    OrbState.THINKING &&
                turbulence > 0f
            ) {

                val turbulenceWave =
                    sin(
                        elapsedSeconds *
                            (
                                1.10f +
                                    particle.angularVelocity *
                                    0.80f
                                ) +
                            particle.phase
                    )

                particle.angle +=
                    turbulenceWave *
                        turbulence *
                        0.10f *
                        dt

                particle.orbitRadius *=
                    1f +
                        turbulenceWave *
                        turbulence *
                        0.0035f *
                        dt
            }

            /*
             * Apply the organic field after state-specific movement.
             * The effect is deliberately small per frame to preserve
             * smoothness on lower-end devices.
             */
            particle.orbitRadius *=
                1f +
                    (
                        fieldExpansion -
                            1f
                    ) *
                    0.10f *
                    dt
        }
    }

    /**
     * Return a stable read-only view for rendering.
     *
     * The renderer should not mutate Particle objects.
     */
    fun particles():
        List<Particle> =
        particles

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
    }

    /**
     * Supply the organic motion envelope from OrbMotionController.
     *
     * Values are normalized to safe visual ranges.
     */
    fun setMotionEnvelope(
        breath: Float,
        pulse: Float,
        fieldWarp: Float,
        audioEnergy: Float
    ) {

        this.breath =
            breath.coerceIn(
                0f,
                1f
            )

        this.pulse =
            pulse.coerceIn(
                0f,
                1f
            )

        this.fieldWarp =
            fieldWarp.coerceIn(
                0f,
                2f
            )

        this.audioEnergy =
            audioEnergy.coerceIn(
                0f,
                1f
            )
    }

    /**
     * Reverse the field rotation.
     */
    fun reverseRotation() {

        rotationDirection *=
            -1f
    }

    /**
     * Current simulation time.
     */
    fun elapsedSeconds():
        Float =
        elapsedSeconds

    /**
     * Create one particle according to its visual layer.
     */
    private fun createParticle():
        Particle {

        val layer =
            chooseLayer()

        val orbitMin =
            minOrbitRadius(
                layer
            )

        val orbitMax =
            maxOrbitRadius(
                layer
            )

        val orbitRadius =
            random.nextFloatBetween(
                orbitMin,
                orbitMax
            )

        val depth =
            when (layer) {

                Layer.MICRO ->
                    random.nextFloatBetween(
                        0.25f,
                        0.90f
                    )

                Layer.ORBIT ->
                    random.nextFloatBetween(
                        0.40f,
                        1.00f
                    )

                Layer.LARGE ->
                    random.nextFloatBetween(
                        0.55f,
                        1.00f
                    )
            }

        val size =
            when (layer) {

                Layer.MICRO ->
                    random.nextFloatBetween(
                        0.6f,
                        1.5f
                    )

                Layer.ORBIT ->
                    random.nextFloatBetween(
                        1.0f,
                        2.6f
                    )

                Layer.LARGE ->
                    random.nextFloatBetween(
                        2.2f,
                        5.0f
                    )
            }

        val alpha =
            when (layer) {

                Layer.MICRO ->
                    random.nextFloatBetween(
                        0.18f,
                        0.58f
                    )

                Layer.ORBIT ->
                    random.nextFloatBetween(
                        0.30f,
                        0.78f
                    )

                Layer.LARGE ->
                    random.nextFloatBetween(
                        0.38f,
                        0.90f
                    )
            }

        return Particle(

            angle =
                random.nextFloat() *
                    (Math.PI.toFloat() * 2f),

            orbitRadius =
                orbitRadius,

            depth =
                depth,

            radialVelocity =
                random.nextFloatBetween(
                    -0.035f,
                    0.035f
                ),

            angularVelocity =
                when (layer) {

                    Layer.MICRO ->
                        random.nextFloatBetween(
                            0.75f,
                            1.20f
                        )

                    Layer.ORBIT ->
                        random.nextFloatBetween(
                            0.90f,
                            1.35f
                        )

                    Layer.LARGE ->
                        random.nextFloatBetween(
                            0.55f,
                            0.95f
                        )
                },

            size =
                size,

            alpha =
                alpha,

            phase =
                random.nextFloat() *
                    (Math.PI.toFloat() * 2f),

            layer =
                layer
        )
    }

    /**
     * Roughly:
     *
     * 65% micro
     * 27% orbital
     * 8% large
     */
    private fun chooseLayer():
        Layer {

        val value =
            random.nextFloat()

        return when {

            value < 0.65f ->
                Layer.MICRO

            value < 0.92f ->
                Layer.ORBIT

            else ->
                Layer.LARGE
        }
    }

    private fun minOrbitRadius(
        layer: Layer
    ): Float {

        return when (layer) {

            Layer.MICRO ->
                0.88f

            Layer.ORBIT ->
                0.94f

            Layer.LARGE ->
                1.00f
        }
    }

    private fun maxOrbitRadius(
        layer: Layer
    ): Float {

        return when (layer) {

            Layer.MICRO ->
                1.42f

            Layer.ORBIT ->
                1.68f

            Layer.LARGE ->
                1.52f
        }
    }

    private fun stateRotationMultiplier(
        state: OrbState
    ): Float {

        return when (state) {

            OrbState.LISTENING ->
                0.55f

            OrbState.HEARING ->
                1.15f

            OrbState.THINKING ->
                1.65f

            OrbState.SPEAKING ->
                1.30f

            OrbState.ERROR ->
                0.90f

            OrbState.PAUSED ->
                0.08f

            OrbState.PERMISSION_REQUIRED ->
                0.30f
        }
    }

    private fun activityMultiplier(
        state: OrbState,
        activity: OrbActivity
    ): Float {

        var multiplier =
            when (activity) {

                OrbActivity.NONE ->
                    1f

                OrbActivity.SEARCHING ->
                    1.12f

                OrbActivity.RESEARCHING ->
                    1.25f

                OrbActivity.EXECUTING_TOOL ->
                    1.18f

                OrbActivity.CONTROLLING_DEVICE ->
                    1.22f

                OrbActivity.WAITING_CONFIRMATION ->
                    0.72f

                OrbActivity.SUCCESS ->
                    1.08f
            }

        if (
            state ==
                OrbState.PAUSED
        ) {

            multiplier *=
                0.25f
        }

        return multiplier
    }
}

/**
 * Kotlin Random helper with explicit Float ranges.
 */
private fun Random.nextFloatBetween(
    min: Float,
    max: Float
): Float {

    return min +
        nextFloat() *
        (max - min)
}

/**
 * Simple float square-root helper kept here so future
 * renderer code does not need additional math utilities.
 */
private fun radialMagnitude(
    x: Float,
    y: Float
): Float {

    return sqrt(
        x * x +
            y * y
    )
}
