package com.webwithroni.voicejarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val micPermissionCode = 100
    private lateinit var statusText: TextView
    private lateinit var speech: SpeechController
    private lateinit var tts: TtsController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.install(this)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statusText.setTextIsSelectable(true)
        statusText.text = "Previous crash log:\n" + CrashLogger.readLog(this) + "\n\n---\n\n"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionCode)
        }

        tts = TtsController(
            context = this,
            onSpeakStart = { runOnUiThread { statusText.append("\nSpeaking...") } },
            onSpeakDone = { runOnUiThread { statusText.append("\nListening again...") }; startListening() }
        )

        speech = SpeechController(
            context = this,
            onFinalResult = { text -> handleUserSpeech(text) },
            onError = { msg -> runOnUiThread { statusText.append("\n$msg") }; startListening() },
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
            onError = { err -> runOnUiThread { statusText.append("\n$err") }; startListening() }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        speech.stop()
        tts.shutdown()
    }
}
