package com.arya.companion.ui

import androidx.compose.ui.graphics.Color

data class Palette(val accent: Color, val accent2: Color)

/** Colour themes Arya can switch to with the changeBackground tool. */
object Themes {
    val all: Map<String, Palette> = linkedMapOf(
        "violet" to Palette(Color(0xFF8B5CF6), Color(0xFFC4B5FD)),
        "crimson" to Palette(Color(0xFFEF4444), Color(0xFFFCA5A5)),
        "emerald" to Palette(Color(0xFF10B981), Color(0xFF6EE7B7)),
        "celestial" to Palette(Color(0xFF38BDF8), Color(0xFFBAE6FD)),
        "gold" to Palette(Color(0xFFF59E0B), Color(0xFFFDE68A)),
        "rose" to Palette(Color(0xFFF43F5E), Color(0xFFFDA4AF)),
        "charcoal" to Palette(Color(0xFF9CA3AF), Color(0xFFE5E7EB)),
    )

    val names: List<String> get() = all.keys.toList()

    fun of(name: String): Palette = all[name] ?: all.getValue("violet")

    /** Returns the canonical theme name, or null when the input is not a known colour. */
    fun resolve(input: String): String? = input.trim().lowercase().takeIf { it in all }
}
