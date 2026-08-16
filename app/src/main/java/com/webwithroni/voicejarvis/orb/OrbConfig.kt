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
    val reducedMotionGlowScale: Float = 0.65f,

    /**
     * Depth of the organic breathing motion.
     */
    val breathDepth: Float = 0.035f,

    /**
     * Additional slow idle pulse layered over breathing.
     */
    val idlePulseDepth: Float = 0.008f,

    /**
     * Core response to voice/audio energy.
     */
    val coreAudioScale: Float = 0.055f,

    /**
     * Energy-shell response to voice/audio energy.
     */
    val shellAudioScale: Float = 0.085f,

    /**
     * Particle-field expansion caused by strong audio peaks.
     */
    val particleAudioScale: Float = 0.12f,

    /**
     * Neural turbulence intensity while THINKING.
     */
    val thinkingTurbulence: Float = 0.32f,

    /**
     * Additional thinking rotation multiplier.
     */
    val thinkingRotationMultiplier: Float = 1.9f,

    /**
     * Speaking expansion multiplier.
     */
    val speakingExpansionMultiplier: Float = 1.25f,

    /**
     * Speaking ripple propagation speed.
     */
    val speakingRippleSpeed: Float = 2.2f,

    /**
     * Error instability intensity.
     */
    val errorInstability: Float = 0.22f,

    /**
     * State transition smoothing duration.
     */
    val stateTransitionMs: Long = 420L
)
