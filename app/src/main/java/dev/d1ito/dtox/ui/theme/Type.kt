package dev.d1ito.dtox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.ReadingFont

/**
 * Libron (SIL OFL 1.1, Reserved Font Name "Libron"; see res/raw/libron_license.txt). The files
 * are bundled unmodified: subsetting or editing them would require renaming the font.
 */
val LibronFontFamily =
    FontFamily(
        Font(R.font.libron_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.libron_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.libron_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.libron_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )

/**
 * OpenDyslexic (SIL OFL 1.1, Reserved Font Name "OpenDyslexic"; see
 * res/raw/opendyslexic_license.txt, D-37). Bundled unmodified, like Libron.
 */
val OpenDyslexicFontFamily =
    FontFamily(
        Font(R.font.opendyslexic_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.opendyslexic_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.opendyslexic_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.opendyslexic_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )

/**
 * IBM Plex Mono (SIL OFL 1.1, Reserved Font Name "Plex"; see res/raw/ibm_plex_mono_license.txt,
 * D-38). Bundled unmodified, like Libron.
 */
val IbmPlexMonoFontFamily =
    FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.ibm_plex_mono_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.ibm_plex_mono_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.ibm_plex_mono_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )

/**
 * @param font the typeface chosen in the settings (RF-14).
 * @return its bundled family, with Regular, Italic, Bold and BoldItalic files, so no weight or
 *   style is ever synthesized (D-26).
 */
fun fontFamilyOf(font: ReadingFont): FontFamily =
    when (font) {
        ReadingFont.LIBRON -> LibronFontFamily
        ReadingFont.OPEN_DYSLEXIC -> OpenDyslexicFontFamily
        ReadingFont.IBM_PLEX_MONO -> IbmPlexMonoFontFamily
    }

// Scale from docs/04-design.md. The bundled families have no medium weight, so headings use bold.
private val baseFontSize = 18.sp
private val readingLineHeight = 1.6.em
private const val HEADING_ONE_SCALE = 1.4f
private const val HEADING_TWO_SCALE = 1.2f

/**
 * Typography mapped onto Material roles: `titleLarge` is H1 (book/chapter title), `titleMedium`
 * is H2, `titleSmall` is H3 and `bodyLarge` is the running paragraph. The other roles keep
 * Material's sizes for interface text. Every role uses [fontFamily]: one family for reading and
 * interface (docs/04-design.md, Tipografía).
 *
 * @param fontFamily family for every role, usually [fontFamilyOf] the user's choice.
 * @return the typography for the Material theme.
 */
fun typographyOf(fontFamily: FontFamily): Typography {
    val paragraphStyle =
        TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = baseFontSize,
            lineHeight = readingLineHeight,
        )
    val headingStyle = paragraphStyle.copy(fontWeight = FontWeight.Bold)
    return Typography().run {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = fontFamily),
            displayMedium = displayMedium.copy(fontFamily = fontFamily),
            displaySmall = displaySmall.copy(fontFamily = fontFamily),
            headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
            headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
            headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
            titleLarge = headingStyle.copy(fontSize = baseFontSize * HEADING_ONE_SCALE),
            titleMedium = headingStyle.copy(fontSize = baseFontSize * HEADING_TWO_SCALE),
            titleSmall = headingStyle,
            bodyLarge = paragraphStyle,
            bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
            bodySmall = bodySmall.copy(fontFamily = fontFamily),
            labelLarge = labelLarge.copy(fontFamily = fontFamily),
            labelMedium = labelMedium.copy(fontFamily = fontFamily),
            labelSmall = labelSmall.copy(fontFamily = fontFamily),
        )
    }
}

/** The default typography, in Libron (D-13). */
val Typography = typographyOf(LibronFontFamily)
