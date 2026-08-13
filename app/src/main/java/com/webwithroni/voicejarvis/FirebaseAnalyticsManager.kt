package com.webwithroni.voicejarvis

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Central Firebase Analytics gateway for Voice Jarvis.
 *
 * Analytics is intentionally separate from FirebaseManager:
 *
 * FirebaseManager:
 * - conversations
 * - turns
 * - telemetry documents
 * - errors
 * - feedback
 *
 * FirebaseAnalyticsManager:
 * - product usage
 * - reliability events
 * - feature usage
 * - non-sensitive performance metadata
 *
 * NEVER send:
 * - raw microphone/audio data
 * - API keys
 * - access tokens
 * - passwords
 * - OTP/PIN
 * - payment details
 * - unrestricted screen text
 * - full private transcripts
 */
object FirebaseAnalyticsManager {

    private const val TAG = "VJAnalytics"

    private var analytics:
        FirebaseAnalytics? = null

    @Volatile
    private var enabled = true

    fun initialize(
        context: Context
    ) {
        try {
            analytics =
                FirebaseAnalytics.getInstance(
                    context.applicationContext
                )

            analytics?.setAnalyticsCollectionEnabled(
                enabled
            )

            Log.d(
                TAG,
                "Firebase Analytics initialized."
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Analytics initialization failed.",
                e
            )
        }
    }

    fun setEnabled(
        enabled: Boolean
    ) {
        this.enabled = enabled

        analytics
            ?.setAnalyticsCollectionEnabled(
                enabled
            )
    }

    fun isEnabled(): Boolean =
        enabled

    /*
     * ---------------------------------------------------------
     * SESSION
     * ---------------------------------------------------------
     */

    fun sessionStarted() {
        log(
            "jarvis_session_started"
        )
    }

    fun sessionEnded(
        durationMs: Long? = null
    ) {
        log(
            "jarvis_session_ended",
            mapOf(
                "duration_ms" to durationMs
            )
        )
    }

    /*
     * ---------------------------------------------------------
     * VOICE
     * ---------------------------------------------------------
     */

    fun voiceTurnStarted() {
        log(
            "voice_turn_started"
        )
    }

    fun voiceTurnCompleted(
        durationMs: Long? = null,
        firstResponseLatencyMs: Long? = null,
        provider: String? = null,
        interrupted: Boolean = false
    ) {
        log(
            "voice_turn_completed",
            mapOf(
                "duration_ms" to durationMs,
                "first_response_latency_ms" to
                    firstResponseLatencyMs,
                "provider" to provider,
                "interrupted" to interrupted
            )
        )
    }

    fun voiceInterrupted() {
        log(
            "voice_interrupted"
        )
    }

    /*
     * ---------------------------------------------------------
     * TOOL EXECUTION
     * ---------------------------------------------------------
     */

    fun toolStarted(
        tool: String
    ) {
        log(
            "tool_started",
            mapOf(
                "tool" to normalizeName(tool)
            )
        )
    }

    fun toolCompleted(
        tool: String,
        durationMs: Long? = null
    ) {
        log(
            "tool_completed",
            mapOf(
                "tool" to normalizeName(tool),
                "duration_ms" to durationMs
            )
        )
    }

    fun toolFailed(
        tool: String
    ) {
        log(
            "tool_failed",
            mapOf(
                "tool" to normalizeName(tool)
            )
        )
    }

    /*
     * ---------------------------------------------------------
     * RESEARCH
     * ---------------------------------------------------------
     */

    fun researchStarted() {
        log(
            "deep_research_started"
        )
    }

    fun researchCompleted(
        provider: String? = null,
        sourceCount: Int? = null,
        durationMs: Long? = null
    ) {
        log(
            "deep_research_completed",
            mapOf(
                "provider" to provider,
                "source_count" to sourceCount,
                "duration_ms" to durationMs
            )
        )
    }

    fun researchFailed(
        provider: String? = null
    ) {
        log(
            "deep_research_failed",
            mapOf(
                "provider" to provider
            )
        )
    }

    /*
     * ---------------------------------------------------------
     * DEVICE / SCREEN ACTIONS
     * ---------------------------------------------------------
     */

    fun actionStarted(
        action: String,
        riskLevel: String? = null
    ) {
        log(
            "device_action_started",
            mapOf(
                "action" to normalizeName(action),
                "risk_level" to riskLevel
            )
        )
    }

    fun actionVerified(
        action: String,
        method: String? = null,
        durationMs: Long? = null
    ) {
        log(
            "device_action_verified",
            mapOf(
                "action" to normalizeName(action),
                "method" to method,
                "duration_ms" to durationMs
            )
        )
    }

    fun actionFailed(
        action: String,
        reason: String? = null
    ) {
        log(
            "device_action_failed",
            mapOf(
                "action" to normalizeName(action),
                "reason" to sanitizeValue(reason)
            )
        )
    }

    fun actionRecovered(
        action: String,
        attempt: Int
    ) {
        log(
            "device_action_recovered",
            mapOf(
                "action" to normalizeName(action),
                "attempt" to attempt
                    .coerceAtLeast(1)
            )
        )
    }

    /*
     * ---------------------------------------------------------
     * PERMISSIONS
     * ---------------------------------------------------------
     */

    fun permissionRequested(
        permissionType: String
    ) {
        log(
            "permission_requested",
            mapOf(
                "permission_type" to
                    normalizeName(permissionType)
            )
        )
    }

    fun permissionResult(
        permissionType: String,
        granted: Boolean
    ) {
        log(
            "permission_result",
            mapOf(
                "permission_type" to
                    normalizeName(permissionType),
                "granted" to granted
            )
        )
    }

    /*
     * ---------------------------------------------------------
     * APP / FEATURE USAGE
     * ---------------------------------------------------------
     */

    fun featureUsed(
        feature: String
    ) {
        log(
            "feature_used",
            mapOf(
                "feature" to normalizeName(feature)
            )
        )
    }

    fun settingsChanged(
        setting: String
    ) {
        log(
            "settings_changed",
            mapOf(
                "setting" to normalizeName(setting)
            )
        )
    }

    /*
     * ---------------------------------------------------------
     * ERROR
     * ---------------------------------------------------------
     */

    fun appError(
        source: String,
        type: String
    ) {
        log(
            "jarvis_error",
            mapOf(
                "source" to normalizeName(source),
                "error_type" to
                    normalizeName(type)
            )
        )
    }

    /*
     * ---------------------------------------------------------
     * LOW-LEVEL EVENT GATEWAY
     * ---------------------------------------------------------
     */

    private fun log(
        event: String,
        params: Map<String, Any?> = emptyMap()
    ) {
        if (!enabled) {
            return
        }

        val instance =
            analytics
                ?: return

        try {

            val bundle =
                Bundle()

            params.forEach { (key, value) ->

                when (value) {

                    is String ->
                        bundle.putString(
                            sanitizeKey(key),
                            sanitizeValue(value)
                        )

                    is Int ->
                        bundle.putLong(
                            sanitizeKey(key),
                            value.toLong()
                        )

                    is Long ->
                        bundle.putLong(
                            sanitizeKey(key),
                            value
                        )

                    is Boolean ->
                        bundle.putBoolean(
                            sanitizeKey(key),
                            value
                        )

                    is Double ->
                        bundle.putDouble(
                            sanitizeKey(key),
                            value
                        )

                    is Float ->
                        bundle.putDouble(
                            sanitizeKey(key),
                            value.toDouble()
                        )
                }
            }

            instance.logEvent(
                sanitizeEventName(event),
                bundle
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Analytics event failed: $event",
                e
            )
        }
    }

    private fun normalizeName(
        value: String?
    ): String? {
        return value
            ?.trim()
            ?.lowercase()
            ?.replace(
                Regex("[^a-z0-9_]+"),
                "_"
            )
            ?.trim('_')
            ?.take(40)
            ?.takeIf {
                it.isNotBlank()
            }
    }

    private fun sanitizeValue(
        value: String?
    ): String? {
        return value
            ?.replace(
                Regex(
                    "(?i)(api[_-]?key|token|authorization|password|otp|pin)\\s*[:=]\\s*\\S+"
                ),
                "$1=[REDACTED]"
            )
            ?.take(100)
            ?.takeIf {
                it.isNotBlank()
            }
    }

    private fun sanitizeKey(
        value: String
    ): String {
        return value
            .lowercase()
            .replace(
                Regex("[^a-z0-9_]+"),
                "_"
            )
            .take(40)
    }

    private fun sanitizeEventName(
        value: String
    ): String {
        return value
            .lowercase()
            .replace(
                Regex("[^a-z0-9_]+"),
                "_"
            )
            .take(40)
    }
}
