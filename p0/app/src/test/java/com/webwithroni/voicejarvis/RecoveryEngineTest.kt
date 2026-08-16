package com.webwithroni.voicejarvis

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * RecoveryEngine loop safety tests.
 *
 * Runs on the JVM (no device / no Robolectric). ActionExecutor is
 * mocked so no Android APIs execute, and RecoveryObservability is
 * neutralised so no Firebase calls happen.
 *
 * Guarantees under test:
 *  - bounded attempts (no infinite retry)
 *  - uncertain side effects are not blindly repeated
 *  - terminal capability failures stop immediately
 */
class RecoveryEngineTest {

    private lateinit var executor: ActionExecutor
    private lateinit var engine: RecoveryEngine

    @Before
    fun setUp() {
        mockkObject(RecoveryObservability)
        every { RecoveryObservability.actionStarted(any()) } just Runs
        every { RecoveryObservability.actionFailed(any(), any()) } just Runs
        every { RecoveryObservability.actionVerified(any(), any(), any()) } just Runs
        every { RecoveryObservability.actionRecovered(any(), any(), any()) } just Runs

        executor = mockk()
        engine = RecoveryEngine(executor)
    }

    @After
    fun tearDown() = unmockkAll()

    private fun executed(action: String) =
        ActionResult(status = ActionStatus.EXECUTED, action = action, message = "ok", verified = false)

    @Test
    fun scrollStopsAfterMaxAttempts() {
        every { executor.execute(any(), any(), any(), any()) } returns executed("scroll")
        val unknown = ActionResult(ActionStatus.UNKNOWN, "scroll", "cannot prove", false)

        val result = engine.execute(
            request = ActionRequest(action = "scroll", risk = ActionRisk.SAFE),
            verify = { _, _ -> unknown },
            captureFingerprint = { null }
        )

        // Exactly MAX_ATTEMPTS executions — never an infinite loop.
        verify(exactly = RecoveryPolicy.MAX_ATTEMPTS) { executor.execute(any(), any(), any(), any()) }
        assertEquals(ActionStatus.UNKNOWN, result.status)
        assertEquals(false, result.verified)
    }

    @Test
    fun uncertainSideEffectIsNotRepeated() {
        every { executor.execute(any(), any(), any(), any()) } returns executed("tap")
        val unknown = ActionResult(ActionStatus.UNKNOWN, "tap", "cannot prove", false)

        engine.execute(
            request = ActionRequest(action = "tap", risk = ActionRisk.SAFE),
            verify = { _, _ -> unknown },
            captureFingerprint = { null }
        )

        // A tap whose side effect is uncertain must be executed only once.
        verify(exactly = 1) { executor.execute(any(), any(), any(), any()) }
    }

    @Test
    fun capabilityUnavailableStopsImmediately() {
        every { executor.execute(any(), any(), any(), any()) } returns
            ActionResult(ActionStatus.UNAVAILABLE, "call", "no permission", false)
        var verifyCalls = 0

        val result = engine.execute(
            request = ActionRequest(action = "call", risk = ActionRisk.MEDIUM),
            verify = { _, _ -> verifyCalls++; ActionResult(ActionStatus.UNKNOWN, "call", "", false) },
            captureFingerprint = { null }
        )

        verify(exactly = 1) { executor.execute(any(), any(), any(), any()) }
        assertEquals(0, verifyCalls)
        assertEquals(ActionStatus.UNAVAILABLE, result.status)
    }

    @Test
    fun executorVerifiedResultIsAccepted() {
        every { executor.execute(any(), any(), any(), any()) } returns
            ActionResult(ActionStatus.VERIFIED, "open_app", "opened", verified = true)

        val result = engine.execute(
            request = ActionRequest(action = "open_app", risk = ActionRisk.LOW),
            verify = { _, _ -> ActionResult(ActionStatus.UNKNOWN, "open_app", "", false) },
            captureFingerprint = { null }
        )

        assertEquals(ActionStatus.VERIFIED, result.status)
        assertEquals(true, result.verified)
    }
}
