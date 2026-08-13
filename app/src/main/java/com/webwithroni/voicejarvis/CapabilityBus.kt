package com.webwithroni.voicejarvis

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Canonical capability execution layer.
 *
 * Pipeline:
 *
 * Raw action
 *     ↓
 * ActionPlanner
 *     ↓
 * CapabilityManager
 *     ↓
 * RiskEngine
 *     ↓
 * RecoveryEngine
 *     ↓
 * ActionExecutor
 *     ↓
 * VerificationEngine
 *     ↓
 * Final ActionResult
 *
 * Important:
 *
 * RecoveryEngine never calls CapabilityBus recursively.
 *
 * Security validation happens before execution.
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

    private val recoveryEngine =
        RecoveryEngine(
            executor = executor
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
     * Execute through the full capability pipeline.
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
         * Security policy is evaluated before recovery.
         */
        if (
            !skipConfirmation
        ) {

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
         * Recovery owns:
         *
         * - execution
         * - verification
         * - bounded retry
         * - recovery classification
         */
        return recoveryEngine.execute(

            request =
                request,

            verify = {
                verificationRequest,
                initialFingerprint ->

                VerificationEngine(
                    VoiceJarvisAccessibilityService
                        .instance
                ).verify(
                    request =
                        verificationRequest,
                    initialFingerprint =
                        initialFingerprint
                )
            },

            captureFingerprint = {
                captureFingerprint()
            }
        )
    }

    /**
     * Convenience helper using normal confirmation policy.
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
     * Expose the authoritative capability state.
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
     * Capture immutable screen state for verification.
     *
     * No AccessibilityNodeInfo reference escapes this method.
     */
    private fun captureFingerprint(): String? {

        val service =
            VoiceJarvisAccessibilityService
                .instance
                ?: return null

        return try {

            val root =
                service.rootInActiveWindow
                    ?: return null

            try {

                val elements =
                    mutableListOf<String>()

                collectFingerprintNodes(
                    node =
                        root,
                    output =
                        elements,
                    depth =
                        0
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

                }.take(
                    14_000
                )

            } finally {

                root.recycle()
            }

        } catch (
            _: Exception
        ) {

            null
        }
    }

    /**
     * Recursively collect immutable fingerprint data.
     */
    private fun collectFingerprintNodes(
        node: AccessibilityNodeInfo,
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
            Rect()

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
                    node =
                        child,
                    output =
                        output,
                    depth =
                        depth + 1
                )

            } finally {

                child.recycle()
            }
        }
    }
}
