package com.webwithroni.voicejarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashLogger {

    private const val FILE_NAME = "crash_log.txt"

    fun install(context: Context) {
        val logFile = File(context.filesDir, FILE_NAME)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->

            try {
                val sw = StringWriter()
                val writer = PrintWriter(sw)

                writer.println("VOICE JARVIS CRASH REPORT")
                writer.println("========================")
                writer.println()
                writer.println("Thread: ${thread.name}")
                writer.println("Thread ID: ${thread.id}")
                writer.println()
                writer.println("Exception:")
                throwable.printStackTrace(writer)

                writer.println()
                writer.println("CAUSE:")
                throwable.cause?.printStackTrace(writer)

                writer.flush()

                logFile.writeText(sw.toString())

            } catch (_: Exception) {
                // Crash logging must never create another crash.
            }

            android.os.Process.killProcess(
                android.os.Process.myPid()
            )
        }
    }

    fun hasCrashLog(context: Context): Boolean {
        return File(context.filesDir, FILE_NAME).exists()
    }

    fun readLog(context: Context): String {
        val logFile = File(context.filesDir, FILE_NAME)

        return if (logFile.exists()) {
            logFile.readText()
        } else {
            "No crash log found."
        }
    }

    fun clearLog(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {
        }
    }

    fun shareCrashLog(context: Context) {

        val logFile = File(context.filesDir, FILE_NAME)

        if (!logFile.exists()) {
            return
        }

        try {

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Voice Jarvis Crash Report"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                Intent.createChooser(
                    intent,
                    "Send Voice Jarvis crash report"
                )
            )

        } catch (_: Exception) {
        }
    }
}
