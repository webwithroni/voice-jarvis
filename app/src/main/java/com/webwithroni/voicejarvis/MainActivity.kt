package com.webwithroni.voicejarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val micPermissionCode = 100
    private val ampThreshold = 0.06f

    private lateinit var statusText: TextView
    private lateinit var stateLabel: TextView
    private lateinit var microcopy: TextView
    private lateinit var muteIcon: ImageView
    private lateinit var muteLabel: TextView
    private lateinit var orb: OrbView
    private lateinit var conversationCard: View
    private lateinit var userBubble: TextView
    private lateinit var jarvisBubble: TextView
    private lateinit var debugScroll: View

    private lateinit var audioEngine: AudioEngine
    private lateinit var toolExecutor: ToolExecutor
    private var geminiClient: GeminiLiveClient? = null
    private val handler = Handler(Looper.getMainLooper())

    private var isPaused = false
    private var autoPausedByLifecycle = false
    private var debugVisible = false
    private var voiceActive = false
    private var noMoreAudioIncoming = true
    private var silenceRunnable: Runnable? = null
    private var pendingUserText = ""
    private var pendingJarvisText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.install(this)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        stateLabel = findViewById(R.id.stateLabel)
        microcopy = findViewById(R.id.microcopy)
        muteIcon = findViewById(R.id.muteIcon)
        muteLabel = findViewById(R.id.muteLabel)
        orb = findViewById(R.id.orbView)
        conversationCard = findViewById(R.id.conversationCard)
        userBubble = findViewById(R.id.userBubble)
        jarvisBubble = findViewById(R.id.jarvisBubble)
        debugScroll = findViewById(R.id.debugScroll)
        statusText.setTextIsSelectable(true)
        statusText.text = ""

        muteIcon.isClickable = true
        muteIcon.isFocusable = true
        muteIcon.setOnClickListener {
            Toast.makeText(this, "Mute tapped", Toast.LENGTH_SHORT).show()
            toggleMute()
        }
        muteLabel.setOnClickListener { muteIcon.performClick() }

        findViewById<View>(R.id.settingsButton).setOnClickListener {
            Toast.makeText(this, "Settings — coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.historyButton).setOnClickListener {
            Toast.makeText(this, "History — coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.jarvisBrand).setOnLongClickListener {
            debugVisible = !debugVisible
            debugScroll.visibility = if (debugVisible) View.VISIBLE else View.GONE
            true
        }

        toolExecutor = ToolExecutor(this)
        audioEngine = AudioEngine(
            onMicChunk = { chunk -> geminiClient?.sendAudioChunk(chunk) },
            onMicAmplitude = { level -> runOnUiThread { handleMicAmplitude(level) } },
            onPlaybackAmplitude = { level -> runOnUiThread { orb.setAmplitude(level * 2.2f) } },
            onPlaybackIdle = { runOnUiThread { handlePlaybackIdle() } }
        )

        setJarvisState(JarvisState.THINKING, "CONNECTING", "Waking up Jarvis…")

        val neededPermissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), micPermissionCode)
        } else {
            connectGemini()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val micIndex = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        val micGranted = micIndex != -1 && grantResults.getOrNull(micIndex) == PackageManager.PERMISSION_GRANTED
        if (requestCode == micPermissionCode && micGranted) {
            connectGemini()
        } else if (requestCode == micPermissionCode) {
            setJarvisState(JarvisState.ERROR, "TRY AGAIN", "Microphone access is needed to listen.")
        }
    }

    private fun connectGemini() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            setJarvisState(JarvisState.ERROR, "TRY AGAIN", "Gemini API key missing.")
            log("Gemini API key is empty — set GEMINI_API_KEY secret.")
            return
        }

        val systemPrompt = "You are Jarvis, Roni's personal voice assistant. " +
            "Reply in the same mix of Hindi, Bengali, or English the user used. " +
            "You are speaking ALOUD, so keep responses short, natural, and conversational " +
            "(1-3 sentences), with no markdown or lists. " +
            "You have tools to call contacts, send WhatsApp/SMS drafts, open apps, control the flashlight, " +
            "and set alarms or timers. Use them when the user asks for these actions, then briefly confirm what you did. " +
            "You also have a search_web tool for anything current or time-sensitive: news, prices, scores, weather, " +
            "today's date, or facts that may have changed recently. Always use it instead of guessing for such questions."

        geminiClient = GeminiLiveClient(
            apiKey = apiKey,
            systemPrompt = systemPrompt,
            onSetupComplete = {
                runOnUiThread {
                    log("Gemini Live connected.")
                    audioEngine.startRecording()
                    audioEngine.startPlayback()
                    if (!isPaused) setJarvisState(JarvisState.LISTENING, "LISTENING", "I'm listening.")
                }
            },
            onAudioChunk = { bytes ->
                noMoreAudioIncoming = false
                audioEngine.micSendEnabled = false
                audioEngine.enqueuePlayback(bytes)
                runOnUiThread { if (!isPaused) setJarvisState(JarvisState.SPEAKING, "SPEAKING", "") }
            },
            onInputTranscript = { text ->
                pendingUserText += text
                runOnUiThread { showConversation(pendingUserText, null) }
            },
            onOutputTranscript = { text ->
                pendingJarvisText += text
                runOnUiThread { showConversation(pendingUserText, pendingJarvisText) }
            },
            onToolCall = { id, name, args ->
                val toolResult = toolExecutor.execute(name, args)
                geminiClient?.sendToolResponse(id, name, toolResult)
                runOnUiThread { log("Tool: $name -> ${toolResult.optString("message")}") }
            },
            onTurnComplete = {
                runOnUiThread {
                    if (pendingUserText.isNotBlank()) log("You: $pendingUserText")
                    if (pendingJarvisText.isNotBlank()) log("Jarvis: $pendingJarvisText")
                    pendingUserText = ""
                    pendingJarvisText = ""
                }
                noMoreAudioIncoming = true
            },
            onError = { msg -> runOnUiThread { log(msg) } },
            onDisconnected = {
                runOnUiThread {
                    if (!isPaused) setJarvisState(JarvisState.THINKING, "RECONNECTING", "One moment.")
                }
            }
        )
        geminiClient?.connect()
    }

    private fun handleMicAmplitude(level: Float) {
        if (isPaused) return
        orb.setAmplitude(level * 3f)
        if (level > ampThreshold && audioEngine.micSendEnabled) {
            if (!voiceActive) {
                voiceActive = true
                setJarvisState(JarvisState.HEARING, "HEARING", "Go ahead…")
            }
            silenceRunnable?.let { handler.removeCallbacks(it) }
            silenceRunnable = Runnable {
                voiceActive = false
                if (!isPaused) setJarvisState(JarvisState.THINKING, "THINKING", "Let me think.")
            }
            handler.postDelayed(silenceRunnable!!, 700)
        }
    }

    private fun handlePlaybackIdle() {
        if (noMoreAudioIncoming && !isPaused) {
            audioEngine.micSendEnabled = true
            setJarvisState(JarvisState.LISTENING, "LISTENING", "I'm listening.")
        }
    }

    private fun setJarvisState(state: JarvisState, label: String, sub: String) {
        orb.setJarvisState(state)
        orb.contentDescription = when (state) {
            JarvisState.LISTENING -> "Jarvis is listening"
            JarvisState.HEARING -> "Jarvis is hearing your speech"
            JarvisState.THINKING -> "Jarvis is thinking"
            JarvisState.SPEAKING -> "Jarvis is speaking"
            JarvisState.ERROR -> "Jarvis did not understand"
            JarvisState.PAUSED -> "Jarvis is paused"
        }
        stateLabel.text = label
        microcopy.text = sub
    }

    private fun log(msg: String) { statusText.append("\n$msg") }

    private fun showConversation(userText: String?, jarvisText: String?) {
        if (userText.isNullOrBlank() && jarvisText.isNullOrBlank()) return
        conversationCard.visibility = View.VISIBLE
        if (!userText.isNullOrBlank()) { userBubble.text = userText; userBubble.visibility = View.VISIBLE }
        if (!jarvisText.isNullOrBlank()) { jarvisBubble.text = jarvisText; jarvisBubble.visibility = View.VISIBLE }
        else jarvisBubble.visibility = View.GONE
    }

    private fun toggleMute() {
        isPaused = !isPaused
        if (isPaused) {
            audioEngine.micSendEnabled = false
            audioEngine.clearPlaybackQueue()
            setJarvisState(JarvisState.PAUSED, "PAUSED", "Tap Resume to continue.")
            muteLabel.text = "RESUME"
            muteIcon.setImageResource(android.R.drawable.ic_btn_speak_now)
        } else {
            audioEngine.micSendEnabled = true
            muteLabel.text = "MUTE"
            muteIcon.setImageResource(android.R.drawable.ic_lock_silent_mode)
            setJarvisState(JarvisState.LISTENING, "LISTENING", "I'm listening.")
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isPaused) {
            autoPausedByLifecycle = true
            audioEngine.micSendEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (autoPausedByLifecycle && !isPaused) {
            autoPausedByLifecycle = false
            audioEngine.micSendEnabled = true
            setJarvisState(JarvisState.LISTENING, "LISTENING", "I'm listening.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        geminiClient?.disconnect()
        audioEngine.release()
    }
}
