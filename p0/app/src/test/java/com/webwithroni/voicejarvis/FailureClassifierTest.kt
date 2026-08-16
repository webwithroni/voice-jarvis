package com.webwithroni.voicejarvis

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for the deterministic failure classifier.
 * Covers the recovery-decision inputs: recoverable vs non-recoverable,
 * and side-effect uncertainty (which must never be blindly repeated).
 */
class FailureClassifierTest {

    private fun request(action: String) = ActionRequest(action = action)

    private fun result(status: ActionStatus, verified: Boolean = false, confirm: Boolean = false) =
        ActionResult(status = status, action = "x", message = "", verified = verified, requiresConfirmation = confirm)

    @Test
    fun verifiedActionNeedsNoRecovery() {
        val r = FailureClassifier.classify(request("tap"), result(ActionStatus.VERIFIED, verified = true))
        assertEquals(RecoveryFailure.NONE, r)
    }

    @Test
    fun unavailableIsCapabilityUnavailable() {
        val r = FailureClassifier.classify(request("call"), result(ActionStatus.UNAVAILABLE))
        assertEquals(RecoveryFailure.CAPABILITY_UNAVAILABLE, r)
    }

    @Test
    fun requiresUserIsConfirmationRequired() {
        assertEquals(
            RecoveryFailure.USER_CONFIRMATION_REQUIRED,
            FailureClassifier.classify(request("send_sms"), result(ActionStatus.REQUIRES_USER))
        )
        assertEquals(
            RecoveryFailure.USER_CONFIRMATION_REQUIRED,
            FailureClassifier.classify(request("send_sms"), result(ActionStatus.EXECUTED, confirm = true))
        )
    }

    @Test
    fun failedScrollIsStaleUiOtherwiseExecutionFailed() {
        assertEquals(RecoveryFailure.STALE_UI, FailureClassifier.classify(request("scroll"), result(ActionStatus.FAILED)))
        assertEquals(RecoveryFailure.EXECUTION_FAILED, FailureClassifier.classify(request("tap"), result(ActionStatus.FAILED)))
    }

    @Test
    fun unknownScrollIsVerificationUnknown() {
        assertEquals(RecoveryFailure.VERIFICATION_UNKNOWN, FailureClassifier.classify(request("scroll"), result(ActionStatus.UNKNOWN)))
        assertEquals(RecoveryFailure.VERIFICATION_UNKNOWN, FailureClassifier.classify(request("swipe"), result(ActionStatus.UNKNOWN)))
    }

    @Test
    fun unknownSideEffectActionsAreSideEffectUncertain() {
        listOf("tap", "tap_element", "type", "back", "home", "recents", "open_app", "launch_app").forEach {
            assertEquals("$it UNKNOWN should be SIDE_EFFECT_UNCERTAIN",
                RecoveryFailure.SIDE_EFFECT_UNCERTAIN, FailureClassifier.classify(request(it), result(ActionStatus.UNKNOWN)))
        }
    }

    @Test
    fun unknownOtherActionsAreNonRecoverable() {
        assertEquals(RecoveryFailure.NON_RECOVERABLE, FailureClassifier.classify(request("send_sms"), result(ActionStatus.UNKNOWN)))
    }

    @Test
    fun partialIsSideEffectUncertain() {
        assertEquals(RecoveryFailure.SIDE_EFFECT_UNCERTAIN, FailureClassifier.classify(request("send_sms"), result(ActionStatus.PARTIAL)))
    }
}
