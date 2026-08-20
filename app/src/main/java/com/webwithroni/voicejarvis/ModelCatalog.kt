package com.webwithroni.voicejarvis

/**
 * Gemini Live model catalog.
 *
 * This keeps the selectable model list independent from the UI and runtime wiring.
 */
object ModelCatalog {

    data class Model(
        val id: String,
        val label: String,
        val description: String
    )

    val all: List<Model> = listOf(
        Model(
            id = "models/gemini-2.5-flash-live-preview",
            label = "Gemini 2.5 Flash Live",
            description = "Fast, conversational, and well-suited for live voice sessions."
        ),
        Model(
            id = "models/gemini-3.1-flash-live-preview",
            label = "Gemini 3.1 Flash Live",
            description = "Latest preview model for live audio interaction."
        ),
        Model(
            id = "models/gemini-2.0-flash-live-001",
            label = "Gemini 2.0 Flash Live",
            description = "Stable live audio model for reliable voice sessions."
        )
    )

    const val DEFAULT_MODEL = "models/gemini-3.1-flash-live-preview"

    fun find(id: String?): Model {
        return all.firstOrNull {
            it.id.equals(id, ignoreCase = true)
        } ?: all.first { it.id == DEFAULT_MODEL }
    }

    fun contains(id: String?): Boolean {
        return all.any { it.id.equals(id, ignoreCase = true) }
    }
}
