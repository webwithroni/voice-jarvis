package com.webwithroni.voicejarvis

import android.os.Handler
import android.os.Looper
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

class GeminiLiveClient(
    private val apiKey: String,
    private val systemPrompt: String,
    private val voiceName: String = "Aoede",
    private val model: String =
        "models/gemini-2.5-flash-native-audio-preview-12-2025",

    private val onSetupComplete: () -> Unit,
    private val onAudioChunk: (ByteArray) -> Unit,

    private val onInputTranscript: (String) -> Unit,
    private val onOutputTranscript: (String) -> Unit,

    private val onTurnComplete: () -> Unit,

    /*
     * Gemini Live server-side interruption event.
     *
     * This is critical for real-time barge-in.
     */
    private val onInterrupted: () -> Unit,

    private val onToolCall:
        (id: String, name: String, args: JSONObject) -> Unit,

    private val onError: (String) -> Unit,
    private val onDisconnected: () -> Unit
) {

    private var webSocket: WebSocket? = null

    /*
     * WebSocket transport health.
     *
     * OkHttp ping frames are transport-level keepalive traffic.
     * They are NOT fake microphone/audio input.
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
        Handler(Looper.getMainLooper())

    private var reconnectRunnable: Runnable? = null

    @Volatile
    private var manuallyClosed = false

    @Volatile
    private var setupCompleted = false

    @Volatile
    private var reconnectScheduled = false

    /*
     * Gemini Live session resumption handle.
     *
     * The server periodically sends a new resumable handle.
     * We reuse the latest valid handle when the WebSocket must
     * be recreated.
     */
    @Volatile
    private var sessionResumptionHandle: String? = null

    /*
     * Protect against callbacks from an older WebSocket changing
     * state after a newer connection has already been established.
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
         * Invalidate the previous WebSocket before creating a
         * replacement connection.
         *
         * This is especially important after Gemini GoAway:
         * we never want two live sockets competing for the same
         * assistant session.
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
        transportState = "CONNECTING"

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
                            android.os.SystemClock
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
                            android.os.SystemClock
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

                        setupCompleted = false
                        transportState = "DISCONNECTED"

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

                        setupCompleted = false
                        transportState = "DISCONNECTED"

                        if (!manuallyClosed) {

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

        webSocket = socket
    }

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
                         * Gemini Live connection/session reliability.
                         *
                         * Google recommends session resumption because
                         * the WebSocket connection is periodically reset.
                         * Context compression prevents long-lived audio
                         * sessions from exhausting the context window.
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

                        put(
                            "contextWindowCompression",
                            JSONObject().apply {

                                put(
                                    "slidingWindow",
                                    JSONObject()
                                )
                            }
                        )

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
                                 * Lower temperature makes
                                 * short voice responses more
                                 * predictable.
                                 */
                                put(
                                    "temperature",
                                    0.7
                                )
                            }
                        )

                        /*
                         * Keep transcription enabled and use
                         * server-side VAD tuned for natural Hindi,
                         * Hinglish and English speech.
                         *
                         * 700ms is a deliberate latency/continuity
                         * compromise: long enough for natural pauses,
                         * much safer than the previous aggressive
                         * local 450ms boundary.
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

                        put(
                            "outputAudioTranscription",
                            JSONObject()
                        )

                        put(
                            "inputAudioTranscription",
                            JSONObject()
                        )

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

    private fun handleMessage(
        text: String
    ) {

        try {

            val json =
                JSONObject(text)

            /*
             * Setup complete.
             */
            if (
                json.has(
                    "setupComplete"
                )
            ) {

                setupCompleted = true
                transportState = "READY"

                lastSetupAt =
                    android.os.SystemClock
                        .elapsedRealtime()

                lastInboundMessageAt =
                    lastSetupAt

                onSetupComplete()

                return
            }

            /*
             * Session resumption update.
             *
             * Keep the newest resumable handle.
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
             * Gemini announces an upcoming WebSocket reset
             * with GoAway. Do not wait for a hard failure.
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
                                (timeLeft - 2000L)
                                    .coerceAtMost(5000L)

                            timeLeft > 0L ->
                                timeLeft

                            else ->
                                1000L
                        }
                )
            }

            /*
             * Tool calls.
             */
            json.optJSONObject(
                "toolCall"
            )?.let { toolCall ->

                val calls =
                    toolCall.optJSONArray(
                        "functionCalls"
                    )

                if (calls != null) {

                    for (
                        i in 0 until calls.length()
                    ) {

                        val call =
                            calls.getJSONObject(i)

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
                            ) ?: JSONObject()

                        onToolCall(
                            id,
                            name,
                            args
                        )
                    }
                }

                return
            }

            val serverContent =
                json.optJSONObject(
                    "serverContent"
                ) ?: return

            /*
             * ==================================================
             * CRITICAL: GEMINI SERVER INTERRUPTION
             * ==================================================
             *
             * When the user starts talking while Jarvis is
             * speaking, Gemini can report:
             *
             * serverContent.interrupted = true
             *
             * We immediately notify JarvisService so it can
             * clear AudioTrack/playback.
             */
            if (
                serverContent.optBoolean(
                    "interrupted",
                    false
                )
            ) {

                onInterrupted()

                /*
                 * Do not process stale model audio from
                 * this server message.
                 */
                return
            }

            /*
             * Model audio.
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
                            parts.getJSONObject(i)

                        val inlineData =
                            part.optJSONObject(
                                "inlineData"
                            )

                        val data =
                            inlineData?.optString(
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

                            } catch (e: Exception) {

                                onError(
                                    "Audio decode error: " +
                                        e.message
                                )
                            }
                        }
                    }
                }

            /*
             * Input transcription.
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
                    onInputTranscript(it)
                }

            /*
             * Output transcription.
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
                    onOutputTranscript(it)
                }

            /*
             * Gemini finished the conversational turn.
             */
            if (
                serverContent.optBoolean(
                    "turnComplete",
                    false
                )
            ) {

                onTurnComplete()
            }

        } catch (e: Exception) {

            onError(
                "Parse error: ${e.message}"
            )
        }
    }

    fun sendAudioChunk(
        pcm: ByteArray
    ) {

        if (
            !setupCompleted ||
            pcm.isEmpty()
        ) {
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

            /*
             * Do not silently discard the user's first speech
             * after an unexpected connection loss.
             *
             * Trigger fast recovery and let the next mic chunk
             * use the fresh session.
             */
            if (!manuallyClosed) {
                scheduleReconnect(250L)
            }

            return
        }

        val sent =
            socket.send(
                message.toString()
            )

        if (!sent) {

            setupCompleted = false
            transportState = "RECONNECTING"

            onError(
                "Audio send failed: WebSocket rejected the frame."
            )

            onDisconnected()

            scheduleReconnect(250L)
        }
    }

    fun sendToolResponse(
        id: String,
        name: String,
        response: JSONObject
    ) {

        if (!setupCompleted) {
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

        webSocket?.takeIf {
            setupCompleted
        }?.send(
            message.toString()
        )
    }

    /*
     * Explicit client-side interruption.
     *
     * This is kept as a secondary mechanism.
     * Server-side interrupted events remain the primary
     * interruption path.
     */
    fun sendInterrupt() {

        if (!setupCompleted) {
            return
        }

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

        reconnectScheduled = true
        transportState = "RECONNECTING"

        reconnectRunnable =
            Runnable {

                reconnectScheduled = false
                reconnectRunnable = null

                if (!manuallyClosed) {
                    connect()
                }
            }

        handler.postDelayed(
            reconnectRunnable!!,
            delayMs.coerceIn(250L, 5000L)
        )
    }

    private fun cancelReconnect() {

        reconnectRunnable?.let {
            handler.removeCallbacks(
                it
            )
        }

        reconnectRunnable = null
        reconnectScheduled = false
    }

    private fun cancelTimers() {
        cancelReconnect()
    }

    fun disconnect(
        manual: Boolean = true
    ) {

        manuallyClosed = manual

        cancelTimers()

        synchronized(this) {
            connectionGeneration += 1L
        }

        webSocket?.close(
            1000,
            "Client closed"
        )

        webSocket = null

        setupCompleted = false

        transportState =
            if (manual) {
                "DISCONNECTED"
            } else {
                "RECONNECTING"
            }
    }
}
