package com.webwithroni.voicejarvis

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

    /**
     * Initialize Firebase and establish an anonymous identity.
     */
    fun initialize(
        onComplete: ((Boolean) -> Unit)? = null
    ) {
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

        if (!enabled) {
            currentConversationId = null
        }
    }

    fun isTelemetryEnabled(): Boolean {
        return telemetryEnabled
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

    private fun canWrite(): Boolean {
        return telemetryEnabled && auth.currentUser != null
    }
}
