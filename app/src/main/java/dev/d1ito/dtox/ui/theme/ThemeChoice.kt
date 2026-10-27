package dev.d1ito.dtox.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.d1ito.dtox.domain.ReadingTheme

/**
 * The palette for the theme chosen in the settings (RF-15).
 *
 * @param theme the user's choice; [ReadingTheme.SYSTEM] follows the system light/dark mode.
 * @param systemIsDark whether the system is in dark mode right now.
 * @return paper or night for [ReadingTheme.SYSTEM], otherwise the chosen palette.
 */
fun readingPaletteFor(
    theme: ReadingTheme,
    systemIsDark: Boolean,
): ReadingPalette =
    when (theme) {
        ReadingTheme.SYSTEM -> if (systemIsDark) ReadingPalettes.Night else ReadingPalettes.Paper
        ReadingTheme.PAPER -> ReadingPalettes.Paper
        ReadingTheme.SEPIA -> ReadingPalettes.Sepia
        ReadingTheme.NIGHT -> ReadingPalettes.Night
    }

/**
 * Paints the window background with [palette] and gives the system bars icons that read on it,
 * so rotations, transitions and bars shown by a swipe never flash another theme's color.
 */
@Composable
fun WindowMatchesPalette(palette: ReadingPalette) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }
    }
}
