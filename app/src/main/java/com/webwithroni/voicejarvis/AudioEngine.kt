package com.webwithroni.voicejarvis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class AudioEngine(
    private val onMicChunk: (ByteArray) -> Unit,
    private val onMicAmplitude: (Float) -> Unit,
    private val onPlaybackAmplitude: (Float) -> Unit,
    private val onPlaybackIdle: () -> Unit,
    private val onRecordingError: (String) -> Unit
) {

    companion object {

        private const val TAG =
            "AudioEngine"

        /*
         * Gemini Live input:
         *
         * 16 kHz
         * mono
         * 16-bit PCM
         *
         * 20 ms = 320 samples
         * 320 samples × 2 bytes = 640 bytes
         */
        private const val MIC_SAMPLE_RATE =
            16_000

        private const val MIC_CHANNELS =
            AudioFormat.CHANNEL_IN_MONO

        private const val MIC_ENCODING =
            AudioFormat.ENCODING_PCM_16BIT

        private const val MIC_CHUNK_BYTES =
            640

        /*
         * Gemini native audio output:
         *
         * 24 kHz
         * mono
         * 16-bit PCM
         */
        private const val SPEAKER_SAMPLE_RATE =
            24_000

        private const val SPEAKER_CHANNELS =
            AudioFormat.CHANNEL_OUT_MONO

        private const val SPEAKER_ENCODING =
            AudioFormat.ENCODING_PCM_16BIT

        /*
         * Small bounded queue.
         *
         * Normal playback does not drop audio.
         *
         * Interruption explicitly clears the queue.
         */
        private const val MAX_PLAYBACK_QUEUE =
            48
    }

    private var audioRecord: AudioRecord? =
        null

    private var recordThread: Thread? =
        null


    /*
     * Realtime conversational audio effects.
     *
     * Availability depends on the Android device/OEM.
     */
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    @Volatile
    private var recording =
        false

    /*
     * Public control used by JarvisService.
     *
     * IMPORTANT:
     *
     * This does NOT automatically change when Jarvis
     * starts speaking.
     *
     * Gemini must continue receiving microphone audio
     * so server-side VAD can detect barge-in.
     */
    @Volatile
    var micSendEnabled =
        true

    private var audioTrack: AudioTrack? =
        null

    private var playThread: Thread? =
        null

    @Volatile
    private var playing =
        false

    private val playbackQueue =
        LinkedBlockingQueue<ByteArray>(
            MAX_PLAYBACK_QUEUE
        )

    private val playbackDispatch =
        java.util.concurrent.Executors.newSingleThreadExecutor {
            runnable ->
            Thread(
                runnable,
                "Jarvis-Playback-Dispatch"
            ).apply {
                isDaemon = true
            }
        }


    fun startRecording() {

        if (recording) {
            return
        }

        val minBuffer =
            AudioRecord.getMinBufferSize(
                MIC_SAMPLE_RATE,
                MIC_CHANNELS,
                MIC_ENCODING
            )

        if (
            minBuffer ==
            AudioRecord.ERROR ||
            minBuffer ==
            AudioRecord.ERROR_BAD_VALUE
        ) {

            Log.e(
                TAG,
                "Invalid AudioRecord buffer size: $minBuffer"
            )

            return
        }

        /*
         * Give Android enough internal buffer space,
         * while still reading only 20 ms chunks.
         */
        val recordBufferSize =
            maxOf(
                minBuffer,
                MIC_CHUNK_BYTES * 4
            )

        try {

            audioRecord =
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MIC_SAMPLE_RATE,
                    MIC_CHANNELS,
                    MIC_ENCODING,
                    recordBufferSize
                )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "AudioRecord init failed",
                e
            )

            return
        }

        if (
            audioRecord?.state !=
            AudioRecord.STATE_INITIALIZED
        ) {

            Log.e(
                TAG,
                "AudioRecord failed to initialize"
            )

            audioRecord?.release()

            audioRecord = null

            return
        }
        /*
         * Configure Android conversational audio processing.
         *
         * These effects are optional and device-dependent.
         */

        val audioSessionId =
            audioRecord?.audioSessionId ?: 0

        if (audioSessionId != 0) {

            if (
                AcousticEchoCanceler.isAvailable()
            ) {

                try {

                    echoCanceler =
                        AcousticEchoCanceler.create(
                            audioSessionId
                        )

                    echoCanceler?.enabled = true

                    Log.i(
                        TAG,
                        "AcousticEchoCanceler enabled."
                    )

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "AcousticEchoCanceler failed: ${e.message}"
                    )
                }

            } else {

                Log.i(
                    TAG,
                    "AcousticEchoCanceler unavailable."
                )
            }

            if (
                NoiseSuppressor.isAvailable()
            ) {

                try {

                    noiseSuppressor =
                        NoiseSuppressor.create(
                            audioSessionId
                        )

                    noiseSuppressor?.enabled = true

                    Log.i(
                        TAG,
                        "NoiseSuppressor enabled."
                    )

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "NoiseSuppressor failed: ${e.message}"
                    )
                }

            } else {

                Log.i(
                    TAG,
                    "NoiseSuppressor unavailable."
                )
            }
        }



        recording = true

        micSendEnabled = true

        try {

            audioRecord?.startRecording()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "startRecording failed",
                e
            )

            recording = false

            audioRecord?.release()

            audioRecord = null

            return
        }

        recordThread =
            Thread(
                {



                    /*
                     * Realtime microphone capture.
                     */
                    android.os.Process.setThreadPriority(
                        android.os.Process.THREAD_PRIORITY_URGENT_AUDIO
                    )
val buffer =
                        ByteArray(
                            MIC_CHUNK_BYTES
                        )

                    while (recording) {

                        val recorder =
                            audioRecord
                                ?: break

                        val read =
                            try {

                                recorder.read(
                                    buffer,
                                    0,
                                    buffer.size,
                                    AudioRecord.READ_BLOCKING
                                )

                            } catch (e: Exception) {

                                Log.e(
                                    TAG,
                                    "AudioRecord read failed",
                                    e
                                )

                                break
                            }

                        if (
                            read ==
                            AudioRecord.ERROR_INVALID_OPERATION ||
                            read ==
                            AudioRecord.ERROR_BAD_VALUE
                        ) {

                            continue
                        }
                        if (
                            read ==
                            AudioRecord.ERROR_DEAD_OBJECT
                        ) {

                            Log.e(
                                TAG,
                                "AudioRecord returned ERROR_DEAD_OBJECT."
                            )

                            recording = false

                            onRecordingError(
                                "AudioRecord became unavailable."
                            )

                            break
                        }



                        if (read <= 0) {
                            continue
                        }


                        val chunk =
                            if (
                                read ==
                                buffer.size
                            ) {

                                buffer.copyOf()

                            } else {

                                buffer.copyOf(
                                    read
                                )
                            }

                        /*
                         * Amplitude is UI-only.
                         *
                         * It must NEVER decide whether
                         * Gemini receives audio.
                         */
                        onMicAmplitude(
                            calculateRms(
                                chunk
                            )
                        )

                        if (
                            micSendEnabled &&
                            recording
                        ) {

                            onMicChunk(
                                chunk
                            )
                        }
                    }

                },
                "Jarvis-Microphone"
            )

        recordThread?.start()
    }

    fun stopRecording() {

        recording = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        try {
            recordThread?.join(250)
        } catch (_: Exception) {
        }
        /*
         * Release audio effects before the AudioRecord session.
         */
        try {
            echoCanceler?.enabled = false
        } catch (_: Exception) {
        }

        try {
            echoCanceler?.release()
        } catch (_: Exception) {
        }

        try {
            noiseSuppressor?.enabled = false
        } catch (_: Exception) {
        }

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {
        }

        echoCanceler = null
        noiseSuppressor = null



        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }

        audioRecord = null


        recordThread = null
    }

    fun startPlayback() {

        if (playing) {
            return
        }

        val minBuffer =
            AudioTrack.getMinBufferSize(
                SPEAKER_SAMPLE_RATE,
                SPEAKER_CHANNELS,
                SPEAKER_ENCODING
            )

        if (
            minBuffer ==
            AudioTrack.ERROR ||
            minBuffer ==
            AudioTrack.ERROR_BAD_VALUE
        ) {

            Log.e(
                TAG,
                "Invalid AudioTrack buffer size: $minBuffer"
            )

            return
        }

        val attributes =
            AudioAttributes.Builder()
                .setUsage(
                    AudioAttributes.USAGE_ASSISTANT
                )
                .setContentType(
                    AudioAttributes.CONTENT_TYPE_SPEECH
                )
                .build()

        val format =
            AudioFormat.Builder()
                .setSampleRate(
                    SPEAKER_SAMPLE_RATE
                )
                .setEncoding(
                    SPEAKER_ENCODING
                )
                .setChannelMask(
                    SPEAKER_CHANNELS
                )
                .build()

        val trackBufferSize =
            maxOf(
                minBuffer,
                4096
            )

        try {

            audioTrack =
                AudioTrack(
                    attributes,
                    format,
                    trackBufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

            audioTrack?.play()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "AudioTrack initialization failed",
                e
            )

            audioTrack?.release()

            audioTrack = null

            return
        }

        playing = true

        playThread =
            Thread(
                {



                    /*
                     * Realtime speaker playback.
                     */
                    android.os.Process.setThreadPriority(
                        android.os.Process.THREAD_PRIORITY_URGENT_AUDIO
                    )
while (playing) {

                        val chunk =
                            try {

                                playbackQueue.poll(
                                    100,
                                    TimeUnit.MILLISECONDS
                                )

                            } catch (_: InterruptedException) {

                                null
                            }

                        if (chunk == null) {

                            if (
                                playing &&
                                playbackQueue.isEmpty()
                            ) {

                                onPlaybackIdle()
                            }

                            continue
                        }

                        if (!playing) {
                            break
                        }

                        onPlaybackAmplitude(
                            calculateRms(
                                chunk
                            )
                        )

                        try {

                            var offset =
                                0

                            while (
                                offset <
                                chunk.size &&
                                playing
                            ) {

                                val written =
                                    audioTrack?.write(
                                        chunk,
                                        offset,
                                        chunk.size - offset,
                                        AudioTrack.WRITE_BLOCKING
                                    )
                                        ?: -1

                                if (
                                    written <= 0
                                ) {

                                    Log.w(
                                        TAG,
                                        "AudioTrack wrote $written bytes."
                                    )

                                    break
                                }

                                offset += written
                            }

                        } catch (e: Exception) {

                            Log.e(
                                TAG,
                                "AudioTrack write failed",
                                e
                            )
                        }
                    }

                },
                "Jarvis-Speaker"
            )

        playThread?.start()
    }

    fun enqueuePlayback(
        pcm: ByteArray
    ) {

        if (
            pcm.isEmpty() ||
            !playing
        ) {

            return
        }

        /*
         * IMPORTANT:
         *
         * NEVER block the Gemini WebSocket callback thread.
         *
         * playbackQueue is intentionally bounded and
         * playbackQueue.put() may block when the queue is full.
         *
         * Gemini's handleMessage() runs from the OkHttp
         * WebSocket callback, so blocking here can prevent
         * Gemini from delivering:
         *
         * - interrupted
         * - input transcription
         * - output transcription
         * - turnComplete
         * - generationComplete
         * - goAway
         * - session resumption updates
         *
         * The potentially blocking queue operation therefore
         * runs on a dedicated playback producer thread.
         *
         * Normal playback still NEVER drops PCM.
         *
         * clearPlaybackQueue() remains the explicit discard
         * path for barge-in/interruption.
         */

        val copy =
            pcm.copyOf()

        playbackDispatch.execute {

            try {

                playbackQueue.put(
                    copy
                )

            } catch (_: InterruptedException) {

                Thread.currentThread().interrupt()
            }
        }
    }

    fun clearPlaybackQueue() {

        /*
         * Immediate barge-in flush.
         */
        playbackQueue.clear()

        try {

            audioTrack?.pause()

            audioTrack?.flush()

            audioTrack?.play()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "AudioTrack flush failed: ${e.message}"
            )
        }
    }

    fun stopPlayback() {

        playing = false

        playbackQueue.clear()

        try {
            audioTrack?.stop()
        } catch (_: Exception) {
        }

        try {
            playThread?.join(250)
        } catch (_: Exception) {
        }

        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }

        audioTrack = null

        playThread = null
    }

    fun release() {

        stopRecording()

        stopPlayback()

        /*
         * Stop the playback producer thread.
         */
        playbackDispatch.shutdownNow()
    }

    private fun calculateRms(
        buffer: ByteArray
    ): Float {

        if (buffer.size < 2) {
            return 0f
        }

        var sum =
            0.0

        var i =
            0

        while (
            i + 1 <
            buffer.size
        ) {

            val low =
                buffer[i]
                    .toInt() and 0xFF

            val high =
                buffer[i + 1]
                    .toInt()

            val sample =
                (
                    (high shl 8) or
                        low
                    )
                    .toShort()

            sum +=
                sample.toDouble() *
                    sample.toDouble()

            i += 2
        }

        val samples =
            buffer.size / 2

        if (samples == 0) {
            return 0f
        }

        return (
            sqrt(
                sum / samples
            ) / 32768.0
            )
            .toFloat()
            .coerceIn(
                0f,
                1f
            )
    }
}
