package com.webwithroni.voicejarvis

import android.content.Context

/**
 * Persistent voice preferences for Jarvis.
 *
 * Keeps the selected Gemini Live voice locally on the device.
 * This layer intentionally knows nothing about UI or Gemini networking.
 */
object VoicePreferences {

    private const val PREFS_NAME = "jarvis_voice_preferences"
    private const val KEY_SELECTED_VOICE = "selected_voice"

    @Volatile
    private var initialized = false

    private lateinit var preferences: android.content.SharedPreferences

    @Synchronized
    private fun ensureInitialized(context: Context) {
        if (!initialized) {
            preferences = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            initialized = true
        }
    }

    fun getSelectedVoice(context: Context): String {
        ensureInitialized(context)

        val saved = preferences.getString(
            KEY_SELECTED_VOICE,
            VoiceCatalog.DEFAULT_VOICE
        )

        return if (VoiceCatalog.contains(saved)) {
            saved ?: VoiceCatalog.DEFAULT_VOICE
        } else {
            VoiceCatalog.DEFAULT_VOICE
        }
    }

    fun getSelectedVoiceInfo(context: Context): VoiceCatalog.Voice {
        return VoiceCatalog.find(
            getSelectedVoice(context)
        )
    }

    fun setSelectedVoice(
        context: Context,
        voiceId: String
    ): Boolean {
        ensureInitialized(context)

        if (!VoiceCatalog.contains(voiceId)) {
            return false
        }

        preferences.edit()
            .putString(
                KEY_SELECTED_VOICE,
                VoiceCatalog.find(voiceId).id
            )
            .apply()

        return true
    }

    fun resetToDefault(context: Context) {
        ensureInitialized(context)

        preferences.edit()
            .putString(
                KEY_SELECTED_VOICE,
                VoiceCatalog.DEFAULT_VOICE
            )
            .apply()
    }
}
