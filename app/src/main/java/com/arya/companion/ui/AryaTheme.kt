package com.arya.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AryaBg = Color(0xFF0A0A12)
val AryaSurface = Color(0xFF14141F)
val AryaSurfaceHigh = Color(0xFF1C1C2B)
val AryaText = Color(0xFFF2F1FA)
val AryaMuted = Color(0xFFA3A1BF)

private val Base = Typography()

/** Slightly tighter, heavier type than the Material defaults so headings feel designed rather than stock. */
val AryaTypography = Typography(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 20.sp),
    bodySmall = Base.bodySmall.copy(lineHeight = 17.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val AryaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AryaTheme(themeName: String, content: @Composable () -> Unit) {
    val palette = Themes.of(themeName)
    // Colours glide to the new theme when Arya (or the user) changes it.
    val accent by animateColorAsState(palette.accent, tween(600), label = "accent")
    val accent2 by animateColorAsState(palette.accent2, tween(600), label = "accent2")
    val colors = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = accent2,
        onSecondary = Color(0xFF1A1033),
        background = AryaBg,
        onBackground = AryaText,
        surface = AryaSurface,
        onSurface = AryaText,
        surfaceVariant = AryaSurfaceHigh,
        onSurfaceVariant = AryaMuted,
        error = Color(0xFFFF5A5F),
        onError = Color.White,
        outline = Color(0xFF3A3A55),
    )
    MaterialTheme(colorScheme = colors, typography = AryaTypography, shapes = AryaShapes, content = content)
}
