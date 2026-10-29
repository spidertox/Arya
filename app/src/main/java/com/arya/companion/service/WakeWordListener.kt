package com.arya.companion.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Listens for "Hey Arya" using Android's SpeechRecognizer (no extra model or key).
 *
 * It only looks for the wake word and does nothing with other speech. On Android 13+ the on-device
 * recognizer is used when an offline language pack is installed; otherwise the default recognizer is used,
 * which may process audio in Google's speech service.
 *
 * Must be created, started and stopped on the main thread.
 */
class WakeWordListener(
    private val context: Context,
    private val onWake: () -> Unit,
    private val onProblem: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var useOnDevice = true
    private var errorStreak = 0
    private var lastWakeAt = 0L

    private val restart = Runnable { listen() }

    fun start() {
        if (running) return
        running = true
        errorStreak = 0
        listen()
    }

    fun stop() {
        running = false
        main.removeCallbacks(restart)
        destroyRecognizer()
    }

    private fun listen() {
        if (!running) return
        try {
            if (recognizer == null && !createRecognizer()) return
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "startListening failed", e)
            destroyRecognizer()
            scheduleRestart(backoffMs())
        }
    }

    private fun createRecognizer(): Boolean {
        destroyRecognizer()
        val created = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && useOnDevice &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> null
        }
        if (created == null) {
            running = false
            onProblem("Speech recognition is not available on this phone, so “Hey Arya” is off.")
            return false
        }
        created.setRecognitionListener(listener)
        recognizer = created
        return true
    }

    private fun destroyRecognizer() {
        val r = recognizer
        recognizer = null
        if (r == null) return
        try {
            r.cancel()
        } catch (e: Exception) {
            // already stopped
        }
        try {
            r.destroy()
        } catch (e: Exception) {
            // already destroyed
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        main.removeCallbacks(restart)
        if (running) main.postDelayed(restart, delayMs)
    }

    private fun backoffMs(): Long {
        errorStreak++
        return minOf(10_000L, 800L * errorStreak)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            errorStreak = 0
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) = handleError(error)

        override fun onResults(results: Bundle?) {
            if (!handleMatches(results)) scheduleRestart(RESTART_FAST_MS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            handleMatches(partialResults)
        }
    }

    private fun handleError(error: Int) {
        if (!running) return
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(RESTART_FAST_MS)
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                destroyRecognizer()
                scheduleRestart(1_000)
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                running = false
                destroyRecognizer()
                onProblem("Arya needs microphone permission to hear “Hey Arya”.")
            }
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                // No offline language pack: fall back to the default recognizer.
                useOnDevice = false
                destroyRecognizer()
                scheduleRestart(500)
            }
            else -> {
                destroyRecognizer()
                scheduleRestart(backoffMs())
            }
        }
    }

    private fun handleMatches(bundle: Bundle?): Boolean {
        if (!running || bundle == null) return false
        val heard = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        if (heard.none { matchesWakeWord(it) }) return false

        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeAt < COOLDOWN_MS) return true
        lastWakeAt = now
        Log.i(TAG, "Wake word heard")
        // Release the microphone before the conversation takes it.
        stop()
        onWake()
        return true
    }

    companion object {
        private const val TAG = "AryaWake"
        private const val RESTART_FAST_MS = 250L
        private const val COOLDOWN_MS = 3_000L

        // "arya" and the spellings speech recognizers commonly produce for it.
        private val LATIN = Regex("\\b(arya|aarya|aaryaa|aryaa|aria|aaria|ariya|arja)\\b")

        // Devanagari spellings (escaped so the source encoding never matters): आर्य, अर्य, आरिय, अरिय
        private val DEVANAGARI = listOf(
            "\u0906\u0930\u094D\u092F",
            "\u0905\u0930\u094D\u092F",
            "\u0906\u0930\u093F\u092F",
            "\u0905\u0930\u093F\u092F",
        )

        fun matchesWakeWord(heard: String): Boolean {
            val text = heard.lowercase(Locale.ROOT)
            return LATIN.containsMatchIn(text) || DEVANAGARI.any { text.contains(it) }
        }
    }
}
