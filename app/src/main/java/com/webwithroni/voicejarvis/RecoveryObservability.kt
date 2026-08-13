package com.webwithroni.voicejarvis

/**
 * Central observability gateway for RecoveryEngine.
 *
 * Privacy rule:
 *
 * Never record:
 * - screen text
 * - transcripts
 * - coordinates
 * - accessibility content
 * - action parameters
 * - app targets
 * - API keys
 * - tokens
 *
 * Only coarse operational metadata is sent to Firebase Analytics.
 */
object RecoveryObservability {

    /**
     * Record that an action entered the recovery pipeline.
     */
    fun actionStarted(
        request: ActionRequest
    ) {

        FirebaseAnalyticsManager.actionStarted(
            action = request.action,
            riskLevel = request.risk.name
        )
    }

    /**
     * Record a successful verified action.
     */
    fun actionVerified(
        request: ActionRequest,
        attempt: Int,
        method: String? = null
    ) {

        FirebaseAnalyticsManager.actionVerified(
            action = request.action,
            method =
                method
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        normalizeMethod(it)
                    }
                    ?: "recovery_attempt_$attempt"
        )
    }

    /**
     * Record a failed action/recovery path.
     */
    fun actionFailed(
        request: ActionRequest,
        failure: RecoveryFailure
    ) {

        FirebaseAnalyticsManager.actionFailed(
            action = request.action,
            reason =
                failure.name
        )
    }

    /**
     * Record that automatic recovery actually happened.
     */
    fun actionRecovered(
        request: ActionRequest,
        attempt: Int,
        strategy: String
    ) {

        FirebaseAnalyticsManager.actionRecovered(
            action =
                request.action
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: "unknown",
            attempt =
                attempt
                    .coerceAtLeast(1)
        )

        /*
         * Strategy is intentionally NOT sent as a separate event
         * parameter because the current Firebase Analytics API
         * does not expose a dedicated recovery-strategy field.
         *
         * The strategy remains normalized locally for future
         * extension without changing the current event contract.
         */
        normalizeMethod(
            strategy
        )
    }

    /**
     * Record a final unrecoverable result.
     */
    fun finalFailure(
        request: ActionRequest,
        failure: RecoveryFailure,
        attempts: Int
    ) {

        FirebaseAnalyticsManager.actionFailed(
            action =
                request.action,
            reason =
                "${failure.name}_after_${attempts.coerceAtLeast(1)}_attempts"
        )
    }

    /**
     * Normalize a small operational label.
     *
     * This prevents accidental long/free-form values from
     * entering the analytics layer.
     */
    private fun normalizeMethod(
        value: String
    ): String {

        return value
            .trim()
            .lowercase()
            .replace(
                Regex("[^a-z0-9_]+"),
                "_"
            )
            .trim('_')
            .take(40)
            .ifBlank {
                "unknown"
            }
    }
}
