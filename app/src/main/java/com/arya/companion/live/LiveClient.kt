package com.arya.companion.live

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * One WebSocket connection to the Gemini Live API (BidiGenerateContent) using the user's own API key.
 * Messages are JSON; audio travels as base64 PCM inside them.
 */
class LiveClient(private val apiKey: String, private val callbacks: Callbacks) {

    interface Callbacks {
        fun onSetupComplete(client: LiveClient)
        fun onMessage(client: LiveClient, message: JSONObject)
        fun onClosed(client: LiveClient, reason: String)
    }

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var closedByUs = false

    fun connect(setup: JSONObject) {
        val request = Request.Builder().url("$ENDPOINT?key=$apiKey").build()
        socket = http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(JSONObject().put("setup", setup).toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    finish(describeClose(code, reason))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    finish(describeClose(code, reason))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    finish(describeFailure(t, response))
                }
            },
        )
    }

    private fun handle(text: String) {
        val message = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        if (message.has("setupComplete")) callbacks.onSetupComplete(this) else callbacks.onMessage(this, message)
    }

    private fun finish(reason: String) {
        if (closedByUs) return
        closedByUs = true
        callbacks.onClosed(this, reason)
    }

    fun sendAudio(pcm16k: ByteArray) {
        val audio = JSONObject()
            .put("data", Base64.encodeToString(pcm16k, Base64.NO_WRAP))
            .put("mimeType", "audio/pcm;rate=16000")
        send(JSONObject().put("realtimeInput", JSONObject().put("audio", audio)))
    }

    fun sendText(text: String) {
        send(JSONObject().put("realtimeInput", JSONObject().put("text", text)))
    }

    fun sendToolResponse(functionResponses: JSONArray) {
        send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", functionResponses)))
    }

    private fun send(message: JSONObject) {
        socket?.send(message.toString())
    }

    fun close() {
        closedByUs = true
        try {
            socket?.close(1000, null)
        } catch (e: Exception) {
            // already closed
        }
        socket = null
    }

    private fun describeClose(code: Int, reason: String): String = when {
        reason.contains("API key", ignoreCase = true) -> "Gemini rejected the API key. Check it in Settings."
        reason.isNotBlank() -> reason
        code == 1000 || code == 1001 -> "The voice connection ended."
        else -> "The voice connection was closed (code $code)."
    }

    private fun describeFailure(t: Throwable, response: Response?): String = when (response?.code) {
        400 -> "Gemini rejected the request. Check the model name in Settings."
        401, 403 -> "Gemini rejected the API key. Check it in Settings."
        404 -> "Gemini does not know that model. Check the model name in Settings."
        429 -> "Gemini is rate-limiting this key. Try again in a moment."
        else -> t.message?.takeIf { it.isNotBlank() } ?: "Could not reach Gemini. Check your internet connection."
    }

    private companion object {
        const val ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // the socket stays open for the whole conversation
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
