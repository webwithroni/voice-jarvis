package com.webwithroni.voicejarvis

/**
 * Central action safety policy.
 *
 * The LLM may suggest an action, but it does not decide:
 *
 * - the action risk
 * - whether confirmation is required
 * - whether verification is required
 *
 * Those decisions remain deterministic here.
 */
object RiskEngine {

    fun riskFor(
        action: String
    ): ActionRisk {

        return when (
            action.trim().lowercase()
        ) {

            /*
             * SAFE
             *
             * These actions do not directly create external
             * communication, purchases, account changes, or
             * other sensitive side effects.
             */
            "read_screen",
            "scroll",
            "swipe",
            "tap",
            "tap_element",
            "back",
            "home",
            "recents",
            "get_battery" ->
                ActionRisk.SAFE

            /*
             * LOW
             *
             * Normal navigation/control actions.
             */
            "launch_app",
            "open_app",
            "media_control",
            "type",
            "toggle_flashlight",
            "set_volume",
            "set_alarm",
            "set_timer" ->
                ActionRisk.LOW

            /*
             * MEDIUM
             *
             * These can communicate externally or modify user
             * state and therefore require explicit confirmation.
             */
            "send_message",
            "send_sms",
            "call",
            "delete_file",
            "change_settings" ->
                ActionRisk.MEDIUM

            /*
             * HIGH
             *
             * Account/security-changing actions.
             */
            "install_app",
            "account_change",
            "security_change" ->
                ActionRisk.HIGH

            /*
             * CRITICAL
             *
             * Financial operations always require explicit
             * user authorization.
             */
            "payment",
            "prepare_payment",
            "financial_transfer",
            "purchase",
            "subscription" ->
                ActionRisk.CRITICAL

            /*
             * Unknown actions fail closed into MEDIUM.
             */
            else ->
                ActionRisk.MEDIUM
        }
    }

    /**
     * Determine whether the user must explicitly confirm
     * before execution.
     */
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

    /**
     * Determine whether the action needs observable
     * post-action verification.
     *
     * Important:
     *
     * EXECUTED != VERIFIED.
     *
     * Only actions with a meaningful verification strategy
     * belong here.
     */
    fun requiresVerification(
        action: String
    ): Boolean {

        return when (
            action.trim().lowercase()
        ) {

            /*
             * Screen-state actions.
             */
            "open_app",
            "launch_app",
            "scroll",
            "swipe",
            "tap",
            "tap_element",
            "type",
            "back",
            "home",
            "recents" ->
                true

            /*
             * These already have application-specific or
             * future verification strategies.
             *
             * They remain enabled here so the capability
             * pipeline does not silently treat them as verified.
             */
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
