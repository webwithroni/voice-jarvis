package com.webwithroni.voicejarvis

/**
 * Gemini Live voice catalogue.
 *
 * These are the currently documented Gemini prebuilt voices.
 *
 * Keep this file independent from the realtime client so the UI,
 * preferences and runtime all use the same source of truth.
 */
object VoiceCatalog {

    data class Voice(
        val id: String,
        val name: String,
        val character: String
    )

    val all: List<Voice> = listOf(
        Voice("Zephyr", "Zephyr", "Bright"),
        Voice("Puck", "Puck", "Upbeat"),
        Voice("Charon", "Charon", "Informative"),
        Voice("Kore", "Kore", "Firm"),
        Voice("Fenrir", "Fenrir", "Excitable"),
        Voice("Leda", "Leda", "Youthful"),
        Voice("Orus", "Orus", "Firm"),
        Voice("Aoede", "Aoede", "Breezy"),
        Voice("Callirrhoe", "Callirrhoe", "Easy-going"),
        Voice("Autonoe", "Autonoe", "Bright"),
        Voice("Enceladus", "Enceladus", "Breathy"),
        Voice("Iapetus", "Iapetus", "Clear"),
        Voice("Umbriel", "Umbriel", "Easy-going"),
        Voice("Algieba", "Algieba", "Smooth"),
        Voice("Despina", "Despina", "Smooth"),
        Voice("Erinome", "Erinome", "Clear"),
        Voice("Algenib", "Algenib", "Gravelly"),
        Voice("Rasalgethi", "Rasalgethi", "Informative"),
        Voice("Laomedeia", "Laomedeia", "Upbeat"),
        Voice("Achernar", "Achernar", "Soft"),
        Voice("Alnilam", "Alnilam", "Firm"),
        Voice("Schedar", "Schedar", "Even"),
        Voice("Gacrux", "Gacrux", "Mature"),
        Voice("Pulcherrima", "Pulcherrima", "Forward"),
        Voice("Achird", "Achird", "Friendly"),
        Voice("Zubenelgenubi", "Zubenelgenubi", "Casual"),
        Voice("Vindemiatrix", "Vindemiatrix", "Gentle"),
        Voice("Sadachbia", "Sadachbia", "Lively"),
        Voice("Sadaltager", "Sadaltager", "Knowledgeable"),
        Voice("Sulafat", "Sulafat", "Warm")
    )

    const val DEFAULT_VOICE = "Aoede"

    fun find(id: String?): Voice {
        return all.firstOrNull {
            it.id.equals(id, ignoreCase = true)
        } ?: all.first {
            it.id == DEFAULT_VOICE
        }
    }

    fun contains(id: String?): Boolean {
        return all.any {
            it.id.equals(id, ignoreCase = true)
        }
    }
}
