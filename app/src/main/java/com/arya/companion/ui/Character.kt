package com.arya.companion.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arya.companion.R
import com.arya.companion.live.AryaEngine
import com.arya.companion.live.LiveState

/**
 * Arya's portrait. It is a still image, so "talking" is shown with motion and a glow that follows her voice
 * (no lip-sync). Replace res/drawable-nodpi/arya_portrait.webp to change the character.
 */
@Composable
fun Character(state: LiveState, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary

    val transition = rememberInfiniteTransition(label = "arya-idle")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val sway by transition.animateFloat(
        initialValue = -0.7f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "sway",
    )

    // Smoothed voice level, written every frame and only read while drawing (no recomposition).
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            level += (AryaEngine.levelNow() - level) * 0.35f
        }
    }

    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val height = if (keyboardOpen) {
        110.dp
    } else {
        (LocalConfiguration.current.screenHeightDp * 0.31f).dp.coerceIn(160.dp, 320.dp)
    }

    Box(modifier.height(height).aspectRatio(900f / 738f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                val radius = size.minDimension * 0.5f * (1f + level * 0.25f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = (0.26f + level * 0.55f).coerceAtMost(0.85f)), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            },
        )
        Image(
            painter = painterResource(R.drawable.arya_portrait),
            contentDescription = "Arya",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val amplitude = if (state == LiveState.Speaking) 3f else 5f
                    translationY = -bob * amplitude * density
                    rotationZ = if (state == LiveState.Thinking) sway else 0f
                    val grow = if (state == LiveState.Speaking) 0.014f else 0.008f
                    scaleX = 1f + grow * bob
                    scaleY = 1f + grow * bob
                    alpha = if (state == LiveState.Connecting) 0.7f + 0.3f * bob else 1f
                    transformOrigin = TransformOrigin(0.5f, 0.85f)
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                // Soft fade where the full-length artwork is cropped at the bottom.
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(0.82f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
    }
}
