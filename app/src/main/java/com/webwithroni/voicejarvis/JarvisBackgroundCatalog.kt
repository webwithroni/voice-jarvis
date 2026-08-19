package com.webwithroni.voicejarvis

import androidx.annotation.DrawableRes

/**
 * Canonical catalogue for the JARVIS cinematic background system.
 *
 * Backgrounds are semantic system states, not screen-specific assets.
 *
 * Keep all background mapping in this single source of truth.
 */
enum class JarvisBackgroundId {

    ORIGIN,

    INTELLIGENCE_FIELD,

    CONNECTION,

    RESPONSE,

    SYSTEM_CORE,

    COGNITIVE_FLOW,

    MEMORY_FIELD,

    SYNTHESIS_FIELD,

    INTENT_FIELD,

    EXECUTION_FIELD,

    COMMUNICATION_FIELD,

    UNIFIED_INTELLIGENCE
}

data class JarvisBackground(
    val id: JarvisBackgroundId,

    @DrawableRes
    val drawableRes: Int,

    val name: String,

    val semanticRole: String
)

object JarvisBackgroundCatalog {

    val all: List<JarvisBackground> =
        listOf(

            JarvisBackground(
                id =
                    JarvisBackgroundId.ORIGIN,

                drawableRes =
                    R.drawable.jarvis_bg_01_origin,

                name =
                    "Origin",

                semanticRole =
                    "Identity and beginning."
            ),

            JarvisBackground(
                id =
                    JarvisBackgroundId.INTELLIGENCE_FIELD,

                drawableRes =
                    R.drawable.jarvis_bg_02_the_intelligence_field,

                name =
                    "The Intelligence Field",

                semanticRole =
                    "Capabilities and intelligence."
            ),

            JarvisBackground(
                id =
                    JarvisBackgroundId.CONNECTION,

                drawableRes =
                    R.drawable.jarvis_bg_03_the_connection,

                name =
                    "The Connection",

                semanticRole =
                    "Voice and connectivity."
            ),

            JarvisBackground(
                id =
                    JarvisBackgroundId.RESPONSE,

                drawableRes =
                    R.drawable.jarvis_bg_04_the_response,

                name =
                    "The Response",

                semanticRole =
                    "Interaction and feedback."
            ),

            JarvisBackground(
                id =
                    JarvisBackgroundId.SYSTEM_CORE,

                drawableRes =
                    R.drawable.jarvis_bg_05_the_system_core,

                name =
                    "The System Core",

                semanticRole =
                    "Central JARVIS identity."
            ),

            JarvisBackground(
                id =
                    JarvisBackgroundId.COGNITIVE_FLOW,

                drawableRes =
                    R.drawable.jarvis_bg_06_cognitive_flow,

                name =
                    "Cognitive Flow",

                semanticRole =
                    "Thinking and reasoning."
            ),

            JarvisBackground(
                id =
                    JarvisBackgroundId.MEMORY_FIELD,

                drawableRes =
                    R.drawable.jarvis_bg_07_memory_field,

                name =
                    "Memory Field",

                semanticRole =
                    "Memory and personalization."
            ),

            JarvisBackground(
                id =
                    JarvisBackgroundId.SYNTHESIS_FIELD,

                drawableRes =
                    R.drawable.jarvis_bg_08_synthesis_field,

                name =
                    "Synthesis Field",

                semanticRole =
                    "Combining context and knowledge."
            ),

            JarvisBackground(
                id =
                    JarvisBackgroundId.INTENT_FIELD,

                drawableRes =
                    R.drawable.jarvis_bg_09_intent_field,

                name =
                    "Intent Field",

                semanticRole =
                    "Understanding user intent."
            ),

            JarvisBackground(
                id =
                    JarvisBackgroundId.EXECUTION_FIELD,

                drawableRes =
                    R.drawable.jarvis_bg_10_execution_field,

                name =
                    "Execution Field",

                semanticRole =
                    "Actions and tools."
            ),

            JarvisBackground(
                id =
                    JarvisBackgroundId.COMMUNICATION_FIELD,

                drawableRes =
                    R.drawable.jarvis_bg_11_communication_field,

                name =
                    "Communication Field",

                semanticRole =
                    "Messaging and communication."
            ),

            JarvisBackground(
                id =
                    JarvisBackgroundId.UNIFIED_INTELLIGENCE,

                drawableRes =
                    R.drawable.jarvis_bg_12_unified_intelligence,

                name =
                    "Unified Intelligence",

                semanticRole =
                    "Complete JARVIS system state."
            )
        )

    fun find(
        id: JarvisBackgroundId
    ): JarvisBackground {

        return all.first {
            it.id == id
        }
    }

    fun contains(
        id: JarvisBackgroundId
    ): Boolean {

        return all.any {
            it.id == id
        }
    }
}
