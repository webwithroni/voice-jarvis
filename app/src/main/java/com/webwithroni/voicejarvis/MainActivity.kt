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
            statusText.text = "Model ready. Voice Jarvis is alive."
        } else {
            statusText.text = "Preparing model..."
            ModelManager.downloadAndExtract(
                this,
                onProgress = { msg -> runOnUiThread { statusText.text = msg } },
                onDone = { runOnUiThread { statusText.text = "Model ready. Restart to activate." } }
            )
        }
    }
}
