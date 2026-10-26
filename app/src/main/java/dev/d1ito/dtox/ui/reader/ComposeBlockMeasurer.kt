package dev.d1ito.dtox.ui.reader

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.textOrNull
import dev.d1ito.dtox.pagination.BlockLayout
import dev.d1ito.dtox.pagination.BlockMeasurer
import dev.d1ito.dtox.pagination.LineLayout
import kotlin.math.ceil

/**
 * [BlockMeasurer] backed by Compose text layout, using [ReadingStyles] so measuring and drawing
 * agree. Not thread-safe: [textMeasurer] keeps a cache, so use one instance per pagination job
 * and call it from one thread at a time. Production pagination runs off the main thread
 * (docs/03-architecture.md §Pagination, step 4).
 *
 * @param textMeasurer from `rememberTextMeasurer()`.
 * @param styles the styles [PageContent] will draw with.
 * @param density converts the style's dp values to pixels.
 */
class ComposeBlockMeasurer(
    private val textMeasurer: TextMeasurer,
    private val styles: ReadingStyles,
    private val density: Density,
) : BlockMeasurer {
    override fun measure(
        block: Block,
        availableWidth: Int,
    ): BlockLayout {
        val style = styles.styleOf(block)
        val indent = with(density) { style.startIndent.roundToPx() }
        val layout =
            textMeasurer.measure(
                text = block.textOrNull ?: SEPARATOR_GLYPHS,
                style = style.textStyle,
                constraints = Constraints(maxWidth = (availableWidth - indent).coerceAtLeast(1)),
            )
        val lines = linesOf(layout, hasOwnText = block.textOrNull != null)
        return BlockLayout(
            spacingBefore = with(density) { style.spacingBefore.roundToPx() },
            lines = lines,
        )
    }

    /**
     * Each line height is rounded up on its own, so any run of lines measures at least as tall as
     * the same lines drawn as a separate fragment (whose height is the rounded-up sum).
     */
    private fun linesOf(
        layout: TextLayoutResult,
        hasOwnText: Boolean,
    ): List<LineLayout> =
        (0 until layout.lineCount).map { line ->
            val height = ceil(layout.getLineBottom(line) - layout.getLineTop(line)).toInt()
            if (hasOwnText) {
                LineLayout(layout.getLineStart(line), layout.getLineEnd(line), height)
            } else {
                // A separator's glyphs are decoration: no offsets inside the (empty) block text.
                LineLayout(0, 0, height)
            }
        }
}
