package com.webwithroni.voicejarvis.orb

/**
 * Primary visual state of the Jarvis Orb.
 *
 * These are the ONLY top-level Orb states in V1.
 *
 * Do not add task-specific states here.
 * Use OrbActivity for contextual activity instead.
 */
enum class OrbState {

    /**
     * Jarvis is ready and waiting for the user.
     */
    LISTENING,

    /**
     * Jarvis is actively receiving user voice input.
     */
    HEARING,

    /**
     * Jarvis is processing a request.
     */
    THINKING,

    /**
     * Jarvis is generating voice output.
     */
    SPEAKING,

    /**
     * Jarvis failed to understand or complete something.
     */
    ERROR,

    /**
     * Jarvis is intentionally inactive.
     */
    PAUSED,

    /**
     * A required Android permission is missing.
     */
    PERMISSION_REQUIRED
}


/**
 * Contextual activity occurring inside a primary Orb state.
 *
 * These are NOT independent visual states.
 *
 * Example:
 *
 *     OrbState.THINKING
 *     +
 *     OrbActivity.RESEARCHING
 *
 * The Orb remains in THINKING while its visual behavior
 * becomes research-oriented.
 */
enum class OrbActivity {

    /**
     * No additional activity.
     */
    NONE,

    /**
     * Jarvis is performing a normal web/search operation.
     */
    SEARCHING,

    /**
     * Jarvis is performing multi-source or deeper research.
     */
    RESEARCHING,

    /**
     * Jarvis is executing a registered capability/tool.
     */
    EXECUTING_TOOL,

    /**
     * Jarvis is controlling the Android device.
     */
    CONTROLLING_DEVICE,

    /**
     * An action is waiting for explicit user confirmation.
     */
    WAITING_CONFIRMATION,

    /**
     * The previous action completed successfully.
     */
    SUCCESS
}
