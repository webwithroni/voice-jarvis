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
    private lateinit var speech: SpeechController
    private lateinit var tts: TtsController
    private val handler = Handler(Looper.getMainLooper())
    private var isPaused = false
    private var debugVisible = false
    private var speakingPulseRunnable: Runnable? = null

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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionCode)
        }

        tts = TtsController(
            context = this,
            onSpeakStart = { runOnUiThread { setJarvisState(JarvisState.SPEAKING, "SPEAKING", "") }; startSpeakingPulse() },
            onSpeakDone = {
                stopSpeakingPulse()
                if (!isPaused) handler.postDelayed({ startListening() }, 800)
            }
        )

        speech = SpeechController(
            context = this,
            onSpeechBegin = { runOnUiThread { setJarvisState(JarvisState.HEARING, "HEARING", "Go ahead…") } },
            onAmplitude = { level -> runOnUiThread { orb.setAmplitude(level) } },
            onFinalResult = { text -> handleUserSpeech(text) },
            onError = { msg ->
                runOnUiThread {
                    log(msg)
                    setJarvisState(JarvisState.ERROR, "TRY AGAIN", friendlyError(msg))
                }
                if (!isPaused) handler.postDelayed({ startListening() }, 1000)
            },
            onListeningStateChanged = { listening ->
                if (listening) runOnUiThread {
                    setJarvisState(JarvisState.LISTENING, "LISTENING", "I'm listening.")
                }
            }
        )

        setJarvisState(JarvisState.LISTENING, "LISTENING", "Say something.")
        startListening()
    }

    private fun friendlyError(raw: String): String = when {
        raw.contains("Network", true) -> "I couldn't reach my AI service."
        raw.contains("permission", true) -> "Microphone access is needed to listen."
        raw.contains("Groq", true) -> "I couldn't reach my AI service."
        else -> "Sorry, say that again?"
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

    private fun startListening() {
        if (isPaused) return
        speech.startListening()
    }

    private fun toggleMute() {
        isPaused = !isPaused
        if (isPaused) {
            speech.stop()
            tts.stop()
            setJarvisState(JarvisState.PAUSED, "PAUSED", "Tap Resume to continue.")
            muteLabel.text = "RESUME"
            muteIcon.setImageResource(android.R.drawable.ic_btn_speak_now)
        } else {
            muteLabel.text = "MUTE"
            muteIcon.setImageResource(android.R.drawable.ic_lock_silent_mode)
            startListening()
        }
    }

    private fun startSpeakingPulse() {
        stopSpeakingPulse()
        var t = 0f
        speakingPulseRunnable = object : Runnable {
            override fun run() {
                t += 0.25f
                val level = (0.4f + 0.4f * Math.abs(Math.sin(t.toDouble()))).toFloat()
                orb.setAmplitude(level)
                handler.postDelayed(this, 90)
            }
        }
        handler.post(speakingPulseRunnable!!)
    }

    private fun stopSpeakingPulse() {
        speakingPulseRunnable?.let { handler.removeCallbacks(it) }
        speakingPulseRunnable = null
    }

    private fun showConversation(userText: String?, jarvisText: String?) {
        conversationCard.visibility = View.VISIBLE
        if (userText != null) {
            userBubble.text = userText
            userBubble.visibility = View.VISIBLE
        }
        if (jarvisText != null) {
            jarvisBubble.text = jarvisText
            jarvisBubble.visibility = View.VISIBLE
        } else {
            jarvisBubble.visibility = View.GONE
        }
    }

    private fun handleUserSpeech(text: String) {
        runOnUiThread {
            log("You: $text")
            showConversation(text, null)
            setJarvisState(JarvisState.THINKING, "THINKING", "Let me think.")
        }
        GroqClient.ask(
            userText = text,
            onResult = { reply ->
                runOnUiThread { log("Jarvis: $reply"); showConversation(text, reply) }
                tts.speak(reply)
            },
            onError = { err ->
                runOnUiThread { log(err); setJarvisState(JarvisState.ERROR, "TRY AGAIN", friendlyError(err)) }
                if (!isPaused) handler.postDelayed({ startListening() }, 1000)
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        speech.stop()
        tts.shutdown()
    }
}
