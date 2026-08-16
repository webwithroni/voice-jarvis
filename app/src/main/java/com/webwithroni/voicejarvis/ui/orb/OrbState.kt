package com.webwithroni.voicejarvis.ui.orb

/**
 * Visual state of the Jarvis Neural Orb.
 *
 * This is intentionally UI-facing and independent from
 * Gemini/network implementation.
 */
enum class OrbState {

    LISTENING,

    HEARING,

    THINKING,

    SPEAKING,

    ERROR,

    PAUSED,

    OFFLINE,

    BACKUP
}
