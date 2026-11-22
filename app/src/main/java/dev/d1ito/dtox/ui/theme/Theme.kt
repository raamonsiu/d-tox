package dev.d1ito.dtox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.d1ito.dtox.domain.ReadingFont
import dev.d1ito.dtox.domain.ReadingTheme

private val LocalReadingPalette = staticCompositionLocalOf { ReadingPalettes.Paper }

/**
 * Applies the D-TOX theme: a [ReadingPalette] plus the reading typography in one font family.
 *
 * @param palette theme to apply, usually [readingPaletteFor] the user's choice; by default it
 *   follows the system light/dark mode (paper/night).
 * @param font typeface of every text, reader and interface (RF-14); Libron by default.
 * @param content UI drawn with the theme.
 */
@Composable
fun DTOXTheme(
    palette: ReadingPalette = systemReadingPalette(),
    font: ReadingFont = ReadingFont.LIBRON,
    content: @Composable () -> Unit,
) {
    val typography = remember(font) { typographyOf(fontFamilyOf(font)) }
    MaterialTheme(colorScheme = palette.toColorScheme(), typography = typography) {
        // Text without an explicit color takes the palette's text color, not Material's default
        // black (screens have no Surface to provide it), so titles stay readable in Night.
        CompositionLocalProvider(
            LocalReadingPalette provides palette,
            LocalContentColor provides palette.text,
            content = content,
        )
    }
}

/** Paper in light mode and night in dark mode, following the system setting. */
@Composable
private fun systemReadingPalette(): ReadingPalette =
    readingPaletteFor(ReadingTheme.SYSTEM, systemIsDark = isSystemInDarkTheme())

/** Access to the current D-TOX theme values from composables. */
object DTOXTheme {
    val palette: ReadingPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalReadingPalette.current
}

/**
 * Maps the palette onto every Material color role so no Material component can introduce an
 * accent, a tonal tint or an elevation color.
 */
private fun ReadingPalette.toColorScheme(): ColorScheme =
    (if (isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = text,
        onPrimary = background,
        primaryContainer = highlight,
        onPrimaryContainer = text,
        secondary = textMuted,
        onSecondary = background,
        secondaryContainer = highlight,
        onSecondaryContainer = text,
        tertiary = textMuted,
        onTertiary = background,
        tertiaryContainer = highlight, // e.g. the selected AM/PM of the time input
        onTertiaryContainer = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = highlight,
        onSurfaceVariant = textMuted,
        surfaceTint = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = background,
        surfaceContainer = background,
        surfaceContainerHigh = background,
        surfaceContainerHighest = background,
        outline = divider,
        outlineVariant = divider,
    )
