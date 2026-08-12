package com.webwithroni.voicejarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class JarvisService : Service() {

    interface UiListener {
        fun onState(state: JarvisState, label: String, sub: String)
        fun onAmplitude(level: Float)
        fun onLog(message: String)
        fun onConversation(userText: String?, jarvisText: String?)
    }

    inner class LocalBinder : Binder() {
        fun getService(): JarvisService = this@JarvisService
    }

    private val binder = LocalBinder()
    var listener: UiListener? = null

    private lateinit var audioEngine: AudioEngine
    private lateinit var toolExecutor: ToolExecutor
    private var geminiClient: GeminiLiveClient? = null
    private val handler = Handler(Looper.getMainLooper())

    var isPaused = false
        private set
    private var voiceActive = false
    private var noMoreAudioIncoming = true
    private var silenceRunnable: Runnable? = null
    private var pendingUserText = ""
    private var pendingJarvisText = ""
    private val ampThreshold = 0.06f

    var currentState = JarvisState.THINKING
        private set
    var currentLabel = "CONNECTING"
        private set
    var currentSub = "Waking up Jarvis…"
        private set
    private val logBuffer = StringBuilder()
    private var lastUserText: String? = null
    private var lastJarvisText: String? = null

    companion object {
        const val CHANNEL_ID = "jarvis_voice_channel"
        const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Waking up Jarvis…"))

        toolExecutor = ToolExecutor(this)
        audioEngine = AudioEngine(
            onMicChunk = { chunk -> geminiClient?.sendAudioChunk(chunk) },
            onMicAmplitude = { level -> handler.post { handleMicAmplitude(level) } },
            onPlaybackAmplitude = { level -> handler.post { updateAmplitude(level * 2.2f) } },
            onPlaybackIdle = { handler.post { handlePlaybackIdle() } }
        )

        connectGemini()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder = binder

    private fun connectGemini() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            pushState(JarvisState.ERROR, "TRY AGAIN", "Gemini API key missing.")
            log("Gemini API key is empty — set GEMINI_API_KEY secret.")
            return
        }

        val systemPrompt = "You are Jarvis, Roni's personal voice assistant. " +
            "Reply in the same mix of Hindi, Bengali, or English the user used. " +
            "You are speaking ALOUD, so keep responses short, natural, and conversational " +
            "(1-3 sentences), with no markdown or lists. " +
            "You have tools to call contacts, send WhatsApp/SMS drafts, open apps, control the flashlight, " +
            "and set alarms or timers. You can also control media playback, adjust volume, open the browser, search Google, " +
            "start navigation, look up contact numbers, copy text to clipboard, and get the current location. " +
            "Use these when the user asks for such actions, then briefly confirm what you did. " +
            "Before saying an app isn't installed, always try the open_app tool first — never guess. " +
            "For anything without a dedicated tool — like ordering food, using a specific app's UI, or any multi-step " +
            "task inside an app — use read_screen first to see what's on screen, then tap_element/type_text/scroll_screen/go_back " +
            "to operate it step by step like a human would, re-reading the screen after each action. This is a last resort, " +
            "only when no direct tool covers the request. " +
            "IMPORTANT: when you draft a WhatsApp or SMS message with send_whatsapp/send_sms, do NOT claim it was sent — " +
            "tell the user it's ready and ask 'bhej du?' or similar, and only call send_last_message after they clearly confirm. " +
            "You can also answer_call or end_call when the user asks — but only end_call if they clearly and recently asked to hang up, " +
            "never on an ambiguous or misheard word, since this is an irreversible action. " +
            "You also have a search_web tool for anything current or time-sensitive: news, prices, scores, weather, " +
            "today's date, or facts that may have changed recently. Always use it instead of guessing for such questions."

        geminiClient = GeminiLiveClient(
            apiKey = apiKey,
            systemPrompt = systemPrompt,
            onSetupComplete = {
                handler.post {
                    log("Gemini Live connected.")
                    audioEngine.startRecording()
                    audioEngine.startPlayback()
                    if (!isPaused) pushState(JarvisState.LISTENING, "LISTENING", "I'm listening.")
                }
            },
            onAudioChunk = { bytes ->
                noMoreAudioIncoming = false
                audioEngine.micSendEnabled = false
                audioEngine.enqueuePlayback(bytes)
                handler.post { if (!isPaused) pushState(JarvisState.SPEAKING, "SPEAKING", "") }
            },
            onInputTranscript = { text ->
                pendingUserText += text
                handler.post { pushConversation(pendingUserText, null) }
            },
            onOutputTranscript = { text ->
                pendingJarvisText += text
                handler.post { pushConversation(pendingUserText, pendingJarvisText) }
            },
            onToolCall = { id, name, args ->
                val toolResult = toolExecutor.execute(name, args)
                geminiClient?.sendToolResponse(id, name, toolResult)
                handler.post { log("Tool: $name -> ${toolResult.optString("message")}") }
            },
            onTurnComplete = {
                handler.post {
                    if (pendingUserText.isNotBlank()) log("You: $pendingUserText")
                    if (pendingJarvisText.isNotBlank()) log("Jarvis: $pendingJarvisText")
                    pendingUserText = ""
                    pendingJarvisText = ""
                }
                noMoreAudioIncoming = true
            },
            onError = { msg -> handler.post { log(msg) } },
            onDisconnected = {
                handler.post {
                    if (!isPaused) pushState(JarvisState.THINKING, "RECONNECTING", "One moment.")
                }
            }
        )
        geminiClient?.connect()
    }

    private fun handleMicAmplitude(level: Float) {
        if (isPaused) return
        updateAmplitude(level * 3f)
        if (level > ampThreshold && audioEngine.micSendEnabled) {
            if (!voiceActive) {
                voiceActive = true
                pushState(JarvisState.HEARING, "HEARING", "Go ahead…")
            }
            silenceRunnable?.let { handler.removeCallbacks(it) }
            silenceRunnable = Runnable {
                voiceActive = false
                if (!isPaused) pushState(JarvisState.THINKING, "THINKING", "Let me think.")
            }
            handler.postDelayed(silenceRunnable!!, 700)
        }
    }

    private fun handlePlaybackIdle() {
        if (noMoreAudioIncoming && !isPaused) {
            audioEngine.micSendEnabled = true
            pushState(JarvisState.LISTENING, "LISTENING", "I'm listening.")
        }
    }

    fun toggleMute() {
        isPaused = !isPaused
        if (isPaused) {
            audioEngine.micSendEnabled = false
            audioEngine.clearPlaybackQueue()
            pushState(JarvisState.PAUSED, "PAUSED", "Tap Resume to continue.")
        } else {
            audioEngine.micSendEnabled = true
            pushState(JarvisState.LISTENING, "LISTENING", "I'm listening.")
        }
    }

    private fun pushState(state: JarvisState, label: String, sub: String) {
        currentState = state
        currentLabel = label
        currentSub = sub
        listener?.onState(state, label, sub)
        updateNotification(label)
    }

    private fun updateAmplitude(level: Float) {
        listener?.onAmplitude(level)
    }

    private fun pushConversation(userText: String?, jarvisText: String?) {
        lastUserText = userText ?: lastUserText
        lastJarvisText = jarvisText
        listener?.onConversation(userText, jarvisText)
    }

    fun getLastConversation(): Pair<String?, String?> = lastUserText to lastJarvisText
    fun getLogSnapshot(): String = logBuffer.toString()

    private fun log(msg: String) {
        logBuffer.append("\n").append(msg)
        listener?.onLog(msg)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Jarvis Voice", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Jarvis")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(stateLabel: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, buildNotification(stateLabel))
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        geminiClient?.disconnect()
        audioEngine.release()
    }
}
