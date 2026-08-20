package com.webwithroni.voicejarvis

/**
 * Canonical state for the active Jarvis session.
 *
 * This model is deliberately platform-independent so transition policy can be
 * tested without an emulator or a live provider connection.
 */
enum class RuntimeInteractionState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    INTERRUPTED,
    PAUSED,
    ERROR
}

enum class RuntimeConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

enum class RuntimeLifecycleState {
    STOPPED,
    ACTIVE
}

data class JarvisRuntimeState(
    val lifecycle: RuntimeLifecycleState = RuntimeLifecycleState.STOPPED,
    val interaction: RuntimeInteractionState = RuntimeInteractionState.IDLE,
    val connection: RuntimeConnectionState = RuntimeConnectionState.DISCONNECTED,
    val activeTurnId: String? = null,
    val userTranscript: String = "",
    val assistantTranscript: String = "",
    val pendingConfirmationAction: String? = null,
    val errorMessage: String? = null
)

sealed interface JarvisRuntimeEvent {
    data object ServiceStarted : JarvisRuntimeEvent
    data object ServiceStopped : JarvisRuntimeEvent
    data object ConnectRequested : JarvisRuntimeEvent
    data object Connected : JarvisRuntimeEvent
    data object ConnectionLost : JarvisRuntimeEvent
    data object ReconnectRequested : JarvisRuntimeEvent
    data object ListeningStarted : JarvisRuntimeEvent
    data object ThinkingStarted : JarvisRuntimeEvent
    data object ResponseStarted : JarvisRuntimeEvent
    data object Interrupted : JarvisRuntimeEvent
    data object Cancelled : JarvisRuntimeEvent
    data object Paused : JarvisRuntimeEvent
    data object TurnCompleted : JarvisRuntimeEvent
    data object Reset : JarvisRuntimeEvent
    data class UserTranscriptUpdated(val text: String) : JarvisRuntimeEvent
    data class AssistantTranscriptUpdated(val text: String) : JarvisRuntimeEvent
    data class ConfirmationRequested(val action: String) : JarvisRuntimeEvent
    data object ConfirmationCleared : JarvisRuntimeEvent
    data object ConfirmationExpired : JarvisRuntimeEvent
    data class Failed(val message: String) : JarvisRuntimeEvent
}

object JarvisRuntimeReducer {

    fun reduce(
        state: JarvisRuntimeState,
        event: JarvisRuntimeEvent
    ): JarvisRuntimeState {
        return when (event) {
            JarvisRuntimeEvent.ServiceStarted ->
                state.copy(
                    lifecycle = RuntimeLifecycleState.ACTIVE,
                    errorMessage = null
                )

            JarvisRuntimeEvent.ServiceStopped ->
                state.copy(
                    lifecycle = RuntimeLifecycleState.STOPPED,
                    interaction = RuntimeInteractionState.IDLE,
                    connection = RuntimeConnectionState.DISCONNECTED,
                    activeTurnId = null,
                    pendingConfirmationAction = null,
                    errorMessage = null
                )

            JarvisRuntimeEvent.ConnectRequested ->
                state.copy(
                    connection = RuntimeConnectionState.CONNECTING,
                    interaction = RuntimeInteractionState.THINKING,
                    errorMessage = null
                )

            JarvisRuntimeEvent.Connected ->
                state.copy(
                    connection = RuntimeConnectionState.CONNECTED,
                    interaction = RuntimeInteractionState.LISTENING,
                    errorMessage = null
                )

            JarvisRuntimeEvent.ConnectionLost ->
                state.copy(
                    connection = RuntimeConnectionState.DISCONNECTED,
                    interaction = RuntimeInteractionState.ERROR,
                    errorMessage = "Connection lost"
                )

            JarvisRuntimeEvent.ReconnectRequested ->
                state.copy(
                    connection = RuntimeConnectionState.RECONNECTING,
                    interaction = RuntimeInteractionState.THINKING,
                    errorMessage = null
                )

            JarvisRuntimeEvent.ListeningStarted ->
                state.copy(
                    interaction = RuntimeInteractionState.LISTENING,
                    errorMessage = null
                )

            JarvisRuntimeEvent.ThinkingStarted ->
                state.copy(
                    interaction = RuntimeInteractionState.THINKING,
                    activeTurnId = state.activeTurnId ?: newTurnId(),
                    errorMessage = null
                )

            JarvisRuntimeEvent.ResponseStarted ->
                state.copy(
                    interaction = RuntimeInteractionState.SPEAKING,
                    errorMessage = null
                )

            JarvisRuntimeEvent.Interrupted ->
                state.copy(
                    interaction = RuntimeInteractionState.INTERRUPTED,
                    errorMessage = null
                )

            JarvisRuntimeEvent.Cancelled ->
                state.copy(
                    interaction = RuntimeInteractionState.LISTENING,
                    activeTurnId = null,
                    userTranscript = "",
                    assistantTranscript = "",
                    pendingConfirmationAction = null,
                    errorMessage = null
                )

            JarvisRuntimeEvent.Paused ->
                state.copy(
                    interaction = RuntimeInteractionState.PAUSED
                )

            JarvisRuntimeEvent.TurnCompleted ->
                state.copy(
                    interaction = RuntimeInteractionState.LISTENING,
                    activeTurnId = null,
                    userTranscript = "",
                    assistantTranscript = "",
                    errorMessage = null
                )

            JarvisRuntimeEvent.Reset ->
                JarvisRuntimeState(
                    connection = state.connection
                )

            is JarvisRuntimeEvent.UserTranscriptUpdated ->
                state.copy(
                    userTranscript = event.text
                )

            is JarvisRuntimeEvent.AssistantTranscriptUpdated ->
                state.copy(
                    assistantTranscript = event.text
                )

            is JarvisRuntimeEvent.ConfirmationRequested ->
                state.copy(
                    pendingConfirmationAction = event.action
                )

            JarvisRuntimeEvent.ConfirmationCleared ->
                state.copy(
                    pendingConfirmationAction = null
                )

            JarvisRuntimeEvent.ConfirmationExpired ->
                state.copy(
                    interaction = RuntimeInteractionState.LISTENING,
                    pendingConfirmationAction = null,
                    errorMessage = null
                )

            is JarvisRuntimeEvent.Failed ->
                state.copy(
                    interaction = RuntimeInteractionState.ERROR,
                    errorMessage = event.message
                )
        }
    }

    private fun newTurnId(): String =
        "turn-${System.nanoTime()}"
}
