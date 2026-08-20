package com.webwithroni.voicejarvis

import android.content.Context

/**
 * Persist the selected Gemini Live model locally.
 */
object ModelPreferences {

    private const val PREFS_NAME = "jarvis_model_preferences"
    private const val KEY_SELECTED_MODEL = "selected_model"

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

    fun getSelectedModel(context: Context): String {
        ensureInitialized(context)

        val saved = preferences.getString(KEY_SELECTED_MODEL, ModelCatalog.DEFAULT_MODEL)

        return if (ModelCatalog.contains(saved)) {
            saved ?: ModelCatalog.DEFAULT_MODEL
        } else {
            ModelCatalog.DEFAULT_MODEL
        }
    }

    fun getSelectedModelInfo(context: Context): ModelCatalog.Model {
        return ModelCatalog.find(getSelectedModel(context))
    }

    fun setSelectedModel(context: Context, modelId: String): Boolean {
        ensureInitialized(context)

        if (!ModelCatalog.contains(modelId)) {
            return false
        }

        preferences.edit()
            .putString(KEY_SELECTED_MODEL, ModelCatalog.find(modelId).id)
            .apply()

        return true
    }

    fun resetToDefault(context: Context) {
        ensureInitialized(context)

        preferences.edit()
            .putString(KEY_SELECTED_MODEL, ModelCatalog.DEFAULT_MODEL)
            .apply()
    }
}
