package com.webwithroni.voicejarvis

import android.media.*
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
    private val micSampleRate = 16000
    private val speakerSampleRate = 24000

    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    @Volatile private var recording = false
    @Volatile var micSendEnabled = true

    private var audioTrack: AudioTrack? = null
    private var playThread: Thread? = null
    @Volatile private var playing = false
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()

    fun startRecording() {
        val minBuf = AudioRecord.getMinBufferSize(micSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, 3200)
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                micSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
        } catch (e: Exception) {
            Log.e("AudioEngine", "AudioRecord init failed: ${e.message}")
            return
        }
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return

        recording = true
        audioRecord?.startRecording()
        recordThread = Thread {
            val buffer = ByteArray(1024)
            while (recording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    onMicAmplitude(rms(chunk))
                    if (micSendEnabled) onMicChunk(chunk)
                }
            }
        }
        recordThread?.start()
    }

    fun stopRecording() {
        recording = false
        try { audioRecord?.stop() } catch (e: Exception) {}
        audioRecord?.release()
        audioRecord = null
        recordThread = null
    }

    fun startPlayback() {
        val minBuf = AudioTrack.getMinBufferSize(speakerSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(speakerSampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        audioTrack = AudioTrack(attrs, format, maxOf(minBuf, 4096), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        audioTrack?.play()
        playing = true

        playThread = Thread {
            while (playing) {
                val chunk = playbackQueue.poll(300, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    onPlaybackAmplitude(rms(chunk))
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    onPlaybackIdle()
                }
            }
        }
        playThread?.start()
    }

    fun enqueuePlayback(pcm: ByteArray) {
        playbackQueue.offer(pcm)
    }

    fun clearPlaybackQueue() {
        playbackQueue.clear()
        try { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.play() } catch (e: Exception) {}
    }

    fun stopPlayback() {
        playing = false
        playbackQueue.clear()
        try { audioTrack?.stop() } catch (e: Exception) {}
        audioTrack?.release()
        audioTrack = null
        playThread = null
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }

    private fun rms(buffer: ByteArray): Float {
        if (buffer.size < 2) return 0f
        var sum = 0.0
        var i = 0
        while (i < buffer.size - 1) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sum += (sample * sample).toDouble()
            i += 2
        }
        val samples = buffer.size / 2
        if (samples == 0) return 0f
        val rmsVal = sqrt(sum / samples) / 32768.0
        return rmsVal.toFloat().coerceIn(0f, 1f)
    }
}
