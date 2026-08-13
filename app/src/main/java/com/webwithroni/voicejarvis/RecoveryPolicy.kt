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
     * Produce the alternate request for a safe retry.
     *
     * scroll → swipe
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
                    action = "swipe"
                )

            else ->
                null
        }
    }
}
