package com.webwithroni.voicejarvis

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.webwithroni.voicejarvis.orb.HumanoidOrbView
import com.webwithroni.voicejarvis.orb.OrbActivity
import com.webwithroni.voicejarvis.orb.OrbState

class MainActivity : AppCompatActivity(), JarvisService.UiListener {

    private val permissionCode = 100
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    private lateinit var statusText: TextView
    private lateinit var stateLabel: TextView
    private lateinit var microcopy: TextView
    private lateinit var muteIcon: ImageView
    private lateinit var muteLabel: TextView
    private lateinit var orb: HumanoidOrbView
    private lateinit var orbCenterIcon: ImageView
    private lateinit var waveformLeft: WaveformBarsView
    private lateinit var waveformRight: WaveformBarsView
    private lateinit var conversationCard: View
    private lateinit var userBlock: View
    private lateinit var jarvisBlock: View
    private lateinit var userBubble: TextView
    private lateinit var jarvisBubble: TextView
    private lateinit var userTime: TextView
    private lateinit var jarvisTime: TextView
    private lateinit var debugScroll: View
    private lateinit var pausedCard: View
    private lateinit var tipsCard: View
    private lateinit var thinkingCard: View
    private lateinit var currentRequestText: TextView
    private lateinit var processStep0: TextView
    private lateinit var processStep1: TextView
    private lateinit var processStep2: TextView
    private lateinit var permissionCard: View
    private lateinit var enableMicButton: Button

    private var service: JarvisService? = null
    private var bound = false
    private var debugVisible = false
    private var micPermanentlyDenied = false
    private var lastUserTextCache: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private var thinkingStepIndex = 0
    private var thinkingRunnable: Runnable? = null

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

        /*
         * Firebase authentication runs in the background.
         * It must never block Jarvis voice startup.
         *
         * Conversation sessions are created later,
         * when an actual voice interaction begins.
         */
        /*
         * Firebase App Check.
         *
         * This initialization is deliberately non-blocking.
         * Voice startup must never wait for attestation.
         */
        try {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
        } catch (e: Exception) {
            android.util.Log.w(
                "VoiceJarvis",
                "App Check initialization unavailable: ${e.message}"
            )
        }

        FirebaseCrashlyticsManager.initialize()

        FirebaseAnalyticsManager.initialize(
            this
        )

        FirebasePerformanceManager.initialize()

        FirebaseManager.initialize(this) { success ->
            if (success) {
                android.util.Log.d(
                    "VoiceJarvis",
                    "Firebase ready."
                )
            } else {
                android.util.Log.w(
                    "VoiceJarvis",
                    "Firebase unavailable. Voice continues normally."
                )
            }
        }

        if (CrashLogger.hasCrashLog(this)) {
            showCrashRecovery()
            return
        }

        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        stateLabel = findViewById(R.id.stateLabel)
        microcopy = findViewById(R.id.microcopy)
        muteIcon = findViewById(R.id.muteIcon)
        muteLabel = findViewById(R.id.muteLabel)
        orb = findViewById(R.id.orbView)
        orbCenterIcon = findViewById(R.id.orbCenterIcon)
        waveformLeft = findViewById(R.id.waveformLeft)
        waveformRight = findViewById(R.id.waveformRight)
        conversationCard = findViewById(R.id.conversationCard)
        userBlock = findViewById(R.id.userBlock)
        jarvisBlock = findViewById(R.id.jarvisBlock)
        userBubble = findViewById(R.id.userBubble)
        jarvisBubble = findViewById(R.id.jarvisBubble)
        userTime = findViewById(R.id.userTime)
        jarvisTime = findViewById(R.id.jarvisTime)
        debugScroll = findViewById(R.id.debugScroll)
        pausedCard = findViewById(R.id.pausedCard)
        tipsCard = findViewById(R.id.tipsCard)
        thinkingCard = findViewById(R.id.thinkingCard)
        currentRequestText = findViewById(R.id.currentRequestText)
        processStep0 = findViewById(R.id.processStep0)
        processStep1 = findViewById(R.id.processStep1)
        processStep2 = findViewById(R.id.processStep2)
        permissionCard = findViewById(R.id.permissionCard)
        enableMicButton = findViewById(R.id.enableMicButton)
        statusText.setTextIsSelectable(true)

        muteIcon.setOnClickListener { service?.toggleMute(); reflectMuteUi() }
        muteLabel.setOnClickListener { muteIcon.performClick() }

        syncOrbMotionPreference()

        enableMicButton.setOnClickListener {
            if (micPermanentlyDenied) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                ensurePermissionsThenStart()
            }
        }

        findViewById<View>(R.id.settingsButton).setOnClickListener {
            startActivity(
                Intent(this, JarvisScreensActivity::class.java).apply {
                    putExtra(
                        JarvisScreensActivity.ROUTE,
                        JarvisScreensActivity.SETTINGS
                    )
                }
            )
        }

        findViewById<View>(R.id.historyButton).setOnClickListener {
            startActivity(
                Intent(this, JarvisScreensActivity::class.java).apply {
                    putExtra(
                        JarvisScreensActivity.ROUTE,
                        JarvisScreensActivity.HISTORY
                    )
                }
            )
        }
        findViewById<View>(R.id.jarvisBrand).setOnLongClickListener {
            debugVisible = !debugVisible
            debugScroll.visibility = if (debugVisible) View.VISIBLE else View.GONE
            true
        }

        setJarvisState(JarvisState.THINKING, "CONNECTING", "Waking up Jarvis…")
        ensurePermissionsThenStart()
    }

    private fun showCrashRecovery() {

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setBackgroundColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.vj_bg_canvas
                )
            )
            setPadding(48, 80, 48, 48)
        }

        val title = TextView(this).apply {
            text = "VOICE JARVIS"
            textSize = 18f
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.vj_text_primary
                )
            )
            letterSpacing = 0.25f
        }

        val state = TextView(this).apply {
            text = "CRASH RECOVERY"
            textSize = 22f
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.vj_state_error
                )
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 16)
        }

        val message = TextView(this).apply {
            text = "Jarvis closed unexpectedly during the previous launch. A diagnostic report has been saved."
            textSize = 15f
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.vj_text_secondary
                )
            )
            gravity = android.view.Gravity.CENTER
        }

        val report = TextView(this).apply {
            text = CrashLogger.readLog(this@MainActivity)
            textSize = 12f
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.vj_text_tertiary
                )
            )
            setPadding(20, 20, 20, 20)
            setTextIsSelectable(true)
            setBackgroundResource(R.drawable.vj_card_background)
            maxLines = 12
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val shareButton = Button(this).apply {
            text = "Share crash report"
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.vj_bg_canvas
                )
            )
            background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.vj_button_primary
            )
            setOnClickListener {
                CrashLogger.shareCrashLog(this@MainActivity)
            }
        }

        val retryButton = Button(this).apply {
            text = "Clear report & try again"
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.vj_text_primary
                )
            )
            background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.vj_button_secondary
            )
            setOnClickListener {
                CrashLogger.clearLog(this@MainActivity)
                recreate()
            }
        }

        root.addView(
            title,
            android.widget.LinearLayout.LayoutParams(
                -1,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            state,
            android.widget.LinearLayout.LayoutParams(
                -1,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            message,
            android.widget.LinearLayout.LayoutParams(
                -1,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val reportParams = android.widget.LinearLayout.LayoutParams(
            -1,
            0,
            1f
        ).apply {
            topMargin = 32
            bottomMargin = 24
        }

        root.addView(report, reportParams)

        root.addView(
            shareButton,
            android.widget.LinearLayout.LayoutParams(
                -1,
                54
            ).apply {
                bottomMargin = 12
            }
        )

        root.addView(
            retryButton,
            android.widget.LinearLayout.LayoutParams(
                -1,
                54
            )
        )

        setContentView(root)
    }

    /**
     * Synchronize the new Orb with Android's animation scale.
     *
     * animation scale == 0
     *     -> reduced motion
     *
     * animation scale > 0
     *     -> full motion
     */
    private fun syncOrbMotionPreference() {

        val animationScale =
            try {

                Settings.Global.getFloat(
                    contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                )

            } catch (_: Settings.SettingNotFoundException) {

                1f
            }

        orb.setReducedMotion(
            animationScale <= 0f
        )
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
            permissionCard.visibility = View.GONE
            startJarvisService()
        }
    }

    private fun startJarvisService() {
        val intent = Intent(this, JarvisService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {

        super.onResume()

        if (
            ::orb.isInitialized
        ) {
            syncOrbMotionPreference()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != permissionCode) return
        val micIndex = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        val micGranted = micIndex != -1 && grantResults.getOrNull(micIndex) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            permissionCard.visibility = View.GONE
            startJarvisService()
        } else {

            micPermanentlyDenied =
                micIndex != -1 &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    )

            permissionCard.visibility =
                View.VISIBLE

            setJarvisState(
                JarvisState.ERROR,
                "TRY AGAIN",
                "Microphone access is needed to listen."
            )

            orb.setState(
                OrbState.PERMISSION_REQUIRED
            )

            orb.setActivity(
                OrbActivity.NONE
            )
        }
    }

    private fun reflectMuteUi() {
        val svc = service ?: return
        muteLabel.text = if (svc.isPaused) "RESUME" else "MUTE"
        muteIcon.setImageResource(
            if (svc.isPaused) android.R.drawable.ic_btn_speak_now
            else android.R.drawable.ic_lock_silent_mode
        )
    }

    private fun stateColor(state: JarvisState): Int = when (state) {
        JarvisState.THINKING -> ContextCompat.getColor(this, R.color.accent_violet)
        JarvisState.ERROR -> ContextCompat.getColor(this, R.color.state_error)
        JarvisState.PAUSED -> ContextCompat.getColor(this, R.color.state_muted)
        else -> ContextCompat.getColor(this, R.color.accent_cyan)
    }

    private fun setJarvisState(
        state: JarvisState,
        label: String,
        sub: String
    ) {

        val orbState =
            when (state) {

                JarvisState.LISTENING ->
                    OrbState.LISTENING

                JarvisState.HEARING ->
                    OrbState.HEARING

                JarvisState.THINKING ->
                    OrbState.THINKING

                JarvisState.SPEAKING ->
                    OrbState.SPEAKING

                JarvisState.ERROR ->
                    OrbState.ERROR

                JarvisState.PAUSED ->
                    OrbState.PAUSED
            }

        orb.setState(
            orbState
        )

        val orbActivity =
            when {

                label.trim().equals(
                    "CONFIRMATION REQUIRED",
                    ignoreCase = true
                ) ->
                    OrbActivity.WAITING_CONFIRMATION

                label.trim().equals(
                    "DONE",
                    ignoreCase = true
                ) ->
                    OrbActivity.SUCCESS

                else ->
                    OrbActivity.NONE
            }

        orb.setActivity(
            orbActivity
        )

        FirebaseCrashlyticsManager.setJarvisState(
            state.name
        )

        orb.contentDescription =
            when (state) {

                JarvisState.LISTENING ->
                    "Jarvis is listening"

                JarvisState.HEARING ->
                    "Jarvis is hearing your speech"

                JarvisState.THINKING ->
                    "Jarvis is thinking"

                JarvisState.SPEAKING ->
                    "Jarvis is speaking"

                JarvisState.ERROR ->
                    "Jarvis did not understand"

                JarvisState.PAUSED ->
                    "Jarvis is paused"
            }

        stateLabel.text =
            label

        stateLabel.setTextColor(
            stateColor(state)
        )

        microcopy.text =
            sub

        val color =
            stateColor(state)

        waveformLeft.setBarColor(
            color
        )

        waveformRight.setBarColor(
            color
        )

        pausedCard.visibility =
            if (
                state ==
                    JarvisState.PAUSED
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        tipsCard.visibility =
            if (
                state ==
                    JarvisState.ERROR
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        thinkingCard.visibility =
            if (
                state ==
                    JarvisState.THINKING
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        if (
            state ==
                JarvisState.PAUSED
        ) {

            orbCenterIcon.visibility =
                View.VISIBLE

            orbCenterIcon.setImageResource(
                android.R.drawable.ic_media_pause
            )

            orbCenterIcon.setColorFilter(
                ContextCompat.getColor(
                    this,
                    R.color.text_secondary
                )
            )

        } else if (
            permissionCard.visibility ==
            View.VISIBLE
        ) {

            orbCenterIcon.visibility =
                View.VISIBLE

            orbCenterIcon.setImageResource(
                android.R.drawable.ic_lock_silent_mode
            )

            orbCenterIcon.setColorFilter(
                ContextCompat.getColor(
                    this,
                    R.color.state_error
                )
            )

        } else {

            orbCenterIcon.visibility =
                View.GONE
        }

        if (
            state ==
                JarvisState.THINKING
        ) {

            currentRequestText.text =
                lastUserTextCache
                    .ifBlank {
                        "—"
                    }

            startThinkingChecklist()

        } else {

            stopThinkingChecklist()
        }
    }

    private fun startThinkingChecklist() {
        stopThinkingChecklist()
        thinkingStepIndex = 0
        thinkingRunnable = object : Runnable {
            override fun run() {
                updateThinkingSteps(thinkingStepIndex)
                thinkingStepIndex = (thinkingStepIndex + 1).coerceAtMost(2)
                handler.postDelayed(this, 900)
            }
        }
        handler.post(thinkingRunnable!!)
    }

    private fun stopThinkingChecklist() {
        thinkingRunnable?.let { handler.removeCallbacks(it) }
        thinkingRunnable = null
    }

    private fun updateThinkingSteps(activeIndex: Int) {
        val labels = listOf("Understanding your question", "Working on it", "Preparing response")
        val views = listOf(processStep0, processStep1, processStep2)
        for (i in views.indices) {
            val marker = when {
                i < activeIndex -> "✓ "
                i == activeIndex -> "● "
                else -> "○ "
            }
            views[i].text = marker + labels[i]
        }
    }

    private fun showConversation(userText: String?, jarvisText: String?) {
        if (userText.isNullOrBlank() && jarvisText.isNullOrBlank()) return
        conversationCard.visibility = View.VISIBLE
        val now = timeFormat.format(Date())
        if (!userText.isNullOrBlank()) {
            lastUserTextCache = userText
            userBubble.text = userText
            userTime.text = now
            userBlock.visibility = View.VISIBLE
        }
        if (!jarvisText.isNullOrBlank()) {
            jarvisBubble.text = jarvisText
            jarvisTime.text = now
            jarvisBlock.visibility = View.VISIBLE
        } else {
            jarvisBlock.visibility = View.GONE
        }
    }

    override fun onState(state: JarvisState, label: String, sub: String) {
        runOnUiThread { setJarvisState(state, label, sub) }
    }

    override fun onMicAmplitude(
        level: Float
    ) {

        runOnUiThread {

            /*
             * Mic signal is meaningful for HEARING.
             *
             * OrbAudioReactive inside HumanoidOrbView
             * performs the visual noise-gate, normalization,
             * and smoothing.
             */
            if (
                orb.currentState() ==
                    OrbState.HEARING
            ) {

                orb.setMicAmplitude(
                    level
                )
            }

            waveformLeft.setLevel(
                level
            )

            waveformRight.setLevel(
                level
            )
        }
    }

    override fun onPlaybackAmplitude(
        level: Float
    ) {

        runOnUiThread {

            /*
             * Playback signal is meaningful for SPEAKING.
             *
             * This prevents microphone noise from driving the
             * speaking animation.
             */
            if (
                orb.currentState() ==
                    OrbState.SPEAKING
            ) {

                orb.setPlaybackAmplitude(
                    level
                )
            }
        }
    }

    override fun onLog(message: String) {
        runOnUiThread { statusText.append("\n$message") }
    }

    override fun onConversation(userText: String?, jarvisText: String?) {
        runOnUiThread { showConversation(userText, jarvisText) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopThinkingChecklist()
        if (bound) {
            service?.listener = null
            unbindService(connection)
            bound = false
        }
    }
}
