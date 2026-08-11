package com.webwithroni.voicejarvis

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashLogger {
    fun install(context: Context) {
        val logFile = File(context.filesDir, "crash_log.txt")
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            logFile.writeText(sw.toString())
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    fun readLog(context: Context): String {
        val logFile = File(context.filesDir, "crash_log.txt")
        return if (logFile.exists()) logFile.readText() else "No crash log found."
    }
}
