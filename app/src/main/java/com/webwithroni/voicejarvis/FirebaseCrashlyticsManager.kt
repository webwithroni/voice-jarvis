package com.webwithroni.voicejarvis

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics

/**
 * Central Crashlytics gateway for Voice Jarvis.
 *
 * Rules:
 * - Never send raw microphone audio.
 * - Never send API keys.
 * - Never send authentication secrets.
 * - Prefer structured context over arbitrary logs.
 */
object FirebaseCrashlyticsManager {

    private const val TAG = "VJCrashlytics"

    private val crashlytics by lazy {
        Firebase.crashlytics
    }

    fun initialize() {
        try {
            crashlytics.setCustomKey(
                "app_version",
                BuildConfig.VERSION_NAME
            )

            crashlytics.setCustomKey(
                "platform",
                "android"
            )

            Log.d(
                TAG,
                "Crashlytics initialized."
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Crashlytics initialization failed.",
                e
            )
        }
    }

    fun setJarvisState(
        state: String
    ) {
        try {
            crashlytics.setCustomKey(
                "jarvis_state",
                state
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Unable to update Crashlytics state.",
                e
            )
        }
    }

    fun setProvider(
        provider: String
    ) {
        try {
            crashlytics.setCustomKey(
                "ai_provider",
                provider
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Unable to update provider context.",
                e
            )
        }
    }

    fun setTool(
        tool: String
    ) {
        try {
            crashlytics.setCustomKey(
                "active_tool",
                tool
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Unable to update tool context.",
                e
            )
        }
    }

    fun recordException(
        throwable: Throwable,
        context: String? = null
    ) {
        try {
            context?.let {
                crashlytics.setCustomKey(
                    "error_context",
                    it
                )
            }

            crashlytics.recordException(
                throwable
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Unable to record exception.",
                e
            )
        }
    }

    fun log(
        message: String
    ) {
        try {
            crashlytics.log(message)
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Unable to write Crashlytics log.",
                e
            )
        }
    }

    fun setCollectionEnabled(
        enabled: Boolean
    ) {
        try {
            crashlytics.isCrashlyticsCollectionEnabled =
                enabled
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Unable to change Crashlytics collection state.",
                e
            )
        }
    }
}
