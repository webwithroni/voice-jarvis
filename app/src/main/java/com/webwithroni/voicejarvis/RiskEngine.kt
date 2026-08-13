package com.webwithroni.voicejarvis

/**
 * Central action safety policy.
 *
 * This is intentionally deterministic. The LLM may suggest
 * an action, but the LLM does not decide whether confirmation
 * is required.
 */
object RiskEngine {

    fun riskFor(
        action: String
    ): ActionRisk {

        return when (
            action.lowercase()
        ) {

            "read_screen",
            "scroll",
            "swipe",
            "tap",
            "back",
            "home",
            "recents",
            "get_battery",
            "get_device_info" ->
                ActionRisk.SAFE

            "launch_app",
            "open_app",
            "media_control",
            "type" ->
                ActionRisk.LOW

            "send_message",
            "send_sms",
            "call",
            "delete_file",
            "change_settings" ->
                ActionRisk.MEDIUM

            "install_app",
            "account_change",
            "security_change" ->
                ActionRisk.HIGH

            "payment",
            "prepare_payment",
            "financial_transfer",
            "purchase",
            "subscription" ->
                ActionRisk.CRITICAL

            else ->
                ActionRisk.MEDIUM
        }
    }

    fun requiresConfirmation(
        risk: ActionRisk
    ): Boolean {

        return when (risk) {

            ActionRisk.SAFE ->
                false

            ActionRisk.LOW ->
                false

            ActionRisk.MEDIUM ->
                true

            ActionRisk.HIGH ->
                true

            ActionRisk.CRITICAL ->
                true
        }
    }

    fun requiresVerification(
        action: String
    ): Boolean {

        return when (
            action.lowercase()
        ) {

            "scroll",
            "swipe",
            "tap",
            "type",
            "send_message",
            "send_sms",
            "call",
            "payment",
            "change_settings" ->
                true

            else ->
                false
        }
    }
}
