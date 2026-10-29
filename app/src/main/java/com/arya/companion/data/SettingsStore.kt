package com.arya.companion.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WakeMode { Off, InApp, Always }

const val DEFAULT_MODEL = "gemini-3.1-flash-live-preview"
val VOICES = listOf("Aoede", "Kore", "Leda", "Zephyr", "Puck", "Charon", "Fenrir", "Orus")

data class AryaSettings(
    /** The user's own Gemini API key. Encrypted at rest, sent only to Google. */
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val voiceName: String = "Aoede",
    val userName: String = "",
    val theme: String = "violet",
    val wakeMode: WakeMode = WakeMode.Off,
    /** The user has seen and accepted the microphone disclosure. */
    val micDisclosureAccepted: Boolean = false,
    /** The user has seen and accepted the wake-word disclosure. */
    val wakeDisclosureAccepted: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("arya_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    val settings: StateFlow<AryaSettings> = state.asStateFlow()

    @Synchronized
    fun update(transform: (AryaSettings) -> AryaSettings) {
        val next = transform(state.value)
        state.value = next
        save(next)
    }

    private fun load(): AryaSettings {
        val defaults = AryaSettings()
        return AryaSettings(
            apiKey = SecretBox.decrypt(prefs.getString("apiKeyEnc", "") ?: ""),
            model = prefs.getString("model", null)?.takeIf { it.isNotBlank() } ?: defaults.model,
            voiceName = prefs.getString("voice", null)?.takeIf { it in VOICES } ?: defaults.voiceName,
            userName = prefs.getString("userName", "") ?: "",
            theme = prefs.getString("theme", null) ?: defaults.theme,
            wakeMode = runCatching { WakeMode.valueOf(prefs.getString("wakeMode", "Off") ?: "Off") }.getOrDefault(WakeMode.Off),
            micDisclosureAccepted = prefs.getBoolean("micDisclosure", false),
            wakeDisclosureAccepted = prefs.getBoolean("wakeDisclosure", false),
        )
    }

    private fun save(s: AryaSettings) {
        prefs.edit()
            .putString("apiKeyEnc", SecretBox.encrypt(s.apiKey))
            .putString("model", s.model)
            .putString("voice", s.voiceName)
            .putString("userName", s.userName)
            .putString("theme", s.theme)
            .putString("wakeMode", s.wakeMode.name)
            .putBoolean("micDisclosure", s.micDisclosureAccepted)
            .putBoolean("wakeDisclosure", s.wakeDisclosureAccepted)
            .apply()
    }
}
