package com.webwithroni.voicejarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the deterministic recovery policy.
 * Guarantees: bounded retries, no infinite retry, and uncertain
 * side effects are never authorised for automatic retry.
 */
class RecoveryPolicyTest {

    private fun request(action: String) = ActionRequest(action = action)

    @Test
    fun maxAttemptsIsBounded() {
        assertEquals(2, RecoveryPolicy.MAX_ATTEMPTS)
    }

    @Test
    fun scrollStaleUiIsRecoverableOnFirstAttempt() {
        assertTrue(RecoveryPolicy.canRecover(request("scroll"), RecoveryFailure.STALE_UI, attempt = 1))
        assertTrue(RecoveryPolicy.canRecover(request("scroll"), RecoveryFailure.VERIFICATION_UNKNOWN, attempt = 1))
    }

    @Test
    fun noRecoveryOnceMaxAttemptsReached() {
        // no infinite retry
        assertFalse(RecoveryPolicy.canRecover(request("scroll"), RecoveryFailure.STALE_UI, attempt = 2))
        assertFalse(RecoveryPolicy.canRecover(request("scroll"), RecoveryFailure.STALE_UI, attempt = 3))
    }

    @Test
    fun nonScrollActionsAreNotAutoRecoverable() {
        assertFalse(RecoveryPolicy.canRecover(request("tap"), RecoveryFailure.STALE_UI, attempt = 1))
        assertFalse(RecoveryPolicy.canRecover(request("send_sms"), RecoveryFailure.EXECUTION_FAILED, attempt = 1))
    }

    @Test
    fun uncertainSideEffectIsNeverRecovered() {
        assertFalse(RecoveryPolicy.canRecover(request("scroll"), RecoveryFailure.SIDE_EFFECT_UNCERTAIN, attempt = 1))
        assertFalse(RecoveryPolicy.canRecover(request("tap"), RecoveryFailure.SIDE_EFFECT_UNCERTAIN, attempt = 1))
    }

    @Test
    fun executionFailedScrollIsNotRecoverable() {
        // Only STALE_UI / VERIFICATION_UNKNOWN are recoverable for scroll.
        assertFalse(RecoveryPolicy.canRecover(request("scroll"), RecoveryFailure.EXECUTION_FAILED, attempt = 1))
    }

    @Test
    fun recoverRequestOnlyForScroll() {
        assertEquals("scroll", RecoveryPolicy.recoverRequest(request("scroll"))?.action)
        assertNull(RecoveryPolicy.recoverRequest(request("tap")))
    }
}
