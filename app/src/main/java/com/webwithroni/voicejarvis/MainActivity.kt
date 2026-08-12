package com.webwithroni.voicejarvis

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), JarvisService.UiListener {

    private val permissionCode = 100

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

    private var service: JarvisService? = null
    private var bound = false
    private var debugVisible = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as JarvisService.LocalBinder
            service = localBinder.getService()
            service?.listener = this@MainActivity
            bound = true

            service?.let { svc ->
                onState(svc.currentState, svc.currentLabel, svc.currentSub)
                statusText.text = svc.getLogSnapshot()
                val (u, j) = svc.getLastConversation()
                if (u != null) onConversation(u, j)
                reflectMuteUi()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

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

        muteIcon.setOnClickListener { service?.toggleMute(); reflectMuteUi() }
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

        setJarvisState(JarvisState.THINKING, "CONNECTING", "Waking up Jarvis…")
        ensurePermissionsThenStart()
    }

    private fun ensurePermissionsThenStart() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), permissionCode)
        } else {
            startJarvisService()
        }
    }

    private fun startJarvisService() {
        val intent = Intent(this, JarvisService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != permissionCode) return
        val micIndex = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        val micGranted = micIndex != -1 && grantResults.getOrNull(micIndex) == PackageManager.PERMISSION_GRANTED
        if (micGranted) startJarvisService()
        else setJarvisState(JarvisState.ERROR, "TRY AGAIN", "Microphone access is needed to listen.")
    }

    private fun reflectMuteUi() {
        val svc = service ?: return
        muteLabel.text = if (svc.isPaused) "RESUME" else "MUTE"
        muteIcon.setImageResource(
            if (svc.isPaused) android.R.drawable.ic_btn_speak_now
            else android.R.drawable.ic_lock_silent_mode
        )
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

    private fun showConversation(userText: String?, jarvisText: String?) {
        if (userText.isNullOrBlank() && jarvisText.isNullOrBlank()) return
        conversationCard.visibility = View.VISIBLE
        if (!userText.isNullOrBlank()) { userBubble.text = userText; userBubble.visibility = View.VISIBLE }
        if (!jarvisText.isNullOrBlank()) { jarvisBubble.text = jarvisText; jarvisBubble.visibility = View.VISIBLE }
        else jarvisBubble.visibility = View.GONE
    }

    override fun onState(state: JarvisState, label: String, sub: String) {
        runOnUiThread { setJarvisState(state, label, sub) }
    }

    override fun onAmplitude(level: Float) {
        runOnUiThread { orb.setAmplitude(level) }
    }

    override fun onLog(message: String) {
        runOnUiThread { statusText.append("\n$message") }
    }

    override fun onConversation(userText: String?, jarvisText: String?) {
        runOnUiThread { showConversation(userText, jarvisText) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            service?.listener = null
            unbindService(connection)
            bound = false
        }
        // Service is intentionally NOT stopped here — it's a foreground
        // service and keeps running in the background so Jarvis stays
        // alive when the screen locks or the app is closed.
    }
}
