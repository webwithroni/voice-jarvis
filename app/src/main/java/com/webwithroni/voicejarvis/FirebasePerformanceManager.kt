package com.webwithroni.voicejarvis

import android.util.Log
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import java.util.concurrent.atomic.AtomicLong

/**
 * Central Firebase Performance gateway.
 *
 * Only timing/performance metadata is recorded.
 *
 * Never record:
 * - transcripts
 * - raw audio
 * - API keys
 * - tokens
 * - payment data
 * - private screen content
 *
 * Lifecycle rule:
 * Every start call returns/owns an explicit trace handle.
 * This prevents stale reconnect callbacks from closing
 * a newer trace.
 */
object FirebasePerformanceManager {

    private const val TAG = "VJPerformance"

    private val performance:
        FirebasePerformance by lazy {
            FirebasePerformance.getInstance()
        }

    @Volatile
    private var enabled = true

    private val nextId =
        AtomicLong(0L)

    private data class TraceHandle(
        val id: String,
        val trace: Trace
    )

    @Volatile
    private var geminiConnection:
        TraceHandle? = null

    @Volatile
    private var voiceTurn:
        TraceHandle? = null

    private val toolTraces =
        mutableMapOf<String, TraceHandle>()

    fun setEnabled(
        enabled: Boolean
    ) {
        this.enabled = enabled

        try {
            performance.isPerformanceCollectionEnabled =
                enabled
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Unable to update Performance collection state.",
                e
            )
        }

        if (!enabled) {
            close()
        }
    }

    fun isEnabled(): Boolean =
        enabled

    fun initialize() {
        try {
            performance.isPerformanceCollectionEnabled =
                enabled

            Log.d(
                TAG,
                "Firebase Performance initialized."
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Performance initialization failed.",
                e
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * GEMINI CONNECTION
     * ---------------------------------------------------------
     */

    fun startGeminiConnection(): String? {

        if (!enabled) {
            return null
        }

        return try {

            finishGeminiConnection(
                handleId = null,
                success = false
            )

            val handle =
                createTrace(
                    traceName =
                        "gemini_connection"
                )

            geminiConnection =
                handle

            handle.id

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to start Gemini trace.",
                e
            )

            null
        }
    }

    fun finishGeminiConnection(
        handleId: String?,
        success: Boolean
    ) {

        val current =
            geminiConnection
                ?: return

        if (
            handleId != null &&
            current.id != handleId
        ) {
            return
        }

        geminiConnection = null

        finishTrace(
            current.trace,
            success = success
        )
    }

    /*
     * ---------------------------------------------------------
     * VOICE TURN
     * ---------------------------------------------------------
     */

    fun startVoiceTurn(): String? {

        if (!enabled) {
            return null
        }

        return try {

            finishVoiceTurn(
                handleId = null
            )

            val handle =
                createTrace(
                    traceName =
                        "jarvis_voice_turn"
                )

            voiceTurn =
                handle

            handle.id

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to start voice trace.",
                e
            )

            null
        }
    }

    fun setVoiceTurnMetric(
        handleId: String?,
        name: String,
        value: Long
    ) {

        val current =
            voiceTurn
                ?: return

        if (
            handleId != null &&
            current.id != handleId
        ) {
            return
        }

        try {

            current.trace.putMetric(
                name,
                value.coerceAtLeast(0L)
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to write voice metric.",
                e
            )
        }
    }

    fun setVoiceTurnAttribute(
        handleId: String?,
        name: String,
        value: String
    ) {

        val current =
            voiceTurn
                ?: return

        if (
            handleId != null &&
            current.id != handleId
        ) {
            return
        }

        try {

            current.trace.putAttribute(
                name,
                value.take(100)
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to write voice attribute.",
                e
            )
        }
    }

    fun finishVoiceTurn(
        handleId: String?
    ) {

        val current =
            voiceTurn
                ?: return

        if (
            handleId != null &&
            current.id != handleId
        ) {
            return
        }

        voiceTurn = null

        finishTrace(
            current.trace
        )
    }

    /*
     * ---------------------------------------------------------
     * TOOL EXECUTION
     * ---------------------------------------------------------
     */

    fun startTool(
        tool: String
    ): String? {

        if (!enabled) {
            return null
        }

        return try {

            val normalizedTool =
                tool
                    .trim()
                    .lowercase()
                    .replace(
                        Regex("[^a-z0-9_]+"),
                        "_"
                    )
                    .take(40)

            val handle =
                createTrace(
                    traceName =
                        "tool_execution"
                )

            handle.trace.putAttribute(
                "tool",
                normalizedTool
            )

            synchronized(toolTraces) {
                toolTraces[handle.id] =
                    handle
            }

            handle.id

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to start tool performance trace.",
                e
            )

            null
        }
    }

    fun finishTool(
        traceId: String?,
        success: Boolean
    ) {

        if (traceId == null) {
            return
        }

        val handle =
            synchronized(toolTraces) {
                toolTraces.remove(
                    traceId
                )
            }
                ?: return

        finishTrace(
            handle.trace,
            success = success
        )
    }

    /*
     * ---------------------------------------------------------
     * CLEANUP
     * ---------------------------------------------------------
     */

    fun close() {

        val currentGemini =
            geminiConnection

        geminiConnection = null

        currentGemini?.let {
            finishTrace(
                it.trace,
                success = false
            )
        }

        val currentVoice =
            voiceTurn

        voiceTurn = null

        currentVoice?.let {
            finishTrace(
                it.trace
            )
        }

        synchronized(toolTraces) {

            val traces =
                toolTraces.values.toList()

            toolTraces.clear()

            traces.forEach {
                finishTrace(
                    it.trace
                )
            }
        }
    }

    /*
     * ---------------------------------------------------------
     * INTERNAL
     * ---------------------------------------------------------
     */

    private fun createTrace(
        traceName: String
    ): TraceHandle {

        val id =
            "$traceName-${nextId.incrementAndGet()}"

        val trace =
            performance.newTrace(
                traceName
            )

        trace.start()

        return TraceHandle(
            id = id,
            trace = trace
        )
    }

    private fun finishTrace(
        trace: Trace,
        success: Boolean? = null
    ) {

        try {

            success?.let {
                trace.putAttribute(
                    "success",
                    it.toString()
                )
            }

            trace.stop()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to finish Performance trace.",
                e
            )
        }
    }
}
