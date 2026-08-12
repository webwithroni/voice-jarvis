package com.webwithroni.voicejarvis

import android.os.Handler
import android.os.Looper
import android.util.Base64
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val apiKey: String,
    private val systemPrompt: String,
    private val voiceName: String = "Aoede",
    private val model: String = "models/gemini-2.5-flash-native-audio-preview-12-2025",
    private val onSetupComplete: () -> Unit,
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onInputTranscript: (String) -> Unit,
    private val onOutputTranscript: (String) -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onError: (String) -> Unit,
    private val onDisconnected: () -> Unit
) {
    private var webSocket: WebSocket? = null

    // No automatic OkHttp-level ping — mobile carrier networks often silently
    // drop/ignore WebSocket ping frames even when the data channel is fine.
    // We rely entirely on our own app-level keepalive (silent audio chunk every 8s).
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.MILLISECONDS)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private var keepAliveRunnable: Runnable? = null
    private var sessionRenewRunnable: Runnable? = null
    private var manuallyClosed = false

    fun connect() {
        manuallyClosed = false
        cancelTimers()

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { sendSetup() }
            override fun onMessage(webSocket: WebSocket, text: String) { handleMessage(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { handleMessage(bytes.utf8()) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError("WebSocket error: ${t.javaClass.simpleName}: ${t.message}")
                cancelTimers()
                onDisconnected()
                scheduleReconnect()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                cancelTimers()
                onDisconnected()
                if (!manuallyClosed) scheduleReconnect()
            }
        })
        scheduleKeepAlive()
        scheduleSessionRenew()
    }

    private fun sendSetup() {
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", model)
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().put("AUDIO"))
                    put("speech_config", JSONObject().apply {
                        put("voice_config", JSONObject().apply {
                            put("prebuilt_voice_config", JSONObject().put("voice_name", voiceName))
                        })
                    })
                    put("temperature", 0.9)
                })
                put("output_audio_transcription", JSONObject())
                put("input_audio_transcription", JSONObject())
            })
        }
        webSocket?.send(setup.toString())
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            if (json.has("setupComplete")) { onSetupComplete(); return }

            val serverContent = json.optJSONObject("serverContent") ?: return
            val modelTurn = serverContent.optJSONObject("modelTurn")
            modelTurn?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val inlineData = parts.getJSONObject(i).optJSONObject("inlineData")
                    val data = inlineData?.optString("data")
                    if (!data.isNullOrEmpty()) onAudioChunk(Base64.decode(data, Base64.DEFAULT))
                }
            }
            serverContent.optJSONObject("outputTranscription")?.optString("text")
                ?.takeIf { it.isNotEmpty() }?.let(onOutputTranscript)
            serverContent.optJSONObject("inputTranscription")?.optString("text")
                ?.takeIf { it.isNotEmpty() }?.let(onInputTranscript)
            if (serverContent.optBoolean("turnComplete", false)) onTurnComplete()
        } catch (e: Exception) {
            onError("Parse error: ${e.message}")
        }
    }

    fun sendAudioChunk(pcm: ByteArray) {
        val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
        val msg = JSONObject().apply {
            put("realtime_input", JSONObject().apply {
                put("media_chunks", JSONArray().put(JSONObject().apply {
                    put("mime_type", "audio/pcm;rate=16000")
                    put("data", b64)
                }))
            })
        }
        webSocket?.send(msg.toString())
    }

    fun sendInterrupt() {
        val msg = JSONObject().apply {
            put("client_content", JSONObject().apply {
                put("turns", JSONArray())
                put("turn_complete", true)
            })
        }
        webSocket?.send(msg.toString())
    }

    private fun scheduleKeepAlive() {
        keepAliveRunnable = object : Runnable {
            override fun run() {
                sendAudioChunk(ByteArray(320))
                handler.postDelayed(this, 8000)
            }
        }
        handler.postDelayed(keepAliveRunnable!!, 8000)
    }

    private fun scheduleSessionRenew() {
        sessionRenewRunnable = Runnable { disconnect(manual = false); connect() }
        handler.postDelayed(sessionRenewRunnable!!, 540_000)
    }

    private fun cancelTimers() {
        keepAliveRunnable?.let { handler.removeCallbacks(it) }
        sessionRenewRunnable?.let { handler.removeCallbacks(it) }
        keepAliveRunnable = null
        sessionRenewRunnable = null
    }

    private fun scheduleReconnect() {
        if (manuallyClosed) return
        handler.postDelayed({ connect() }, 3000)
    }

    fun disconnect(manual: Boolean = true) {
        manuallyClosed = manual
        cancelTimers()
        webSocket?.close(1000, "Client closed")
        webSocket = null
    }
}
