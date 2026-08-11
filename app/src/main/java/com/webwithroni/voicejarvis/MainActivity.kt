package com.webwithroni.voicejarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val micPermissionCode = 100
    private lateinit var statusText: TextView
    private lateinit var stateLabel: TextView
    private lateinit var microcopy: TextView
    private lateinit var muteButton: TextView
    private lateinit var orb: OrbView
    private lateinit var speech: SpeechController
    private lateinit var tts: TtsController
    private val handler = Handler(Looper.getMainLooper())
    private var isPaused = false
    private var speakingPulseRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.install(this)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        stateLabel = findViewById(R.id.stateLabel)
        microcopy = findViewById(R.id.microcopy)
        muteButton = findViewById(R.id.muteButton)
        orb = findViewById(R.id.orbView)
        statusText.setTextIsSelectable(true)
        statusText.text = ""
        muteButton.setOnClickListener { toggleMute() }

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
                runOnUiThread { log("Listening again...") }
                if (!isPaused) handler.postDelayed({ startListening() }, 800)
            }
        )

        speech = SpeechController(
            context = this,
            onSpeechBegin = { runOnUiThread { setJarvisState(JarvisState.HEARING, "HEARING", "Go ahead…") } },
            onAmplitude = { level -> runOnUiThread { orb.setAmplitude(level) } },
            onFinalResult = { text -> handleUserSpeech(text) },
            onError = { msg ->
                runOnUiThread { log(msg); setJarvisState(JarvisState.ERROR, "DIDN'T CATCH THAT", "Try again.") }
                if (!isPaused) handler.postDelayed({ startListening() }, 900)
            },
            onListeningStateChanged = { listening ->
                if (listening) runOnUiThread {
                    setJarvisState(JarvisState.LISTENING, "LISTENING", "I'm listening.")
                    log("Listening...")
                }
            }
        )

        startListening()
    }

    private fun setJarvisState(state: JarvisState, label: String, sub: String) {
        orb.setJarvisState(state)
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
            setJarvisState(JarvisState.PAUSED, "PAUSED", "Tap to resume.")
            muteButton.text = "RESUME"
        } else {
            muteButton.text = "MUTE"
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

    private fun handleUserSpeech(text: String) {
        runOnUiThread { log("You: $text"); setJarvisState(JarvisState.THINKING, "THINKING", "One moment.") }
        GroqClient.ask(
            userText = text,
            onResult = { reply -> runOnUiThread { log("Jarvis: $reply") }; tts.speak(reply) },
            onError = { err ->
                runOnUiThread { log(err); setJarvisState(JarvisState.ERROR, "DIDN'T CATCH THAT", "Try again.") }
                if (!isPaused) handler.postDelayed({ startListening() }, 900)
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
