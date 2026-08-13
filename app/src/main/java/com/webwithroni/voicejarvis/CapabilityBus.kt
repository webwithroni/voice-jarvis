package com.webwithroni.voicejarvis

import android.content.Context

/**
 * Canonical capability execution layer.
 *
 * Architecture:
 *
 * Raw action
 *     ↓
 * ActionPlanner
 *     ↓
 * CapabilityManager
 *     ↓
 * RiskEngine
 *     ↓
 * Capture pre-state
 *     ↓
 * ActionExecutor
 *     ↓
 * VerificationEngine
 *     ↓
 * Final ActionResult
 *
 * This is intentionally the single entry point for device actions.
 *
 * Web/research tools may remain on ToolExecutor temporarily.
 */
class CapabilityBus(
    context: Context
) {

    private val capabilityManager =
        CapabilityManager(
            context
        )

    private val planner =
        ActionPlanner(
            capabilityManager
        )

    private val executor =
        ActionExecutor(
            context
        )

    private val verificationEngine =
        VerificationEngine(
            VoiceJarvisAccessibilityService.instance
        )

    /**
     * Plan an action without executing it.
     */
    fun plan(
        action: String,
        target: String? = null,
        parameters: Map<String, String> = emptyMap()
    ): ActionRequest {

        return planner.plan(
            action = action,
            target = target,
            parameters = parameters
        )
    }

    /**
     * Validate an already planned action.
     *
     * Returns null when the action may proceed.
     */
    fun validate(
        request: ActionRequest
    ): ActionResult? {

        return planner.validate(
            request
        )
    }

    /**
     * Execute through the complete capability pipeline.
     */
    fun execute(
        action: String,
        target: String? = null,
        parameters: Map<String, String> = emptyMap(),
        skipConfirmation: Boolean = false
    ): ActionResult {

        val request =
            planner.plan(
                action = action,
                target = target,
                parameters = parameters
            )

        /*
         * Security policy is always evaluated before execution.
         */
        if (!skipConfirmation) {

            val validation =
                planner.validate(
                    request
                )

            if (
                validation != null
            ) {
                return validation
            }
        }

        /*
         * Capture the state immediately before the action.
         *
         * This is only needed for actions where screen-state
         * verification makes sense.
         */
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

        /*
         * Execute the actual action.
         */
        val executionResult =
            try {

                executor.execute(
                    action = request.action,
                    target = request.target,
                    parameters = request.parameters,
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
                        "Capability execution failed: " +
                            (
                                e.message
                                    ?: e.javaClass.simpleName
                            ),
                    verified = false
                )
            }

        /*
         * Never attempt verification after a hard execution failure.
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
         * Only verify actions that have an explicit strategy.
         */
        if (
            !RiskEngine.requiresVerification(
                request.action
            )
        ) {

            return executionResult
        }

        /*
         * Verify the observed post-state.
         */
        val verificationResult =
            VerificationEngine(
                VoiceJarvisAccessibilityService.instance
            ).verify(
                request = request,
                initialFingerprint =
                    initialFingerprint
            )

        /*
         * A real verification failure/unknown must not be
         * silently converted into success.
         */
        return when (
            verificationResult.status
        ) {

            ActionStatus.VERIFIED -> {

                verificationResult.copy(
                    data =
                        executionResult.data +
                            verificationResult.data
                )
            }

            ActionStatus.UNKNOWN -> {

                ActionResult(
                    status =
                        ActionStatus.UNKNOWN,
                    action =
                        request.action,
                    message =
                        verificationResult.message,
                    verified = false,
                    data =
                        executionResult.data +
                            verificationResult.data
                )
            }

            else -> {

                verificationResult
            }
        }
    }

    /**
     * Convenience helper using the normal confirmation policy.
     */
    fun executeSafe(
        action: String,
        target: String? = null,
        parameters: Map<String, String> = emptyMap()
    ): ActionResult {

        return execute(
            action = action,
            target = target,
            parameters = parameters,
            skipConfirmation = false
        )
    }

    /**
     * Expose authoritative capability state.
     */
    fun capabilityFor(
        action: String
    ): CapabilityState {

        val request =
            planner.plan(
                action = action
            )

        return capabilityManager.canExecute(
            request
        )
    }

    /**
     * Capture the current visible screen.
     *
     * This intentionally duplicates no screen-node references.
     * VerificationEngine owns the actual snapshot representation.
     */
    private fun captureFingerprint(): String? {

        val service =
            VoiceJarvisAccessibilityService.instance
                ?: return null

        return try {

            val root =
                service.rootInActiveWindow
                    ?: return null

            /*
             * Build a lightweight deterministic snapshot locally.
             *
             * We do not retain root or child nodes.
             */
            try {

                val elements =
                    mutableListOf<String>()

                collectFingerprintNodes(
                    node = root,
                    output = elements,
                    depth = 0
                )

                buildString {

                    append(
                        root.packageName
                            ?.toString()
                            .orEmpty()
                    )

                    append('|')

                    elements
                        .take(80)
                        .forEach {
                            append(it)
                            append(';')
                        }

                }.take(14_000)

            } finally {

                root.recycle()
            }

        } catch (
            _: Exception
        ) {

            null
        }
    }

    private fun collectFingerprintNodes(
        node: android.view.accessibility.AccessibilityNodeInfo,
        output: MutableList<String>,
        depth: Int
    ) {

        if (
            output.size >= 80 ||
            depth > 28
        ) {
            return
        }

        val text =
            node.text
                ?.toString()
                .orEmpty()

        val description =
            node.contentDescription
                ?.toString()
                .orEmpty()

        val className =
            node.className
                ?.toString()
                .orEmpty()

        val bounds =
            android.graphics.Rect()

        node.getBoundsInScreen(
            bounds
        )

        if (
            text.isNotBlank() ||
            description.isNotBlank() ||
            bounds.width() > 0 ||
            bounds.height() > 0
        ) {

            output.add(
                buildString {

                    append(text)
                    append('|')

                    append(description)
                    append('|')

                    append(className)
                    append('|')

                    append(bounds.left)
                    append(',')

                    append(bounds.top)
                    append(',')

                    append(bounds.right)
                    append(',')

                    append(bounds.bottom)

                }
            )
        }

        for (
            index in 0 until node.childCount
        ) {

            if (
                output.size >= 80
            ) {
                break
            }

            val child =
                node.getChild(
                    index
                )
                    ?: continue

            try {

                collectFingerprintNodes(
                    node = child,
                    output = output,
                    depth = depth + 1
                )

            } finally {

                child.recycle()
            }
        }
    }
}
