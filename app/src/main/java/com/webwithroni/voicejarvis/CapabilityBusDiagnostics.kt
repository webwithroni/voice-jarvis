package com.webwithroni.voicejarvis

import android.content.Context

/**
 * Non-destructive diagnostics for the Capability Bus.
 *
 * This class does not execute device actions.
 * It only verifies planning, capability mapping,
 * risk assignment, and validation behavior.
 */
object CapabilityBusDiagnostics {

    data class Check(
        val name: String,
        val passed: Boolean,
        val message: String
    )

    fun run(
        context: Context
    ): List<Check> {

        val bus =
            CapabilityBus(context)

        val checks =
            mutableListOf<Check>()

        /*
         * SAFE action should be accepted.
         */
        val readScreen =
            bus.plan(
                action = "read screen"
            )

        checks += Check(
            name = "Normalize read_screen",
            passed =
                readScreen.action ==
                    "read_screen",
            message =
                readScreen.action
        )

        /*
         * Unknown action must NOT map to APP_CONTROL.
         */
        val unknown =
            bus.plan(
                action = "definitely_not_a_real_action"
            )

        val unknownCapability =
            bus.capabilityFor(
                "definitely_not_a_real_action"
            )

        checks += Check(
            name = "Reject unknown capability",
            passed =
                unknownCapability.id ==
                    CapabilityManager.UNKNOWN &&
                !unknownCapability.available,
            message =
                "capability=${unknownCapability.id}, " +
                    "available=${unknownCapability.available}"
        )

        /*
         * Payment must remain critical.
         */
        val payment =
            bus.plan(
                action = "payment"
            )

        checks += Check(
            name = "Payment risk is CRITICAL",
            passed =
                payment.risk ==
                    ActionRisk.CRITICAL,
            message =
                payment.risk.name
        )

        /*
         * Medium-risk actions must require confirmation.
         */
        val sendSms =
            bus.plan(
                action = "send_sms"
            )

        val smsValidation =
            bus.validate(
                sendSms
            )

        checks += Check(
            name = "SMS requires confirmation",
            passed =
                sendSms.risk ==
                    ActionRisk.MEDIUM &&
                smsValidation
                    ?.status ==
                    ActionStatus.REQUIRES_USER,
            message =
                smsValidation
                    ?.status
                    ?.name
                    ?: "NULL"
        )

        /*
         * Safe read action should not be blocked by risk policy.
         *
         * Capability availability may still depend on Accessibility.
         */
        val validation =
            bus.validate(
                readScreen
            )

        checks += Check(
            name = "Safe action risk policy",
            passed =
                validation == null ||
                    validation.status ==
                    ActionStatus.UNAVAILABLE,
            message =
                validation
                    ?.message
                    ?: "ALLOWED"
        )

        return checks
    }
}
