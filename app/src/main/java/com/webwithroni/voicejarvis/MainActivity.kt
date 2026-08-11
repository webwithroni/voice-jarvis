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
    private lateinit var speech: SpeechController
    private lateinit var tts: TtsController
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.install(this)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statusText.setTextIsSelectable(true)
        statusText.text = ""

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionCode)
        }

        tts = TtsController(
            context = this,
            onSpeakStart = { runOnUiThread { statusText.append("\nSpeaking...") } },
            onSpeakDone = {
                runOnUiThread { statusText.append("\nListening again...") }
                handler.postDelayed({ startListening() }, 800)
            }
        )

        speech = SpeechController(
            context = this,
            onFinalResult = { text -> handleUserSpeech(text) },
            onError = { msg ->
                runOnUiThread { statusText.append("\n$msg") }
                handler.postDelayed({ startListening() }, 800)
            },
            onListeningStateChanged = { listening -> if (listening) runOnUiThread { statusText.append("\nListening...") } }
        )

        startListening()
    }

    private fun startListening() {
        speech.startListening()
    }

    private fun handleUserSpeech(text: String) {
        runOnUiThread { statusText.append("\nYou: $text\nThinking...") }
        GroqClient.ask(
            userText = text,
            onResult = { reply ->
                runOnUiThread { statusText.append("\nJarvis: $reply") }
                tts.speak(reply)
            },
            onError = { err ->
                runOnUiThread { statusText.append("\n$err") }
                handler.postDelayed({ startListening() }, 800)
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
