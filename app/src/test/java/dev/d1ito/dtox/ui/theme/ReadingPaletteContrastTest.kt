package dev.d1ito.dtox.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA minimum contrast for normal text (docs/04-design.md). */
private const val MINIMUM_TEXT_CONTRAST = 4.5f

/** Offset WCAG adds to both luminances so pure black does not divide by zero. */
private const val LUMINANCE_OFFSET = 0.05f

class ReadingPaletteContrastTest {
    @Test
    fun everyPalette_textMeetsMinimumContrastOnBackground() {
        ReadingPalettes.all.forEach { palette ->
            assertReadable(palette.text, palette.background, palette)
        }
    }

    @Test
    fun everyPalette_mutedTextMeetsMinimumContrastOnBackground() {
        ReadingPalettes.all.forEach { palette ->
            assertReadable(palette.textMuted, palette.background, palette)
        }
    }

    private fun assertReadable(
        foreground: Color,
        background: Color,
        palette: ReadingPalette,
    ) {
        val contrast = contrastRatio(foreground, background)
        assertTrue(
            "Contrast $contrast < $MINIMUM_TEXT_CONTRAST in $palette",
            contrast >= MINIMUM_TEXT_CONTRAST,
        )
    }

    /** WCAG 2 contrast ratio between two opaque colors, from 1 (equal) to 21 (black/white). */
    private fun contrastRatio(
        first: Color,
        second: Color,
    ): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
    }
}
