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

// Scale from docs/04-design.md. Libron has no medium weight, so headings use its bold.
private val baseFontSize = 18.sp
private val readingLineHeight = 1.6.em
private const val HEADING_ONE_SCALE = 1.4f
private const val HEADING_TWO_SCALE = 1.2f

private val paragraphStyle =
    TextStyle(
        fontFamily = LibronFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = baseFontSize,
        lineHeight = readingLineHeight,
    )

private val headingStyle = paragraphStyle.copy(fontWeight = FontWeight.Bold)

/** Material's default scale with Libron on every role: one family for reading and interface. */
private val interfaceTypography =
    Typography().run {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = LibronFontFamily),
            displayMedium = displayMedium.copy(fontFamily = LibronFontFamily),
            displaySmall = displaySmall.copy(fontFamily = LibronFontFamily),
            headlineLarge = headlineLarge.copy(fontFamily = LibronFontFamily),
            headlineMedium = headlineMedium.copy(fontFamily = LibronFontFamily),
            headlineSmall = headlineSmall.copy(fontFamily = LibronFontFamily),
            bodyMedium = bodyMedium.copy(fontFamily = LibronFontFamily),
            bodySmall = bodySmall.copy(fontFamily = LibronFontFamily),
            labelLarge = labelLarge.copy(fontFamily = LibronFontFamily),
            labelMedium = labelMedium.copy(fontFamily = LibronFontFamily),
            labelSmall = labelSmall.copy(fontFamily = LibronFontFamily),
        )
    }

/**
 * Typography mapped onto Material roles: `titleLarge` is H1 (book/chapter title), `titleMedium`
 * is H2, `titleSmall` is H3 and `bodyLarge` is the running paragraph. The other roles keep
 * Material's sizes for interface text, all in Libron.
 */
val Typography =
    interfaceTypography.copy(
        titleLarge = headingStyle.copy(fontSize = baseFontSize * HEADING_ONE_SCALE),
        titleMedium = headingStyle.copy(fontSize = baseFontSize * HEADING_TWO_SCALE),
        titleSmall = headingStyle,
        bodyLarge = paragraphStyle,
    )
