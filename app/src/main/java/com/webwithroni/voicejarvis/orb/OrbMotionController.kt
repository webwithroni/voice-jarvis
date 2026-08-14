package com.webwithroni.voicejarvis.orb

import kotlin.math.PI
import kotlin.math.sin

/**
 * Central animation and motion controller for the Jarvis Orb.
 *
 * Responsibilities:
 *
 * - state-dependent motion
 * - breathing
 * - orbital speed
 * - audio response
 * - activity intensity
 * - error transition timing
 * - reduced-motion behavior
 *
 * This class does not render anything.
 *
 * Renderer/View consumes the calculated motion values.
 */
class OrbMotionController(
    private val config: OrbConfig = OrbConfig()
) {

    /**
     * Snapshot of the current visual motion.
     *
     * Renderer should treat this as read-only.
     */
    data class Snapshot(
        val scale: Float,
        val rotationSpeed: Float,
        val glowMultiplier: Float,
        val particleMultiplier: Float,
        val faceEnergy: Float,
        val audioAmplitude: Float,
        val errorProgress: Float,
        val activityIntensity: Float
    )

    private var state =
        OrbState.LISTENING

    private var activity =
        OrbActivity.NONE

    private var audioAmplitude =
        0f

    private var reducedMotion =
        false

    private var elapsedSeconds =
        0f

    private var errorElapsedSeconds =
        0f

    private var snapshot =
        Snapshot(
            scale = 1f,
            rotationSpeed = config.baseRotationSpeed,
            glowMultiplier = config.glowIntensity,
            particleMultiplier = 1f,
            faceEnergy = 0.5f,
            audioAmplitude = 0f,
            errorProgress = 0f,
            activityIntensity = 1f
        )

    /**
     * Change base visual state.
     */
    fun setState(
        value: OrbState
    ) {

        if (state == value) {
            return
        }

        state =
            value

        if (
            state ==
                OrbState.ERROR
        ) {
            errorElapsedSeconds =
                0f
        }
    }

    /**
     * Change contextual activity.
     */
    fun setActivity(
        value: OrbActivity
    ) {

        activity =
            value
    }

    /**
     * Supply already-cleaned audio amplitude.
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
     * Enable reduced motion.
     */
    fun setReducedMotion(
        enabled: Boolean
    ) {

        reducedMotion =
            enabled
    }

    /**
     * Reset animation state.
     */
    fun reset() {

        state =
            OrbState.LISTENING

        activity =
            OrbActivity.NONE

        audioAmplitude =
            0f

        elapsedSeconds =
            0f

        errorElapsedSeconds =
            0f

        snapshot =
            Snapshot(
                scale = 1f,
                rotationSpeed =
                    config.baseRotationSpeed,
                glowMultiplier =
                    config.glowIntensity,
                particleMultiplier = 1f,
                faceEnergy = 0.5f,
                audioAmplitude = 0f,
                errorProgress = 0f,
                activityIntensity = 1f
            )
    }

    /**
     * Advance the motion simulation.
     */
    fun update(
        deltaSeconds: Float
    ): Snapshot {

        val dt =
            deltaSeconds
                .coerceIn(
                    0f,
                    0.05f
                )

        elapsedSeconds +=
            dt

        if (
            state ==
                OrbState.ERROR
        ) {

            errorElapsedSeconds +=
                dt
        } else {

            errorElapsedSeconds =
                0f
        }

        val activityIntensity =
            activityIntensity()

        val breathing =
            breathingScale()

        val scale =
            if (reducedMotion) {
                1f
            } else {
                breathing
            }

        val stateRotation =
            stateRotationSpeed()

        val rotation =
            if (reducedMotion) {
                0f
            } else {
                stateRotation *
                    activityIntensity
            }

        val glow =
            glowMultiplier(
                activityIntensity
            )

        val particles =
            particleMultiplier(
                activityIntensity
            )

        val face =
            faceEnergy(
                activityIntensity
            )

        val errorProgress =
            errorProgress()

        snapshot =
            Snapshot(
                scale = scale,
                rotationSpeed = rotation,
                glowMultiplier = glow,
                particleMultiplier = particles,
                faceEnergy = face,
                audioAmplitude =
                    audioAmplitude,
                errorProgress =
                    errorProgress,
                activityIntensity =
                    activityIntensity
            )

        return snapshot
    }

    /**
     * Current calculated motion snapshot.
     */
    fun current():
        Snapshot =
        snapshot

    /**
     * Current base state.
     */
    fun currentState():
        OrbState =
        state

    /**
     * Current contextual activity.
     */
    fun currentActivity():
        OrbActivity =
        activity

    /**
     * Returns the slow breathing scale for idle/listening.
     */
    private fun breathingScale():
        Float {

        return when (state) {

            OrbState.LISTENING -> {

                val progress =
                    (
                        elapsedSeconds * 1000f /
                            config.listeningBreathMs
                    )
                        .coerceAtLeast(
                            0f
                        )

                val cycle =
                    progress *
                        (2f * PI.toFloat())

                val wave =
                    (
                        sin(cycle) + 1f
                    ) * 0.5f

                1f +
                    (
                        config.breathingScale -
                            1f
                        ) *
                        wave
            }

            OrbState.HEARING -> {

                1f +
                    audioAmplitude *
                    0.028f
            }

            OrbState.THINKING -> {

                1f +
                    (
                        sin(
                            elapsedSeconds *
                                1.4f
                        ) *
                        0.012f
                    )
            }

            OrbState.SPEAKING -> {

                1f +
                    audioAmplitude *
                    0.035f
            }

            OrbState.ERROR -> {

                1f +
                    errorPulse() *
                    0.022f
            }

            OrbState.PAUSED ->
                1f

            OrbState.PERMISSION_REQUIRED -> {

                1f +
                    sin(
                        elapsedSeconds *
                            1.2f
                    ) *
                    0.004f
            }
        }
    }

    /**
     * State-specific orbital movement.
     */
    private fun stateRotationSpeed():
        Float {

        return when (state) {

            OrbState.LISTENING ->
                config.baseRotationSpeed *
                    0.55f

            OrbState.HEARING ->
                config.baseRotationSpeed *
                    (
                        0.90f +
                            audioAmplitude *
                            0.45f
                        )

            OrbState.THINKING ->
                config.baseRotationSpeed *
                    1.55f

            OrbState.SPEAKING ->
                config.baseRotationSpeed *
                    (
                        0.85f +
                            audioAmplitude *
                            0.60f
                        )

            OrbState.ERROR ->
                config.baseRotationSpeed *
                    1.10f

            OrbState.PAUSED ->
                0f

            OrbState.PERMISSION_REQUIRED ->
                config.baseRotationSpeed *
                    0.20f
        }
    }

    /**
     * Activity-based motion multiplier.
     */
    private fun activityIntensity():
        Float {

        return when (activity) {

            OrbActivity.NONE ->
                1f

            OrbActivity.SEARCHING ->
                1.10f

            OrbActivity.RESEARCHING ->
                1.30f

            OrbActivity.EXECUTING_TOOL ->
                1.20f

            OrbActivity.CONTROLLING_DEVICE ->
                1.15f

            OrbActivity.WAITING_CONFIRMATION ->
                0.80f

            OrbActivity.SUCCESS ->
                1.25f
        }
    }

    /**
     * Glow response.
     */
    private fun glowMultiplier(
        intensity: Float
    ): Float {

        val audioBoost =
            audioAmplitude *
                config.audioResponse

        val base =
            when (state) {

                OrbState.LISTENING ->
                    0.90f

                OrbState.HEARING ->
                    1.00f

                OrbState.THINKING ->
                    1.12f

                OrbState.SPEAKING ->
                    1.00f

                OrbState.ERROR ->
                    1.15f

                OrbState.PAUSED ->
                    0.25f

                OrbState.PERMISSION_REQUIRED ->
                    0.55f
            }

        val result =
            base *
                intensity *
                (
                    1f +
                        audioBoost * 0.22f
                    ) *
                config.glowIntensity

        return if (
            reducedMotion
        ) {

            result *
                config.reducedMotionGlowScale

        } else {

            result
        }
    }

    /**
     * Particle movement multiplier.
     */
    private fun particleMultiplier(
        intensity: Float
    ): Float {

        val base =
            when (state) {

                OrbState.LISTENING ->
                    0.85f

                OrbState.HEARING ->
                    1.00f +
                        audioAmplitude *
                        0.40f

                OrbState.THINKING ->
                    1.35f

                OrbState.SPEAKING ->
                    1.00f +
                        audioAmplitude *
                        0.50f

                OrbState.ERROR ->
                    1.10f

                OrbState.PAUSED ->
                    0.12f

                OrbState.PERMISSION_REQUIRED ->
                    0.35f
            }

        val result =
            base *
                intensity

        return if (
            reducedMotion
        ) {

            result *
                config.reducedMotionParticleScale

        } else {

            result
        }
    }

    /**
     * Face energy level.
     */
    private fun faceEnergy(
        intensity: Float
    ): Float {

        val base =
            when (state) {

                OrbState.LISTENING ->
                    0.48f

                OrbState.HEARING ->
                    0.60f

                OrbState.THINKING ->
                    0.72f

                OrbState.SPEAKING ->
                    0.68f

                OrbState.ERROR ->
                    0.82f

                OrbState.PAUSED ->
                    0.14f

                OrbState.PERMISSION_REQUIRED ->
                    0.38f
            }

        val audioBoost =
            when (
                state
            ) {

                OrbState.HEARING,
                OrbState.SPEAKING ->
                    audioAmplitude *
                        0.32f

                else ->
                    0f
            }

        return (
            base *
                intensity +
                audioBoost
            )
            .coerceIn(
                0f,
                1f
            )
    }

    /**
     * Progress through the ERROR transition.
     *
     * 0 -> 1 over configured duration.
     */
    private fun errorProgress():
        Float {

        if (
            state !=
                OrbState.ERROR
        ) {
            return 0f
        }

        if (
            config.errorTransitionMs <=
                0L
        ) {
            return 1f
        }

        return (
            errorElapsedSeconds * 1000f /
                config.errorTransitionMs
            )
            .coerceIn(
                0f,
                1f
            )
    }

    /**
     * Brief red error pulse.
     */
    private fun errorPulse():
        Float {

        val progress =
            errorProgress()

        if (
            progress >= 1f
        ) {
            return 0f
        }

        val wave =
            sin(
                progress *
                    PI.toFloat()
            )

        return wave
            .coerceIn(
                0f,
                1f
            )
    }
}
