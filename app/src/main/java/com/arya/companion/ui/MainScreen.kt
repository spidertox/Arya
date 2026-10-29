package com.arya.companion.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arya.companion.Graph
import com.arya.companion.data.WakeMode
import com.arya.companion.live.AryaEngine
import com.arya.companion.live.ChatLine
import com.arya.companion.live.ChatRole
import com.arya.companion.live.LiveState
import java.time.LocalTime

private class Suggestion(val emoji: String, val title: String, val prompt: String)

private val SUGGESTIONS = listOf(
    Suggestion("⏱️", "Set a 5 minute timer", "Set a timer for 5 minutes"),
    Suggestion("📱", "Open YouTube", "Open YouTube"),
    Suggestion("🎨", "Change the theme to emerald", "Change the theme to emerald"),
    Suggestion("💬", "Chat with me in Hinglish", "Let's chat in Hinglish. Tell me something interesting about yourself."),
)

private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello, night owl"
}

@Composable
fun MainScreen(onOpenSettings: () -> Unit, onToggleMic: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val state by AryaEngine.state.collectAsStateWithLifecycle()
    val lines by AryaEngine.lines.collectAsStateWithLifecycle()
    val error by AryaEngine.error.collectAsStateWithLifecycle()
    val settings by Graph.settings.settings.collectAsStateWithLifecycle()

    var draft by rememberSaveable { mutableStateOf("") }
    var actionLine by remember { mutableStateOf<ChatLine?>(null) }
    var reportLine by remember { mutableStateOf<ChatLine?>(null) }
    val listState = rememberLazyListState()

    val showTyping = state == LiveState.Thinking && lines.lastOrNull()?.role == ChatRole.User
    val itemCount = lines.size + if (showTyping) 1 else 0
    val lastLength = lines.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(itemCount, lastLength) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            AryaEngine.sendText(clean)
        }
    }

    val ringColor by animateColorAsState(
        when (state) {
            LiveState.Disconnected -> colors.outline
            LiveState.Listening -> Color(0xFF34D399)
            LiveState.Speaking -> colors.primary
            else -> Color(0xFFFBBF24)
        },
        tween(300),
        label = "ring",
    )

    AuroraBackground {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // ---- top bar ---------------------------------------------------------------------
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaceAvatar(diameter = 40.dp, ring = ringColor)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Arya", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (settings.wakeMode == WakeMode.Off) "Voice companion" else "Say “Hey Arya”",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                GlassIconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = colors.onSurface)
                }
            }

            // ---- stage -----------------------------------------------------------------------
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Character(state)
                StatePill(
                    state = state,
                    idleHint = if (settings.wakeMode == WakeMode.Off) "Tap the mic to talk" else "Say “Hey Arya” or tap the mic",
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }

            // ---- conversation ----------------------------------------------------------------
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (lines.isEmpty()) {
                    EmptyState(userName = settings.userName, onPick = { send(it) })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().fadeTop(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(lines, key = { it.id }) { line ->
                            Bubble(line, Modifier.animateItem()) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                actionLine = line
                            }
                        }
                        if (showTyping) {
                            item(key = "typing") { TypingBubble(Modifier.animateItem()) }
                        }
                    }
                }

                error?.let { message ->
                    ErrorBanner(message, onDismiss = { AryaEngine.clearError() }, modifier = Modifier.align(Alignment.TopCenter))
                }
            }

            // ---- composer --------------------------------------------------------------------
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.weight(1f).glass(RoundedCornerShape(28.dp), fill = 0.075f)) {
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Message Arya…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            send(draft)
                            draft = ""
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = colors.primary,
                            focusedPlaceholderColor = colors.onSurfaceVariant,
                            unfocusedPlaceholderColor = colors.onSurfaceVariant,
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    send(draft)
                                    draft = ""
                                },
                                enabled = draft.isNotBlank(),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (draft.isNotBlank()) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        },
                    )
                }
                MicButton(state = state, onClick = onToggleMic)
            }
        }
    }

    actionLine?.let { line ->
        MessageActionsDialog(
            line = line,
            onCopy = {
                copyToClipboard(context, line.text)
                actionLine = null
            },
            onReport = {
                reportLine = line
                actionLine = null
            },
            onDismiss = { actionLine = null },
        )
    }
    reportLine?.let { line -> ReportDialog(excerpt = line.text, onDismiss = { reportLine = null }) }
}

// ---- pieces ---------------------------------------------------------------------------------------

@Composable
private fun EmptyState(userName: String, onPick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val greeting = remember { greetingFor(LocalTime.now().hour) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (userName.isBlank()) greeting else "$greeting, ${userName.trim()}",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text("What's on your mind? Talk to me, or try one of these.", color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        SUGGESTIONS.forEach { suggestion ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(18.dp), fill = 0.06f)
                    .clickable(role = Role.Button) { onPick(suggestion.prompt) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text(suggestion.emoji)
                }
                Text(suggestion.title, modifier = Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text("›", color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun Bubble(line: ChatLine, modifier: Modifier = Modifier, onLongPress: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val isUser = line.role == ChatRole.User
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 20.dp,
    )
    val bubbleModifier = Modifier
        .widthIn(max = 300.dp)
        .then(
            if (isUser) {
                Modifier.clip(shape).background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.9f), colors.primary.copy(alpha = 0.62f))))
            } else {
                Modifier.glass(shape, fill = 0.07f, border = 0.12f)
            },
        )
        .pointerInput(line.id) { detectTapGestures(onLongPress = { onLongPress() }) }
        .padding(horizontal = 14.dp, vertical = 10.dp)

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            FaceAvatar(diameter = 28.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            line.text,
            modifier = bubbleModifier,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isUser) Color.White else colors.onSurface,
        )
    }
}

@Composable
private fun TypingBubble(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        FaceAvatar(diameter = 28.dp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .glass(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp), fill = 0.07f, border = 0.12f)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            TypingDots(MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.error.copy(alpha = 0.18f))
            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
        Text(message, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Dismiss") }
    }
}

@Composable
private fun MessageActionsDialog(line: ChatLine, onCopy: () -> Unit, onReport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AryaSurfaceHigh,
        shape = RoundedCornerShape(24.dp),
        text = { Text(line.text.take(280) + if (line.text.length > 280) "…" else "") },
        confirmButton = { TextButton(onClick = onCopy) { Text("Copy") } },
        dismissButton = {
            Row {
                if (line.role == ChatRole.Model) TextButton(onClick = onReport) { Text("Report") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager.setPrimaryClip(ClipData.newPlainText("Arya", text))
    // Android 13+ shows its own "copied" confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}
