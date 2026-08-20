package com.webwithroni.voicejarvis

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPreferencesTest {

    @Test
    fun modelSelectionPersistsAndFallsBackToDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val defaultModel = ModelPreferences.getSelectedModel(context)
        assertTrue(ModelCatalog.contains(defaultModel))

        val preferred = "models/gemini-2.5-flash-live-preview"
        assertTrue(ModelPreferences.setSelectedModel(context, preferred))
        assertEquals(preferred, ModelPreferences.getSelectedModel(context))

        ModelPreferences.resetToDefault(context)
        assertEquals(ModelCatalog.DEFAULT_MODEL, ModelPreferences.getSelectedModel(context))
    }
}
