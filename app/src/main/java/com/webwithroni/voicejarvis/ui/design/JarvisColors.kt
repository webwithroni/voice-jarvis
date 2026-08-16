package com.webwithroni.voicejarvis.ui.design

import android.graphics.Color

/**
 * Single source of truth for the Jarvis visual language.
 *
 * UI screens should use these tokens instead of defining
 * their own colors.
 */
object JarvisColors {

    // ─────────────────────────────────────────────
    // BACKGROUND
    // ─────────────────────────────────────────────

    val Background = Color.parseColor("#07090D")
    val Surface = Color.parseColor("#0D1118")
    val Elevated = Color.parseColor("#121821")
    val Border = Color.parseColor("#202733")

    // ─────────────────────────────────────────────
    // TEXT
    // ─────────────────────────────────────────────

    val White = Color.parseColor("#F4F7FB")
    val Secondary = Color.parseColor("#8C97A8")
    val Tertiary = Color.parseColor("#566171")

    // ─────────────────────────────────────────────
    // ACCENTS
    // ─────────────────────────────────────────────

    val Cyan = Color.parseColor("#5CE7FF")
    val Blue = Color.parseColor("#4A8DFF")
    val Violet = Color.parseColor("#9B7CFF")
    val Green = Color.parseColor("#54E38E")
    val Orange = Color.parseColor("#FFB86B")
    val Red = Color.parseColor("#FF7181")

    // ─────────────────────────────────────────────
    // ORB
    // ─────────────────────────────────────────────

    val OrbCore = Color.parseColor("#E8FBFF")
    val OrbPrimary = Color.parseColor("#5CE7FF")
    val OrbSecondary = Color.parseColor("#4A8DFF")
    val OrbEnergy = Color.parseColor("#9B7CFF")

    // ─────────────────────────────────────────────
    // STATE COLORS
    // ─────────────────────────────────────────────

    val StateListening = Cyan
    val StateHearing = Blue
    val StateThinking = Violet
    val StateSpeaking = Green
    val StateError = Red
    val StatePaused = Tertiary
    val StateOffline = Orange
}
