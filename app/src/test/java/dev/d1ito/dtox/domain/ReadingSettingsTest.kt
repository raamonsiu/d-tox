package dev.d1ito.dtox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSettingsTest {
    @Test
    fun defaults_followTheDesignDocument() {
        val defaults = ReadingSettings.Default

        assertEquals(18, defaults.textSizeSp)
        assertTrue(defaults.lineSpacing in ReadingSettings.LINE_SPACING_RANGE)
        assertEquals(PageMargins.NORMAL, defaults.margins)
        assertEquals(ReadingFont.LIBRON, defaults.font)
        assertEquals(ReadingTheme.SYSTEM, defaults.theme)
        assertTrue(defaults.pageAnimation)
        assertTrue(defaults.progressIndicator)
    }

    @Test
    fun edited_acceptsTheEdgesOfEachRange() {
        val smallest =
            ReadingSettings.Default
                .edited(
                    textSizeSp = 14,
                    lineSpacing = 1.5,
                ).getOrThrow()
        val largest =
            ReadingSettings.Default
                .edited(
                    textSizeSp = 28,
                    lineSpacing = 1.7,
                ).getOrThrow()

        assertEquals(14, smallest.textSizeSp)
        assertEquals(1.7, largest.lineSpacing, 0.0)
    }

    @Test
    fun edited_rejectsValuesOutOfRange() {
        val tooSmall = ReadingSettings.Default.edited(textSizeSp = 13).exceptionOrNull()
        val tooLarge = ReadingSettings.Default.edited(textSizeSp = 29).exceptionOrNull()
        val tooTight = ReadingSettings.Default.edited(lineSpacing = 1.4).exceptionOrNull()
        val tooLoose = ReadingSettings.Default.edited(lineSpacing = 1.8).exceptionOrNull()

        assertTrue(tooSmall is ReadingSettingsError.TextSizeOutOfRange)
        assertTrue(tooLarge is ReadingSettingsError.TextSizeOutOfRange)
        assertTrue(tooTight is ReadingSettingsError.LineSpacingOutOfRange)
        assertTrue(tooLoose is ReadingSettingsError.LineSpacingOutOfRange)
    }

    @Test
    fun edited_changesOnlyTheGivenValues() {
        val night = ReadingSettings.Default.edited(theme = ReadingTheme.NIGHT).getOrThrow()

        assertEquals(ReadingTheme.NIGHT, night.theme)
        assertEquals(ReadingSettings.Default.edited(theme = ReadingTheme.NIGHT).getOrThrow(), night)
        assertEquals(ReadingSettings.Default.textSizeSp, night.textSizeSp)
    }
}
