package dev.d1ito.dtox.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA minimum contrast for normal text (docs/04-design.md). */
private const val MINIMUM_TEXT_CONTRAST = 4.5f

/** WCAG AAA minimum contrast for normal text, required of running text (docs/04-design.md). */
private const val ENHANCED_TEXT_CONTRAST = 7f

/** Offset WCAG adds to both luminances so pure black does not divide by zero. */
private const val LUMINANCE_OFFSET = 0.05f

/**
 * Enforces the contrast table of docs/04-design.md: every text-bearing token pair of every theme.
 * Divider and the highlight shading against the background are decorative and not checked.
 */
class ReadingPaletteContrastTest {
    @Test
    fun everyPalette_textMeetsEnhancedContrastOnBackground() {
        ReadingPalettes.all.forEach { palette ->
            assertContrast(palette.text, palette.background, ENHANCED_TEXT_CONTRAST, palette)
        }
    }

    @Test
    fun everyPalette_textMeetsEnhancedContrastOnHighlight() {
        ReadingPalettes.all.forEach { palette ->
            assertContrast(palette.text, palette.highlight, ENHANCED_TEXT_CONTRAST, palette)
        }
    }

    @Test
    fun everyPalette_mutedTextMeetsMinimumContrastOnBackground() {
        ReadingPalettes.all.forEach { palette ->
            assertContrast(palette.textMuted, palette.background, MINIMUM_TEXT_CONTRAST, palette)
        }
    }

    @Test
    fun everyPalette_mutedTextMeetsMinimumContrastOnHighlight() {
        ReadingPalettes.all.forEach { palette ->
            assertContrast(palette.textMuted, palette.highlight, MINIMUM_TEXT_CONTRAST, palette)
        }
    }

    private fun assertContrast(
        foreground: Color,
        background: Color,
        minimumContrast: Float,
        palette: ReadingPalette,
    ) {
        val contrast = contrastRatio(foreground, background)
        assertTrue(
            "Contrast $contrast < $minimumContrast in $palette",
            contrast >= minimumContrast,
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
