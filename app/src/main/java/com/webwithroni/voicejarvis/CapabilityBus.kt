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
 * ActionExecutor
 *     ↓
 * ActionResult
 *
 * This is intentionally the single entry point for device actions.
 *
 * Web/research tools may remain on ToolExecutor temporarily.
 */
class CapabilityBus(
    context: Context
) {

    private val capabilityManager =
        CapabilityManager(context)

    private val planner =
        ActionPlanner(
            capabilityManager
        )

    private val executor =
        ActionExecutor(
            context
        )

    /**
     * Plan an action without executing it.
     *
     * Future uses:
     * - confirmation UI
     * - workflow planning
     * - risk preview
     * - multi-step execution
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
     * Execute an action through the complete
     * planning / capability / risk pipeline.
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

        if (!skipConfirmation) {

            val validation =
                planner.validate(
                    request
                )

            if (validation != null) {
                return validation
            }
        }

        return try {

            executor.execute(
                action = request.action,
                target = request.target,
                parameters = request.parameters,
                skipConfirmation = true
            )

        } catch (e: Exception) {

            ActionResult(
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
    }

    /**
     * Convenience helper for safe actions.
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
}
