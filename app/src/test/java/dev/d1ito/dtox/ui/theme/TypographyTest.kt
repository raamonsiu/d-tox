package dev.d1ito.dtox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import dev.d1ito.dtox.domain.ReadingFont
import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyTest {
    @Test
    fun everyTypographyRole_usesLibron() {
        rolesOf(Typography).forEach { (roleName, style) ->
            assertEquals("$roleName must use Libron", LibronFontFamily, style.fontFamily)
        }
    }

    @Test
    fun everyReadingFont_hasItsOwnBundledFamily() {
        val families = ReadingFont.entries.map(::fontFamilyOf)

        assertEquals(LibronFontFamily, fontFamilyOf(ReadingFont.LIBRON))
        assertEquals(OpenDyslexicFontFamily, fontFamilyOf(ReadingFont.OPEN_DYSLEXIC))
        assertEquals(IbmPlexMonoFontFamily, fontFamilyOf(ReadingFont.IBM_PLEX_MONO))
        assertEquals(ReadingFont.entries.size, families.toSet().size)
    }

    @Test
    fun typographyOfEachFont_usesThatFamilyOnEveryRole() {
        ReadingFont.entries.forEach { font ->
            val family = fontFamilyOf(font)
            rolesOf(typographyOf(family)).forEach { (roleName, style) ->
                assertEquals("$roleName must use the $font family", family, style.fontFamily)
            }
        }
    }

    @Test
    fun headings_useBold_andTheParagraphUsesRegular() {
        ReadingFont.entries.forEach { font ->
            val typography = typographyOf(fontFamilyOf(font))

            listOf(typography.titleLarge, typography.titleMedium, typography.titleSmall)
                .forEach { heading -> assertEquals(FontWeight.Bold, heading.fontWeight) }
            assertEquals(FontWeight.Normal, typography.bodyLarge.fontWeight)
        }
    }

    private fun rolesOf(typography: Typography): Map<String, TextStyle> =
        with(typography) {
            mapOf(
                "displayLarge" to displayLarge,
                "displayMedium" to displayMedium,
                "displaySmall" to displaySmall,
                "headlineLarge" to headlineLarge,
                "headlineMedium" to headlineMedium,
                "headlineSmall" to headlineSmall,
                "titleLarge" to titleLarge,
                "titleMedium" to titleMedium,
                "titleSmall" to titleSmall,
                "bodyLarge" to bodyLarge,
                "bodyMedium" to bodyMedium,
                "bodySmall" to bodySmall,
                "labelLarge" to labelLarge,
                "labelMedium" to labelMedium,
                "labelSmall" to labelSmall,
            )
        }
}
