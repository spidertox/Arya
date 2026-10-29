package com.arya.companion.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/** Gemini Live wants 16 kHz PCM16 mono in and sends 24 kHz PCM16 mono out. */
const val MIC_SAMPLE_RATE = 16_000
const val SPEAKER_SAMPLE_RATE = 24_000
const val MIC_FRAME_SAMPLES = 1_024 // 64 ms

fun rmsOf(samples: ShortArray): Float {
    if (samples.isEmpty()) return 0f
    var energy = 0.0
    for (s in samples) {
        val v = s / 32768.0
        energy += v * v
    }
    return sqrt(energy / samples.size).toFloat()
}

/** RMS of little-endian PCM16 bytes. */
fun rmsOfPcm16(bytes: ByteArray): Float {
    val count = bytes.size / 2
    if (count == 0) return 0f
    var energy = 0.0
    for (i in 0 until count) {
        val v = ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort() / 32768.0
        energy += v * v
    }
    return sqrt(energy / count).toFloat()
}

fun shortsToLittleEndian(samples: ShortArray): ByteArray {
    val out = ByteArray(samples.size * 2)
    for (i in samples.indices) {
        val v = samples[i].toInt()
        out[i * 2] = (v and 0xFF).toByte()
        out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
    }
    return out
}

/** Captures the microphone (voice-communication source, with echo cancellation where the phone offers it). */
class MicCapture(private val onFrame: (ShortArray) -> Unit) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked before the engine starts
    fun start(): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(MIC_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val rec = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(MIC_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, MIC_FRAME_SAMPLES * 2 * 4))
                .build()
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        try {
            if (AcousticEchoCanceler.isAvailable()) echo = AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.setEnabled(true) }
            if (NoiseSuppressor.isAvailable()) noise = NoiseSuppressor.create(rec.audioSessionId)?.also { it.setEnabled(true) }
        } catch (e: Exception) {
            // effects are optional
        }
        record = rec
        try {
            rec.startRecording()
        } catch (e: IllegalStateException) {
            stop()
            return false
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            stop()
            return false
        }
        running = true
        thread = Thread({ readLoop(rec) }, "arya-mic").also { it.start() }
        return true
    }

    private fun readLoop(rec: AudioRecord) {
        val buffer = ShortArray(MIC_FRAME_SAMPLES)
        while (running) {
            val read = rec.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (read > 0) onFrame(buffer.copyOf(read)) else if (read < 0) break
        }
    }

    fun stop() {
        running = false
        try {
            record?.stop()
        } catch (e: Exception) {
            // not recording
        }
        try {
            thread?.join(300)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        echo?.release()
        noise?.release()
        record?.release()
        echo = null
        noise = null
        record = null
        thread = null
    }
}

/** Plays Gemini's 24 kHz PCM16 voice. Chunks are queued and written from a dedicated thread. */
class SpeakerPlayback {
    private var track: AudioTrack? = null
    private var writer: Thread? = null
    private val queue = LinkedBlockingQueue<ByteArray>()

    @Volatile
    private var running = false

    @Volatile
    private var epoch = 0

    /** SystemClock.elapsedRealtime() at which everything queued so far will have finished playing. */
    @Volatile
    private var scheduledEnd = 0L

    fun start(): Boolean {
        val minBuffer = AudioTrack.getMinBufferSize(SPEAKER_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SPEAKER_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, SPEAKER_SAMPLE_RATE * 2)) // about one second
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            return false
        }
        track = t
        t.play()
        running = true
        writer = Thread({ writeLoop(t) }, "arya-speaker").also { it.start() }
        return true
    }

    private fun writeLoop(t: AudioTrack) {
        while (running) {
            val chunk = try {
                queue.poll(100, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                break
            } ?: continue
            val myEpoch = epoch
            var offset = 0
            while (offset < chunk.size && running && myEpoch == epoch) {
                val written = t.write(chunk, offset, chunk.size - offset)
                if (written <= 0) break
                offset += written
            }
        }
    }

    fun enqueue(pcm: ByteArray) {
        if (!running) return
        queue.offer(pcm)
        val durationMs = pcm.size / 2 * 1000L / SPEAKER_SAMPLE_RATE
        scheduledEnd = maxOf(scheduledEnd, SystemClock.elapsedRealtime()) + durationMs
    }

    /** Milliseconds of audio still waiting to play. */
    fun pendingMs(): Long = maxOf(0L, scheduledEnd - SystemClock.elapsedRealtime())

    /** Drops everything that has not played yet (the user interrupted). */
    fun clear() {
        epoch++
        queue.clear()
        scheduledEnd = 0L
        try {
            track?.pause()
            track?.flush()
            track?.play()
        } catch (e: IllegalStateException) {
            // track already released
        }
    }

    fun stop() {
        running = false
        epoch++
        queue.clear()
        try {
            writer?.join(300)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        try {
            track?.stop()
        } catch (e: IllegalStateException) {
            // not playing
        }
        track?.release()
        track = null
        writer = null
        scheduledEnd = 0L
    }
}

/** Puts the phone into a voice-call audio mode (speaker or headset, echo cancellation) for a conversation. */
class AudioRouter(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var previousMode = AudioManager.MODE_NORMAL
    private var focus: AudioFocusRequest? = null

    private val headsetTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID,
    )

    fun enter() {
        previousMode = audio.mode
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        audio.requestAudioFocus(request)
        focus = request
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        routeToBestDevice()
    }

    @Suppress("DEPRECATION")
    private fun routeToBestDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audio.availableCommunicationDevices
            val target = devices.firstOrNull { it.type in headsetTypes }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (target != null) {
                try {
                    audio.setCommunicationDevice(target)
                } catch (e: Exception) {
                    // keep the system default route
                }
            }
        } else {
            val headset = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headsetTypes }
            audio.isSpeakerphoneOn = !headset
        }
    }

    @Suppress("DEPRECATION")
    fun exit() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.clearCommunicationDevice() else audio.isSpeakerphoneOn = false
        } catch (e: Exception) {
            // ignore
        }
        audio.mode = previousMode
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }
}
