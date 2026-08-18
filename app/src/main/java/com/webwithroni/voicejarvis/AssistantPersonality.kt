package com.webwithroni.voicejarvis

/**
 * Defines how JARVIS behaves and communicates.
 *
 * Voice and personality are intentionally separate:
 *
 * Voice       = how JARVIS sounds
 * Personality = how JARVIS behaves
 *
 * The catalog is UI-independent and can therefore be reused by:
 *
 * - onboarding
 * - settings
 * - Gemini system-prompt construction
 * - future personalization features
 */
data class AssistantPersonality(
    val id: String,
    val name: String,
    val description: String,
    val traits: List<String>,
    val previewText: String,
    val systemPrompt: String
)
