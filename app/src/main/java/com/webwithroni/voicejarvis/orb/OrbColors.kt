package com.webwithroni.voicejarvis.orb

/**
 * Canonical visual palette for the Jarvis Orb.
 *
 * Renderer code must reference this object instead of
 * defining state colors inline.
 */
object OrbColors {

    /**
     * Primary Jarvis energy colors.
     */
    const val CYAN =
        0xFF5CE7FF.toInt()

    const val VIOLET =
        0xFF9B7CFF.toInt()

    /**
     * Error / blocked-action visual.
     */
    const val ERROR =
        0xFFFF7181.toInt()

    /**
     * Low-energy inactive visual.
     */
    const val PAUSED =
        0xFF414A58.toInt()

    /**
     * Neutral face / highlight energy.
     */
    const val WHITE =
        0xFFF4F7FB.toInt()

    /**
     * Muted permission warning.
     */
    const val PERMISSION =
        0xFFD95C6A.toInt()
}
