package com.arya.companion.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arya.companion.R
import com.arya.companion.live.AryaEngine
import com.arya.companion.live.LiveState
import kotlin.math.sin

private const val TWO_PI = 6.2831855f

// ---- surfaces & helpers --------------------------------------------------------------------------

/** Frosted-glass look: a faint white fill and hairline border that picks up the animated background behind it. */
fun Modifier.glass(shape: Shape, fill: Float = 0.06f, border: Float = 0.10f): Modifier =
    this.clip(shape)
        .background(Color.White.copy(alpha = fill))
        .border(1.dp, Color.White.copy(alpha = border), shape)

/** Fades the top edge of scrolling content into the background. */
fun Modifier.fadeTop(height: Dp = 28.dp): Modifier =
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = height.toPx()),
                blendMode = BlendMode.DstIn,
            )
        }

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), fill = 0.055f).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun GlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.size(44.dp).glass(CircleShape, fill = 0.07f).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// ---- background -----------------------------------------------------------------------------------

private const val MOTES = 22

/** The app backdrop: ink-dark base, two slowly drifting accent-tinted glows and a few floating motes. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val sway by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(AryaBg)
            .drawBehind {
                val w = size.width
                val h = size.height
                val first = Offset(w * (0.15f + 0.45f * sway), h * 0.14f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(primary.copy(alpha = 0.26f), Color.Transparent), center = first, radius = w * 0.95f),
                    radius = w * 0.95f,
                    center = first,
                )
                val second = Offset(w * (0.95f - 0.5f * sway), h * 0.78f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(secondary.copy(alpha = 0.13f), Color.Transparent), center = second, radius = w * 0.85f),
                    radius = w * 0.85f,
                    center = second,
                )
                for (i in 0 until MOTES) {
                    val seed = i * 0.618034f
                    val x = ((seed * 7.31f) % 1f) * w
                    val speed = 0.35f + (i % 5) * 0.12f
                    val y = ((((seed * 3.17f) % 1f) - drift * speed) % 1f + 1f) % 1f * h
                    val twinkle = 0.5f + 0.5f * sin(drift * TWO_PI * (1 + i % 3) + i)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f + 0.12f * twinkle),
                        radius = (1.2f + (i % 3) * 0.7f) * density,
                        center = Offset(x, y),
                    )
                }
            },
        content = content,
    )
}

// ---- voice feedback --------------------------------------------------------------------------------

/** Smoothed voice level (0..1) for animations; costs nothing while [active] is false. */
@Composable
private fun rememberVoiceLevel(active: Boolean): FloatState {
    val level = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        while (active) {
            withFrameNanos { }
            level.floatValue += (AryaEngine.levelNow() - level.floatValue) * 0.3f
        }
        level.floatValue = 0f
    }
    return level
}

/** Equalizer bars that move with the voice. */
@Composable
fun VoiceBars(active: Boolean, color: Color, modifier: Modifier = Modifier, bars: Int = 5) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "phase",
    )
    val level by rememberVoiceLevel(active)
    Canvas(modifier.size(width = 34.dp, height = 20.dp)) {
        val barWidth = size.width / (bars * 2 - 1)
        for (i in 0 until bars) {
            val wave = (sin(phase * TWO_PI + i * 0.9f) + 1f) / 2f
            val amplitude = if (active) 0.22f + 0.78f * wave * (0.3f + level).coerceAtMost(1f) else 0.18f
            val barHeight = size.height * amplitude.coerceIn(0.14f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * barWidth * 2, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

/** Three bouncing dots: Arya is thinking. */
@Composable
fun TypingDots(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "phase",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        val local = (((phase - index * 0.18f) % 1f) + 1f) % 1f
                        val pulse = (sin(local * TWO_PI) + 1f) / 2f
                        alpha = 0.3f + 0.7f * pulse
                        translationY = -3f * density * pulse
                    }
                    .background(color, CircleShape),
            )
        }
    }
}

/** The small status capsule under the portrait. */
@Composable
fun StatePill(state: LiveState, idleHint: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.glass(RoundedCornerShape(50), fill = 0.09f).padding(horizontal = 16.dp, vertical = 9.dp)) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "state",
        ) { current ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (current) {
                    LiveState.Listening -> VoiceBars(active = true, color = colors.primary)
                    LiveState.Speaking -> VoiceBars(active = true, color = colors.secondary)
                    LiveState.Thinking -> TypingDots(colors.secondary)
                    LiveState.Connecting -> TypingDots(colors.onSurfaceVariant)
                    LiveState.Disconnected -> Box(Modifier.size(8.dp).background(colors.onSurfaceVariant.copy(alpha = 0.6f), CircleShape))
                }
                Text(
                    text = when (current) {
                        LiveState.Disconnected -> idleHint
                        LiveState.Connecting -> "Connecting"
                        LiveState.Listening -> "Listening"
                        LiveState.Thinking -> "Thinking"
                        LiveState.Speaking -> "Speaking"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                )
            }
        }
    }
}

// ---- avatar, buttons -------------------------------------------------------------------------------

/** Round close-up of the portrait's face, optionally with a status ring. */
@Composable
fun FaceAvatar(diameter: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    Box(
        modifier
            .size(diameter)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape).padding(3.dp) else Modifier)
            .clip(CircleShape)
            .background(AryaSurfaceHigh),
    ) {
        Image(
            painter = painterResource(R.drawable.arya_portrait),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 2.3f
                scaleY = 2.3f
                transformOrigin = TransformOrigin(0.5f, 0.27f)
                translationY = size.height * 0.2f
            },
        )
    }
}

/** The big round microphone button with a pulsing ring while a conversation is live. */
@Composable
fun MicButton(state: LiveState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val live = state != LiveState.Disconnected
    val haptics = LocalHapticFeedback.current
    val level by rememberVoiceLevel(live)
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "pulse",
    )
    val top by animateColorAsState(if (live) Color(0xFFFF7A7F) else colors.secondary, tween(300), label = "top")
    val bottom by animateColorAsState(if (live) colors.error else colors.primary, tween(300), label = "bottom")
    val ringColor = if (live) colors.error else colors.primary

    Box(
        modifier
            .size(84.dp)
            .drawBehind {
                if (live) {
                    val base = 32.dp.toPx()
                    drawCircle(
                        color = ringColor.copy(alpha = 0.35f * (1f - pulse)),
                        radius = base + 10.dp.toPx() * pulse,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(color = ringColor.copy(alpha = 0.10f + 0.25f * level), radius = base + 6.dp.toPx() * (0.3f + level))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .shadow(12.dp, CircleShape, ambientColor = ringColor, spotColor = ringColor)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(top, bottom)))
                .clickable(role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (live) R.drawable.ic_stop else R.drawable.ic_mic),
                contentDescription = if (live) "End conversation" else "Start voice conversation",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val brush = if (enabled) Brush.linearGradient(listOf(colors.primary, colors.primary.copy(alpha = 0.78f))) else Brush.linearGradient(listOf(Color(0xFF2A2A3D), Color(0xFF2A2A3D)))
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else colors.onSurfaceVariant)
    }
}

// ---- settings building blocks -----------------------------------------------------------------------

@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), fill = 0.05f).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val background by animateColorAsState(if (selected) colors.primary else Color.Transparent, tween(200), label = "segment")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun ThemeSwatch(name: String, palette: Palette, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(palette.accent2, palette.accent)))
                .then(if (selected) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(
            name.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun VoiceChip(name: String, trait: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) colors.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (selected) colors.primary else Color.White.copy(alpha = 0.10f), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(trait, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
