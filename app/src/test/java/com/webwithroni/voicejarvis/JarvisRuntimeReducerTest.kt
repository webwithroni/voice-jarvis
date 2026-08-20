package com.webwithroni.voicejarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class JarvisRuntimeReducerTest {

    @Test
    fun connectionLifecycleEntersListening() {
        val started = JarvisRuntimeReducer.reduce(
            JarvisRuntimeState(),
            JarvisRuntimeEvent.ServiceStarted
        )
        val connecting = JarvisRuntimeReducer.reduce(
            started,
            JarvisRuntimeEvent.ConnectRequested
        )

        val connected = JarvisRuntimeReducer.reduce(
            connecting,
            JarvisRuntimeEvent.Connected
        )

        assertEquals(RuntimeConnectionState.CONNECTED, connected.connection)
        assertEquals(RuntimeInteractionState.LISTENING, connected.interaction)
        assertEquals(RuntimeLifecycleState.ACTIVE, connected.lifecycle)
        assertEquals(null, connected.errorMessage)
    }

    @Test
    fun connectionFailureAndRecoveryAreExplicit() {
        val connected = JarvisRuntimeState(
            lifecycle = RuntimeLifecycleState.ACTIVE,
            connection = RuntimeConnectionState.CONNECTED,
            interaction = RuntimeInteractionState.SPEAKING
        )
        val lost = JarvisRuntimeReducer.reduce(
            connected,
            JarvisRuntimeEvent.ConnectionLost
        )
        val reconnecting = JarvisRuntimeReducer.reduce(
            lost,
            JarvisRuntimeEvent.ReconnectRequested
        )
        val recovered = JarvisRuntimeReducer.reduce(
            reconnecting,
            JarvisRuntimeEvent.Connected
        )

        assertEquals(RuntimeConnectionState.DISCONNECTED, lost.connection)
        assertEquals(RuntimeInteractionState.ERROR, lost.interaction)
        assertEquals(RuntimeConnectionState.RECONNECTING, reconnecting.connection)
        assertEquals(RuntimeInteractionState.THINKING, reconnecting.interaction)
        assertEquals(RuntimeConnectionState.CONNECTED, recovered.connection)
        assertEquals(RuntimeInteractionState.LISTENING, recovered.interaction)
    }

    @Test
    fun thinkingStartsATurnAndCompletionClearsTurnData() {
        val thinking = JarvisRuntimeReducer.reduce(
            JarvisRuntimeState(connection = RuntimeConnectionState.CONNECTED),
            JarvisRuntimeEvent.ThinkingStarted
        )
        val withTranscript = JarvisRuntimeReducer.reduce(
            JarvisRuntimeReducer.reduce(
                thinking,
                JarvisRuntimeEvent.UserTranscriptUpdated("Set a timer")
            ),
            JarvisRuntimeEvent.AssistantTranscriptUpdated("For how long?")
        )
        val completed = JarvisRuntimeReducer.reduce(
            withTranscript,
            JarvisRuntimeEvent.TurnCompleted
        )

        assertEquals(RuntimeInteractionState.THINKING, thinking.interaction)
        assertNotNull(thinking.activeTurnId)
        assertEquals(RuntimeInteractionState.LISTENING, completed.interaction)
        assertEquals(null, completed.activeTurnId)
        assertEquals("", completed.userTranscript)
        assertEquals("", completed.assistantTranscript)
    }

    @Test
    fun interruptionAndConfirmationRemainExplicit() {
        val speaking = JarvisRuntimeState(
            interaction = RuntimeInteractionState.SPEAKING,
            connection = RuntimeConnectionState.CONNECTED
        )
        val interrupted = JarvisRuntimeReducer.reduce(
            speaking,
            JarvisRuntimeEvent.Interrupted
        )
        val awaitingConfirmation = JarvisRuntimeReducer.reduce(
            interrupted,
            JarvisRuntimeEvent.ConfirmationRequested("send_sms")
        )
        val cleared = JarvisRuntimeReducer.reduce(
            awaitingConfirmation,
            JarvisRuntimeEvent.ConfirmationCleared
        )

        assertEquals(RuntimeInteractionState.INTERRUPTED, interrupted.interaction)
        assertEquals("send_sms", awaitingConfirmation.pendingConfirmationAction)
        assertEquals(null, cleared.pendingConfirmationAction)
    }

    @Test
    fun cancellationClearsTurnAndConfirmationData() {
        val active = JarvisRuntimeState(
            lifecycle = RuntimeLifecycleState.ACTIVE,
            interaction = RuntimeInteractionState.THINKING,
            connection = RuntimeConnectionState.CONNECTED,
            activeTurnId = "turn-1",
            userTranscript = "Set a timer",
            assistantTranscript = "How long?",
            pendingConfirmationAction = "set_timer"
        )
        val cancelled = JarvisRuntimeReducer.reduce(
            active,
            JarvisRuntimeEvent.Cancelled
        )

        assertEquals(RuntimeInteractionState.LISTENING, cancelled.interaction)
        assertEquals(null, cancelled.activeTurnId)
        assertEquals("", cancelled.userTranscript)
        assertEquals("", cancelled.assistantTranscript)
        assertEquals(null, cancelled.pendingConfirmationAction)
    }

    @Test
    fun confirmationExpiryReturnsToListeningAndClearsConfirmation() {
        val awaiting = JarvisRuntimeState(
            lifecycle = RuntimeLifecycleState.ACTIVE,
            interaction = RuntimeInteractionState.THINKING,
            connection = RuntimeConnectionState.CONNECTED,
            pendingConfirmationAction = "send_sms"
        )
        val expired = JarvisRuntimeReducer.reduce(
            awaiting,
            JarvisRuntimeEvent.ConfirmationExpired
        )

        assertEquals(RuntimeInteractionState.LISTENING, expired.interaction)
        assertEquals(null, expired.pendingConfirmationAction)
    }

    @Test
    fun duplicateEventsDoNotCreateNewTurnOrChangeStableState() {
        val first = JarvisRuntimeReducer.reduce(
            JarvisRuntimeState(
                lifecycle = RuntimeLifecycleState.ACTIVE,
                connection = RuntimeConnectionState.CONNECTED
            ),
            JarvisRuntimeEvent.ThinkingStarted
        )
        val duplicateThinking = JarvisRuntimeReducer.reduce(
            first,
            JarvisRuntimeEvent.ThinkingStarted
        )
        val firstTranscript = JarvisRuntimeReducer.reduce(
            duplicateThinking,
            JarvisRuntimeEvent.UserTranscriptUpdated("hello")
        )
        val duplicateTranscript = JarvisRuntimeReducer.reduce(
            firstTranscript,
            JarvisRuntimeEvent.UserTranscriptUpdated("hello")
        )

        assertEquals(first.activeTurnId, duplicateThinking.activeTurnId)
        assertEquals(firstTranscript, duplicateTranscript)
    }

    @Test
    fun serviceStopClearsRuntimeAndDisconnects() {
        val active = JarvisRuntimeState(
            lifecycle = RuntimeLifecycleState.ACTIVE,
            interaction = RuntimeInteractionState.SPEAKING,
            connection = RuntimeConnectionState.CONNECTED,
            activeTurnId = "turn-1",
            pendingConfirmationAction = "send_sms",
            errorMessage = "old error"
        )
        val stopped = JarvisRuntimeReducer.reduce(
            active,
            JarvisRuntimeEvent.ServiceStopped
        )

        assertEquals(RuntimeLifecycleState.STOPPED, stopped.lifecycle)
        assertEquals(RuntimeInteractionState.IDLE, stopped.interaction)
        assertEquals(RuntimeConnectionState.DISCONNECTED, stopped.connection)
        assertEquals(null, stopped.activeTurnId)
        assertEquals(null, stopped.pendingConfirmationAction)
        assertEquals(null, stopped.errorMessage)
    }

    @Test
    fun failurePreservesConnectionAndResetClearsError() {
        val connected = JarvisRuntimeState(
            interaction = RuntimeInteractionState.LISTENING,
            connection = RuntimeConnectionState.CONNECTED
        )
        val failed = JarvisRuntimeReducer.reduce(
            connected,
            JarvisRuntimeEvent.Failed("Provider unavailable")
        )
        val reset = JarvisRuntimeReducer.reduce(
            failed,
            JarvisRuntimeEvent.Reset
        )

        assertEquals(RuntimeInteractionState.ERROR, failed.interaction)
        assertEquals("Provider unavailable", failed.errorMessage)
        assertEquals(RuntimeConnectionState.CONNECTED, reset.connection)
        assertEquals(RuntimeInteractionState.IDLE, reset.interaction)
        assertEquals(null, reset.errorMessage)
    }
}
