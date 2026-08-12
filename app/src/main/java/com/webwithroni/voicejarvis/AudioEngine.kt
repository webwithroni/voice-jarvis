package com.webwithroni.voicejarvis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class AudioEngine(
    private val onMicChunk: (ByteArray) -> Unit,
    private val onMicAmplitude: (Float) -> Unit,
    private val onPlaybackAmplitude: (Float) -> Unit,
    private val onPlaybackIdle: () -> Unit
) {

    companion object {

        private const val TAG = "AudioEngine"

        /*
         * Gemini Live:
         *
         * 16 kHz
         * mono
         * 16-bit PCM
         *
         * 20 ms = 640 bytes
         */
        private const val MIC_SAMPLE_RATE = 16_000
        private const val MIC_CHANNELS =
            AudioFormat.CHANNEL_IN_MONO
        private const val MIC_ENCODING =
            AudioFormat.ENCODING_PCM_16BIT

        private const val MIC_CHUNK_BYTES = 640

        /*
         * Gemini Live output:
         * 24 kHz mono 16-bit PCM.
         */
        private const val SPEAKER_SAMPLE_RATE = 24_000
        private const val SPEAKER_CHANNELS =
            AudioFormat.CHANNEL_OUT_MONO
        private const val SPEAKER_ENCODING =
            AudioFormat.ENCODING_PCM_16BIT

        /*
         * Keep the playback queue small.
         *
         * A huge queue creates audible latency when the
         * user interrupts Jarvis.
         */
        private const val MAX_PLAYBACK_QUEUE = 12
    }

    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null

    @Volatile
    private var recording = false

    @Volatile
    var micSendEnabled = true

    private var audioTrack: AudioTrack? = null
    private var playThread: Thread? = null

    @Volatile
    private var playing = false

    private val playbackQueue =
        LinkedBlockingQueue<ByteArray>(
            MAX_PLAYBACK_QUEUE
        )

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
         * We need enough internal room for Android,
         * but we deliberately READ only 20 ms at a time.
         */
        val recordBufferSize =
            maxOf(
                minBuffer,
                MIC_CHUNK_BYTES * 4
            )

        try {

            audioRecord =
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
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

                        if (read ==
                            AudioRecord.ERROR_INVALID_OPERATION ||
                            read ==
                            AudioRecord.ERROR_BAD_VALUE
                        ) {

                            continue
                        }

                        if (read <= 0) {
                            continue
                        }

                        /*
                         * Always send exactly the bytes
                         * that were actually captured.
                         */
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
                         * It must NOT decide whether audio
                         * gets sent to Gemini.
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

                        if (
                            chunk == null
                        ) {

                            if (playing) {
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

                            var offset = 0

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
                                    ) ?: -1

                                if (written <= 0) {
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

        /*
         * Never allow the playback queue to grow without
         * bounds. Large queues make interruption feel slow.
         */
        if (
            playbackQueue.remainingCapacity() == 0
        ) {

            /*
             * Drop the oldest chunk rather than allowing
             * latency to accumulate.
             */
            playbackQueue.poll()
        }

        playbackQueue.offer(
            pcm.copyOf()
        )
    }

    fun clearPlaybackQueue() {

        playbackQueue.clear()

        try {

            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()

        } catch (_: Exception) {
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
    }

    private fun calculateRms(
        buffer: ByteArray
    ): Float {

        if (buffer.size < 2) {
            return 0f
        }

        var sum = 0.0
        var i = 0

        while (
            i + 1 <
            buffer.size
        ) {

            val low =
                buffer[i].toInt() and 0xFF

            val high =
                buffer[i + 1].toInt()

            val sample =
                ((high shl 8) or low)
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
