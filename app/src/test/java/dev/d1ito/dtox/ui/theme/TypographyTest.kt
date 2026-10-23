package dev.d1ito.dtox.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyTest {
    @Test
    fun everyTypographyRole_usesLibron() {
        val roles =
            with(Typography) {
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
        roles.forEach { (roleName, style) ->
            assertEquals("$roleName must use Libron", LibronFontFamily, style.fontFamily)
        }
    }
}
