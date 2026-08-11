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
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStep: (String) -> Unit
) {
    private var model: Model? = null
    private var speechService: SpeechService? = null

    fun start() {
        try {
            onStep("Step 1: Getting model path...")
            val modelPath = ModelManager.getModelPath(context)

            onStep("Step 2: Loading Model(path)...")
            model = Model(modelPath)

            onStep("Step 3: Model loaded OK. Creating Recognizer...")
            val recognizer = Recognizer(model, 16000.0f)

            onStep("Step 4: Recognizer created OK. Creating SpeechService...")
            speechService = SpeechService(recognizer, 16000.0f)

            onStep("Step 5: SpeechService created OK. Starting listening...")
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
                    val msg = "Recognizer error: ${exception?.javaClass?.simpleName}: ${exception?.message}"
                    Log.e("VoiceRecognizer", msg, exception)
                    onError(msg)
                }

                override fun onTimeout() {
                    Log.d("VoiceRecognizer", "Timeout")
                }
            })

            onStep("Step 6: startListening() called OK. All steps passed.")
        } catch (e: Throwable) {
            val msg = "Init crash: ${e.javaClass.simpleName}: ${e.message}\n${e.stackTrace.take(5).joinToString("\n")}"
            Log.e("VoiceRecognizer", msg, e)
            onError(msg)
        }
    }

    fun stop() {
        speechService?.stop()
        speechService?.shutdown()
        model = null
    }
}
