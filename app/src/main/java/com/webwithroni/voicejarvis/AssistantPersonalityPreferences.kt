package com.webwithroni.voicejarvis

import android.content.Context

/**
 * Persistent JARVIS personality selection.
 *
 * Personality selection is scoped to the authenticated Firebase user.
 */
object AssistantPersonalityPreferences {

    private const val PREFS_NAME =
        "jarvis_personality_preferences"

    private const val KEY_SELECTED_PERSONALITY =
        "selected_personality"

    private fun preferenceKey(
        uid: String
    ): String {

        return "${KEY_SELECTED_PERSONALITY}_${uid}"
    }

    fun getSelectedPersonality(
        context: Context
    ): String {

        val uid =
            AuthManager.userId()
                ?: return AssistantPersonalityCatalog.DEFAULT_PERSONALITY

        val preferences =
            context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

        val saved =
            preferences.getString(
                preferenceKey(uid),
                AssistantPersonalityCatalog.DEFAULT_PERSONALITY
            )

        return if (
            AssistantPersonalityCatalog.contains(
                saved
            )
        ) {
            saved
                ?: AssistantPersonalityCatalog.DEFAULT_PERSONALITY
        } else {
            AssistantPersonalityCatalog.DEFAULT_PERSONALITY
        }
    }

    fun getSelectedPersonalityInfo(
        context: Context
    ): AssistantPersonality {

        return AssistantPersonalityCatalog.find(
            getSelectedPersonality(
                context
            )
        )
    }

    fun setSelectedPersonality(
        context: Context,
        personalityId: String
    ): Boolean {

        val uid =
            AuthManager.userId()
                ?: return false

        if (
            !AssistantPersonalityCatalog.contains(
                personalityId
            )
        ) {
            return false
        }

        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                preferenceKey(uid),
                AssistantPersonalityCatalog.find(
                    personalityId
                ).id
            )
            .apply()

        return true
    }

    fun resetToDefault(
        context: Context
    ) {

        val uid =
            AuthManager.userId()
                ?: return

        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                preferenceKey(uid),
                AssistantPersonalityCatalog.DEFAULT_PERSONALITY
            )
            .apply()
    }
}
