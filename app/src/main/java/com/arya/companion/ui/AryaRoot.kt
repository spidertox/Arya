package com.arya.companion.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arya.companion.AppVisibility
import com.arya.companion.Graph
import com.arya.companion.data.WakeMode
import com.arya.companion.live.AryaEngine
import com.arya.companion.live.LiveState
import com.arya.companion.service.AryaVoiceService
import com.arya.companion.service.WakeWordListener

/** Top of the UI: permissions, disclosures, the "Hey Arya" listener while the app is open, and navigation. */
@Composable
fun AryaRoot() {
    val context = LocalContext.current
    val settings by Graph.settings.settings.collectAsStateWithLifecycle()
    val engineState by AryaEngine.state.collectAsStateWithLifecycle()
    val visible by AppVisibility.visible.collectAsStateWithLifecycle()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showMicDisclosure by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    var afterPermission by remember { mutableStateOf<((Map<String, Boolean>) -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        micGranted = hasPermission(context, Manifest.permission.RECORD_AUDIO)
        val callback = afterPermission
        afterPermission = null
        callback?.invoke(results)
    }

    fun requestThen(permissions: Array<String>, then: (Map<String, Boolean>) -> Unit) {
        afterPermission = then
        permissionLauncher.launch(permissions)
    }

    fun startConversation() = AryaVoiceService.startConversation(context)

    fun askForMicrophoneThenStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS // so the "talking" notification is visible
        }
        requestThen(permissions.toTypedArray()) {
            if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                startConversation()
            } else {
                AryaEngine.reportProblem("Microphone permission is needed to talk with Arya. You can still type to her.")
            }
        }
    }

    fun toggleMicrophone() {
        when {
            engineState != LiveState.Disconnected -> AryaEngine.stop()
            !hasPermission(context, Manifest.permission.RECORD_AUDIO) ->
                if (settings.micDisclosureAccepted) askForMicrophoneThenStart() else showMicDisclosure = true
            else -> startConversation()
        }
    }

    // Re-check the permission whenever the app returns to the screen (the user may have changed it in Android settings).
    LaunchedEffect(visible) {
        micGranted = hasPermission(context, Manifest.permission.RECORD_AUDIO)
    }

    // "Always" mode: keep the foreground service listening. Started only while the app is on screen.
    LaunchedEffect(settings.wakeMode, micGranted, visible, settings.apiKey) {
        if (settings.wakeMode == WakeMode.Always && micGranted && visible && settings.apiKey.isNotBlank()) {
            AryaVoiceService.startWake(context)
        }
    }

    // "While the app is open" mode: listen here, with no service and no notification.
    val listenInApp = settings.wakeMode == WakeMode.InApp && micGranted && visible &&
        settings.apiKey.isNotBlank() && engineState == LiveState.Disconnected
    DisposableEffect(listenInApp) {
        var listener: WakeWordListener? = null
        if (listenInApp) {
            listener = WakeWordListener(
                context = context,
                onWake = { AryaVoiceService.startConversation(context) },
                onProblem = { AryaEngine.reportProblem(it) },
            ).also { it.start() }
        }
        onDispose { listener?.stop() }
    }

    AryaTheme(settings.theme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (settings.apiKey.isBlank()) {
                ApiKeyScreen(onSave = { key -> Graph.settings.update { it.copy(apiKey = key) } })
            } else {
                MainScreen(onOpenSettings = { showSettings = true }, onToggleMic = { toggleMicrophone() })
            }

            AnimatedVisibility(
                visible = showSettings && settings.apiKey.isNotBlank(),
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 10 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 10 },
            ) {
                SettingsScreen(requestThen = { permissions, then -> requestThen(permissions, then) }, onClose = { showSettings = false })
            }

            if (showMicDisclosure) {
                MicDisclosureDialog(
                    onAccept = {
                        showMicDisclosure = false
                        Graph.settings.update { it.copy(micDisclosureAccepted = true) }
                        askForMicrophoneThenStart()
                    },
                    onDismiss = { showMicDisclosure = false },
                )
            }
        }
    }
}
