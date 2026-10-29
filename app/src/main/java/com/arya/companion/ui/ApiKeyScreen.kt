package com.arya.companion.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arya.companion.BuildConfig
import com.arya.companion.R

/** First-run screen: meet Arya, then connect the user's own Gemini API key. */
@Composable
fun ApiKeyScreen(onSave: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    var value by remember { mutableStateOf("") } // not rememberSaveable: never write the key into saved state

    AuroraBackground {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The portrait fades out at the bottom so the headline sits right under her.
            Image(
                painter = painterResource(R.drawable.arya_portrait),
                contentDescription = "Arya",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(0.78f to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
            Text("Meet Arya", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "Your voice AI companion. Talk in Hindi, English or Hinglish, and she answers out loud.",
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Feature("🎙️", "Talk naturally", "Real-time voice. Interrupt her any time, just like a call.")
                Feature("📱", "Gets things done", "Opens apps, sets alarms and timers, searches the web.")
                Feature("🔒", "Private by design", "Your key, your phone. No Arya server, no ads, no tracking.")
            }
            Spacer(Modifier.height(22.dp))

            GlassCard {
                Text("Connect Gemini", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Arya uses your own Google Gemini API key. It is free to create and stays encrypted on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                PrimaryButton("Continue", onClick = { onSave(value.trim()) }, enabled = value.trim().length >= 20)
                TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Get a free key at aistudio.google.com")
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Voice and text you share with Arya are processed by Google's Gemini API under your key. Replies are AI-generated and can be wrong.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { uriHandler.openUri(BuildConfig.PRIVACY_URL) }) { Text("Privacy policy") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Feature(emoji: String, title: String, description: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
