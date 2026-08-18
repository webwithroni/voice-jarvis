package com.webwithroni.voicejarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.webwithroni.voicejarvis.orb.OrbState

/**
 * Lightweight Gemini Live voice preview.
 *
 * This is completely separate from JarvisService.
 *
 * It does not:
 * - record microphone audio
 * - start the full assistant
 * - create a conversation session
 * - change the saved production voice
 */
class VoicePreviewController(
    context: Context,
    private val onStateChanged: (VoicePreviewState) -> Unit,
    private val onPlaybackAmplitude: (Float) -> Unit,
    private val onOrbState: (OrbState) -> Unit
) {

    companion object {
        private const val PREVIEW_TIMEOUT_MS = 12_000L
    }

    private val appContext =
        context.applicationContext

    private val handler =
        Handler(Looper.getMainLooper())

    private var client:
        GeminiLiveClient? = null

    private var audioEngine:
        AudioEngine? = null

    private var active =
        false

    private var shuttingDown =
        false

    private val previewTimeoutRunnable =
        Runnable {

            if (
                active &&
                !shuttingDown &&
                client != null
            ) {

                failPreview()
            }
        }

    fun preview(
        voiceId: String
    ) {

        if (active) {
            stop()
        }

        val apiKey =
            BuildConfig.GEMINI_API_KEY

        if (apiKey.isBlank()) {

            onStateChanged(
                VoicePreviewState.ERROR
            )

            return
        }

        val voice =
            VoiceCatalog.find(
                voiceId
            )

        active =
            true

        shuttingDown =
            false

        onStateChanged(
            VoicePreviewState.LOADING
        )

        onOrbState(
            OrbState.THINKING
        )

        handler.removeCallbacks(
            previewTimeoutRunnable
        )

        handler.postDelayed(
            previewTimeoutRunnable,
            PREVIEW_TIMEOUT_MS
        )

        val engine =
            AudioEngine(
                onMicChunk = {
                    // Preview never records.
                },
                onMicAmplitude = {
                    // Preview never records.
                },
                onPlaybackAmplitude = { level ->

                    handler.post {
                        onPlaybackAmplitude(
                            level
                        )
                    }
                },
                onPlaybackIdle = {

                    handler.post {

                        if (
                            active &&
                            !shuttingDown
                        ) {

                            finishAfterPlayback()
                        }
                    }
                },
                onRecordingError = { error ->

                    handler.post {

                        failPreview()
                    }
                }
            )

        audioEngine =
            engine

        val previewPrompt =
            """
            You are JARVIS during a voice preview.

            Speak only this exact sentence:

            "Hi, I'm JARVIS. This is how I sound."
            """.trimIndent()

        val previewClient =
            GeminiLiveClient(

                apiKey =
                    apiKey,

                systemPrompt =
                    previewPrompt,

                voiceName =
                    voice.id,

                onSetupComplete = {

                    handler.post {

                        if (
                            !active ||
                            shuttingDown
                        ) {
                            return@post
                        }

                        handler.removeCallbacks(
                            previewTimeoutRunnable
                        )

                        engine.startPlayback()

                        onStateChanged(
                            VoicePreviewState.PLAYING
                        )

                        onOrbState(
                            OrbState.SPEAKING
                        )

                        client?.sendText(
                            "Begin the voice preview."
                        )
                    }
                },

                onAudioChunk = { bytes ->

                    if (
                        active &&
                        !shuttingDown
                    ) {

                        engine.enqueuePlayback(
                            bytes
                        )
                    }
                },

                onInputTranscript = {
                    // Preview does not use microphone input.
                },

                onOutputTranscript = {
                    // Transcript is intentionally ignored.
                },

                onTurnComplete = {
                    // AudioEngine determines actual playback completion.
                },

                onInterrupted = {
                    handler.post {
                        stop()
                    }
                },

                onGenerationComplete = {
                    // Wait for AudioEngine playback to drain.
                },

                onToolCall = { id, name, args ->

                    // Preview never needs tools.
                    client?.sendToolResponse(
                        id,
                        name,
                        org.json.JSONObject()
                            .put(
                                "error",
                                "Tools are disabled during voice preview."
                            )
                    )
                },

                onError = {

                    handler.post {

                        failPreview()
                    }
                },

                onDisconnected = {

                    handler.post {

                        if (
                            active &&
                            !shuttingDown
                        ) {

                            onStateChanged(
                                VoicePreviewState.IDLE
                            )
                        }
                    }
                }
            )

        client =
            previewClient

        previewClient.connect()
    }

    private fun failPreview() {

        if (
            shuttingDown
        ) {
            return
        }

        shuttingDown =
            true

        handler.removeCallbacks(
            previewTimeoutRunnable
        )

        client?.disconnect(
            manual = true
        )

        client =
            null

        audioEngine?.stopPlayback()
        audioEngine?.release()

        audioEngine =
            null

        active =
            false

        onStateChanged(
            VoicePreviewState.ERROR
        )

        onOrbState(
            OrbState.LISTENING
        )

        shuttingDown =
            false
    }

    private fun finishAfterPlayback() {

        if (
            shuttingDown
        ) {
            return
        }

        shuttingDown =
            true

        handler.removeCallbacks(
            previewTimeoutRunnable
        )

        onStateChanged(
            VoicePreviewState.IDLE
        )

        onOrbState(
            OrbState.LISTENING
        )

        client?.disconnect(
            manual = true
        )

        client =
            null

        audioEngine?.release()

        audioEngine =
            null

        active =
            false

        shuttingDown =
            false
    }

    fun stop() {

        if (
            !active
        ) {
            return
        }

        shuttingDown =
            true

        handler.removeCallbacks(
            previewTimeoutRunnable
        )

        client?.disconnect(
            manual = true
        )

        client =
            null

        audioEngine?.stopPlayback()
        audioEngine?.release()

        audioEngine =
            null

        active =
            false

        shuttingDown =
            false

        onStateChanged(
            VoicePreviewState.IDLE
        )

        onOrbState(
            OrbState.LISTENING
        )
    }

    fun release() {

        handler.removeCallbacks(
            previewTimeoutRunnable
        )

        stop()
    }
}
