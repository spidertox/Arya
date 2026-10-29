package com.arya.companion.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.arya.companion.BuildConfig

/** Prominent disclosure shown before the first microphone permission request. */
@Composable
fun MicDisclosureDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AryaSurfaceHigh,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Microphone and your voice") },
        text = {
            Text(
                "While a conversation is active, Arya records audio from your microphone and sends it, together with " +
                    "anything you type, to Google's Gemini API using your own API key. Google processes it under its terms.\n\n" +
                    "Arya does not keep recordings, and nothing is recorded when no conversation is running. " +
                    "You can end a conversation at any time with the stop button.",
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

/** Prominent disclosure shown before turning on the "Hey Arya" wake word. */
@Composable
fun WakeDisclosureDialog(always: Boolean, onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AryaSurfaceHigh,
        shape = RoundedCornerShape(24.dp),
        title = { Text("“Hey Arya” wake word") },
        text = {
            Text(
                "To hear the wake word Arya keeps the microphone on and uses Android's speech recognizer. " +
                    "With an offline speech pack (Android 13+) the audio is checked on your phone; otherwise Google's " +
                    "speech service may process it. Nothing is sent to Gemini until you say “Hey Arya”.\n\n" +
                    (
                        if (always) {
                            "In “Always” mode a notification stays visible while Arya listens, even when the app is closed. " +
                                "You can stop it from the notification or in Settings. Battery use goes up.\n\n"
                        } else {
                            "In this mode Arya only listens while the app is open.\n\n"
                        }
                        ) +
                    "False triggers can happen; you can turn the wake word off at any time.",
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Turn on") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Prominent disclosure shown before asking for contacts access. */
@Composable
fun ContactsDisclosureDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AryaSurfaceHigh,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Call by name") },
        text = {
            Text(
                "When you ask Arya to call someone, she searches your contacts on this phone for that name and opens the " +
                    "dialer with the number; you press call yourself.\n\n" +
                    "Contacts are not uploaded or stored. If several contacts match, their names are shared with Gemini " +
                    "so Arya can ask you which one you mean.",
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

private val REPORT_REASONS = listOf("Offensive or unsafe", "Wrong or misleading", "Something else")

/**
 * In-app report flow for AI-generated content. Arya has no server, so the report is handed to the user's
 * email app (or the issue tracker) with the reported text included only if they choose to send it.
 */
@Composable
fun ReportDialog(excerpt: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var reason by remember { mutableStateOf(REPORT_REASONS[0]) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AryaSurfaceHigh,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (excerpt != null) "Report this reply" else "Report a problem") },
        text = {
            Column {
                if (excerpt != null) {
                    Text("“" + excerpt.take(160) + (if (excerpt.length > 160) "…" else "") + "”", modifier = Modifier.padding(bottom = 12.dp))
                }
                REPORT_REASONS.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = reason == option, role = Role.RadioButton, onClick = { reason = option }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == option, onClick = null)
                        Text(option, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Details (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "This opens your email app so you can review the report before sending.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                sendReport(context, reason, excerpt, note)
                onDismiss()
            }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun sendReport(context: Context, reason: String, excerpt: String?, note: String) {
    val body = buildString {
        appendLine("Reason: $reason")
        if (note.isNotBlank()) appendLine("Details: $note")
        if (excerpt != null) {
            appendLine()
            appendLine("Arya said:")
            appendLine(excerpt)
        }
        appendLine()
        appendLine("Arya ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}")
    }
    val intent = if (BuildConfig.REPORT_EMAIL.isNotBlank()) {
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.REPORT_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, "Arya report: $reason")
            .putExtra(Intent.EXTRA_TEXT, body)
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.REPORT_URL))
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // no email app or browser installed
    }
}
