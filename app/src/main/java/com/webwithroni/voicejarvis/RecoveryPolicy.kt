package com.webwithroni.voicejarvis

/**
 * Deterministic recovery policy.
 *
 * This class decides whether a particular classified failure
 * has a safe automatic recovery strategy.
 *
 * V2 remains intentionally conservative.
 */
object RecoveryPolicy {

    /**
     * Maximum number of total attempts for an automatically
     * recoverable action.
     */
    const val MAX_ATTEMPTS = 2

    /**
     * Whether automatic recovery is allowed.
     */
    fun canRecover(
        request: ActionRequest,
        failure: RecoveryFailure,
        attempt: Int
    ): Boolean {

        if (
            attempt >= MAX_ATTEMPTS
        ) {
            return false
        }

        /*
         * Only scroll is automatically recoverable in V2.
         */
        if (
            request.action
                .trim()
                .lowercase() != "scroll"
        ) {
            return false
        }

        return when (
            failure
        ) {

            RecoveryFailure.STALE_UI,
            RecoveryFailure.VERIFICATION_UNKNOWN ->
                true

            else ->
                false
        }
    }

    /**
     * Produce the retry request for a safe recovery.
     *
     * Scroll recovery preserves the semantic scroll action
     * and all original parameters.
     *
     * The executor itself owns the internal fallback strategy
     * between semantic accessibility scrolling and a vertical
     * gesture.
     */
    fun recoverRequest(
        request: ActionRequest
    ): ActionRequest? {

        return when (
            request.action
                .trim()
                .lowercase()
        ) {

            "scroll" ->
                request.copy(
                    action = "scroll"
                )

            else ->
                null
        }
    }
}
