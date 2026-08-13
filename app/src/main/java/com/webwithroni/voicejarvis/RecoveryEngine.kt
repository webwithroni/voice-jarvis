package com.webwithroni.voicejarvis

/**
 * Bounded and conservative action recovery engine.
 *
 * Pipeline:
 *
 * Action
 *   ↓
 * Execute
 *   ↓
 * Verify
 *   ↓
 * FailureClassifier
 *   ↓
 * RecoveryPolicy
 *   ↓
 * Retry / Stop
 *
 * Safety guarantees:
 *
 * 1. No recursive CapabilityBus calls.
 * 2. Maximum attempts are bounded.
 * 3. Only explicitly allowed actions may retry.
 * 4. Side-effect uncertainty never becomes automatic retry.
 * 5. UNKNOWN remains UNKNOWN when proof is unavailable.
 *
 * Observability:
 *
 * RecoveryObservability records only coarse operational
 * metadata. It never receives transcripts, screen text,
 * coordinates, action parameters, or private UI content.
 */
class RecoveryEngine(
    private val executor: ActionExecutor
) {

    /**
     * Execute an action with bounded recovery.
     *
     * CapabilityBus performs security validation before this
     * method is reached.
     */
    fun execute(
        request: ActionRequest,
        verify: (
            request: ActionRequest,
            initialFingerprint: String?
        ) -> ActionResult,
        captureFingerprint: () -> String?
    ): ActionResult {

        RecoveryObservability.actionStarted(
            request
        )

        var currentRequest =
            request

        var lastExecutionResult:
            ActionResult? = null

        var lastVerificationResult:
            ActionResult? = null

        var lastFailure =
            RecoveryFailure.NON_RECOVERABLE

        for (
            attempt in 1..RecoveryPolicy.MAX_ATTEMPTS
        ) {

            /*
             * Capture pre-state only when verification is meaningful.
             */
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

            /*
             * Execute exactly once for this attempt.
             */
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
             * Classify the execution outcome first.
             */
            var failure =
                FailureClassifier.classify(
                    currentRequest,
                    executionResult
                )

            lastFailure =
                failure

            /*
             * Terminal security/capability outcomes.
             */
            if (
                failure ==
                    RecoveryFailure.CAPABILITY_UNAVAILABLE ||
                failure ==
                    RecoveryFailure.USER_CONFIRMATION_REQUIRED
            ) {

                RecoveryObservability.actionFailed(
                    currentRequest,
                    failure
                )

                return withRecoveryMetadata(
                    executionResult,
                    attempt,
                    failure
                )
            }

            /*
             * Hard execution failure may be recoverable for scroll.
             *
             * We only continue when RecoveryPolicy explicitly
             * allows it.
             */
            if (
                failure ==
                    RecoveryFailure.EXECUTION_FAILED ||
                failure ==
                    RecoveryFailure.STALE_UI
            ) {

                if (
                    RecoveryPolicy.canRecover(
                        currentRequest,
                        failure,
                        attempt
                    )
                ) {

                    val recoveredRequest =
                        RecoveryPolicy.recoverRequest(
                            currentRequest
                        )

                    if (
                        recoveredRequest != null
                    ) {

                        RecoveryObservability.actionFailed(
                            currentRequest,
                            failure
                        )

                        RecoveryObservability.actionRecovered(
                            currentRequest,
                            attempt + 1,
                            recoveredRequest.action
                        )

                        currentRequest =
                            recoveredRequest

                        continue
                    }
                }

                RecoveryObservability.actionFailed(
                    currentRequest,
                    failure
                )

                return finalFailure(
                    request =
                        currentRequest,
                    executionResult =
                        executionResult,
                    verificationResult =
                        null,
                    failure =
                        failure,
                    attempts =
                        attempt
                )
            }

            /*
             * If the executor itself already proved success,
             * no second verification pass is required.
             */
            if (
                executionResult.status ==
                    ActionStatus.VERIFIED &&
                executionResult.verified
            ) {

                RecoveryObservability.actionVerified(
                    currentRequest,
                    attempt,
                    "executor_verified"
                )

                return withRecoveryMetadata(
                    executionResult,
                    attempt,
                    RecoveryFailure.NONE
                )
            }

            /*
             * No verification policy means the execution result
             * is the highest level of evidence currently available.
             */
            if (
                !RiskEngine.requiresVerification(
                    currentRequest.action
                )
            ) {

                /*
                 * Do not label plain EXECUTED as VERIFIED.
                 */
                return withRecoveryMetadata(
                    executionResult,
                    attempt,
                    failure
                )
            }

            /*
             * Perform post-action verification.
             */
            val verificationResult =
                try {

                    verify(
                        currentRequest,
                        initialFingerprint
                    )

                } catch (
                    e: Exception
                ) {

                    ActionResult(
                        status =
                            ActionStatus.UNKNOWN,
                        action =
                            currentRequest.action,
                        message =
                            "Verification failed unexpectedly: " +
                                (
                                    e.message
                                        ?: e.javaClass.simpleName
                                ),
                        verified = false
                    )
                }

            lastVerificationResult =
                verificationResult

            /*
             * Verification success is authoritative.
             */
            if (
                verificationResult.status ==
                    ActionStatus.VERIFIED &&
                verificationResult.verified
            ) {

                RecoveryObservability.actionVerified(
                    currentRequest,
                    attempt,
                    "verification_engine"
                )

                if (
                    attempt > 1
                ) {

                    RecoveryObservability.actionRecovered(
                        currentRequest,
                        attempt,
                        "verified_after_recovery"
                    )
                }

                return ActionResult(
                    status =
                        ActionStatus.VERIFIED,
                    action =
                        currentRequest.action,
                    message =
                        verificationResult.message,
                    verified = true,
                    requiresConfirmation =
                        verificationResult
                            .requiresConfirmation,
                    data =
                        executionResult.data +
                            verificationResult.data +
                            mapOf(
                                "recoveryAttempts" to
                                    attempt.toString(),
                                "recoveryFailure" to
                                    RecoveryFailure.NONE.name,
                                "automaticRetry" to
                                    (
                                        if (
                                            attempt > 1
                                        ) {
                                            "true"
                                        } else {
                                            "false"
                                        }
                                    )
                            )
                )
            }

            /*
             * Reclassify based on the verification result.
             */
            failure =
                FailureClassifier.classify(
                    currentRequest,
                    verificationResult
                )

            lastFailure =
                failure

            /*
             * Permission/confirmation during verification is terminal.
             */
            if (
                failure ==
                    RecoveryFailure.CAPABILITY_UNAVAILABLE ||
                failure ==
                    RecoveryFailure.USER_CONFIRMATION_REQUIRED
            ) {

                RecoveryObservability.actionFailed(
                    currentRequest,
                    failure
                )

                return withMergedMetadata(
                    executionResult,
                    verificationResult,
                    attempt,
                    failure
                )
            }

            /*
             * Side-effect uncertainty is NEVER retried.
             */
            if (
                failure ==
                    RecoveryFailure.SIDE_EFFECT_UNCERTAIN
            ) {

                RecoveryObservability.actionFailed(
                    currentRequest,
                    failure
                )

                return withMergedMetadata(
                    executionResult,
                    verificationResult,
                    attempt,
                    failure
                )
            }

            /*
             * Only explicitly recoverable failures may continue.
             */
            if (
                RecoveryPolicy.canRecover(
                    currentRequest,
                    failure,
                    attempt
                )
            ) {

                val recoveredRequest =
                    RecoveryPolicy.recoverRequest(
                        currentRequest
                    )

                if (
                    recoveredRequest != null
                ) {

                    RecoveryObservability.actionFailed(
                        currentRequest,
                        failure
                    )

                    RecoveryObservability.actionRecovered(
                        currentRequest,
                        attempt + 1,
                        recoveredRequest.action
                    )

                    currentRequest =
                        recoveredRequest

                    continue
                }
            }

            /*
             * No safe recovery path remains.
             */
            RecoveryObservability.actionFailed(
                currentRequest,
                failure
            )

            return finalFailure(
                request =
                    currentRequest,
                executionResult =
                    executionResult,
                verificationResult =
                    verificationResult,
                failure =
                    failure,
                attempts =
                    attempt
            )
        }

        RecoveryObservability.actionFailed(
            currentRequest,
            lastFailure
        )

        return finalFailure(
            request =
                currentRequest,
            executionResult =
                lastExecutionResult,
            verificationResult =
                lastVerificationResult,
            failure =
                lastFailure,
            attempts =
                RecoveryPolicy.MAX_ATTEMPTS
        )
    }

    /**
     * Add deterministic recovery metadata to a result.
     */
    private fun withRecoveryMetadata(
        result: ActionResult,
        attempts: Int,
        failure: RecoveryFailure
    ): ActionResult {

        return result.copy(
            data =
                result.data +
                    mapOf(
                        "recoveryAttempts" to
                            attempts.toString(),

                        "recoveryFailure" to
                            failure.name,

                        "automaticRetry" to
                            (
                                if (
                                    attempts > 1
                                ) {
                                    "true"
                                } else {
                                    "false"
                                }
                            )
                    )
        )
    }

    /**
     * Merge execution + verification evidence.
     */
    private fun withMergedMetadata(
        executionResult: ActionResult,
        verificationResult: ActionResult,
        attempts: Int,
        failure: RecoveryFailure
    ): ActionResult {

        return ActionResult(
            status =
                verificationResult.status,

            action =
                verificationResult.action,

            message =
                verificationResult.message,

            verified =
                verificationResult.verified,

            requiresConfirmation =
                verificationResult
                    .requiresConfirmation,

            data =
                executionResult.data +
                    verificationResult.data +
                    mapOf(
                        "recoveryAttempts" to
                            attempts.toString(),

                        "recoveryFailure" to
                            failure.name,

                        "automaticRetry" to
                            "false"
                    )
        )
    }

    /**
     * Produce an honest final failure.
     */
    private fun finalFailure(
        request: ActionRequest,
        executionResult: ActionResult?,
        verificationResult: ActionResult?,
        failure: RecoveryFailure,
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
                if (
                    verificationResult != null
                ) {

                    ActionStatus.UNKNOWN

                } else {

                    executionResult
                        ?.status
                        ?: ActionStatus.UNKNOWN
                },

            action =
                request.action,

            message =
                "Recovery stopped after $attempts attempt(s). " +
                    "$executionMessage " +
                    "$verificationMessage",

            verified =
                false,

            requiresConfirmation =
                false,

            data =
                (executionResult?.data ?: emptyMap()) +
                    (verificationResult?.data ?: emptyMap()) +
                    mapOf(
                        "recoveryAttempts" to
                            attempts.toString(),

                        "recoveryFailure" to
                            failure.name,

                        "automaticRetry" to
                            (
                                if (
                                    attempts > 1
                                ) {
                                    "true"
                                } else {
                                    "false"
                                }
                            )
                    )
        )
    }
}
