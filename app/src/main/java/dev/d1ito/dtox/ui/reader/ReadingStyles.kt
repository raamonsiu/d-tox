package dev.d1ito.dtox.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.ui.theme.DTOXTheme

/** Shown instead of a [Block.Separator], which has no text of its own. */
const val SEPARATOR_GLYPHS = "·  ·  ·"

/** Prefix drawn in the indentation of every list item, outside the block text (D-31). */
const val LIST_ITEM_MARKER = "–"

private const val SECOND_HEADING_LEVEL = 2

/** Marker gutter in multiples of the body font size, so the dash never reaches the text. */
private const val LIST_MARKER_GUTTER_IN_FONT_SIZES = 1.2f

private val headingOneSpacing = 32.dp
private val headingSpacing = 24.dp
private val blockSpacing = 12.dp
private val listItemSpacing = 6.dp
private val quoteIndent = 24.dp
private val listLevelIndent = 16.dp
private val minimumListMarkerGutter = 16.dp

/**
 * How one block is laid out (docs/04-design.md, Markdown-like hierarchy). The same value is used
 * to measure and to draw, so pages never overflow because of a style mismatch.
 *
 * @property textStyle font, size, line height and color of the block text.
 * @property startIndent horizontal space before the text (quotes, lists).
 * @property spacingBefore vertical gap above the block, dropped at the top of a page.
 * @property listMarker text drawn inside [startIndent] on the first line, or `null`.
 * @property listMarkerGutter width reserved for [listMarker] at the end of [startIndent].
 */
@Immutable
data class BlockStyle(
    val textStyle: TextStyle,
    val startIndent: Dp,
    val spacingBefore: Dp,
    val listMarker: String? = null,
    val listMarkerGutter: Dp = 0.dp,
)

/**
 * Builds the [BlockStyle] of each block. Every text style is adjusted with [forPagination].
 *
 * @param heading text style per heading level 1..3.
 * @param body running text style.
 * @param mutedBody style for quotes and separators.
 * @param listMarkerGutter width reserved for the list marker; should grow with the font size.
 */
@Immutable
class ReadingStyles(
    heading: Map<Int, TextStyle>,
    body: TextStyle,
    mutedBody: TextStyle,
    private val listMarkerGutter: Dp,
) {
    private val heading = heading.mapValues { (_, style) -> style.forPagination() }
    private val body = body.forPagination()
    private val mutedBody = mutedBody.forPagination()

    /**
     * @param block any block of a chapter.
     * @return the style used both to measure and to draw [block].
     */
    fun styleOf(block: Block): BlockStyle =
        when (block) {
            is Block.Heading -> headingStyleOf(block.level)
            is Block.Paragraph -> BlockStyle(body, startIndent = 0.dp, spacingBefore = blockSpacing)
            is Block.Quote ->
                BlockStyle(
                    mutedBody,
                    startIndent = quoteIndent,
                    spacingBefore = blockSpacing,
                )
            is Block.ListItem ->
                BlockStyle(
                    textStyle = body,
                    startIndent = listLevelIndent * block.depth + listMarkerGutter,
                    spacingBefore = listItemSpacing,
                    listMarker = LIST_ITEM_MARKER,
                    listMarkerGutter = listMarkerGutter,
                )
            Block.Separator ->
                BlockStyle(
                    mutedBody.copy(textAlign = TextAlign.Center),
                    startIndent = 0.dp,
                    spacingBefore = headingSpacing,
                )
        }

    private fun headingStyleOf(level: Int): BlockStyle {
        val spacing =
            if (level ==
                Block.Heading.HIGHEST_LEVEL
            ) {
                headingOneSpacing
            } else {
                headingSpacing
            }
        val style = heading[level] ?: heading.getValue(Block.Heading.LOWEST_LEVEL)
        return BlockStyle(style, startIndent = 0.dp, spacingBefore = spacing)
    }
}

/**
 * Layout rules that keep drawing equal to measuring (D-31): greedy line breaking without
 * hyphenation, so a fragment starting at a line boundary wraps exactly as the whole block did;
 * and no line height trimming, so every line keeps its full height wherever a fragment starts.
 */
private fun TextStyle.forPagination(): TextStyle =
    copy(
        lineBreak = LineBreak.Simple,
        hyphens = Hyphens.None,
        lineHeightStyle =
            LineHeightStyle(
                LineHeightStyle.Alignment.Center,
                LineHeightStyle.Trim.None,
            ),
    )

/**
 * @return reading styles for the current theme: Libron scale from Typography, colors from the
 *   palette, list gutter scaled with the body font size (and so with the system font scale).
 */
@Composable
@ReadOnlyComposable
fun readingStyles(): ReadingStyles {
    val typography = MaterialTheme.typography
    val palette = DTOXTheme.palette
    val bodyFontSize = with(LocalDensity.current) { typography.bodyLarge.fontSize.toDp() }
    return ReadingStyles(
        heading =
            mapOf(
                Block.Heading.HIGHEST_LEVEL to typography.titleLarge.copy(color = palette.text),
                SECOND_HEADING_LEVEL to typography.titleMedium.copy(color = palette.text),
                Block.Heading.LOWEST_LEVEL to typography.titleSmall.copy(color = palette.text),
            ),
        body = typography.bodyLarge.copy(color = palette.text),
        mutedBody = typography.bodyLarge.copy(color = palette.textMuted),
        listMarkerGutter =
            max(
                minimumListMarkerGutter,
                bodyFontSize * LIST_MARKER_GUTTER_IN_FONT_SIZES,
            ),
    )
}
