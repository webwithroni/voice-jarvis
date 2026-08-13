package com.webwithroni.voicejarvis

/**
 * Converts a raw action intent into a normalized ActionRequest.
 *
 * The planner does NOT execute anything.
 * It also does NOT decide security policy.
 *
 * CapabilityManager + RiskEngine remain authoritative.
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

        val risk =
            RiskEngine.riskFor(
                normalized
            )

        return ActionRequest(
            action = normalized,
            target = target?.trim(),
            parameters = parameters,
            risk = risk
        )
    }

    fun validate(
        request: ActionRequest
    ): ActionResult? {

        val capability =
            capabilityManager.canExecute(
                request
            )

        if (!capability.available) {

            return ActionResult(
                status =
                    ActionStatus.UNAVAILABLE,
                action =
                    request.action,
                message =
                    "${capability.name} is not available.",
                verified = false
            )
        }

        /*
         * Do not trust a risk value supplied by an LLM.
         * Recalculate from the action itself.
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
                    "User confirmation is required.",
                verified = false,
                requiresConfirmation = true
            )
        }

        return null
    }
}
