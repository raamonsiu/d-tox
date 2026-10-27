package dev.d1ito.dtox.ui.theme

import dev.d1ito.dtox.domain.ReadingTheme
import org.junit.Assert.assertSame
import org.junit.Test

class ThemeChoiceTest {
    @Test
    fun systemTheme_followsTheSystemLightOrDarkMode() {
        assertSame(
            ReadingPalettes.Paper,
            readingPaletteFor(ReadingTheme.SYSTEM, systemIsDark = false),
        )
        assertSame(
            ReadingPalettes.Night,
            readingPaletteFor(ReadingTheme.SYSTEM, systemIsDark = true),
        )
    }

    @Test
    fun chosenTheme_ignoresTheSystemMode() {
        listOf(false, true).forEach { systemIsDark ->
            assertSame(ReadingPalettes.Paper, readingPaletteFor(ReadingTheme.PAPER, systemIsDark))
            assertSame(ReadingPalettes.Sepia, readingPaletteFor(ReadingTheme.SEPIA, systemIsDark))
            assertSame(ReadingPalettes.Night, readingPaletteFor(ReadingTheme.NIGHT, systemIsDark))
        }
    }

    @Test
    fun everyTheme_hasAPalette() {
        ReadingTheme.entries.forEach { theme -> readingPaletteFor(theme, systemIsDark = false) }
    }
}
