package com.webwithroni.voicejarvis.orb

/**
 * Converts raw audio amplitude into a clean visual signal.
 *
 * This class intentionally contains no Android audio code.
 *
 * Pipeline:
 *
 * raw amplitude
 *      ↓
 * noise gate
 *      ↓
 * normalization
 *      ↓
 * smoothing
 *      ↓
 * Orb amplitude
 */
class OrbAudioReactive(
    private val config: OrbConfig = OrbConfig()
) {

    private var smoothed =
        0f

    /**
     * Process one raw amplitude sample.
     *
     * @return normalized visual amplitude in [0, 1].
     */
    fun update(
        rawAmplitude: Float
    ): Float {

        val raw =
            rawAmplitude
                .coerceAtLeast(0f)

        /*
         * Noise gate.
         *
         * Small background noise should not move the Orb.
         */
        val gated =
            if (
                raw < config.audioNoiseGate
            ) {
                0f
            } else {
                raw
            }

        /*
         * Convert the gated signal into a useful
         * 0..1 visual range.
         */
        val normalized =
            normalize(
                gated
            )

        /*
         * Exponential smoothing.
         *
         * This removes high-frequency jitter while
         * preserving natural voice movement.
         */
        val smoothing =
            config.audioSmoothing
                .coerceIn(
                    0.01f,
                    1f
                )

        smoothed +=
            (
                normalized -
                    smoothed
                ) * smoothing

        return smoothed
            .coerceIn(
                0f,
                1f
            )
    }

    /**
     * Immediately clear the reactive state.
     */
    fun reset() {

        smoothed =
            0f
    }

    /**
     * Current filtered amplitude.
     */
    fun current():
        Float =
        smoothed

    /**
     * Normalize amplitude.
     *
     * Android audio sources can produce very different
     * amplitude ranges, so the visual layer uses a
     * conservative curve rather than assuming raw input
     * already represents 0..1.
     */
    private fun normalize(
        value: Float
    ): Float {

        /*
         * The configured gate is treated as the lower bound.
         *
         * 0.50f is used as a practical visual ceiling:
         * ordinary speech occupies the useful range while
         * louder peaks saturate gracefully.
         */
        val lower =
            config.audioNoiseGate

        val upper =
            0.50f

        if (
            value <= lower
        ) {
            return 0f
        }

        if (
            value >= upper
        ) {
            return 1f
        }

        return (
            value - lower
            ) / (
                upper - lower
                )
    }
}
