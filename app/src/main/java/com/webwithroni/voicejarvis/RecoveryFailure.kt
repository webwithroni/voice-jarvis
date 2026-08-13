package com.webwithroni.voicejarvis

/**
 * Normalized recovery failure classification.
 *
 * IMPORTANT:
 * A failure class is descriptive.
 * It does not automatically authorize a retry.
 */
enum class RecoveryFailure {

    /**
     * Action completed and verification succeeded.
     */
    NONE,

    /**
     * Action failed because the device/app state may have
     * changed between planning and execution.
     */
    STALE_UI,

    /**
     * Action was dispatched but the expected state could not
     * be observed afterwards.
     */
    VERIFICATION_UNKNOWN,

    /**
     * Action execution itself failed.
     */
    EXECUTION_FAILED,

    /**
     * Capability or permission is unavailable.
     */
    CAPABILITY_UNAVAILABLE,

    /**
     * Explicit user confirmation is required.
     */
    USER_CONFIRMATION_REQUIRED,

    /**
     * The result may indicate a side effect already happened,
     * so repeating it could create a duplicate action.
     */
    SIDE_EFFECT_UNCERTAIN,

    /**
     * No safe automatic recovery strategy exists.
     */
    NON_RECOVERABLE
}
