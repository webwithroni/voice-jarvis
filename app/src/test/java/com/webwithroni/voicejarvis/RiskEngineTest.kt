package com.webwithroni.voicejarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the deterministic risk policy.
 * No Android dependencies — runs on the plain JVM.
 */
class RiskEngineTest {

    @Test
    fun safeActionsAreSafe() {
        listOf("read_screen", "scroll", "swipe", "tap", "tap_element", "back", "home", "recents", "get_battery")
            .forEach { assertEquals("$it should be SAFE", ActionRisk.SAFE, RiskEngine.riskFor(it)) }
    }

    @Test
    fun lowActionsAreLow() {
        listOf("launch_app", "open_app", "media_control", "type", "toggle_flashlight", "set_volume", "set_alarm", "set_timer")
            .forEach { assertEquals("$it should be LOW", ActionRisk.LOW, RiskEngine.riskFor(it)) }
    }

    @Test
    fun mediumActionsAreMedium() {
        listOf("send_message", "send_sms", "call", "delete_file", "change_settings")
            .forEach { assertEquals("$it should be MEDIUM", ActionRisk.MEDIUM, RiskEngine.riskFor(it)) }
    }

    @Test
    fun highActionsAreHigh() {
        listOf("install_app", "account_change", "security_change")
            .forEach { assertEquals("$it should be HIGH", ActionRisk.HIGH, RiskEngine.riskFor(it)) }
    }

    @Test
    fun criticalActionsAreCritical() {
        listOf("payment", "prepare_payment", "financial_transfer", "purchase", "subscription")
            .forEach { assertEquals("$it should be CRITICAL", ActionRisk.CRITICAL, RiskEngine.riskFor(it)) }
    }

    @Test
    fun unknownActionFailsClosedToMedium() {
        assertEquals(ActionRisk.MEDIUM, RiskEngine.riskFor("definitely_not_a_real_action"))
    }

    @Test
    fun riskIsCaseAndWhitespaceInsensitive() {
        // A model must not sneak past the risk gate with odd casing/spacing.
        assertEquals(ActionRisk.CRITICAL, RiskEngine.riskFor("  PAYMENT "))
        assertEquals(ActionRisk.MEDIUM, RiskEngine.riskFor("Send_SMS"))
    }

    @Test
    fun confirmationRequiredForMediumAndAbove() {
        assertFalse(RiskEngine.requiresConfirmation(ActionRisk.SAFE))
        assertFalse(RiskEngine.requiresConfirmation(ActionRisk.LOW))
        assertTrue(RiskEngine.requiresConfirmation(ActionRisk.MEDIUM))
        assertTrue(RiskEngine.requiresConfirmation(ActionRisk.HIGH))
        assertTrue(RiskEngine.requiresConfirmation(ActionRisk.CRITICAL))
    }

    @Test
    fun verificationRequiredForScreenAndSideEffectActions() {
        listOf("open_app", "launch_app", "scroll", "swipe", "tap", "tap_element", "type", "back", "home", "recents",
            "send_message", "send_sms", "call", "payment", "change_settings")
            .forEach { assertTrue("$it should require verification", RiskEngine.requiresVerification(it)) }
    }

    @Test
    fun verificationNotRequiredForPureReadActions() {
        assertFalse(RiskEngine.requiresVerification("get_battery"))
        assertFalse(RiskEngine.requiresVerification("get_location"))
        assertFalse(RiskEngine.requiresVerification("unknown_action"))
    }
}
