package dev.d1ito.dtox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Scale from docs/04-design.md. The system serif is a placeholder until the fonts are packaged.
private val readingFontFamily = FontFamily.Serif
private val baseFontSize = 18.sp
private val readingLineHeight = 1.6.em
private const val HEADING_ONE_SCALE = 1.4f
private const val HEADING_TWO_SCALE = 1.2f

private val paragraphStyle =
    TextStyle(
        fontFamily = readingFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = baseFontSize,
        lineHeight = readingLineHeight,
    )

private val headingStyle = paragraphStyle.copy(fontWeight = FontWeight.Medium)

/**
 * Typography mapped onto Material roles: `titleLarge` is H1 (book/chapter title), `titleMedium`
 * is H2, `titleSmall` is H3 and `bodyLarge` is the running paragraph. One family for the whole app.
 */
val Typography =
    Typography(
        titleLarge = headingStyle.copy(fontSize = baseFontSize * HEADING_ONE_SCALE),
        titleMedium = headingStyle.copy(fontSize = baseFontSize * HEADING_TWO_SCALE),
        titleSmall = headingStyle,
        bodyLarge = paragraphStyle,
    )
