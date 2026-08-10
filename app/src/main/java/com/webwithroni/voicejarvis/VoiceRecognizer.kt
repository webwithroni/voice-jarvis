package com.webwithroni.voicejarvis

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

class VoiceRecognizer(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit
) {
    private var model: Model? = null
    private var speechService: SpeechService? = null

    fun start() {
        val modelPath = ModelManager.getModelPath(context)
        model = Model(modelPath)
        val recognizer = Recognizer(model, 16000.0f)

        speechService = SpeechService(recognizer, 16000.0f)
        speechService?.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                hypothesis?.let { onPartialResult(it) }
            }

            override fun onResult(hypothesis: String?) {
                hypothesis?.let { onFinalResult(it) }
            }

            override fun onFinalResult(hypothesis: String?) {
                hypothesis?.let { onFinalResult(it) }
            }

            override fun onError(exception: Exception?) {
                Log.e("VoiceRecognizer", "Error: ${exception?.message}")
            }

            override fun onTimeout() {
                Log.d("VoiceRecognizer", "Timeout")
            }
        })
    }

    fun stop() {
        speechService?.stop()
        speechService?.shutdown()
        model = null
    }
}
