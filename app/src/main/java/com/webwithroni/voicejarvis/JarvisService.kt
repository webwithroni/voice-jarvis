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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var wakeLock: PowerManager.WakeLock? = null

    private var fallbackSpeech: SpeechController? = null
    private var fallbackTts: TtsController? = null
    private var inFallbackMode = false
    private var consecutiveFailures = 0

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

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceJarvis::VoiceLock")
        wakeLock?.acquire(12 * 60 * 60 * 1000L)

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

    private fun currentDateTimeLine(): String {
        val now = SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", Locale.getDefault()).format(Date())
        return "Right now it is $now. Always treat this as the current date and time when asked, without needing to search."
    }

    private fun buildPrimarySystemPrompt(): String {
        return currentDateTimeLine() + " " +
            "You are Jarvis, Roni's personal voice assistant. " +
            "Reply in the same mix of Hindi, Bengali, or English the user used. " +
            "You are speaking ALOUD, so keep responses short, natural, and conversational " +
            "(1-3 sentences), with no markdown or lists. " +
            "You have tools to call contacts, send WhatsApp/SMS drafts, open apps, control the flashlight, " +
            "and set alarms or timers. You can also control media playback, adjust volume, open the browser, search Google, " +
            "start navigation, look up contact numbers, copy text to clipboard, and get the current location. " +
            "Use these when the user asks for such actions, then briefly confirm what you did. " +
            "Before saying an app isn't installed, always try the open_app tool first — never guess. " +
            "For anything without a dedicated tool, use read_screen first, then tap_element/type_text/scroll_screen/go_back " +
            "to operate it step by step, re-reading the screen after each action. This is a last resort. " +
            "IMPORTANT: when you draft a WhatsApp or SMS message, do NOT claim it was sent — " +
            "ask the user to confirm, and only call send_last_message after they clearly confirm. " +
            "You can answer_call or end_call when asked — only end_call on a clear, recent request, never on an ambiguous word. " +
            "You have a search_web tool for anything current or time-sensitive. Use it instead of guessing."
    }

    private fun buildFallbackSystemPrompt(): String {
        return currentDateTimeLine() + " " +
            "You are Jarvis, Roni's personal voice assistant, currently running in a lightweight backup mode " +
            "because the primary voice connection is temporarily unavailable. " +
            "Reply in the same mix of Hindi, Bengali, or English the user used. Keep replies short and conversational " +
            "(1-3 sentences), no markdown. You cannot control apps or the device right now — if asked to do something " +
            "like calling or sending a message, briefly say that full control will be back shortly, once the main connection returns."
    }

    private fun connectGemini() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            pushState(JarvisState.ERROR, "TRY AGAIN", "Gemini API key missing.")
            log("Gemini API key is empty — set GEMINI_API_KEY secret.")
            return
        }

        geminiClient = GeminiLiveClient(
            apiKey = apiKey,
            systemPrompt = buildPrimarySystemPrompt(),
            onSetupComplete = {
                handler.post {
                    log("Gemini Live connected.")
                    consecutiveFailures = 0
                    exitFallbackMode()
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
                    consecutiveFailures++
                    if (consecutiveFailures >= 2 && !inFallbackMode) {
                        enterFallbackMode()
                    } else if (!isPaused && !inFallbackMode) {
                        pushState(JarvisState.THINKING, "RECONNECTING", "One moment.")
                    }
                }
            }
        )
        geminiClient?.connect()
    }

    private fun enterFallbackMode() {
        if (inFallbackMode) return
        inFallbackMode = true
        log("Primary voice unavailable — switching to backup mode.")
        audioEngine.stopRecording()
        audioEngine.stopPlayback()

        if (fallbackTts == null) {
            fallbackTts = TtsController(
                this,
                onSpeakStart = { pushState(JarvisState.SPEAKING, "SPEAKING", "") },
                onSpeakDone = { if (inFallbackMode && !isPaused) startFallbackListening() }
            )
        }
        if (fallbackSpeech == null) {
            fallbackSpeech = SpeechController(
                this,
                onFinalResult = { text -> handleFallbackUserSpeech(text) },
                onError = { msg ->
                    log(msg)
                    if (inFallbackMode && !isPaused) handler.postDelayed({ startFallbackListening() }, 1000)
                },
                onListeningStateChanged = { listening ->
                    if (listening) pushState(JarvisState.LISTENING, "LISTENING (BACKUP)", "Limited backup mode.")
                }
            )
        }
        if (!isPaused) startFallbackListening()
    }

    private fun exitFallbackMode() {
        if (!inFallbackMode) return
        inFallbackMode = false
        fallbackSpeech?.stop()
        fallbackTts?.stop()
        log("Reconnected — full voice restored.")
    }

    private fun startFallbackListening() {
        if (inFallbackMode && !isPaused) fallbackSpeech?.startListening()
    }

    private fun handleFallbackUserSpeech(text: String) {
        log("You: $text")
        pushConversation(text, null)
        pushState(JarvisState.THINKING, "THINKING", "Let me think.")
        FallbackLLM.ask(
            systemPrompt = buildFallbackSystemPrompt(),
            userText = text,
            onResult = { reply ->
                handler.post {
                    log("Jarvis: $reply")
                    pushConversation(text, reply)
                    fallbackTts?.speak(reply)
                }
            },
            onError = { err ->
                handler.post {
                    log(err)
                    pushState(JarvisState.ERROR, "TRY AGAIN", "Sorry, say that again?")
                    if (inFallbackMode && !isPaused) handler.postDelayed({ startFallbackListening() }, 1200)
                }
            }
        )
    }

    private fun handleMicAmplitude(level: Float) {
        if (isPaused || inFallbackMode) return
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
        if (noMoreAudioIncoming && !isPaused && !inFallbackMode) {
            audioEngine.micSendEnabled = true
            pushState(JarvisState.LISTENING, "LISTENING", "I'm listening.")
        }
    }

    fun toggleMute() {
        isPaused = !isPaused
        if (isPaused) {
            if (inFallbackMode) {
                fallbackSpeech?.stop()
                fallbackTts?.stop()
            } else {
                audioEngine.micSendEnabled = false
                audioEngine.clearPlaybackQueue()
            }
            pushState(JarvisState.PAUSED, "PAUSED", "Tap Resume to continue.")
        } else {
            if (inFallbackMode) {
                startFallbackListening()
            } else {
                audioEngine.micSendEnabled = true
            }
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
        fallbackSpeech?.stop()
        fallbackTts?.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
    }
}
