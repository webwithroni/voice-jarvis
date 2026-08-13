package com.webwithroni.voicejarvis

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import java.util.UUID

/**
 * Central Firebase gateway for Voice Jarvis.
 *
 * Responsibilities:
 * - Anonymous user identity
 * - Conversation/session records
 * - Turn telemetry
 * - Voice/LLM latency metrics
 * - Error events
 * - User feedback
 *
 * Privacy rule:
 * Raw voice audio is NOT stored here.
 * Transcripts are only stored when telemetry is explicitly enabled.
 */
object FirebaseManager {

    private const val TAG = "FirebaseManager"

    private val auth by lazy { Firebase.auth }
    private val db by lazy { Firebase.firestore }

    @Volatile
    private var initialized = false

    @Volatile
    private var telemetryEnabled = true

    private var currentConversationId: String? = null
    private var currentTurnId: String? = null

    private var preferences: android.content.SharedPreferences? = null

    private const val PREFS_NAME = "voice_jarvis_preferences"
    private const val PREF_TELEMETRY_ENABLED = "telemetry_enabled"

    /**
     * Initialize Firebase and establish an anonymous identity.
     */
    fun initialize(
        context: Context? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {

        context?.let {

            preferences =
                it.applicationContext.getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

            telemetryEnabled =
                preferences?.getBoolean(
                    PREF_TELEMETRY_ENABLED,
                    true
                ) ?: true
        }
        if (initialized && auth.currentUser != null) {
            onComplete?.invoke(true)
            return
        }

        initialized = true

        if (auth.currentUser != null) {
            onComplete?.invoke(true)
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener {
                Log.d(TAG, "Anonymous Firebase identity established.")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Firebase anonymous auth failed", error)
                onComplete?.invoke(false)
            }
    }

    /**
     * Enable/disable telemetry collection.
     *
     * This can later be connected to Settings.
     */
    fun setTelemetryEnabled(enabled: Boolean) {

        telemetryEnabled = enabled

        FirebaseAnalyticsManager.setEnabled(
            enabled
        )

        FirebasePerformanceManager.setEnabled(
            enabled
        )

        preferences
            ?.edit()
            ?.putBoolean(
                PREF_TELEMETRY_ENABLED,
                enabled
            )
            ?.apply()

        if (!enabled) {

            currentConversationId = null
            currentTurnId = null
        }
    }

    fun isTelemetryEnabled(): Boolean {
        return telemetryEnabled
    }

    /**
     * Ensure that a conversation exists.
     *
     * Safe to call repeatedly from the voice pipeline.
     * If Firebase authentication is not ready yet, this simply
     * returns null and voice continues normally.
     */
    fun ensureConversationStarted(
        source: String = "voice"
    ): String? {

        if (!telemetryEnabled) return null

        currentConversationId?.let {
            return it
        }

        return startConversation(source)
    }

    /**
     * Start a new conversation session.
     */
    fun startConversation(
        source: String = "voice"
    ): String? {

        if (!canWrite()) return null

        val user = auth.currentUser ?: return null

        val conversationId = UUID.randomUUID().toString()

        currentConversationId = conversationId

        val data = hashMapOf(
            "userId" to user.uid,
            "source" to source,
            "startedAt" to FieldValue.serverTimestamp(),
            "appVersion" to BuildConfig.VERSION_NAME,
            "platform" to "android"
        )

        db.collection("users")
            .document(user.uid)
            .collection("conversations")
            .document(conversationId)
            .set(data)
            .addOnFailureListener {
                Log.e(TAG, "Failed to create conversation", it)
            }

        return conversationId
    }

    /**
     * End the current conversation session.
     */
    fun endConversation() {

        if (!canWrite()) return

        val user = auth.currentUser ?: return
        val conversationId = currentConversationId ?: return

        db.collection("users")
            .document(user.uid)
            .collection("conversations")
            .document(conversationId)
            .set(
                mapOf(
                    "endedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )

        currentConversationId = null
    }

    /**
     * Record one conversational turn.
     *
     * transcript values are optional.
     * Audio itself is never uploaded.
     */
    fun recordTurn(
        role: String,
        transcript: String?,
        state: String?,
        latencyMs: Long? = null,
        provider: String? = null,
        interrupted: Boolean = false
    ) {

        if (!canWrite()) return

        val user = auth.currentUser ?: return
        val conversationId = currentConversationId ?: return

        val turnId = UUID.randomUUID().toString()

        val data = hashMapOf<String, Any>(
            "role" to role,
            "createdAt" to FieldValue.serverTimestamp(),
            "interrupted" to interrupted
        )

        transcript
            ?.takeIf { it.isNotBlank() }
            ?.let {
                data["transcript"] = it.take(4000)
            }

        state
            ?.takeIf { it.isNotBlank() }
            ?.let {
                data["state"] = it
            }

        latencyMs?.let {
            data["latencyMs"] = it
        }

        provider
            ?.takeIf { it.isNotBlank() }
            ?.let {
                data["provider"] = it
            }

        db.collection("users")
            .document(user.uid)
            .collection("conversations")
            .document(conversationId)
            .collection("turns")
            .document(turnId)
            .set(data)
            .addOnFailureListener {
                Log.e(TAG, "Failed to record turn", it)
            }
    }

    /**
     * Record one fully completed conversational turn.
     *
     * This is intentionally written once per turn rather than once
     * per streaming transcript callback.
     *
     * Raw microphone/audio data is never stored.
     */
    fun recordCompletedTurn(
        userTranscript: String?,
        assistantTranscript: String?,
        durationMs: Long?,
        firstResponseLatencyMs: Long?,
        provider: String = "gemini-live",
        interrupted: Boolean = false,
        toolNames: List<String> = emptyList(),
        responseAccepted: Boolean? = null,
        userCorrected: Boolean = false,
        correctionType: String? = null,
        qualityScore: Int? = null
    ): String? {

        if (!canWrite()) return null

        val user = auth.currentUser ?: return null
        val conversationId = currentConversationId ?: return null

        val turnId = UUID.randomUUID().toString()
        currentTurnId = turnId

        val data = hashMapOf<String, Any>(
            "createdAt" to FieldValue.serverTimestamp(),
            "provider" to provider,
            "interrupted" to interrupted
        )

        userTranscript
            ?.takeIf { it.isNotBlank() }
            ?.let {
                data["userTranscript"] = it.take(4000)
            }

        assistantTranscript
            ?.takeIf { it.isNotBlank() }
            ?.let {
                data["assistantTranscript"] = it.take(4000)
            }

        durationMs?.let {
            data["durationMs"] = it.coerceAtLeast(0L)
        }

        firstResponseLatencyMs?.let {
            data["firstResponseLatencyMs"] = it.coerceAtLeast(0L)
        }

        if (toolNames.isNotEmpty()) {
            data["tools"] = toolNames.distinct().take(20)
        }

        responseAccepted?.let {
            data["responseAccepted"] = it
        }

        data["userCorrected"] = userCorrected

        correctionType
            ?.takeIf { it.isNotBlank() }
            ?.let {
                data["correctionType"] = it.take(64)
            }

        qualityScore?.let {
            data["qualityScore"] = it.coerceIn(1, 5)
        }

        db.collection("users")
            .document(user.uid)
            .collection("conversations")
            .document(conversationId)
            .collection("turns")
            .document(turnId)
            .set(data)
            .addOnFailureListener {
                Log.e(TAG, "Failed to record completed turn", it)
            }

        return turnId
    }

    /**
     * Update quality metadata for the latest completed turn.
     *
     * Used by History / conversation feedback UI.
     */
    fun updateTurnQuality(
        turnId: String? = currentTurnId,
        responseAccepted: Boolean? = null,
        userCorrected: Boolean? = null,
        correctionType: String? = null,
        qualityScore: Int? = null
    ) {

        if (!canWrite()) return

        val user = auth.currentUser ?: return
        val conversationId = currentConversationId ?: return
        val resolvedTurnId = turnId ?: return

        val updates = hashMapOf<String, Any>()

        responseAccepted?.let {
            updates["responseAccepted"] = it
        }

        userCorrected?.let {
            updates["userCorrected"] = it
        }

        correctionType
            ?.takeIf { it.isNotBlank() }
            ?.let {
                updates["correctionType"] = it
            }

        qualityScore?.let {
            updates["qualityScore"] = it.coerceIn(1, 5)
        }

        if (updates.isEmpty()) return

        updates["feedbackUpdatedAt"] =
            FieldValue.serverTimestamp()

        db.collection("users")
            .document(user.uid)
            .collection("conversations")
            .document(conversationId)
            .collection("turns")
            .document(resolvedTurnId)
            .set(
                updates,
                SetOptions.merge()
            )
            .addOnFailureListener {
                Log.e(TAG, "Failed to update turn quality", it)
            }
    }

    /**
     * Record performance telemetry.
     */
    fun recordLatency(
        metric: String,
        durationMs: Long,
        provider: String? = null
    ) {

        if (!canWrite()) return

        val user = auth.currentUser ?: return

        val data = hashMapOf<String, Any>(
            "metric" to metric,
            "durationMs" to durationMs,
            "createdAt" to FieldValue.serverTimestamp()
        )

        provider
            ?.takeIf { it.isNotBlank() }
            ?.let {
                data["provider"] = it
            }

        db.collection("users")
            .document(user.uid)
            .collection("telemetry")
            .add(data)
            .addOnFailureListener {
                Log.e(TAG, "Failed to record latency", it)
            }
    }

    /**
     * Record a non-sensitive application error.
     *
     * Do not send API keys, raw audio, or stack traces containing secrets.
     */
    fun recordError(
        source: String,
        type: String,
        message: String?
    ) {

        if (!canWrite()) return

        val user = auth.currentUser ?: return

        val safeMessage =
            message
                ?.replace(Regex("(?i)(api[_-]?key|token|authorization)\\s*[:=]\\s*\\S+"), "$1=[REDACTED]")
                ?.take(1000)

        val data = hashMapOf<String, Any>(
            "source" to source,
            "type" to type,
            "createdAt" to FieldValue.serverTimestamp()
        )

        safeMessage
            ?.takeIf { it.isNotBlank() }
            ?.let {
                data["message"] = it
            }

        db.collection("users")
            .document(user.uid)
            .collection("errors")
            .add(data)
            .addOnFailureListener {
                Log.e(TAG, "Failed to record Firebase error", it)
            }
    }

    /**
     * Store explicit user feedback.
     */
    fun recordFeedback(
        rating: Int,
        comment: String? = null
    ) {

        if (!canWrite()) return

        val user = auth.currentUser ?: return

        val safeRating = rating.coerceIn(1, 5)

        val data = hashMapOf<String, Any>(
            "rating" to safeRating,
            "createdAt" to FieldValue.serverTimestamp()
        )

        comment
            ?.takeIf { it.isNotBlank() }
            ?.let {
                data["comment"] = it.take(2000)
            }

        currentConversationId?.let {
            data["conversationId"] = it
        }

        db.collection("users")
            .document(user.uid)
            .collection("feedback")
            .add(data)
            .addOnFailureListener {
                Log.e(TAG, "Failed to record feedback", it)
            }
    }

    fun getUserId(): String? {
        return auth.currentUser?.uid
    }

    fun getConversationId(): String? {
        return currentConversationId
    }

    fun getCurrentTurnId(): String? {
        return currentTurnId
    }

    private fun canWrite(): Boolean {
        return telemetryEnabled && auth.currentUser != null
    }
}
