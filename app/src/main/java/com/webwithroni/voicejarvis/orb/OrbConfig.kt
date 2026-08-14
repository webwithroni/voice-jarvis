package com.webwithroni.voicejarvis.orb

/**
 * Central visual configuration for the Jarvis Orb.
 *
 * Keep rendering constants here so visual tuning does not
 * require changing renderer or animation logic.
 */
data class OrbConfig(

    /**
     * Approximate total particle count.
     *
     * The particle system may distribute these across
     * multiple visual layers.
     */
    val particleCount: Int = 240,

    /**
     * Relative humanoid core size.
     */
    val coreScale: Float = 0.62f,

    /**
     * Relative outer energy shell size.
     */
    val shellScale: Float = 1.08f,

    /**
     * Maximum idle breathing expansion.
     */
    val breathingScale: Float = 1.035f,

    /**
     * Base orbital rotation speed.
     */
    val baseRotationSpeed: Float = 0.30f,

    /**
     * Global glow multiplier.
     */
    val glowIntensity: Float = 1.0f,

    /**
     * How strongly cleaned audio amplitude affects
     * Orb animation.
     */
    val audioResponse: Float = 1.0f,

    /**
     * Minimum amplitude accepted by the visual
     * audio-reactive pipeline.
     */
    val audioNoiseGate: Float = 0.04f,

    /**
     * Smoothing factor for audio-reactive motion.
     */
    val audioSmoothing: Float = 0.12f,

    /**
     * Listening breathing cycle duration.
     */
    val listeningBreathMs: Long = 2800L,

    /**
     * Error transition duration.
     */
    val errorTransitionMs: Long = 800L,

    /**
     * Reduced-motion particle intensity.
     */
    val reducedMotionParticleScale: Float = 0.15f,

    /**
     * Reduced-motion glow intensity.
     */
    val reducedMotionGlowScale: Float = 0.65f
)
