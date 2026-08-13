package com.webwithroni.voicejarvis

/**
 * Converts raw actions into normalized ActionRequest objects.
 *
 * Planner responsibilities:
 * - normalize action names
 * - calculate authoritative risk
 * - attach capability information
 *
 * Planner does NOT execute actions.
 */
class ActionPlanner(
    private val capabilityManager: CapabilityManager
) {

    fun plan(
        action: String,
        target: String? = null,
        parameters: Map<String, String> = emptyMap()
    ): ActionRequest {

        val normalized =
            action
                .trim()
                .lowercase()
                .replace(
                    Regex("\\s+"),
                    "_"
                )

        return ActionRequest(
            action = normalized,
            target = target
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                },
            parameters = parameters,
            risk =
                RiskEngine.riskFor(
                    normalized
                )
        )
    }

    /**
     * Validate a planned action.
     *
     * null = allowed to continue.
     */
    fun validate(
        request: ActionRequest
    ): ActionResult? {

        val capability =
            capabilityManager
                .canExecute(
                    request
                )

        /*
         * Unknown or unavailable capabilities
         * must never reach an executor.
         */
        if (!capability.available) {

            return ActionResult(
                status =
                    ActionStatus.UNAVAILABLE,
                action =
                    request.action,
                message =
                    if (
                        capability.id ==
                        CapabilityManager.UNKNOWN
                    ) {
                        "Capability '${request.action}' is not registered."
                    } else {
                        "${capability.name} is not available."
                    },
                verified = false,
                requiresConfirmation = false
            )
        }

        /*
         * Never trust risk supplied by an upstream model.
         */
        val authoritativeRisk =
            RiskEngine.riskFor(
                request.action
            )

        if (
            authoritativeRisk !=
            request.risk
        ) {

            return ActionResult(
                status =
                    ActionStatus.FAILED,
                action =
                    request.action,
                message =
                    "Action risk validation failed.",
                verified = false
            )
        }

        if (
            RiskEngine.requiresConfirmation(
                authoritativeRisk
            )
        ) {

            return ActionResult(
                status =
                    ActionStatus.REQUIRES_USER,
                action =
                    request.action,
                message =
                    "User confirmation is required before this action.",
                verified = false,
                requiresConfirmation = true
            )
        }

        return null
    }
}
