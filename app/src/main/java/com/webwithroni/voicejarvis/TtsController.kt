package com.webwithroni.voicejarvis

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsController(
    context: Context,
    private val onSpeakStart: () -> Unit,
    private val onSpeakDone: () -> Unit
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                ready = true
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { onSpeakStart() }
            override fun onDone(utteranceId: String?) { onSpeakDone() }
            override fun onError(utteranceId: String?) { onSpeakDone() }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utterance")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
