package com.webwithroni.voicejarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private val micPermissionCode = 100
    private var recognizer: VoiceRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.setTextIsSelectable(true)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                micPermissionCode
            )
        }

        try {
            if (ModelManager.isModelReady(this)) {
                checkModelStructureAndStart(statusText)
            } else {
                statusText.text = "Preparing model..."
                ModelManager.downloadAndExtract(
                    this,
                    onProgress = { msg -> runOnUiThread { statusText.text = msg } },
                    onDone = {
                        runOnUiThread {
                            checkModelStructureAndStart(statusText)
                        }
                    }
                )
            }
        } catch (e: Throwable) {
            statusText.text = "Outer crash: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun checkModelStructureAndStart(statusText: TextView) {
        val modelDir = File(ModelManager.getModelPath(this))
        val contents = modelDir.listFiles()?.joinToString("\n") { it.name } ?: "EMPTY"
        statusText.text = "Model folder contents:\n$contents\n\nStarting recognizer..."
        startRecognition(statusText)
    }

    private fun startRecognition(statusText: TextView) {
        recognizer = VoiceRecognizer(
            context = this,
            onPartialResult = { text -> runOnUiThread { statusText.text = text } },
            onFinalResult = { text -> runOnUiThread { statusText.text = "You said: $text" } },
            onError = { msg -> runOnUiThread { statusText.text = msg } }
        )
        recognizer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.stop()
    }
}
