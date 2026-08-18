package com.webwithroni.voicejarvis

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini Live V2 client.
 *
 * Pipeline:
 *
 * Microphone PCM
 *      ↓
 * realtimeInput.audio
 *      ↓
 * Gemini Live
 *      ↓
 * serverContent
 *      ├── model audio
 *      ├── input transcript
 *      ├── output transcript
 *      ├── interrupted
 *      ├── turnComplete
 *      └── generationComplete
 *
 * Connection reliability:
 *
 * - Session resumption
 * - Context window compression
 * - GoAway handling
 * - Generation-safe callbacks
 * - OkHttp WebSocket ping
 *
 * Gemini 3.1 Flash Live:
 *
 * models/gemini-3.1-flash-live-preview
 */
class GeminiLiveClient(
    private val apiKey: String,
    private val systemPrompt: String,
    private val voiceName: String = "Aoede",
    private val model: String =
        "models/gemini-3.1-flash-live-preview",

    private val onSetupComplete: () -> Unit,
    private val onAudioChunk: (ByteArray) -> Unit,

    private val onInputTranscript: (String) -> Unit,
    private val onOutputTranscript: (String) -> Unit,

    private val onTurnComplete: () -> Unit,

    /**
     * Gemini server-side interruption.
     *
     * This is the primary barge-in signal.
     */
    private val onInterrupted: () -> Unit,

    /**
     * Gemini generation finished.
     *
     * This is separate from turnComplete because
     * generation completion is useful for controlling
     * the playback/listening state.
     */
    private val onGenerationComplete: () -> Unit,

    private val onToolCall:
        (id: String, name: String, args: JSONObject) -> Unit,

    private val onError: (String) -> Unit,
    private val onDisconnected: () -> Unit
) {

    private var webSocket: WebSocket? = null

    /**
     * WebSocket transport health.
     *
     * OkHttp ping frames are transport-level keepalive
     * traffic. They are NOT microphone/audio input.
     */
    private val client =
        OkHttpClient.Builder()
            .readTimeout(
                0,
                TimeUnit.MILLISECONDS
            )
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                15,
                TimeUnit.SECONDS
            )
            .pingInterval(
                20,
                TimeUnit.SECONDS
            )
            .build()

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var reconnectRunnable: Runnable? = null

    @Volatile
    private var manuallyClosed = false

    @Volatile
    private var setupCompleted = false

    @Volatile
    private var reconnectScheduled = false

    /**
     * Latest resumable Gemini session handle.
     */
    @Volatile
    private var sessionResumptionHandle: String? = null

    /**
     * Prevent callbacks from an obsolete WebSocket
     * from modifying the current connection.
     */
    @Volatile
    private var connectionGeneration: Long = 0L

    @Volatile
    private var transportState =
        "DISCONNECTED"

    @Volatile
    private var lastInboundMessageAt: Long = 0L

    @Volatile
    private var lastSetupAt: Long = 0L

    fun connect() {

        if (manuallyClosed) {
            return
        }

        cancelReconnect()

        /*
         * Replace any previous socket.
         *
         * Only one active WebSocket is allowed.
         */
        val previousSocket =
            webSocket

        webSocket = null

        previousSocket?.close(
            1000,
            "Replacing connection"
        )

        val generation =
            synchronized(this) {

                connectionGeneration += 1L

                connectionGeneration
            }

        setupCompleted = false

        transportState =
            "CONNECTING"

        val url =
            "wss://generativelanguage.googleapis.com/ws/" +
                "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent" +
                "?key=$apiKey"

        val request =
            Request.Builder()
                .url(url)
                .build()

        val socket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {

                    private fun isCurrentConnection():
                        Boolean {

                        return generation ==
                            connectionGeneration
                    }

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        if (
                            !isCurrentConnection() ||
                            manuallyClosed
                        ) {

                            webSocket.close(
                                1000,
                                "Superseded"
                            )

                            return
                        }

                        transportState =
                            "CONNECTED"

                        sendSetup()
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        if (
                            !isCurrentConnection()
                        ) {
                            return
                        }

                        lastInboundMessageAt =
                            SystemClock
                                .elapsedRealtime()

                        handleMessage(
                            text
                        )
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        bytes: ByteString
                    ) {

                        if (
                            !isCurrentConnection()
                        ) {
                            return
                        }

                        lastInboundMessageAt =
                            SystemClock
                                .elapsedRealtime()

                        handleMessage(
                            bytes.utf8()
                        )
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        if (
                            !isCurrentConnection()
                        ) {
                            return
                        }

                        setupCompleted =
                            false

                        transportState =
                            "DISCONNECTED"

                        onError(
                            "WebSocket error: " +
                                "${t.javaClass.simpleName}: ${t.message}"
                        )

                        onDisconnected()

                        scheduleReconnect()
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        if (
                            !isCurrentConnection()
                        ) {
                            return
                        }

                        setupCompleted =
                            false

                        transportState =
                            "DISCONNECTED"

                        if (
                            !manuallyClosed
                        ) {

                            onError(
                                "WebSocket closed: " +
                                    "code=$code reason=$reason"
                            )

                            onDisconnected()

                            scheduleReconnect()
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        if (
                            !isCurrentConnection()
                        ) {
                            return
                        }

                        transportState =
                            "RECONNECTING"
                    }
                }
            )

        webSocket =
            socket
    }

    /**
     * Send the Gemini Live session configuration.
     */
    private fun sendSetup() {

        val setup =
            JSONObject().apply {

                put(
                    "setup",
                    JSONObject().apply {

                        put(
                            "model",
                            model
                        )

                        put(
                            "systemInstruction",
                            JSONObject().apply {

                                put(
                                    "parts",
                                    JSONArray().put(
                                        JSONObject().put(
                                            "text",
                                            systemPrompt
                                        )
                                    )
                                )
                            }
                        )

                        /*
                         * Session resumption.
                         *
                         * If Gemini previously supplied a valid
                         * handle, use it for the replacement socket.
                         */
                        put(
                            "sessionResumption",
                            JSONObject().apply {

                                sessionResumptionHandle
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?.let {

                                        put(
                                            "handle",
                                            it
                                        )
                                    }
                            }
                        )

                        /*
                         * Long-running voice sessions.
                         */
                        put(
                            "contextWindowCompression",
                            JSONObject().apply {

                                put(
                                    "slidingWindow",
                                    JSONObject()
                                )
                            }
                        )

                        /*
                         * Audio generation configuration.
                         */
                        put(
                            "generationConfig",
                            JSONObject().apply {

                                put(
                                    "responseModalities",
                                    JSONArray().put(
                                        "AUDIO"
                                    )
                                )

                                put(
                                    "speechConfig",
                                    JSONObject().apply {

                                        put(
                                            "voiceConfig",
                                            JSONObject().apply {

                                                put(
                                                    "prebuiltVoiceConfig",
                                                    JSONObject().put(
                                                        "voiceName",
                                                        voiceName
                                                    )
                                                )
                                            }
                                        )
                                    }
                                )

                                /*
                                 * Gemini 3.1 uses thinkingLevel.
                                 *
                                 * Minimal keeps latency low.
                                 */
                                put(
                                    "thinkingConfig",
                                    JSONObject().put(
                                        "thinkingLevel",
                                        "minimal"
                                    )
                                )
                            }
                        )

                        /*
                         * Server-side automatic activity detection.
                         *
                         * Audio input is streamed continuously in
                         * small chunks. Gemini decides turn boundaries.
                         */
                        put(
                            "realtimeInputConfig",
                            JSONObject().apply {

                                put(
                                    "automaticActivityDetection",
                                    JSONObject().apply {

                                        put(
                                            "disabled",
                                            false
                                        )

                                        put(
                                            "startOfSpeechSensitivity",
                                            "START_SENSITIVITY_LOW"
                                        )

                                        put(
                                            "endOfSpeechSensitivity",
                                            "END_SENSITIVITY_LOW"
                                        )

                                        put(
                                            "prefixPaddingMs",
                                            120
                                        )

                                        put(
                                            "silenceDurationMs",
                                            700
                                        )
                                    }
                                )

                                put(
                                    "activityHandling",
                                    "START_OF_ACTIVITY_INTERRUPTS"
                                )

                                put(
                                    "turnCoverage",
                                    "TURN_INCLUDES_ALL_INPUT"
                                )
                            }
                        )

                        /*
                         * Server-side transcription.
                         */
                        put(
                            "outputAudioTranscription",
                            JSONObject()
                        )

                        put(
                            "inputAudioTranscription",
                            JSONObject()
                        )

                        /*
                         * Gemini function calling.
                         */
                        put(
                            "tools",
                            JSONArray().put(
                                JSONObject().put(
                                    "functionDeclarations",
                                    ToolDeclarations.all()
                                )
                            )
                        )
                    }
                )
            }

        val sent =
            webSocket?.send(
                setup.toString()
            ) ?: false

        if (!sent) {

            onError(
                "Failed to send Gemini setup message."
            )
        }
    }

    /**
     * Parse every Gemini Live server event.
     *
     * IMPORTANT:
     *
     * Gemini 3.1 may place multiple content parts
     * inside the same serverContent event.
     *
     * Therefore we process every field independently.
     */
    private fun handleMessage(
        text: String
    ) {

        try {

            val json =
                JSONObject(text)

            /*
             * ==================================================
             * SETUP COMPLETE
             * ==================================================
             */
            if (
                json.has(
                    "setupComplete"
                )
            ) {

                setupCompleted =
                    true

                transportState =
                    "READY"

                lastSetupAt =
                    SystemClock
                        .elapsedRealtime()

                lastInboundMessageAt =
                    lastSetupAt

                onSetupComplete()
            }

            /*
             * ==================================================
             * SESSION RESUMPTION
             * ==================================================
             */
            json.optJSONObject(
                "sessionResumptionUpdate"
            )?.let { update ->

                val resumable =
                    update.optBoolean(
                        "resumable",
                        false
                    )

                val newHandle =
                    update.optString(
                        "newHandle"
                    )

                if (
                    resumable &&
                    newHandle.isNotBlank()
                ) {

                    sessionResumptionHandle =
                        newHandle
                }
            }

            /*
             * ==================================================
             * GO AWAY
             * ==================================================
             *
             * Gemini tells us before the current WebSocket
             * is terminated.
             */
            json.optJSONObject(
                "goAway"
            )?.let { goAway ->

                val timeLeft =
                    goAway.optLong(
                        "timeLeft",
                        0L
                    )

                transportState =
                    "RECONNECTING"

                scheduleReconnect(
                    delayMs =
                        when {

                            timeLeft > 5000L ->
                                (
                                    timeLeft -
                                        2000L
                                    )
                                    .coerceAtMost(
                                        5000L
                                    )

                            timeLeft > 0L ->
                                timeLeft

                            else ->
                                1000L
                        }
                )
            }

            /*
             * ==================================================
             * TOOL CALL
             * ==================================================
             */
            json.optJSONObject(
                "toolCall"
            )?.let { toolCall ->

                val calls =
                    toolCall.optJSONArray(
                        "functionCalls"
                    )

                if (
                    calls != null
                ) {

                    for (
                        i in 0 until calls.length()
                    ) {

                        val call =
                            calls.getJSONObject(
                                i
                            )

                        val id =
                            call.optString(
                                "id"
                            )

                        val name =
                            call.optString(
                                "name"
                            )

                        val args =
                            call.optJSONObject(
                                "args"
                            )
                                ?: JSONObject()

                        onToolCall(
                            id,
                            name,
                            args
                        )
                    }
                }

                /*
                 * Do NOT return here.
                 *
                 * A future server event may contain both
                 * tool-related data and other fields.
                 */
            }

            /*
             * ==================================================
             * SERVER CONTENT
             * ==================================================
             */
            val serverContent =
                json.optJSONObject(
                    "serverContent"
                )
                    ?: return

            /*
             * ==================================================
             * INTERRUPTION
             * ==================================================
             *
             * This must happen immediately.
             *
             * JarvisService will clear the playback queue.
             */
            if (
                serverContent.optBoolean(
                    "interrupted",
                    false
                )
            ) {

                onInterrupted()
            }

            /*
             * ==================================================
             * MODEL CONTENT
             * ==================================================
             *
             * Process ALL parts in the event.
             *
             * Gemini 3.1 can send multiple content parts
             * together.
             */
            val modelTurn =
                serverContent.optJSONObject(
                    "modelTurn"
                )

            modelTurn
                ?.optJSONArray(
                    "parts"
                )
                ?.let { parts ->

                    for (
                        i in 0 until parts.length()
                    ) {

                        val part =
                            parts.getJSONObject(
                                i
                            )

                        /*
                         * Audio can appear as inlineData.
                         */
                        val inlineData =
                            part.optJSONObject(
                                "inlineData"
                            )

                        val data =
                            inlineData
                                ?.optString(
                                    "data"
                                )

                        if (
                            !data.isNullOrEmpty()
                        ) {

                            try {

                                val audio =
                                    Base64.decode(
                                        data,
                                        Base64.DEFAULT
                                    )

                                if (
                                    audio.isNotEmpty()
                                ) {

                                    onAudioChunk(
                                        audio
                                    )
                                }

                            } catch (
                                e: Exception
                            ) {

                                onError(
                                    "Audio decode error: " +
                                        e.message
                                )
                            }
                        }
                    }
                }

            /*
             * ==================================================
             * INPUT TRANSCRIPTION
             * ==================================================
             */
            serverContent
                .optJSONObject(
                    "inputTranscription"
                )
                ?.optString(
                    "text"
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {

                    onInputTranscript(
                        it
                    )
                }

            /*
             * ==================================================
             * OUTPUT TRANSCRIPTION
             * ==================================================
             */
            serverContent
                .optJSONObject(
                    "outputTranscription"
                )
                ?.optString(
                    "text"
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {

                    onOutputTranscript(
                        it
                    )
                }

            /*
             * ==================================================
             * TURN COMPLETE
             * ==================================================
             */
            if (
                serverContent.optBoolean(
                    "turnComplete",
                    false
                )
            ) {

                onTurnComplete()
            }

            /*
             * ==================================================
             * GENERATION COMPLETE
             * ==================================================
             *
             * Treat this independently from turnComplete.
             */
            if (
                serverContent.optBoolean(
                    "generationComplete",
                    false
                )
            ) {

                onGenerationComplete()
            }

        } catch (
            e: Exception
        ) {

            onError(
                "Parse error: ${e.message}"
            )
        }
    }

    /**
     * Send a text turn to Gemini Live.
     *
     * Used by lightweight voice-preview sessions where no
     * microphone input is required.
     */
    fun sendText(
        text: String
    ) {

        if (
            !setupCompleted ||
            text.isBlank()
        ) {
            return
        }

        val message =
            JSONObject().apply {

                put(
                    "clientContent",
                    JSONObject().apply {

                        put(
                            "turns",
                            JSONArray().put(
                                JSONObject().apply {

                                    put(
                                        "role",
                                        "user"
                                    )

                                    put(
                                        "parts",
                                        JSONArray().put(
                                            JSONObject().apply {
                                                put(
                                                    "text",
                                                    text
                                                )
                                            }
                                        )
                                    )
                                }
                            )
                        )

                        put(
                            "turnComplete",
                            true
                        )
                    }
                )
            }

        webSocket?.send(
            message.toString()
        )
    }

    /**
     * Send one PCM audio chunk.
     *
     * Expected input:
     *
     * 16 kHz
     * mono
     * PCM 16-bit
     *
     * AudioEngine currently generates 20 ms chunks.
     */
    fun sendAudioChunk(
        pcm: ByteArray
    ) {

        if (
            !setupCompleted ||
            pcm.isEmpty()
        ) {

            if (
                !manuallyClosed
            ) {

                scheduleReconnect(
                    250L
                )
            }

            return
        }

        val base64 =
            Base64.encodeToString(
                pcm,
                Base64.NO_WRAP
            )

        val message =
            JSONObject().apply {

                put(
                    "realtimeInput",
                    JSONObject().apply {

                        put(
                            "audio",
                            JSONObject().apply {

                                put(
                                    "data",
                                    base64
                                )

                                put(
                                    "mimeType",
                                    "audio/pcm;rate=16000"
                                )
                            }
                        )
                    }
                )
            }

        val socket =
            webSocket

        if (
            socket == null ||
            !setupCompleted
        ) {

            if (
                !manuallyClosed
            ) {

                scheduleReconnect(
                    250L
                )
            }

            return
        }

        val sent =
            socket.send(
                message.toString()
            )

        if (!sent) {

            setupCompleted =
                false

            transportState =
                "RECONNECTING"

            onError(
                "Audio send failed: WebSocket rejected the frame."
            )

            onDisconnected()

            scheduleReconnect(
                250L
            )
        }
    }

    /**
     * Send synchronous Gemini tool response.
     */
    fun sendToolResponse(
        id: String,
        name: String,
        response: JSONObject
    ) {

        if (
            !setupCompleted
        ) {
            return
        }

        val message =
            JSONObject().apply {

                put(
                    "toolResponse",
                    JSONObject().apply {

                        put(
                            "functionResponses",
                            JSONArray().put(
                                JSONObject().apply {

                                    put(
                                        "id",
                                        id
                                    )

                                    put(
                                        "name",
                                        name
                                    )

                                    put(
                                        "response",
                                        response
                                    )
                                }
                            )
                        )
                    }
                )
            }

        webSocket
            ?.takeIf {
                setupCompleted
            }
            ?.send(
                message.toString()
            )
    }

    /**
     * Explicit client-side interruption.
     *
     * Server-side interruption remains the primary path.
     */
    fun sendInterrupt() {

        if (
            !setupCompleted
        ) {
            return
        }

        /*
         * Keep this as a compatibility fallback.
         *
         * Gemini 3.1 prefers realtime input/activity
         * driven interruption.
         */
        val message =
            JSONObject().apply {

                put(
                    "clientContent",
                    JSONObject().apply {

                        put(
                            "turns",
                            JSONArray()
                        )

                        put(
                            "turnComplete",
                            true
                        )
                    }
                )
            }

        webSocket?.send(
            message.toString()
        )
    }

    private fun scheduleReconnect(
        delayMs: Long = 1000L
    ) {

        if (
            manuallyClosed ||
            reconnectScheduled
        ) {

            return
        }

        reconnectScheduled =
            true

        transportState =
            "RECONNECTING"

        reconnectRunnable =
            Runnable {

                reconnectScheduled =
                    false

                reconnectRunnable =
                    null

                if (
                    !manuallyClosed
                ) {

                    connect()
                }
            }

        handler.postDelayed(
            reconnectRunnable!!,
            delayMs.coerceIn(
                250L,
                5000L
            )
        )
    }

    private fun cancelReconnect() {

        reconnectRunnable?.let {

            handler.removeCallbacks(
                it
            )
        }

        reconnectRunnable =
            null

        reconnectScheduled =
            false
    }

    private fun cancelTimers() {

        cancelReconnect()
    }

    fun disconnect(
        manual: Boolean = true
    ) {

        manuallyClosed =
            manual

        cancelTimers()

        synchronized(this) {

            connectionGeneration +=
                1L
        }

        webSocket?.close(
            1000,
            "Client closed"
        )

        webSocket =
            null

        setupCompleted =
            false

        transportState =
            if (
                manual
            ) {
                "DISCONNECTED"
            } else {
                "RECONNECTING"
            }
    }
}
