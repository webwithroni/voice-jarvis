package com.webwithroni.voicejarvis

import android.content.Context
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

object ModelManager {

    private const val MODEL_URL =
        "https://github.com/webwithroni/voice-jarvis/releases/download/models-v1/vosk-model-small-en-in-0.4.zip"
    private const val MODEL_DIR = "vosk-model"

    fun isModelReady(context: Context): Boolean {
        val dir = File(context.filesDir, MODEL_DIR)
        return dir.exists() && dir.list()?.isNotEmpty() == true
    }

    fun downloadAndExtract(context: Context, onProgress: (String) -> Unit, onDone: () -> Unit) {
        Thread {
            try {
                onProgress("Downloading model...")
                val zipFile = File(context.filesDir, "model.zip")
                URL(MODEL_URL).openStream().use { input ->
                    zipFile.outputStream().use { output -> input.copyTo(output) }
                }

                onProgress("Extracting model...")
                val targetDir = File(context.filesDir, MODEL_DIR)
                targetDir.mkdirs()

                ZipInputStream(zipFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(targetDir, entry.name.substringAfter("/"))
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { zis.copyTo(it) }
                        }
                        entry = zis.nextEntry
                    }
                }

                zipFile.delete()
                onProgress("Model ready.")
                onDone()
            } catch (e: Exception) {
                onProgress("Error: ${e.message}")
            }
        }.start()
    }

    fun getModelPath(context: Context): String {
        return File(context.filesDir, MODEL_DIR).absolutePath
    }
}
