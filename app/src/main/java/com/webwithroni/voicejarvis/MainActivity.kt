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
    private var recognizer: VoiceRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                micPermissionCode
            )
        }

        if (ModelManager.isModelReady(this)) {
            statusText.text = "Listening..."
            startRecognition(statusText)
        } else {
            statusText.text = "Preparing model..."
            ModelManager.downloadAndExtract(
                this,
                onProgress = { msg -> runOnUiThread { statusText.text = msg } },
                onDone = {
                    runOnUiThread {
                        statusText.text = "Listening..."
                        startRecognition(statusText)
                    }
                }
            )
        }
    }

    private fun startRecognition(statusText: TextView) {
        recognizer = VoiceRecognizer(
            context = this,
            onPartialResult = { text -> runOnUiThread { statusText.text = text } },
            onFinalResult = { text -> runOnUiThread { statusText.text = "You said: $text" } }
        )
        recognizer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.stop()
    }
}
