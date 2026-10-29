package com.arya.companion.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Base64
import androidx.core.content.ContextCompat
import com.arya.companion.Graph
import com.arya.companion.data.AryaSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

enum class LiveState { Disconnected, Connecting, Listening, Thinking, Speaking }

enum class ChatRole { User, Model }

data class ChatLine(val id: Long, val role: ChatRole, val text: String, val closed: Boolean)

/**
 * Arya's voice engine: one conversation with Gemini Live at a time.
 *
 *   microphone (16 kHz PCM) ──► Gemini Live ──► voice (24 kHz PCM) ──► speaker
 *
 * It is a process-wide singleton so a conversation survives the Activity (it is hosted by AryaVoiceService
 * while running). All mutable state is touched only on the single "arya-engine" thread.
 */
object AryaEngine : LiveClient.Callbacks {
    // Turn taking and barge-in tuning (carried over from MYRAA).
    private const val VOICE_THRESHOLD = 0.02f
    private const val VOICE_START_FRAMES = 2
    private const val VOICE_STOP_DELAY_MS = 500L
    private const val BARGE_IN_GUARD_MS = 700L
    private const val BARGE_IN_START_FRAMES = 4
    private const val BARGE_IN_THRESHOLD = 0.045f
    private const val THINKING_TIMEOUT_MS = 6_000L
    private const val IDLE_TIMEOUT_MS = 90_000L
    private const val MAX_RESUME_ATTEMPTS = 3
    private const val MAX_LINES = 60

    private lateinit var context: Context
    private lateinit var tools: ToolRunner

    private val _state = MutableStateFlow(LiveState.Disconnected)
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private val _lines = MutableStateFlow<List<ChatLine>>(emptyList())
    val lines: StateFlow<List<ChatLine>> = _lines.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor { r -> Thread(r, "arya-engine") }.asCoroutineDispatcher(),
    )

    // ---- engine-thread state -----------------------------------------------------------------
    private var active = false
    private var useMicrophone = true
    private var client: LiveClient? = null
    private var connecting: LiveClient? = null
    private var resumeHandle: String? = null
    private var resumeAttempts = 0
    private var mic: MicCapture? = null
    private var player: SpeakerPlayback? = null
    private var router: AudioRouter? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var idleJob: Job? = null
    private var thinkingJob: Job? = null
    private var watcherJob: Job? = null
    private val pendingTexts = ArrayDeque<String>()
    private var lineId = 0L

    private var userSpeaking = false
    private var voiceFrames = 0
    private var lastVoiceAt = 0L
    private var speechStartedAt = 0L

    // ---- levels read by the UI from any thread -----------------------------------------------
    @Volatile
    private var inLevel = 0f

    @Volatile
    private var outRms = 0f

    @Volatile
    private var outRmsAt = 0L

    fun init(app: Context) {
        context = app.applicationContext
        tools = ToolRunner(context)
    }

    // ---- public API --------------------------------------------------------------------------

    /** Starts a conversation. With [withMicrophone] false it is text-in, voice-out. */
    fun start(withMicrophone: Boolean) {
        if (_state.value != LiveState.Disconnected) return
        _error.value = null
        _state.value = LiveState.Connecting
        scope.launch { startInternal(withMicrophone) }
    }

    fun stop() {
        scope.launch { stopInternal() }
    }

    /** Sends typed text; starts a text conversation first when none is running. */
    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            appendLine(ChatRole.User, trimmed, closed = true)
            val live = client
            if (active && live != null) {
                if (_state.value == LiveState.Speaking) handleInterruption()
                live.sendText(trimmed)
                touchActivity()
                setState(LiveState.Thinking)
            } else {
                pendingTexts.addLast(trimmed)
                if (!active) {
                    _error.value = null
                    _state.value = LiveState.Connecting
                    startInternal(withMicrophone = false)
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    /** Shows a message in the app's error banner (used by the voice service and the wake-word listener). */
    fun reportProblem(message: String) {
        _error.value = message
    }

    /** 0..1 loudness for the character glow: Arya's voice while she speaks, the microphone otherwise. */
    fun levelNow(): Float {
        return if (_state.value == LiveState.Speaking) {
            if (SystemClock.elapsedRealtime() - outRmsAt < 150) min(1f, outRms * 4f) else 0f
        } else {
            inLevel
        }
    }

    // ---- lifecycle ---------------------------------------------------------------------------

    private fun startInternal(withMicrophone: Boolean) {
        if (active) return
        val settings = Graph.settings.settings.value
        if (settings.apiKey.isBlank()) return fail("Add your Gemini API key in Settings first.")
        if (withMicrophone &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            return fail("Microphone permission is needed to talk with Arya.")
        }
        active = true
        useMicrophone = withMicrophone
        resumeAttempts = 0
        resumeHandle = null

        router = AudioRouter(context).also { it.enter() }
        val speaker = SpeakerPlayback()
        if (!speaker.start()) return fail("Could not open the speaker.")
        player = speaker

        connectClient(null)
    }

    private fun stopInternal() {
        active = false
        idleJob?.cancel()
        thinkingJob?.cancel()
        watcherJob?.cancel()
        client?.close()
        connecting?.close()
        client = null
        connecting = null
        mic?.stop()
        mic = null
        player?.stop()
        player = null
        router?.exit()
        router = null
        releaseWakeLock()
        userSpeaking = false
        voiceFrames = 0
        speechStartedAt = 0L
        resumeHandle = null
        pendingTexts.clear()
        inLevel = 0f
        outRms = 0f
        _state.value = LiveState.Disconnected
    }

    private fun fail(message: String) {
        _error.value = message
        stopInternal()
    }

    // ---- connection --------------------------------------------------------------------------

    private fun connectClient(handle: String?) {
        val settings = Graph.settings.settings.value
        val next = LiveClient(settings.apiKey, this)
        connecting?.close()
        connecting = next
        next.connect(buildSetup(settings, handle))
    }

    private fun buildSetup(settings: AryaSettings, handle: String?): JSONObject {
        val voice = JSONObject().put(
            "voiceConfig",
            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", settings.voiceName)),
        )
        val persona = Persona.build(settings, Graph.memories.memories.value)
        return JSONObject()
            .put("model", "models/" + settings.model.removePrefix("models/"))
            .put(
                "generationConfig",
                JSONObject().put("responseModalities", JSONArray().put("AUDIO")).put("speechConfig", voice),
            )
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", persona))))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", ToolSpecs.declarations())))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            .put("sessionResumption", JSONObject().also { if (handle != null) it.put("handle", handle) })
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
    }

    // LiveClient.Callbacks arrive on OkHttp threads; hop onto the engine thread.
    override fun onSetupComplete(client: LiveClient) {
        scope.launch { handleSetupComplete(client) }
    }

    override fun onMessage(client: LiveClient, message: JSONObject) {
        scope.launch { handleMessage(client, message) }
    }

    override fun onClosed(client: LiveClient, reason: String) {
        scope.launch { handleClosed(client, reason) }
    }

    private fun handleSetupComplete(ready: LiveClient) {
        if (!active || ready !== connecting) return
        val previous = client
        client = ready
        connecting = null
        previous?.close()

        if (mic == null && useMicrophone) {
            val capture = MicCapture { frame -> scope.launch { onMicFrame(frame) } }
            if (!capture.start()) return fail("Could not open the microphone. Another app may be using it.")
            mic = capture
        }
        if (_state.value == LiveState.Connecting) setState(LiveState.Listening)
        acquireWakeLock()
        touchActivity()

        if (pendingTexts.isNotEmpty()) {
            while (pendingTexts.isNotEmpty()) ready.sendText(pendingTexts.removeFirst())
            setState(LiveState.Thinking)
        }
    }

    private fun handleClosed(closed: LiveClient, reason: String) {
        if (!active) return
        if (closed === connecting) {
            connecting = null
            if (client == null) fail(reason)
            return
        }
        if (closed !== client) return
        client = null
        val handle = resumeHandle
        if (handle != null && resumeAttempts < MAX_RESUME_ATTEMPTS) {
            resumeAttempts++
            connectClient(handle)
        } else {
            fail(reason)
        }
    }

    // ---- messages from Gemini ----------------------------------------------------------------

    private fun handleMessage(from: LiveClient, message: JSONObject) {
        if (!active || from !== client) return

        message.optJSONObject("sessionResumptionUpdate")?.let { update ->
            val handle = update.optString("newHandle")
            if (update.optBoolean("resumable") && handle.isNotEmpty()) {
                resumeHandle = handle
                resumeAttempts = 0
            }
        }
        // Long sessions end with a GoAway; reconnect seamlessly using the resumption handle.
        if (message.has("goAway") && resumeHandle != null && connecting == null) connectClient(resumeHandle)

        message.optJSONObject("serverContent")?.let { content ->
            content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data").orEmpty()
                    if (data.isNotEmpty()) playChunk(data)
                }
            }
            if (content.optBoolean("interrupted")) handleInterruption()

            content.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }
                ?.let { appendLine(ChatRole.User, it) }
            content.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }
                ?.let { appendLine(ChatRole.Model, it) }

            if (content.optBoolean("turnComplete")) closeLastLine()
        }

        message.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            scope.launch { runToolCalls(from, calls) }
        }
    }

    private suspend fun runToolCalls(from: LiveClient, calls: JSONArray) {
        touchActivity()
        val responses = JSONArray()
        for (i in 0 until calls.length()) {
            val call = calls.optJSONObject(i) ?: continue
            val name = call.optString("name")
            val output = tools.run(name, call.optJSONObject("args") ?: JSONObject())
            responses.put(
                JSONObject()
                    .put("id", call.optString("id"))
                    .put("name", name)
                    .put("response", JSONObject().put("output", output)),
            )
        }
        if (active) (client ?: from).sendToolResponse(responses)
    }

    // ---- speaker -----------------------------------------------------------------------------

    private fun playChunk(base64: String) {
        val speaker = player ?: return
        val pcm = try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            return
        }
        touchActivity()
        if (_state.value != LiveState.Speaking || speaker.pendingMs() == 0L) speechStartedAt = SystemClock.elapsedRealtime()
        setState(LiveState.Speaking)
        outRms = rmsOfPcm16(pcm)
        outRmsAt = SystemClock.elapsedRealtime()
        speaker.enqueue(pcm)
        ensurePlaybackWatcher()
    }

    /** Flips back to Listening once everything queued has been played. */
    private fun ensurePlaybackWatcher() {
        if (watcherJob?.isActive == true) return
        watcherJob = scope.launch {
            var quietPolls = 0
            while (isActive && active) {
                delay(60)
                if (_state.value != LiveState.Speaking) break
                quietPolls = if ((player?.pendingMs() ?: 0L) == 0L) quietPolls + 1 else 0
                if (quietPolls >= 2) {
                    speechStartedAt = 0L
                    setState(LiveState.Listening)
                    break
                }
            }
        }
    }

    private fun handleInterruption() {
        player?.clear()
        speechStartedAt = 0L
        if (active) setState(LiveState.Listening)
    }

    // ---- microphone --------------------------------------------------------------------------

    private fun onMicFrame(samples: ShortArray) {
        val live = client ?: return
        if (!active || _state.value == LiveState.Connecting) return
        val level = rmsOf(samples)
        inLevel = min(1f, level * 6f)
        if (!gateMicrophone(level)) return
        live.sendAudio(shortsToLittleEndian(samples))
    }

    /**
     * Local voice-activity gate. While Arya is silent every frame is forwarded (Gemini's own VAD needs a
     * continuous stream). While she speaks, speaker echo is not forwarded unless a louder, sustained voice
     * shows the user is really talking over her (barge-in).
     */
    private fun gateMicrophone(level: Float): Boolean {
        val now = SystemClock.elapsedRealtime()
        val outputActive = _state.value == LiveState.Speaking || (player?.pendingMs() ?: 0L) > 0L

        if (outputActive && speechStartedAt > 0L && now - speechStartedAt < BARGE_IN_GUARD_MS) {
            voiceFrames = 0
            return false
        }

        val outputLevel = if (outputActive && now - outRmsAt < 150) outRms else 0f
        val threshold = if (outputActive) max(BARGE_IN_THRESHOLD, min(0.1f, outputLevel * 0.32f + 0.012f)) else VOICE_THRESHOLD
        val requiredFrames = if (outputActive) BARGE_IN_START_FRAMES else VOICE_START_FRAMES

        if (level >= threshold) {
            lastVoiceAt = now
            voiceFrames++
            if (voiceFrames == requiredFrames) touchActivity()
            if (!userSpeaking && voiceFrames >= requiredFrames) {
                userSpeaking = true
                if (outputActive) handleInterruption()
                if (_state.value == LiveState.Thinking) setState(LiveState.Listening)
            }
            return !outputActive || userSpeaking
        }

        voiceFrames = 0
        if (userSpeaking && now - lastVoiceAt >= VOICE_STOP_DELAY_MS) {
            userSpeaking = false
            if (_state.value == LiveState.Listening) setState(LiveState.Thinking)
        }
        return !outputActive || userSpeaking
    }

    // ---- transcript & housekeeping -----------------------------------------------------------

    private fun appendLine(role: ChatRole, text: String, closed: Boolean = false) {
        val list = _lines.value
        val last = list.lastOrNull()
        _lines.value = if (last != null && last.role == role && !last.closed) {
            list.dropLast(1) + last.copy(text = last.text + text, closed = closed)
        } else {
            (list + ChatLine(++lineId, role, text, closed)).takeLast(MAX_LINES)
        }
    }

    private fun closeLastLine() {
        val list = _lines.value
        if (list.isEmpty()) return
        _lines.value = list.dropLast(1) + list.last().copy(closed = true)
    }

    private fun setState(next: LiveState) {
        if (_state.value == next) return
        _state.value = next
        thinkingJob?.cancel()
        if (next == LiveState.Thinking) {
            thinkingJob = scope.launch {
                delay(THINKING_TIMEOUT_MS)
                if (_state.value == LiveState.Thinking) setState(LiveState.Listening)
            }
        }
    }

    /** Restarts the idle countdown; the conversation ends after 90 s of silence so the mic is released. */
    private fun touchActivity() {
        idleJob?.cancel()
        if (!active) return
        idleJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            if (active) stopInternal()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = context.getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Arya:conversation").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
