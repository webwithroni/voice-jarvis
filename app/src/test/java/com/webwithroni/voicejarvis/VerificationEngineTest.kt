package com.webwithroni.voicejarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VerificationEngine test.
 *
 * Deep on-screen verification requires a live AccessibilityService and
 * is covered by NATIVE ANDROID (instrumented) validation. Here we assert
 * the conservative contract: with no accessibility service, an action is
 * reported UNKNOWN (not VERIFIED) — EXECUTED must never imply VERIFIED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerificationEngineTest {

    @Test
    fun withoutServiceVerificationIsUnknownNotVerified() {
        val engine = VerificationEngine(null)
        val result = engine.verify(ActionRequest(action = "open_app"))
        assertEquals(ActionStatus.UNKNOWN, result.status)
        assertFalse(result.verified)
    }
}
