package com.arya.companion.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arya.companion.BuildConfig
import com.arya.companion.Graph
import com.arya.companion.data.VOICES
import com.arya.companion.data.WakeMode
import com.arya.companion.device.DeviceActions
import com.arya.companion.live.AryaEngine
import com.arya.companion.service.AryaVoiceService

/** Short, honest descriptions of Gemini's prebuilt voices. */
private val VOICE_TRAITS = mapOf(
    "Aoede" to "Breezy",
    "Kore" to "Firm",
    "Leda" to "Youthful",
    "Zephyr" to "Bright",
    "Puck" to "Upbeat",
    "Charon" to "Informative",
    "Fenrir" to "Excitable",
    "Orus" to "Firm",
)

/** Full-screen settings. [requestThen] asks for runtime permissions and reports the result. */
@Composable
fun SettingsScreen(
    requestThen: (Array<String>, (Map<String, Boolean>) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme
    val settings by Graph.settings.settings.collectAsStateWithLifecycle()
    val memories by Graph.memories.memories.collectAsStateWithLifecycle()

    var keyDraft by remember { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf(settings.userName) }
    var modelDraft by remember { mutableStateOf(settings.model) }

    var pendingWakeMode by remember { mutableStateOf<WakeMode?>(null) }
    var showContactsDisclosure by remember { mutableStateOf(false) }
    var contactsGranted by remember { mutableStateOf(DeviceActions.hasContactsPermission(context)) }
    var showReport by remember { mutableStateOf(false) }

    fun applyWakeMode(mode: WakeMode) {
        Graph.settings.update { it.copy(wakeMode = mode) }
        // "Always" is started by AryaRoot once the permissions are in place; anything else stops the service.
        if (mode != WakeMode.Always) AryaVoiceService.stopWake(context)
    }

    fun ensurePermissionsThenApply(mode: WakeMode) {
        val missing = mutableListOf<String>()
        if (!hasPermission(context, Manifest.permission.RECORD_AUDIO)) missing += Manifest.permission.RECORD_AUDIO
        if (mode == WakeMode.Always && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        if (missing.isEmpty()) {
            applyWakeMode(mode)
        } else {
            requestThen(missing.toTypedArray()) {
                if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                    applyWakeMode(mode)
                } else {
                    AryaEngine.reportProblem("Microphone permission is needed for “Hey Arya”.")
                }
            }
        }
    }

    fun chooseWakeMode(mode: WakeMode) {
        when {
            mode == WakeMode.Off -> applyWakeMode(mode)
            !settings.wakeDisclosureAccepted -> pendingWakeMode = mode
            else -> ensurePermissionsThenApply(mode)
        }
    }

    Box(Modifier.fillMaxSize().background(AryaBg)) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // ---- header ----------------------------------------------------------------------
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.onSurface)
                }
                Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 14.dp))
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // ---- you ---------------------------------------------------------------------
                Section("You") {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = {
                            nameDraft = it
                            Graph.settings.update { s -> s.copy(userName = it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("What should Arya call you?") },
                    )
                }

                // ---- voice -------------------------------------------------------------------
                Section("Voice") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VOICES.forEach { voice ->
                            VoiceChip(
                                name = voice,
                                trait = VOICE_TRAITS[voice].orEmpty(),
                                selected = voice == settings.voiceName,
                                onClick = { Graph.settings.update { it.copy(voiceName = voice) } },
                            )
                        }
                    }
                    Hint("Applies the next time you start a conversation.")
                }

                // ---- look --------------------------------------------------------------------
                Section("Colour theme") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Themes.all.forEach { (name, palette) ->
                            ThemeSwatch(
                                name = name,
                                palette = palette,
                                selected = name == settings.theme,
                                onClick = { Graph.settings.update { it.copy(theme = name) } },
                            )
                        }
                    }
                    Hint("You can also just ask Arya: “change the theme to gold”.")
                }

                // ---- wake word ---------------------------------------------------------------
                Section("“Hey Arya” wake word") {
                    SegmentedControl(
                        options = listOf("Off", "App open", "Always"),
                        selectedIndex = when (settings.wakeMode) {
                            WakeMode.Off -> 0
                            WakeMode.InApp -> 1
                            WakeMode.Always -> 2
                        },
                        onSelect = { index -> chooseWakeMode(WakeMode.entries[index]) },
                    )
                    Hint(
                        when (settings.wakeMode) {
                            WakeMode.Off -> "Arya only listens when you tap the mic."
                            WakeMode.InApp -> "Arya listens for her name only while the app is on screen."
                            WakeMode.Always ->
                                "Arya keeps listening in the background and shows a notification while she does. " +
                                    "If Android stops her, exclude Arya from battery optimisation."
                        },
                    )
                }

                // ---- phone commands ----------------------------------------------------------
                Section("Phone commands") {
                    Hint(
                        "Arya can open apps, set alarms and timers, open the dialer, search the web and switch the flashlight " +
                            "when you ask. Calls only open the dialer; you press call yourself.",
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Call by contact name", style = MaterialTheme.typography.bodyLarge)
                            Hint(if (contactsGranted) "Allowed" else "Off")
                        }
                        if (!contactsGranted) OutlinedButton(onClick = { showContactsDisclosure = true }) { Text("Allow") }
                    }
                }

                // ---- memories ----------------------------------------------------------------
                Section("Memories (${memories.size})") {
                    if (memories.isEmpty()) {
                        Hint("Arya has not saved anything about you yet. Memories stay on this phone, and you can delete any of them here.")
                    } else {
                        memories.reversed().forEach { memory ->
                            Row(
                                Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), fill = 0.05f).padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(memory.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.secondary)
                                    Text(memory.text, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { Graph.memories.delete(memory.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete memory", tint = colors.onSurfaceVariant)
                                }
                            }
                        }
                        TextButton(onClick = { Graph.memories.clear() }) { Text("Clear all memories") }
                    }
                }

                // ---- advanced ----------------------------------------------------------------
                Section("Advanced") {
                    TextButton(onClick = { showAdvanced = !showAdvanced }) {
                        Text(if (showAdvanced) "Hide API key and model" else "API key and model")
                    }
                    AnimatedVisibility(visible = showAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = keyDraft,
                                onValueChange = { keyDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Gemini API key") },
                                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { Graph.settings.update { it.copy(apiKey = keyDraft.trim()) } },
                                    enabled = keyDraft.trim().length >= 20 && keyDraft.trim() != settings.apiKey,
                                ) { Text("Save key") }
                                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                            }
                            OutlinedTextField(
                                value = modelDraft,
                                onValueChange = {
                                    modelDraft = it
                                    if (it.isNotBlank()) Graph.settings.update { s -> s.copy(model = it.trim()) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Live model") },
                            )
                            Hint("Changes apply the next time you start a conversation.")
                        }
                    }
                }

                // ---- about -------------------------------------------------------------------
                Section("About") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { uriHandler.openUri(BuildConfig.PRIVACY_URL) }) { Text("Privacy policy") }
                        OutlinedButton(onClick = { showReport = true }) { Text("Report a problem") }
                    }
                    Hint("Arya ${BuildConfig.VERSION_NAME} · open source (Apache-2.0). Replies are generated by AI and can be wrong.")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    pendingWakeMode?.let { mode ->
        WakeDisclosureDialog(
            always = mode == WakeMode.Always,
            onAccept = {
                pendingWakeMode = null
                Graph.settings.update { it.copy(wakeDisclosureAccepted = true) }
                ensurePermissionsThenApply(mode)
            },
            onDismiss = { pendingWakeMode = null },
        )
    }
    if (showContactsDisclosure) {
        ContactsDisclosureDialog(
            onAccept = {
                showContactsDisclosure = false
                requestThen(arrayOf(Manifest.permission.READ_CONTACTS)) {
                    contactsGranted = DeviceActions.hasContactsPermission(context)
                }
            },
            onDismiss = { showContactsDisclosure = false },
        )
    }
    if (showReport) ReportDialog(excerpt = null, onDismiss = { showReport = false })
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        GlassCard(content = content)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
