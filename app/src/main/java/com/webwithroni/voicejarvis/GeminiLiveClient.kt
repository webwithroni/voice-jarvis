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
            .build()

    private val handler =
        Handler(Looper.getMainLooper())

    private var keepAliveRunnable: Runnable? = null
    private var sessionRenewRunnable: Runnable? = null
    private var reconnectRunnable: Runnable? = null

    @Volatile
    private var manuallyClosed = false

    @Volatile
    private var setupCompleted = false

    @Volatile
    private var reconnectScheduled = false

    fun connect() {

        if (manuallyClosed) {
            return
        }

        cancelTimers()

        setupCompleted = false
        reconnectScheduled = false

        val url =
            "wss://generativelanguage.googleapis.com/ws/" +
                "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent" +
                "?key=$apiKey"

        val request =
            Request.Builder()
                .url(url)
                .build()

        webSocket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        sendSetup()
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        handleMessage(text)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        bytes: ByteString
                    ) {

                        handleMessage(
                            bytes.utf8()
                        )
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        setupCompleted = false

                        onError(
                            "WebSocket error: " +
                                "${t.javaClass.simpleName}: ${t.message}"
                        )

                        cancelTimers()

                        onDisconnected()

                        scheduleReconnect()
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        setupCompleted = false

                        cancelTimers()

                        if (!manuallyClosed) {

                            onError(
                                "WebSocket closed: " +
                                    "code=$code reason=$reason"
                            )

                            onDisconnected()

                            scheduleReconnect()
                        }
                    }
                }
            )
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
                         * Keep transcription enabled.
                         */
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

                onSetupComplete()

                scheduleKeepAlive()

                scheduleSessionRenew()

                return
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

        val sent =
            webSocket?.send(
                message.toString()
            ) ?: false

        if (!sent) {

            onError(
                "Audio send failed: WebSocket is not open."
            )
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

        webSocket?.send(
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

    private fun scheduleKeepAlive() {

        keepAliveRunnable =
            object : Runnable {

                override fun run() {

                    if (
                        !manuallyClosed &&
                        setupCompleted
                    ) {

                        /*
                         * Silent PCM keep-alive.
                         *
                         * 320 bytes = 10 ms at 16 kHz
                         * mono 16-bit.
                         */
                        sendAudioChunk(
                            ByteArray(320)
                        )

                        handler.postDelayed(
                            this,
                            8000
                        )
                    }
                }
            }

        handler.postDelayed(
            keepAliveRunnable!!,
            8000
        )
    }

    private fun scheduleSessionRenew() {

        sessionRenewRunnable =
            Runnable {

                if (
                    !manuallyClosed
                ) {

                    disconnect(
                        manual = false
                    )

                    connect()
                }
            }

        handler.postDelayed(
            sessionRenewRunnable!!,
            540_000
        )
    }

    private fun scheduleReconnect() {

        if (
            manuallyClosed ||
            reconnectScheduled
        ) {
            return
        }

        reconnectScheduled = true

        reconnectRunnable =
            Runnable {

                reconnectScheduled = false

                if (!manuallyClosed) {
                    connect()
                }
            }

        handler.postDelayed(
            reconnectRunnable!!,
            3000
        )
    }

    private fun cancelTimers() {

        keepAliveRunnable?.let {
            handler.removeCallbacks(it)
        }

        sessionRenewRunnable?.let {
            handler.removeCallbacks(it)
        }

        reconnectRunnable?.let {
            handler.removeCallbacks(it)
        }

        keepAliveRunnable = null
        sessionRenewRunnable = null
        reconnectRunnable = null

        reconnectScheduled = false
    }

    fun disconnect(
        manual: Boolean = true
    ) {

        manuallyClosed = manual

        cancelTimers()

        webSocket?.close(
            1000,
            "Client closed"
        )

        webSocket = null

        setupCompleted = false
    }
}
