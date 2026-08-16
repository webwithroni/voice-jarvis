package com.webwithroni.voicejarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ActionPlanner integration tests.
 *
 * Runs on the JVM via Robolectric (no device needed) because
 * CapabilityManager references Android types at class-load time.
 * A FakeCapabilityEnvironment gives deterministic availability.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionPlannerTest {

    private fun planner(env: FakeCapabilityEnvironment) =
        ActionPlanner(CapabilityManager(env))

    private fun accessibilityOn() =
        FakeCapabilityEnvironment().enable(RuntimeService.ACCESSIBILITY)

    @Test
    fun normalizesActionName() {
        val req = planner(accessibilityOn()).plan("Read Screen")
        assertEquals("read_screen", req.action)
    }

    @Test
    fun blankTargetBecomesNull() {
        val req = planner(accessibilityOn()).plan("open_app", target = "   ")
        assertNull(req.target)
    }

    @Test
    fun assignsAuthoritativeRisk() {
        assertEquals(ActionRisk.CRITICAL, planner(accessibilityOn()).plan("payment").risk)
        assertEquals(ActionRisk.SAFE, planner(accessibilityOn()).plan("read_screen").risk)
    }

    @Test
    fun unknownCapabilityIsRejected() {
        val p = planner(accessibilityOn())
        val res = p.validate(p.plan("definitely_not_a_real_action"))
        assertEquals(ActionStatus.UNAVAILABLE, res?.status)
    }

    @Test
    fun mediumRiskRequiresConfirmation() {
        val p = planner(accessibilityOn())
        val res = p.validate(p.plan("send_sms"))
        assertEquals(ActionStatus.REQUIRES_USER, res?.status)
    }

    @Test
    fun modelSuppliedRiskCannotOverrideAuthoritativeRisk() {
        val p = planner(accessibilityOn())
        // A hand-built request lying about its risk must be rejected.
        val forged = ActionRequest(action = "read_screen", risk = ActionRisk.CRITICAL)
        val res = p.validate(forged)
        assertEquals(ActionStatus.FAILED, res?.status)
    }

    @Test
    fun safeActionAllowedWhenServiceAvailable() {
        val p = planner(accessibilityOn())
        assertNull(p.validate(p.plan("read_screen")))
    }

    @Test
    fun safeActionBlockedWhenServiceUnavailable() {
        val p = ActionPlanner(CapabilityManager(FakeCapabilityEnvironment()))
        val res = p.validate(p.plan("read_screen"))
        assertEquals(ActionStatus.UNAVAILABLE, res?.status)
    }
}
