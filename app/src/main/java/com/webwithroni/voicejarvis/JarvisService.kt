package com.webwithroni.voicejarvis

import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.webwithroni.voicejarvis.orb.OrbActivity

class JarvisService : Service() {

    /*
     * Android conversational audio session.
     *
     * Jarvis is both:
     *
     * - microphone input
     * - realtime speech output
     *
     * Keep the audio mode/focus lifecycle explicit so the
     * Android audio system knows this is a communication session.
     */
    private var jarvisAudioFocusRequest:
        AudioFocusRequest? = null

    private var jarvisPreviousAudioMode:
        Int? = null

    private var jarvisAudioFocusOwned =
        false


    interface UiListener {

        fun onState(
            state: JarvisState,
            label: String,
            sub: String
        )

        fun onMicAmplitude(
            level: Float
        )

        fun onPlaybackAmplitude(
            level: Float
        )

        fun onActivity(
            activity: OrbActivity
        )

        fun onLog(
            message: String
        )

        fun onConversation(
            userText: String?,
            jarvisText: String?
        )
    }

    inner class LocalBinder : Binder() {

        fun getService(): JarvisService =
            this@JarvisService
    }

    private val binder =
        LocalBinder()

    var listener: UiListener? =
        null

    private lateinit var audioEngine:
        AudioEngine

    private lateinit var toolExecutor:
        ToolExecutor

    private lateinit var capabilityBusToolBridge:
        CapabilityBusToolBridge

    private lateinit var capabilityBus:
        CapabilityBus

    private var geminiClient:
        GeminiLiveClient? = null

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var wakeLock:
        PowerManager.WakeLock? = null

    private var fallbackSpeech:
        SpeechController? = null

    private var fallbackTts:
        TtsController? = null

    private var inFallbackMode =
        false

    private var consecutiveFailures =
        0

    var isPaused =
        false
        private set

    private var voiceActive =
        false

    private var noMoreAudioIncoming =
        true

    private var silenceRunnable:
        Runnable? = null

    /*
     * Gemini transcription is streamed in chunks.
     */
    private var latestUserTranscript =
        ""

    private var latestJarvisTranscript =
        ""

    /*
     * Firebase telemetry.
     */
    private var firebaseTurnStartedAt:
        Long = 0L

    private var firebaseFirstResponseRecorded =
        false

    private var firebaseFirstResponseLatencyMs:
        Long? = null

    private var firebaseTurnInterrupted =
        false

    private var firebaseResponseAccepted:
        Boolean? = null

    private var firebaseUserCorrected =
        false

    private var firebaseCorrectionType:
        String? = null

    private var firebaseQualityScore:
        Int? = null

    private var firebaseCurrentTurnId:
        String? = null

    private var firebaseConversationStarted =
        false

    /*
     * Firebase Performance trace handles.
     */
    private var geminiPerformanceTraceId:
        String? = null

    private var voicePerformanceTraceId:
        String? = null

    private val firebaseTurnTools =
        mutableListOf<String>()

    /*
     * 3.3.3.10A — Runtime OrbActivity ownership.
     *
     * Every active tool receives a unique generation token.
     * An older tool completion can never clear a newer tool's
     * visual activity.
     */
    private val orbActivityLock =
        Any()

    private var orbActivityGeneration =
        0L

    private val activeOrbActivities =
        linkedMapOf<Long, OrbActivity>()

    @Volatile
    private var orbWaitingForConfirmation =
        false

    private val ampThreshold =
        0.045f

    /*
     * UI debounce only.
     *
     * Gemini owns the real conversational turn boundary.
     */
    private val silenceTimeoutMs =
        700L

    var currentState =
        JarvisState.THINKING
        private set

    var currentLabel =
        "CONNECTING"
        private set

    var currentSub =
        "Waking up Jarvis…"
        private set

    private val logBuffer =
        StringBuilder()

    private var lastUserText:
        String? = null

    private var lastJarvisText:
        String? = null

    /**
     * Durable confirmation state.
     *
     * A medium/high/critical action is first rejected by the
     * deterministic RiskEngine. The complete normalized action
     * is retained here until the user explicitly confirms or
     * rejects it.
     *
     * This state belongs to JarvisService because the service
     * owns the realtime conversation lifecycle.
     */
    private data class PendingConfirmation(
        val action: String,
        val target: String?,
        val parameters: Map<String, String>,
        val createdAt: Long
    )

    @Volatile
    private var pendingConfirmation:
        PendingConfirmation? = null

    /*
     * A confirmation request is intentionally short-lived.
     *
     * This prevents an old side-effecting action from being
     * executed minutes later after the conversation context
     * has changed.
     */
    private val confirmationTimeoutMs =
        60_000L

    companion object {

        const val CHANNEL_ID =
            "jarvis_voice_channel"

        const val NOTIF_ID =
            1001
    }

    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIF_ID,
            buildNotification(
                "Waking up Jarvis…"
            )
        )

        val powerManager =
            getSystemService(
                POWER_SERVICE
            ) as PowerManager

        wakeLock =
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VoiceJarvis::VoiceLock"
            )

        wakeLock?.acquire(
            12 * 60 * 60 * 1000L
        )

        toolExecutor =
            ToolExecutor(this)

        capabilityBus =
            CapabilityBus(this)

        /*
         * IMPORTANT:
         *
         * Keep the existing CapabilityBusToolBridge
         * construction from the project architecture.
         *
         * If your constructor differs, do NOT guess it here.
         * Use the existing project constructor.
         */
        audioEngine =
            AudioEngine(

                onMicChunk = { chunk ->

                    /*
                     * V2:
                     *
                     * We DO NOT disable microphone transmission
                     * merely because Jarvis is speaking.
                     *
                     * Gemini server-side VAD needs the microphone
                     * stream to detect barge-in.
                     */
                    if (
                        !isPaused &&
                        !inFallbackMode
                    ) {

                        geminiClient
                            ?.sendAudioChunk(
                                chunk
                            )
                    }
                },

                onMicAmplitude = { level ->

                    handler.post {

                        handleMicAmplitude(
                            level
                        )
                    }
                },

                onPlaybackAmplitude = { level ->

                    handler.post {

                        updatePlaybackAmplitude(
                            level * 2.2f
                        )
                    }
                },

                onPlaybackIdle = {

                    handler.post {

                        handlePlaybackIdle()
                    }
                },

                onRecordingError = { message ->

                    handler.post {

                        log(
                            "Microphone recovery required: $message"
                        )

                        if (
                            !isPaused &&
                            !inFallbackMode
                        ) {

                            audioEngine.stopRecording()

                            handler.postDelayed(
                                {

                                    if (
                                        !isPaused &&
                                        !inFallbackMode
                                    ) {

                                        log(
                                            "Restarting microphone capture."
                                        )

                                        acquireJarvisAudioSession()


                                        audioEngine.startRecording()
                                    }

                                },
                                300L
                            )
                        }
                    }
                }
            )

        /*
         * NOTE:
         *
         * capabilityBusToolBridge must be initialized by the
         * existing project wiring before a Capability Bus tool
         * is executed.
         *
         * We intentionally do not invent its constructor here.
         */

        connectGemini()
    }


    /**
     * Acquire Android audio focus and communication mode for
     * the active Jarvis voice session.
     *
     * Idempotent:
     * calling this repeatedly does not create multiple
     * focus requests.
     */
    private fun acquireJarvisAudioSession() {

        if (jarvisAudioFocusOwned) {
            return
        }

        val audioManager =
            getSystemService(
                AUDIO_SERVICE
            ) as AudioManager

        jarvisPreviousAudioMode =
            audioManager.mode

        val attributes =
            AudioAttributes.Builder()
                .setUsage(
                    AudioAttributes.USAGE_ASSISTANT
                )
                .setContentType(
                    AudioAttributes.CONTENT_TYPE_SPEECH
                )
                .build()

        val focusRequest =
            AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
                .setAudioAttributes(
                    attributes
                )
                .setAcceptsDelayedFocusGain(
                    false
                )
                .setOnAudioFocusChangeListener { change ->

                    log(
                        "Jarvis audio focus changed: $change"
                    )
                }
                .build()

        val result =
            audioManager.requestAudioFocus(
                focusRequest
            )

        if (
            result ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {

            jarvisAudioFocusRequest =
                focusRequest

            /*
             * MODE_IN_COMMUNICATION is the Android audio mode
             * intended for realtime two-way communication.
             */
            audioManager.mode =
                AudioManager.MODE_IN_COMMUNICATION

            jarvisAudioFocusOwned =
                true

            log(
                "Jarvis audio session acquired."
            )

        } else {

            jarvisAudioFocusRequest =
                null

            jarvisAudioFocusOwned =
                false

            log(
                "Jarvis audio focus request was not granted."
            )
        }
    }


    /**
     * Release Jarvis's Android audio session.
     *
     * Restores the audio mode that existed before Jarvis
     * acquired the communication session.
     */
    private fun releaseJarvisAudioSession() {

        if (!jarvisAudioFocusOwned) {
            return
        }

        val audioManager =
            getSystemService(
                AUDIO_SERVICE
            ) as AudioManager

        try {

            jarvisAudioFocusRequest?.let {

                audioManager.abandonAudioFocusRequest(
                    it
                )
            }

        } catch (e: Exception) {

            log(
                "Audio focus release failed: ${e.message}"
            )
        }

        jarvisAudioFocusRequest =
            null

        jarvisAudioFocusOwned =
            false

        jarvisPreviousAudioMode?.let {

            try {

                audioManager.mode =
                    it

            } catch (e: Exception) {

                log(
                    "Audio mode restore failed: ${e.message}"
                )
            }
        }

        jarvisPreviousAudioMode =
            null

        log(
            "Jarvis audio session released."
        )
    }


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        return START_STICKY
    }

    override fun onBind(
        intent: Intent?
    ): IBinder {

        return binder
    }

    private fun currentDateTimeLine():
        String {

        val now =
            SimpleDateFormat(
                "EEEE, d MMMM yyyy, h:mm a",
                Locale.getDefault()
            )
                .format(
                    Date()
                )

        return """
            Current date and time: $now.
            Treat this as the current date and time.
            Do not invent another date or time.
        """.trimIndent()
    }

    private fun buildPrimarySystemPrompt():
        String {

        val personality =
            AssistantPersonalityPreferences.getSelectedPersonalityInfo(
                this
            )

        return """
            ${currentDateTimeLine()}

            You are JARVIS, Roni's personal real-time voice assistant.

            PERSONALITY PROFILE:
            You are operating in the "${personality.name}" personality.

            ${personality.description}

            PERSONALITY TRAITS:
            ${personality.traits.joinToString(", ")}

            PERSONALITY BEHAVIOR:
            ${personality.systemPrompt}

            CONVERSATION STYLE:
            - Speak naturally.
            - Be extremely concise.
            - Sound like a human voice assistant, not a chatbot.
            - Never give unnecessary explanations.
            - Never use markdown.
            - Never use bullet points.
            - Never repeat the user's question.

            RESPONSE LENGTH:
            - Greetings: 1-5 words.
            - Simple questions: 1 short sentence.
            - Confirmations: 1-6 words.
            - Device actions: 1 short confirmation.
            - Only give longer answers when Roni explicitly asks for details.

            LANGUAGE:
            - Understand Bengali, Hindi, English, Hinglish and Banglish.
            - Reply in the language or natural language mix Roni used.
            - Do not translate unless asked.
            - Preserve names exactly when you understand them.
            - If speech is ambiguous, ask a very short clarification instead of guessing.

            REAL-TIME BEHAVIOR:
            - Answer immediately when enough information is available.
            - Do not overthink simple questions.
            - Do not search the web unless the question genuinely requires current information.
            - Do not call a tool when a normal conversational answer is enough.
            - If Roni starts speaking while you are speaking, stop and listen immediately.

            TOOLS:
            You can call contacts, prepare WhatsApp/SMS drafts, open apps,
            control flashlight, alarms, timers, media, volume, browser,
            Google search, navigation, contacts, clipboard and location.

            When a dedicated tool exists, use it.

            Before claiming an app is unavailable, try open_app first.

            For unsupported UI actions:
            use read_screen first, then operate step by step.

            WHATSAPP AND SMS:
            Never claim a message was sent unless the user explicitly confirmed sending.
            Draft first.
            Only send after clear confirmation.

            CALLS:
            You may answer or end calls when explicitly requested.
            Never end a call because of an ambiguous word.

            CURRENT INFORMATION:
            Use search_web only when necessary for current or time-sensitive information.
        """.trimIndent()
    }

    private fun buildFallbackSystemPrompt():
        String {

        val personality =
            AssistantPersonalityPreferences.getSelectedPersonalityInfo(
                this
            )

        return """
            ${currentDateTimeLine()}

            You are JARVIS in lightweight backup voice mode.

            PERSONALITY PROFILE:
            You are operating in the "${personality.name}" personality.

            ${personality.systemPrompt}

            Speak naturally and extremely briefly.

            Understand Bengali, Hindi, English, Hinglish and Banglish.

            Reply using the same language style as the user.

            Greetings and simple questions should normally be answered
            in one short sentence.

            Never use markdown.
            Never give unnecessary explanation.

            Device-control features are temporarily unavailable.
        """.trimIndent()
    }

    private fun connectGemini() {

        val apiKey =
            BuildConfig.GEMINI_API_KEY

        if (apiKey.isBlank()) {

            pushState(
                JarvisState.ERROR,
                "TRY AGAIN",
                "Gemini API key missing."
            )

            log(
                "Gemini API key is empty."
            )

            return
        }

        val connectionPerformanceTraceId =
            FirebasePerformanceManager
                .startGeminiConnection()

        geminiPerformanceTraceId =
            connectionPerformanceTraceId

        geminiClient =
            GeminiLiveClient(

                apiKey =
                    apiKey,

                systemPrompt =
                    buildPrimarySystemPrompt(),

                voiceName =
                    VoicePreferences.getSelectedVoice(
                        this@JarvisService
                    ),

                onSetupComplete = {

                    handler.post {

                        log(
                            "Gemini 3.1 Live connected."
                        )

                        FirebasePerformanceManager
                            .finishGeminiConnection(
                                handleId =
                                    connectionPerformanceTraceId,
                                success =
                                    true
                            )

                        if (
                            geminiPerformanceTraceId ==
                            connectionPerformanceTraceId
                        ) {

                            geminiPerformanceTraceId =
                                null
                        }

                        consecutiveFailures =
                            0

                        exitFallbackMode()

                        acquireJarvisAudioSession()


                        audioEngine.startRecording()

                        audioEngine.startPlayback()

                        /*
                         * Mic remains enabled.
                         *
                         * This is intentional for Gemini VAD.
                         */
                        audioEngine.micSendEnabled =
                            !isPaused

                        if (!isPaused) {

                            pushState(
                                JarvisState.LISTENING,
                                "LISTENING",
                                "I'm listening."
                            )
                        }
                    }
                },

                onAudioChunk = { bytes ->

                    noMoreAudioIncoming =
                        false

                    /*
                     * CRITICAL V2 CHANGE:
                     *
                     * Do NOT disable micSendEnabled here.
                     *
                     * Gemini needs continued microphone audio
                     * for server-side interruption detection.
                     */

                    if (
                        firebaseTurnStartedAt > 0L &&
                        !firebaseFirstResponseRecorded
                    ) {

                        val latencyMs =
                            (
                                SystemClockCompat.elapsedRealtime() -
                                    firebaseTurnStartedAt
                                )
                                .coerceAtLeast(0L)

                        firebaseFirstResponseLatencyMs =
                            latencyMs

                        FirebasePerformanceManager
                            .setVoiceTurnMetric(
                                handleId =
                                    voicePerformanceTraceId,
                                name =
                                    "first_audio_ms",
                                value =
                                    latencyMs
                            )

                        firebaseFirstResponseRecorded =
                            true

                        FirebaseManager.recordLatency(
                            metric =
                                "time_to_first_audio",
                            durationMs =
                                latencyMs,
                            provider =
                                "gemini-live"
                        )
                    }

                    audioEngine.enqueuePlayback(
                        bytes
                    )

                    handler.post {

                        if (!isPaused) {

                            pushState(
                                JarvisState.SPEAKING,
                                "SPEAKING",
                                ""
                            )
                        }
                    }
                },

                onInputTranscript = { text ->

                    val cleaned =
                        normalizeTranscript(
                            text
                        )

                    if (cleaned.isNotBlank()) {

                        /*
                         * Confirmation is handled locally before
                         * normal Gemini turn accumulation.
                         *
                         * This prevents "yes" from becoming an
                         * ordinary conversational turn while a
                         * side-effecting action is pending.
                         */
                        if (
                            pendingConfirmation != null
                        ) {

                            /*
                             * IMPORTANT:
                             *
                             * While a side-effecting action is
                             * awaiting confirmation, ALL user input
                             * stays local.
                             *
                             * Nothing ambiguous is allowed to fall
                             * through into Gemini, because Gemini must
                             * never be able to reinterpret an uncertain
                             * confirmation as a fresh tool request.
                             */
                            resumePendingConfirmation(
                                cleaned
                            )

                        } else {

                            if (
                                !firebaseConversationStarted
                            ) {

                            val conversationId =
                                FirebaseManager
                                    .ensureConversationStarted(
                                        "voice"
                                    )

                            if (
                                conversationId != null
                            ) {

                                firebaseConversationStarted =
                                    true
                            }
                        }

                        if (
                            firebaseTurnStartedAt == 0L
                        ) {

                            firebaseTurnStartedAt =
                                SystemClockCompat
                                    .elapsedRealtime()

                            voicePerformanceTraceId =
                                FirebasePerformanceManager
                                    .startVoiceTurn()

                            FirebaseAnalyticsManager
                                .voiceTurnStarted()
                        }

                        latestUserTranscript =
                            mergeTranscript(
                                latestUserTranscript,
                                cleaned
                            )

                        lastUserText =
                            latestUserTranscript

                            handler.post {

                                pushConversation(
                                    latestUserTranscript,
                                    null
                                )
                            }
                        }
                    }
                },

                onOutputTranscript = { text ->

                    val cleaned =
                        normalizeTranscript(
                            text
                        )

                    if (cleaned.isNotBlank()) {

                        if (
                            firebaseTurnStartedAt > 0L &&
                            !firebaseFirstResponseRecorded
                        ) {

                            val latencyMs =
                                (
                                    SystemClockCompat
                                        .elapsedRealtime() -
                                        firebaseTurnStartedAt
                                    )
                                    .coerceAtLeast(0L)

                            firebaseFirstResponseLatencyMs =
                                latencyMs

                            firebaseFirstResponseRecorded =
                                true

                            FirebaseManager.recordLatency(
                                metric =
                                    "first_assistant_transcript",
                                durationMs =
                                    latencyMs,
                                provider =
                                    "gemini-live"
                            )
                        }

                        latestJarvisTranscript =
                            mergeTranscript(
                                latestJarvisTranscript,
                                cleaned
                            )

                        lastJarvisText =
                            latestJarvisTranscript

                        handler.post {

                            pushConversation(
                                latestUserTranscript,
                                latestJarvisTranscript
                            )
                        }
                    }
                },

                onInterrupted = {

                    firebaseTurnInterrupted =
                        true

                    if (
                        firebaseConversationStarted
                    ) {

                        FirebaseManager.recordLatency(
                            metric =
                                "interrupted",
                            durationMs =
                                1L,
                            provider =
                                "gemini-live"
                        )
                    }

                    handler.post {

                        /*
                         * FIRST:
                         *
                         * Kill all queued assistant audio.
                         */
                        audioEngine.clearPlaybackQueue()

                        /*
                         * SECOND:
                         *
                         * Keep microphone open.
                         */
                        audioEngine.micSendEnabled =
                            !isPaused

                        noMoreAudioIncoming =
                            true

                        voiceActive =
                            true

                        pushState(
                            JarvisState.HEARING,
                            "HEARING",
                            "Go ahead…"
                        )

                        log(
                            "Jarvis interrupted by user."
                        )
                    }
                },

                onGenerationComplete = {

                    /*
                     * Gemini finished generating the current response.
                     *
                     * IMPORTANT:
                     *
                     * This is NOT treated as the same event as
                     * turnComplete.
                     *
                     * Gemini may emit generationComplete before
                     * turnComplete because realtime playback can
                     * still be finishing.
                     */
                    handler.post {

                        log(
                            "Gemini generation complete."
                        )
                    }
                },

                onToolCall = {
                        id,
                        name,
                        args ->

                    synchronized(
                        firebaseTurnTools
                    ) {

                        if (
                            !firebaseTurnTools
                                .contains(name)
                        ) {

                            firebaseTurnTools.add(
                                name
                            )
                        }
                    }

                    FirebaseAnalyticsManager
                        .toolStarted(
                            name
                        )

                    /*
                     * Begin lifecycle-owned OrbActivity.
                     *
                     * This generation belongs exclusively to the
                     * current tool invocation.
                     */
                    val orbActivityGeneration =
                        beginOrbActivity(
                            name
                        )

                    val toolStartedAt =
                        SystemClockCompat
                            .elapsedRealtime()

                    val busHandles =
                        try {

                            capabilityBusToolBridge
                                .handles(
                                    name
                                )

                        } catch (
                            e: UninitializedPropertyAccessException
                        ) {

                            false
                        }

                    val performanceTraceId =
                        FirebasePerformanceManager
                            .startTool(
                                name
                            )

                    val result =
                        try {

                            if (
                                busHandles
                            ) {

                                val plannedRequest =
                                    planCapabilityToolCall(
                                        name,
                                        args
                                    )

                                val actionResult =
                                    capabilityBusToolBridge
                                        .execute(
                                            name,
                                            args
                                        )

                                actionResultToJson(
                                    actionResult,
                                    plannedRequest
                                )

                            } else {

                                toolExecutor.execute(
                                    name,
                                    args
                                )
                            }

                        } catch (
                            e: Exception
                        ) {

                            FirebaseAnalyticsManager
                                .toolFailed(
                                    name
                                )

                            FirebasePerformanceManager
                                .finishTool(
                                    performanceTraceId,
                                    success =
                                        false
                                )

                            JSONObjectCompat.error(
                                e.message
                            )
                        }

                    val toolDurationMs =
                        SystemClockCompat
                            .elapsedRealtime() -
                            toolStartedAt

                    val toolSucceeded =
                        result.optBoolean(
                            "success",
                            true
                        )

                    if (
                        toolSucceeded
                    ) {

                        FirebaseAnalyticsManager
                            .toolCompleted(
                                tool =
                                    name,
                                durationMs =
                                    toolDurationMs
                            )

                    } else {

                        FirebaseAnalyticsManager
                            .toolFailed(
                                name
                            )
                    }

                    FirebasePerformanceManager
                        .finishTool(
                            performanceTraceId,
                            toolSucceeded
                        )



                                        /*
                     * 3.3.3.10A — Complete this tool's activity ownership.
                     *
                     * A generation token prevents stale tool completions from
                     * clearing a newer operation.
                     */
                    val confirmationRequired =
                        pendingConfirmation != null

                    handler.post {
                        finishOrbActivity(
                            orbActivityGeneration,
                            confirmationRequired
                        )
                    }

                    geminiClient
                        ?.sendToolResponse(
                            id,
                            name,
                            result
                        )

                    handler.post {

                        log(
                            "Tool: $name"
                        )

                        if (
                            busHandles
                        ) {

                            log(
                                "Capability Bus: $name"
                            )
                        }
                    }
                },

                onTurnComplete = {

                    val turnEndedAt =
                        SystemClockCompat
                            .elapsedRealtime()

                    val turnStartedAt =
                        firebaseTurnStartedAt

                    val userTranscript =
                        latestUserTranscript

                    val assistantTranscript =
                        latestJarvisTranscript

                    val durationMs =
                        if (
                            turnStartedAt > 0L
                        ) {

                            (
                                turnEndedAt -
                                    turnStartedAt
                                )
                                .coerceAtLeast(0L)

                        } else {

                            null
                        }

                    val tools =
                        synchronized(
                            firebaseTurnTools
                        ) {

                            firebaseTurnTools
                                .toList()
                        }

                    if (
                        firebaseConversationStarted &&
                        (
                            userTranscript.isNotBlank() ||
                            assistantTranscript.isNotBlank()
                        )
                    ) {

                        firebaseCurrentTurnId =
                            FirebaseManager
                                .recordCompletedTurn(

                                    userTranscript =
                                        userTranscript,

                                    assistantTranscript =
                                        assistantTranscript,

                                    durationMs =
                                        durationMs,

                                    firstResponseLatencyMs =
                                        firebaseFirstResponseLatencyMs,

                                    provider =
                                        "gemini-live",

                                    interrupted =
                                        firebaseTurnInterrupted,

                                    toolNames =
                                        tools,

                                    responseAccepted =
                                        firebaseResponseAccepted,

                                    userCorrected =
                                        firebaseUserCorrected,

                                    correctionType =
                                        firebaseCorrectionType,

                                    qualityScore =
                                        firebaseQualityScore
                                )

                        FirebaseAnalyticsManager
                            .voiceTurnCompleted(
                                durationMs =
                                    durationMs,
                                firstResponseLatencyMs =
                                    firebaseFirstResponseLatencyMs,
                                provider =
                                    "gemini-live",
                                interrupted =
                                    firebaseTurnInterrupted
                            )

                        FirebasePerformanceManager
                            .setVoiceTurnMetric(
                                handleId =
                                    voicePerformanceTraceId,
                                name =
                                    "duration_ms",
                                value =
                                    durationMs ?: 0L
                            )

                        FirebasePerformanceManager
                            .setVoiceTurnAttribute(
                                handleId =
                                    voicePerformanceTraceId,
                                name =
                                    "interrupted",
                                value =
                                    firebaseTurnInterrupted
                                        .toString()
                            )
                    }

                    FirebasePerformanceManager
                        .finishVoiceTurn(
                            handleId =
                                voicePerformanceTraceId
                        )

                    voicePerformanceTraceId =
                        null

                    firebaseTurnStartedAt =
                        0L

                    firebaseFirstResponseRecorded =
                        false

                    firebaseFirstResponseLatencyMs =
                        null

                    firebaseTurnInterrupted =
                        false

                    firebaseResponseAccepted =
                        null

                    firebaseUserCorrected =
                        false

                    firebaseCorrectionType =
                        null

                    firebaseQualityScore =
                        null

                    synchronized(
                        firebaseTurnTools
                    ) {

                        firebaseTurnTools.clear()
                    }

                    handler.post {

                        if (
                            userTranscript.isNotBlank()
                        ) {

                            log(
                                "You: $userTranscript"
                            )
                        }

                        if (
                            assistantTranscript.isNotBlank()
                        ) {

                            log(
                                "Jarvis: $assistantTranscript"
                            )
                        }

                        latestUserTranscript =
                            ""

                        latestJarvisTranscript =
                            ""
                    }

                    noMoreAudioIncoming =
                        true
                },

                onError = { message ->

                    handler.post {

                        log(
                            message
                        )
                    }
                },

                onDisconnected = {

                    FirebasePerformanceManager
                        .finishGeminiConnection(
                            handleId =
                                connectionPerformanceTraceId,
                            success =
                                false
                        )

                    if (
                        geminiPerformanceTraceId ==
                        connectionPerformanceTraceId
                    ) {

                        geminiPerformanceTraceId =
                            null
                    }

                    handler.post {

                        consecutiveFailures++

                        if (
                            consecutiveFailures >= 2 &&
                            !inFallbackMode
                        ) {

                            enterFallbackMode()

                        } else if (
                            !isPaused &&
                            !inFallbackMode
                        ) {

                            pushState(
                                JarvisState.THINKING,
                                "RECONNECTING",
                                "One moment."
                            )
                        }
                    }
                }
            )

        geminiClient?.connect()
    }

    /**
     * 3.3.3.9A — Runtime tool-to-visual activity classification.
     *
     * JarvisState = conversational state.
     * OrbActivity = active operation.
     */
    private fun classifyOrbActivity(
        toolName: String
    ): OrbActivity {

        return when (
            toolName
                .trim()
                .lowercase(Locale.getDefault())
        ) {

            "search_web",
            "search_google" ->
                OrbActivity.SEARCHING

            "deep_research" ->
                OrbActivity.RESEARCHING

            "toggle_flashlight",
            "set_volume",
            "set_alarm",
            "set_timer",
            "navigate_to",
            "read_screen",
            "tap_element",
            "type_text",
            "scroll_screen",
            "go_back",
            "go_home",
            "open_accessibility_settings" ->
                OrbActivity.CONTROLLING_DEVICE

            else ->
                OrbActivity.EXECUTING_TOOL
        }
    }

    private fun executeCapability(
        action: String,
        target: String? = null,
        parameters: Map<String, String> =
            emptyMap()
    ): ActionResult {

        return capabilityBus.execute(
            action =
                action,
            target =
                target,
            parameters =
                parameters
        )
    }

    /**
     * Convert one migrated Gemini tool call into the exact
     * ActionRequest used by the Capability Bus planner.
     *
     * This must mirror CapabilityBusToolBridge.execute()
     * without executing the action.
     */
    private fun planCapabilityToolCall(
        name: String,
        args: org.json.JSONObject
    ): ActionRequest {

        return when (
            name.trim()
        ) {

            "open_app" -> {

                val appName =
                    args.optString(
                        "app_name"
                    )
                        .trim()

                capabilityBus.plan(
                    action = "open_app",
                    target = appName
                )
            }

            "read_screen" -> {

                capabilityBus.plan(
                    action = "read_screen"
                )
            }

            "scroll_screen" -> {

                val direction =
                    args.optString(
                        "direction",
                        "down"
                    )
                        .trim()
                        .lowercase()

                capabilityBus.plan(
                    action = "scroll",
                    parameters =
                        mapOf(
                            "direction" to
                                direction
                        )
                )
            }

            "tap_element" -> {

                val id =
                    args.optInt(
                        "id",
                        -1
                    )

                capabilityBus.plan(
                    action = "tap_element",
                    parameters =
                        mapOf(
                            "id" to
                                id.toString()
                        )
                )
            }

            "go_back" -> {

                capabilityBus.plan(
                    action = "back"
                )
            }

            "go_home" -> {

                capabilityBus.plan(
                    action = "home"
                )
            }

            "get_battery" -> {

                capabilityBus.plan(
                    action = "get_battery"
                )
            }

            "toggle_flashlight" -> {

                val enabled =
                    args.optBoolean(
                        "on",
                        false
                    )

                capabilityBus.plan(
                    action = "toggle_flashlight",
                    parameters =
                        mapOf(
                            "on" to
                                enabled.toString()
                        )
                )
            }

            "set_volume" -> {

                val percent =
                    args.optInt(
                        "percent",
                        -1
                    )

                capabilityBus.plan(
                    action = "set_volume",
                    parameters =
                        mapOf(
                            "percent" to
                                percent.toString()
                        )
                )
            }

            "set_alarm" -> {

                val hour =
                    args.optInt(
                        "hour",
                        -1
                    )

                val minute =
                    args.optInt(
                        "minute",
                        -1
                    )

                val label =
                    args.optString(
                        "label",
                        ""
                    )

                capabilityBus.plan(
                    action = "set_alarm",
                    parameters =
                        mapOf(
                            "hour" to
                                hour.toString(),
                            "minute" to
                                minute.toString(),
                            "label" to
                                label
                        )
                )
            }

            "set_timer" -> {

                val seconds =
                    args.optInt(
                        "seconds",
                        -1
                    )

                val label =
                    args.optString(
                        "label",
                        ""
                    )

                capabilityBus.plan(
                    action = "set_timer",
                    parameters =
                        mapOf(
                            "seconds" to
                                seconds.toString(),
                            "label" to
                                label
                        )
                )
            }

            "media_control" -> {

                val command =
                    args.optString(
                        "action",
                        ""
                    )
                        .trim()

                capabilityBus.plan(
                    action = "media_control",
                    parameters =
                        mapOf(
                            "action" to
                                command
                        )
                )
            }

            "call_contact" -> {

                val nameOrNumber =
                    args.optString(
                        "name_or_number",
                        ""
                    )
                        .trim()

                capabilityBus.plan(
                    action = "call",
                    target =
                        nameOrNumber
                )
            }

            "send_sms" -> {

                val nameOrNumber =
                    args.optString(
                        "name_or_number",
                        ""
                    )
                        .trim()

                val message =
                    args.optString(
                        "message",
                        ""
                    )

                capabilityBus.plan(
                    action = "send_sms",
                    target =
                        nameOrNumber,
                    parameters =
                        mapOf(
                            "message" to
                                message
                        )
                )
            }

            else -> {

                capabilityBus.plan(
                    action =
                        name.trim()
                )
            }
        }
    }

    private fun actionResultToJson(
        result: ActionResult,
        request: ActionRequest? = null
    ): org.json.JSONObject {

        if (
            result.status ==
                ActionStatus.REQUIRES_USER &&
            result.requiresConfirmation &&
            request != null
        ) {
            setPendingConfirmation(
                request
            )
        }

        val json =
            org.json.JSONObject()

        json.put(
            "success",
            result.status ==
                ActionStatus.EXECUTED ||
                result.status ==
                ActionStatus.VERIFIED
        )

        json.put(
            "status",
            result.status
                .name
                .lowercase()
        )

        json.put(
            "message",
            result.message
        )

        json.put(
            "verified",
            result.verified
        )

        json.put(
            "requiresConfirmation",
            result.requiresConfirmation
        )

        if (
            result.data.isNotEmpty()
        ) {

            val data =
                org.json.JSONObject()

            result.data.forEach {
                    (key, value) ->

                data.put(
                    key,
                    value
                )
            }

            json.put(
                "data",
                data
            )
        }

        return json
    }

    /**
     * Store one action that is waiting for explicit user
     * confirmation.
     *
     * Replaces any older pending action so stale side effects
     * cannot be resumed accidentally.
     */
    private fun setPendingConfirmation(
        request: ActionRequest
    ) {

        pendingConfirmation =
            PendingConfirmation(
                action =
                    request.action,
                target =
                    request.target,
                parameters =
                    request.parameters.toMap(),
                createdAt =
                    SystemClockCompat.elapsedRealtime()
            )

        log(
            "Confirmation required for: ${request.action}"
        )

        orbWaitingForConfirmation =
            true

        pushOrbActivity(
            OrbActivity.WAITING_CONFIRMATION
        )

        pushState(
            JarvisState.THINKING,
            "CONFIRMATION REQUIRED",
            "Please confirm."
        )
    }

    private fun clearPendingConfirmation() {

        pendingConfirmation = null
    }

    private fun isAffirmativeConfirmation(
        text: String
    ): Boolean {

        val normalized =
            normalizeTranscript(
                text
            )
                .lowercase(Locale.getDefault())

        return normalized in setOf(
            "yes",
            "yeah",
            "yep",
            "yup",
            "okay",
            "ok",
            "confirm",
            "confirmed",
            "do it",
            "go ahead",
            "send it",
            "haan",
            "hmm yes",
            "thik ache",
            "ঠিক আছে",
            "হ্যাঁ"
        )
    }

    private fun isNegativeConfirmation(
        text: String
    ): Boolean {

        val normalized =
            normalizeTranscript(
                text
            )
                .lowercase(Locale.getDefault())

        return normalized in setOf(
            "no",
            "nope",
            "cancel",
            "cancel it",
            "don't",
            "dont",
            "stop",
            "never mind",
            "না",
            "নাহ",
            "বাদ দাও",
            "cancel koro na"
        )
    }

    /**
     * Resume the exact action previously blocked by RiskEngine.
     *
     * The action is executed only after a positive confirmation
     * from the user. Confirmation bypass is limited to this
     * stored request; it is never model-controlled.
     */
    private fun resumePendingConfirmation(
        confirmationText: String
    ): Boolean {

        val pending =
            pendingConfirmation
                ?: return true

        /*
         * Confirmation requests expire quickly.
         *
         * Never execute an action merely because an old
         * PendingConfirmation object still exists.
         */
        val age =
            (
                SystemClockCompat.elapsedRealtime() -
                    pending.createdAt
            )

        if (
            age < 0L ||
            age > confirmationTimeoutMs
        ) {

            clearPendingConfirmation()

            clearOrbConfirmationActivity()

            pushState(
                JarvisState.LISTENING,
                "CONFIRMATION EXPIRED",
                "The previous action expired. Please ask again."
            )

            log(
                "Pending action expired before confirmation: ${pending.action}"
            )

            return true
        }

        if (
            isNegativeConfirmation(
                confirmationText
            )
        ) {

            clearPendingConfirmation()

            clearOrbConfirmationActivity()

            pushState(
                JarvisState.LISTENING,
                "CANCELLED",
                ""
            )

            log(
                "Pending action cancelled by user."
            )

            return true
        }

        if (
            isAffirmativeConfirmation(
                confirmationText
            )
        ) {

            /*
             * Consume the pending request BEFORE execution.
             *
             * This prevents duplicate execution if the audio
             * pipeline delivers the same confirmation transcript
             * more than once.
             */
            clearPendingConfirmation()

            clearOrbConfirmationActivity()

            val result =
                capabilityBus.execute(
                    action =
                        pending.action,
                    target =
                        pending.target,
                    parameters =
                        pending.parameters,
                    skipConfirmation =
                        true
                )

            log(
                "Confirmed action ${pending.action}: ${result.status.name}"
            )

            handler.post {

                when (result.status) {

                    ActionStatus.EXECUTED,
                    ActionStatus.VERIFIED -> {

                        pushState(
                            JarvisState.LISTENING,
                            "DONE",
                            result.message
                        )
                    }

                    else -> {

                        pushState(
                            JarvisState.ERROR,
                            "ACTION FAILED",
                            result.message
                        )
                    }
                }
            }

            return true
        }

        /*
         * IMPORTANT:
         *
         * Ambiguous input is consumed locally.
         * It MUST NOT reach Gemini.
         */
        pushState(
            JarvisState.THINKING,
            "CONFIRMATION REQUIRED",
            "Please say yes to continue or no to cancel."
        )

        log(
            "Ambiguous confirmation input rejected locally: $confirmationText"
        )

        return true
    }

    private fun normalizeTranscript(
        text: String
    ): String {

        return text
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun mergeTranscript(
        previous: String,
        incoming: String
    ): String {

        val next =
            normalizeTranscript(
                incoming
            )

        if (next.isBlank()) {
            return previous
        }

        if (previous.isBlank()) {
            return next
        }

        if (previous == next) {
            return previous
        }

        if (
            next.startsWith(previous)
        ) {

            return next
        }

        if (
            previous.startsWith(next)
        ) {

            return previous
        }

        val previousWords =
            previous.split(" ")

        val nextWords =
            next.split(" ")

        val maxOverlap =
            minOf(
                8,
                previousWords.size,
                nextWords.size
            )

        for (
            size in maxOverlap downTo 1
        ) {

            val previousSuffix =
                previousWords
                    .takeLast(size)

            val nextPrefix =
                nextWords
                    .take(size)

            if (
                previousSuffix ==
                nextPrefix
            ) {

                val remainder =
                    nextWords
                        .drop(size)
                        .joinToString(" ")

                return if (
                    remainder.isBlank()
                ) {

                    previous

                } else {

                    "$previous $remainder"
                }
            }
        }

        return "$previous $next"
    }

    private fun enterFallbackMode() {

        if (inFallbackMode) {
            return
        }

        inFallbackMode =
            true

        log(
            "Primary voice unavailable — backup mode."
        )

        audioEngine.stopRecording()

        audioEngine.stopPlayback()

        if (fallbackTts == null) {

            fallbackTts =
                TtsController(

                    this,

                    onSpeakStart = {

                        pushState(
                            JarvisState.SPEAKING,
                            "SPEAKING",
                            ""
                        )
                    },

                    onSpeakDone = {

                        if (
                            inFallbackMode &&
                            !isPaused
                        ) {

                            startFallbackListening()
                        }
                    }
                )
        }

        if (fallbackSpeech == null) {

            fallbackSpeech =
                SpeechController(

                    this,

                    onFinalResult = {
                        text ->

                        handleFallbackUserSpeech(
                            text
                        )
                    },

                    onError = {
                        message ->

                        log(
                            message
                        )

                        if (
                            inFallbackMode &&
                            !isPaused
                        ) {

                            handler.postDelayed(
                                {
                                    startFallbackListening()
                                },
                                500
                            )
                        }
                    },

                    onListeningStateChanged = {
                        listening ->

                        if (listening) {

                            pushState(
                                JarvisState.LISTENING,
                                "LISTENING",
                                "Backup voice mode."
                            )
                        }
                    }
                )
        }

        if (!isPaused) {

            startFallbackListening()
        }
    }

    private fun exitFallbackMode() {

        if (!inFallbackMode) {
            return
        }

        inFallbackMode =
            false

        fallbackSpeech?.stop()

        fallbackTts?.stop()

        log(
            "Full voice restored."
        )
    }

    private fun startFallbackListening() {

        if (
            inFallbackMode &&
            !isPaused
        ) {

            fallbackSpeech
                ?.startListening()
        }
    }

    private fun handleFallbackUserSpeech(
        text: String
    ) {

        val cleaned =
            normalizeTranscript(
                text
            )

        if (cleaned.isBlank()) {
            return
        }

        log(
            "You: $cleaned"
        )

        pushConversation(
            cleaned,
            null
        )

        pushState(
            JarvisState.THINKING,
            "THINKING",
            "One moment."
        )

        FallbackLLM.ask(

            systemPrompt =
                buildFallbackSystemPrompt(),

            userText =
                cleaned,

            onResult = {
                reply ->

                handler.post {

                    val cleanReply =
                        normalizeTranscript(
                            reply
                        )

                    log(
                        "Jarvis: $cleanReply"
                    )

                    pushConversation(
                        cleaned,
                        cleanReply
                    )

                    fallbackTts
                        ?.speak(
                            cleanReply
                        )
                }
            },

            onError = {
                error ->

                handler.post {

                    log(
                        error
                    )

                    pushState(
                        JarvisState.ERROR,
                        "TRY AGAIN",
                        "Please say that again."
                    )

                    if (
                        inFallbackMode &&
                        !isPaused
                    ) {

                        handler.postDelayed(
                            {
                                startFallbackListening()
                            },
                            700
                        )
                    }
                }
            }
        )
    }

    private fun handleMicAmplitude(
        level: Float
    ) {

        if (
            isPaused ||
            inFallbackMode
        ) {

            return
        }

        updateMicAmplitude(
            level * 3f
        )

        /*
         * This is UI state only.
         *
         * It does NOT gate microphone transmission.
         */
        if (
            level > ampThreshold &&
            audioEngine.micSendEnabled
        ) {

            if (!voiceActive) {

                voiceActive =
                    true

                pushState(
                    JarvisState.HEARING,
                    "HEARING",
                    "Go ahead…"
                )
            }

            silenceRunnable?.let {

                handler.removeCallbacks(
                    it
                )
            }

            silenceRunnable =
                Runnable {

                    voiceActive =
                        false

                    if (!isPaused) {

                        pushState(
                            JarvisState.THINKING,
                            "THINKING",
                            ""
                        )
                    }
                }

            handler.postDelayed(
                silenceRunnable!!,
                silenceTimeoutMs
            )
        }
    }

    private fun handlePlaybackIdle() {

        /*
         * Microphone remains continuously active in V2.
         *
         * Playback becoming idle changes the UI state only.
         * It does not control microphone transport.
         */

        if (
            noMoreAudioIncoming &&
            !isPaused &&
            !inFallbackMode
        ) {

            pushState(
                JarvisState.LISTENING,
                "LISTENING",
                "I'm listening."
            )
        }
    }

    fun toggleMute() {

        isPaused =
            !isPaused

        if (isPaused) {

            if (inFallbackMode) {

                fallbackSpeech?.stop()

                fallbackTts?.stop()

            } else {

                /*
                 * User explicitly paused the assistant.
                 */

                audioEngine.clearPlaybackQueue()
            }

            pushState(
                JarvisState.PAUSED,
                "PAUSED",
                "Tap Resume to continue."
            )

        } else {

            if (inFallbackMode) {

                startFallbackListening()

            } else {

                /*
                 * Resume realtime microphone.
                 */
                audioEngine.micSendEnabled =
                    true
            }

            pushState(
                JarvisState.LISTENING,
                "LISTENING",
                "I'm listening."
            )
        }
    }

    /*
     * Public interruption method.
     *
     * UI/wake-word layer can use this later.
     */
    fun interruptSpeaking() {

        if (isPaused) {
            return
        }

        audioEngine.clearPlaybackQueue()

        audioEngine.micSendEnabled =
            true

        noMoreAudioIncoming =
            true

        pushState(
            JarvisState.LISTENING,
            "LISTENING",
            "I'm listening."
        )
    }

    private fun pushState(
        state: JarvisState,
        label: String,
        sub: String
    ) {

        currentState =
            state

        currentLabel =
            label

        currentSub =
            sub

        listener?.onState(
            state,
            label,
            sub
        )

        updateNotification(
            label
        )
    }

    private fun updateMicAmplitude(
        level: Float
    ) {

        listener?.onMicAmplitude(
            level
        )
    }

    private fun pushOrbActivity(
        activity: OrbActivity
    ) {
        listener?.onActivity(
            activity
        )
    }

    /**
     * Start ownership of one tool's visual activity.
     */
    private fun beginOrbActivity(
        toolName: String
    ): Long {

        val activity =
            classifyOrbActivity(
                toolName
            )

        val generation =
            synchronized(
                orbActivityLock
            ) {

                orbActivityGeneration +=
                    1L

                val owner =
                    orbActivityGeneration

                activeOrbActivities[owner] =
                    activity

                owner
            }

        if (
            !orbWaitingForConfirmation
        ) {
            pushOrbActivity(
                activity
            )
        }

        return generation
    }

    /**
     * Finish one tool's visual activity ownership.
     *
     * The generation token guarantees that an old completion
     * cannot wipe a newer active tool.
     */
    private fun finishOrbActivity(
        generation: Long,
        confirmationRequired: Boolean
    ) {

        var nextActivity =
            OrbActivity.NONE

        synchronized(
            orbActivityLock
        ) {

            activeOrbActivities.remove(
                generation
            )

            if (
                confirmationRequired
            ) {

                orbWaitingForConfirmation =
                    true

            } else if (
                !orbWaitingForConfirmation &&
                activeOrbActivities.isNotEmpty()
            ) {

                nextActivity =
                    activeOrbActivities
                        .values
                        .last()
            }
        }

        if (
            confirmationRequired
        ) {

            pushOrbActivity(
                OrbActivity.WAITING_CONFIRMATION
            )

            return
        }

        if (
            orbWaitingForConfirmation
        ) {
            return
        }

        pushOrbActivity(
            nextActivity
        )
    }

    /**
     * Clear the confirmation visual lifecycle.
     */
    private fun clearOrbConfirmationActivity() {

        synchronized(
            orbActivityLock
        ) {

            orbWaitingForConfirmation =
                false

            activeOrbActivities.clear()
        }

        pushOrbActivity(
            OrbActivity.NONE
        )
    }

    private fun updatePlaybackAmplitude(
        level: Float
    ) {

        listener?.onPlaybackAmplitude(
            level
        )
    }

    private fun pushConversation(
        userText: String?,
        jarvisText: String?
    ) {

        lastUserText =
            userText ?: lastUserText

        lastJarvisText =
            jarvisText ?: lastJarvisText

        listener?.onConversation(
            userText,
            jarvisText
        )
    }

    fun getLastConversation():
        Pair<String?, String?> =

        lastUserText to
            lastJarvisText

    fun getLogSnapshot():
        String =

        logBuffer.toString()

    private fun log(
        message: String
    ) {

        logBuffer
            .append("\n")
            .append(message)

        listener?.onLog(
            message
        )
    }

    fun markLatestResponseAccepted(
        accepted: Boolean
    ) {

        firebaseResponseAccepted =
            accepted

        FirebaseManager.updateTurnQuality(
            turnId =
                firebaseCurrentTurnId,
            responseAccepted =
                accepted
        )
    }

    fun markLatestResponseCorrected(
        correctionType: String
    ) {

        firebaseUserCorrected =
            true

        firebaseCorrectionType =
            correctionType
                .trim()
                .uppercase()
                .takeIf {
                    it.isNotBlank()
                }

        FirebaseManager.updateTurnQuality(
            turnId =
                firebaseCurrentTurnId,
            userCorrected =
                true,
            correctionType =
                firebaseCorrectionType
        )
    }

    fun setLatestQualityScore(
        score: Int
    ) {

        firebaseQualityScore =
            score.coerceIn(
                1,
                5
            )

        FirebaseManager.updateTurnQuality(
            turnId =
                firebaseCurrentTurnId,
            qualityScore =
                firebaseQualityScore
        )
    }

    fun submitFeedback(
        rating: Int,
        comment: String? = null
    ) {

        FirebaseManager.recordFeedback(
            rating =
                rating,
            comment =
                comment
        )
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Jarvis Voice",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.setShowBadge(
                false
            )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager?.createNotificationChannel(
                channel
            )
        }
    }

    private fun buildNotification(
        text: String
    ): Notification {

        val openIntent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "Voice Jarvis"
            )
            .setContentText(
                text
            )
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setContentIntent(
                pendingIntent
            )
            .setOngoing(
                true
            )
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    private fun updateNotification(
        stateLabel: String
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager?.notify(
            NOTIF_ID,
            buildNotification(
                stateLabel
            )
        )
    }

    override fun onDestroy() {

        clearPendingConfirmation()

        super.onDestroy()

        handler.removeCallbacksAndMessages(
            null
        )

        FirebasePerformanceManager.close()

        geminiPerformanceTraceId =
            null

        voicePerformanceTraceId =
            null

        geminiClient?.disconnect()

        audioEngine.release()

        fallbackSpeech?.stop()

        fallbackTts?.shutdown()

        wakeLock?.let {

            if (it.isHeld) {

                it.release()
            }
        }


        releaseJarvisAudioSession()
    }
}

/*
 * Small local compatibility helpers.
 *
 * They keep the main service readable and avoid changing
 * your existing Firebase / JSON architecture.
 */
private object SystemClockCompat {

    fun elapsedRealtime(): Long =
        android.os.SystemClock.elapsedRealtime()
}

private object JSONObjectCompat {

    fun error(
        message: String?
    ): org.json.JSONObject {

        return org.json.JSONObject().apply {

            put(
                "success",
                false
            )

            put(
                "error",
                message ?: "Unknown tool error"
            )
        }
    }
}
