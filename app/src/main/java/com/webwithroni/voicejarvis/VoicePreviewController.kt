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

    /*
     * Streaming playback has three distinct milestones:
     *
     * 1. Gemini produced at least one audio chunk.
     * 2. Gemini finished generation.
     * 3. AudioEngine drained the playback queue.
     *
     * Playback is complete only after all three are true.
     *
     * This prevents AudioEngine's initial empty-queue idle signal
     * from terminating the preview before Gemini sends audio.
     */
    private var receivedAudio =
        false

    private var generationComplete =
        false

    private var playbackIdleObserved =
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

        receivedAudio =
            false

        generationComplete =
            false

        playbackIdleObserved =
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

                            playbackIdleObserved =
                                true

                            maybeFinishAfterPlayback()
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

                model =
                    ModelPreferences.getSelectedModel(
                        appContext
                    ),

                onSetupComplete = {

                    handler.post {

                        if (
                            !active ||
                            shuttingDown
                        ) {
                            return@post
                        }

                        /*
                         * Keep the preview timeout active until
                         * playback actually completes.
                         *
                         * Setup success only proves the WebSocket
                         * session is configured. It does NOT prove
                         * that Gemini returned audio.
                         */
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

                        receivedAudio =
                            true

                        /*
                         * An idle event seen before the first
                         * audio chunk is no longer meaningful.
                         */
                        playbackIdleObserved =
                            false

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

                    handler.post {

                        if (
                            active &&
                            !shuttingDown
                        ) {

                            generationComplete =
                                true

                            maybeFinishAfterPlayback()
                        }
                    }
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

                            failPreview()
                        }
                    }
                }
            )

        client =
            previewClient

        previewClient.connect()
    }

    private fun maybeFinishAfterPlayback() {

        if (
            !active ||
            shuttingDown
        ) {
            return
        }

        /*
         * Do not finish merely because the playback queue is
         * temporarily empty.
         *
         * Gemini must have actually produced audio AND finished
         * generation before queue-idle means completion.
         */
        if (
            !receivedAudio ||
            !generationComplete ||
            !playbackIdleObserved
        ) {
            return
        }

        finishAfterPlayback()
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

        receivedAudio =
            false

        generationComplete =
            false

        playbackIdleObserved =
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

        receivedAudio =
            false

        generationComplete =
            false

        playbackIdleObserved =
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

        receivedAudio =
            false

        generationComplete =
            false

        playbackIdleObserved =
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
