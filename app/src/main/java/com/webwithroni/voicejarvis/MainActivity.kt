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
    private var geminiClient: GeminiLiveClient? = null
    private val handler = Handler(Looper.getMainLooper())

    private var isPaused = false
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

        muteIcon.setOnClickListener { toggleMute() }
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

        audioEngine = AudioEngine(
            onMicChunk = { chunk -> geminiClient?.sendAudioChunk(chunk) },
            onMicAmplitude = { level -> runOnUiThread { handleMicAmplitude(level) } },
            onPlaybackAmplitude = { level -> runOnUiThread { orb.setAmplitude(level * 2.2f) } },
            onPlaybackIdle = { runOnUiThread { handlePlaybackIdle() } }
        )

        setJarvisState(JarvisState.THINKING, "CONNECTING", "Waking up Jarvis…")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionCode)
        } else {
            connectGemini()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == micPermissionCode && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
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
            "(1-3 sentences), with no markdown or lists."

        geminiClient = GeminiLiveClient(
            apiKey = apiKey,
            systemPrompt = systemPrompt,
            onSetupComplete = {
                runOnUiThread {
                    log("Gemini Live connected.")
                    audioEngine.startRecording()
                    audioEngine.startPlayback()
                    setJarvisState(JarvisState.LISTENING, "LISTENING", "I'm listening.")
                }
            },
            onAudioChunk = { bytes ->
                noMoreAudioIncoming = false
                audioEngine.micSendEnabled = false
                audioEngine.enqueuePlayback(bytes)
                runOnUiThread { setJarvisState(JarvisState.SPEAKING, "SPEAKING", "") }
            },
            onInputTranscript = { text ->
                pendingUserText += text
                runOnUiThread { showConversation(pendingUserText, null) }
            },
            onOutputTranscript = { text ->
                pendingJarvisText += text
                runOnUiThread { showConversation(pendingUserText, pendingJarvisText) }
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
            geminiClient?.sendInterrupt()
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        geminiClient?.disconnect()
        audioEngine.release()
    }
}
