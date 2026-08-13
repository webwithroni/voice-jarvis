package com.webwithroni.voicejarvis

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

class JarvisService : Service() {

    interface UiListener {

        fun onState(
            state: JarvisState,
            label: String,
            sub: String
        )

        fun onAmplitude(
            level: Float
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

                        updateAmplitude(
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

        return """
            ${currentDateTimeLine()}

            You are JARVIS, Roni's personal real-time voice assistant.

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

        return """
            ${currentDateTimeLine()}

            You are JARVIS in lightweight backup voice mode.

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

                                val actionResult =
                                    capabilityBusToolBridge
                                        .execute(
                                            name,
                                            args
                                        )

                                actionResultToJson(
                                    actionResult
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

    private fun actionResultToJson(
        result: ActionResult
    ): org.json.JSONObject {

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

        updateAmplitude(
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

        if (
            noMoreAudioIncoming &&
            !isPaused &&
            !inFallbackMode
        ) {

            audioEngine.micSendEnabled =
                true

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
                audioEngine.micSendEnabled =
                    false

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

    private fun updateAmplitude(
        level: Float
    ) {

        listener?.onAmplitude(
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
