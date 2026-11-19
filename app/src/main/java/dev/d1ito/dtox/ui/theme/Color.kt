package dev.d1ito.dtox.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors of one reading theme (see docs/04-design.md).
 *
 * There is no accent color: hierarchy comes from size, weight and space. Every theme shares
 * this structure and only changes the values.
 *
 * @property isDark whether the palette is light text on a dark background.
 * @property background page and screen background.
 * @property text running text; must keep a contrast of at least 7:1 (AAA) on [background] and
 *   on [highlight].
 * @property textMuted secondary text (quotes, progress); at least 4.5:1 (AA) on [background] and
 *   on [highlight].
 * @property divider thin separator lines; decorative, no contrast requirement.
 * @property highlight subtle shading behind highlighted passages; text drawn on it must stay
 *   readable, the shading itself is deliberately low-contrast.
 */
@Immutable
data class ReadingPalette(
    val isDark: Boolean,
    val background: Color,
    val text: Color,
    val textMuted: Color,
    val divider: Color,
    val highlight: Color,
)

/** The reading themes offered by the app. Pure white and pure black are avoided on purpose. */
object ReadingPalettes {
    val Paper =
        ReadingPalette(
            isDark = false,
            background = Color(0xFFF7F5EF),
            text = Color(0xFF1C1C1A),
            textMuted = Color(0xFF676661),
            divider = Color(0xFFDEDBD2),
            highlight = Color(0xFFE9E4D2),
        )

    val Sepia =
        ReadingPalette(
            isDark = false,
            background = Color(0xFFF1E9D2),
            text = Color(0xFF2B261B),
            textMuted = Color(0xFF635B48),
            divider = Color(0xFFD6CCB0),
            highlight = Color(0xFFE0D4AE),
        )

    val Night =
        ReadingPalette(
            isDark = true,
            background = Color(0xFF0F0F0E),
            text = Color(0xFFD9D6CC),
            textMuted = Color(0xFF96948A),
            divider = Color(0xFF2A2926),
            highlight = Color(0xFF2E2D27),
        )

    /** All palettes, so checks such as contrast tests cover every theme. */
    val all: List<ReadingPalette> = listOf(Paper, Sepia, Night)
}
