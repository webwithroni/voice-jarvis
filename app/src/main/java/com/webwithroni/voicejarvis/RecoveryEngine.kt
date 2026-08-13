package com.webwithroni.voicejarvis

/**
 * Bounded and conservative action recovery engine.
 *
 * V1 recovery policy:
 *
 * Only low-risk, retry-safe actions receive automatic recovery.
 *
 * Currently:
 *
 *     scroll
 *       ↓
 *     retry as swipe
 *
 * Important safety rule:
 *
 * Verification failure does NOT automatically mean that the action
 * should be repeated.
 *
 * For side-effecting actions such as:
 *
 * - tap
 * - tap_element
 * - type
 * - open_app
 * - back
 * - home
 * - recents
 *
 * we never blindly repeat the action.
 *
 * This prevents duplicate taps, duplicate text entry, repeated
 * navigation, or unintended repeated app launches.
 *
 * Recovery never calls CapabilityBus recursively.
 */
class RecoveryEngine(
    private val executor: ActionExecutor
) {

    companion object {

        private const val MAX_ATTEMPTS = 2
    }

    /**
     * Execute an action with conservative recovery.
     *
     * Security checks have already been performed by CapabilityBus.
     *
     * This layer is responsible only for:
     *
     * 1. execution
     * 2. verification
     * 3. safe recovery
     */
    fun execute(
        request: ActionRequest,
        verify: (
            request: ActionRequest,
            initialFingerprint: String?
        ) -> ActionResult,
        captureFingerprint: () -> String?
    ): ActionResult {

        /*
         * Only actions explicitly listed here are allowed to retry.
         */
        if (
            !isRecoverable(
                request.action
            )
        ) {

            return executeOnce(
                request = request,
                verify = verify,
                captureFingerprint = captureFingerprint
            )
        }

        var currentRequest =
            request

        var lastExecutionResult:
            ActionResult? = null

        var lastVerificationResult:
            ActionResult? = null

        for (
            attempt in 1..MAX_ATTEMPTS
        ) {

            val initialFingerprint =
                if (
                    RiskEngine.requiresVerification(
                        currentRequest.action
                    )
                ) {

                    captureFingerprint()

                } else {

                    null
                }

            val executionResult =
                try {

                    executor.execute(
                        action =
                            currentRequest.action,
                        target =
                            currentRequest.target,
                        parameters =
                            currentRequest.parameters,
                        skipConfirmation = true
                    )

                } catch (
                    e: Exception
                ) {

                    ActionResult(
                        status =
                            ActionStatus.FAILED,
                        action =
                            currentRequest.action,
                        message =
                            "Action execution failed: " +
                                (
                                    e.message
                                        ?: e.javaClass.simpleName
                                ),
                        verified = false
                    )
                }

            lastExecutionResult =
                executionResult

            /*
             * Permission failures and confirmation requirements
             * are never fixed by retrying.
             */
            if (
                executionResult.status ==
                    ActionStatus.UNAVAILABLE ||
                executionResult.status ==
                    ActionStatus.REQUIRES_USER
            ) {

                return executionResult
            }

            /*
             * Hard failure:
             *
             * For the only V1 recoverable action (scroll), the
             * alternate strategy can still be attempted.
             */
            if (
                executionResult.status ==
                    ActionStatus.FAILED
            ) {

                if (
                    attempt >= MAX_ATTEMPTS
                ) {

                    return executionResult.copy(
                        data =
                            executionResult.data +
                                mapOf(
                                    "recoveryAttempts" to
                                        attempt.toString()
                                )
                    )
                }

                currentRequest =
                    recoverRequest(
                        currentRequest
                    )

                continue
            }

            /*
             * If execution was already verified, we're done.
             */
            if (
                executionResult.status ==
                    ActionStatus.VERIFIED &&
                executionResult.verified
            ) {

                return executionResult
            }

            /*
             * Actions without verification requirements use
             * the execution result directly.
             */
            if (
                !RiskEngine.requiresVerification(
                    currentRequest.action
                )
            ) {

                return executionResult
            }

            /*
             * Observe the actual post-action state.
             */
            val verificationResult =
                verify(
                    currentRequest,
                    initialFingerprint
                )

            lastVerificationResult =
                verificationResult

            when (
                verificationResult.status
            ) {

                ActionStatus.VERIFIED -> {

                    return verificationResult.copy(
                        data =
                            executionResult.data +
                                verificationResult.data +
                                mapOf(
                                    "recoveryAttempts" to
                                        attempt.toString()
                                )
                    )
                }

                ActionStatus.UNAVAILABLE,
                ActionStatus.REQUIRES_USER -> {

                    return verificationResult.copy(
                        data =
                            executionResult.data +
                                verificationResult.data +
                                mapOf(
                                    "recoveryAttempts" to
                                        attempt.toString()
                                )
                    )
                }

                ActionStatus.UNKNOWN,
                ActionStatus.FAILED -> {

                    /*
                     * IMPORTANT:
                     *
                     * We do not retry the action unless the action
                     * is explicitly considered retry-safe.
                     */
                    if (
                        attempt >= MAX_ATTEMPTS
                    ) {

                        return finalUnknown(
                            request =
                                currentRequest,
                            executionResult =
                                executionResult,
                            verificationResult =
                                verificationResult,
                            attempts =
                                attempt
                        )
                    }

                    /*
                     * For scroll, change the implementation strategy:
                     *
                     * accessibility scroll
                     *        ↓
                     * real swipe
                     */
                    currentRequest =
                        recoverRequest(
                            currentRequest
                        )
                }

                else -> {

                    return finalUnknown(
                        request =
                            currentRequest,
                        executionResult =
                            executionResult,
                        verificationResult =
                            verificationResult,
                        attempts =
                            attempt
                    )
                }
            }
        }

        return finalUnknown(
            request =
                currentRequest,
            executionResult =
                lastExecutionResult,
            verificationResult =
                lastVerificationResult,
            attempts =
                MAX_ATTEMPTS
        )
    }

    /**
     * Execute one non-recoverable action exactly once.
     *
     * This is critical for preventing duplicate side effects.
     */
    private fun executeOnce(
        request: ActionRequest,
        verify: (
            request: ActionRequest,
            initialFingerprint: String?
        ) -> ActionResult,
        captureFingerprint: () -> String?
    ): ActionResult {

        val initialFingerprint =
            if (
                RiskEngine.requiresVerification(
                    request.action
                )
            ) {

                captureFingerprint()

            } else {

                null
            }

        val executionResult =
            try {

                executor.execute(
                    action =
                        request.action,
                    target =
                        request.target,
                    parameters =
                        request.parameters,
                    skipConfirmation = true
                )

            } catch (
                e: Exception
            ) {

                return ActionResult(
                    status =
                        ActionStatus.FAILED,
                    action =
                        request.action,
                    message =
                        "Action execution failed: " +
                            (
                                e.message
                                    ?: e.javaClass.simpleName
                            ),
                    verified = false
                )
            }

        /*
         * Do not verify after a hard execution failure.
         */
        if (
            executionResult.status ==
                ActionStatus.FAILED ||
            executionResult.status ==
                ActionStatus.UNAVAILABLE ||
            executionResult.status ==
                ActionStatus.REQUIRES_USER
        ) {

            return executionResult
        }

        /*
         * Executor already verified it.
         */
        if (
            executionResult.status ==
                ActionStatus.VERIFIED &&
            executionResult.verified
        ) {

            return executionResult
        }

        /*
         * No verification policy.
         */
        if (
            !RiskEngine.requiresVerification(
                request.action
            )
        ) {

            return executionResult
        }

        val verificationResult =
            verify(
                request,
                initialFingerprint
            )

        return when (
            verificationResult.status
        ) {

            ActionStatus.VERIFIED -> {

                verificationResult.copy(
                    data =
                        executionResult.data +
                            verificationResult.data +
                            mapOf(
                                "recoveryAttempts" to
                                    "1"
                            )
                )
            }

            ActionStatus.UNAVAILABLE,
            ActionStatus.REQUIRES_USER -> {

                verificationResult.copy(
                    data =
                        executionResult.data +
                            verificationResult.data +
                            mapOf(
                                "recoveryAttempts" to
                                    "1"
                            )
                )
            }

            ActionStatus.UNKNOWN,
            ActionStatus.FAILED -> {

                /*
                 * The action may have happened.
                 *
                 * We refuse to repeat a potentially side-effecting
                 * action merely because verification was inconclusive.
                 */
                ActionResult(
                    status =
                        ActionStatus.UNKNOWN,
                    action =
                        request.action,
                    message =
                        "Action was executed, but its result could not be verified safely. " +
                            "No automatic retry was performed.",
                    verified = false,
                    data =
                        executionResult.data +
                            verificationResult.data +
                            mapOf(
                                "recoveryAttempts" to
                                    "1",
                                "automaticRetry" to
                                    "false"
                            )
                )
            }

            else -> {

                executionResult
            }
        }
    }

    /**
     * Explicit V1 allow-list.
     *
     * Only scroll is currently safe to recover automatically.
     */
    private fun isRecoverable(
        action: String
    ): Boolean {

        return action
            .trim()
            .lowercase()
            .let {
                it == "scroll"
            }
    }

    /**
     * Convert the scroll strategy into a gesture strategy.
     */
    private fun recoverRequest(
        request: ActionRequest
    ): ActionRequest {

        return when (
            request.action
        ) {

            "scroll" -> {

                request.copy(
                    action = "swipe"
                )
            }

            else -> {

                request
            }
        }
    }

    /**
     * Honest final result after recovery is exhausted.
     *
     * UNKNOWN remains UNKNOWN.
     */
    private fun finalUnknown(
        request: ActionRequest,
        executionResult: ActionResult?,
        verificationResult: ActionResult?,
        attempts: Int
    ): ActionResult {

        val executionMessage =
            executionResult
                ?.message
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "Action execution completed."

        val verificationMessage =
            verificationResult
                ?.message
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "Post-action verification was inconclusive."

        return ActionResult(
            status =
                ActionStatus.UNKNOWN,
            action =
                request.action,
            message =
                "Recovery completed after $attempts attempt(s). " +
                    "$executionMessage " +
                    "$verificationMessage",
            verified = false,
            data =
                (executionResult?.data ?: emptyMap()) +
                    (verificationResult?.data ?: emptyMap()) +
                    mapOf(
                        "recoveryAttempts" to
                            attempts.toString(),
                        "automaticRetry" to
                            if (
                                attempts > 1
                            ) {
                                "true"
                            } else {
                                "false"
                            }
                    )
        )
    }
}
