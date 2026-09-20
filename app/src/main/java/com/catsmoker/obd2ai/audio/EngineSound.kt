package com.catsmoker.obd2ai.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Optional entertainment: an RPM-driven engine hum synthesized on-device
 * (sine + harmonics through AudioTrack, no samples, no network). Frequency
 * follows RPM, gain fades with silence at idle. Fully isolated: it only
 * reads [rpm], never touches diagnostics, and dies with mute or the screen.
 */
class EngineSound {
    @Volatile var rpm: Int = 0
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    private var thread: Thread? = null

    fun start() {
        if (running.getAndSet(true)) return
        thread = Thread({ render() }, "EngineSound").apply { isDaemon = true; start() }
    }

    fun stop() {
        running.set(false)
        runCatching { thread?.join(600) }
        thread = null
    }

    private fun render() {
        val sampleRate = 22050
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf.coerceAtLeast(4096))
            .build()
        try {
            track.play()
            var phase = 0.0
            var freq = 70.0
            var gain = 0.0
            val chunk = ShortArray(1024)
            val tau = 2.0 * Math.PI
            while (running.get()) {
                val targetFreq = 55.0 + rpm.coerceIn(0, 8000) * 0.028
                freq += (targetFreq - freq) * 0.08
                val targetGain = if (rpm > 400) 0.5 else 0.0
                gain += (targetGain - gain) * 0.05
                for (i in chunk.indices) {
                    phase = (phase + tau * freq / sampleRate) % tau
                    val s = (Math.sin(phase) * 0.55 +
                        Math.sin(phase * 2.0) * 0.28 +
                        Math.sin(phase * 3.0) * 0.17) * gain
                    chunk[i] = (s * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
                }
                track.write(chunk, 0, chunk.size)
            }
            // Fade out so stopping does not click.
            repeat(5) {
                gain *= 0.6f
                for (i in chunk.indices) {
                    phase = (phase + tau * freq / sampleRate) % tau
                    val s = (Math.sin(phase) * 0.55 +
                        Math.sin(phase * 2.0) * 0.28 +
                        Math.sin(phase * 3.0) * 0.17) * gain
                    chunk[i] = (s * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
                }
                track.write(chunk, 0, chunk.size)
            }
        } catch (e: Exception) {
            Log.e("EngineSound", "Playback failed", e)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
