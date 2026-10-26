package dev.d1ito.dtox.pagination

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Block

/**
 * One laid-out line of a block's text.
 *
 * @property startOffset first `Char` of the line in the block text, inclusive.
 * @property endOffset end of the line in the block text, exclusive.
 * @property height line height in pixels, including line spacing.
 */
data class LineLayout(
    val startOffset: Int,
    val endOffset: Int,
    val height: Int,
)

/**
 * How a block lays out at a given width.
 *
 * @property spacingBefore vertical gap in pixels above the block; dropped at the top of a page.
 * @property lines the block's lines in order; empty for a block that takes no space.
 */
data class BlockLayout(
    val spacingBefore: Int,
    val lines: List<LineLayout>,
)

/** Measures blocks with the current typography. The Android implementation wraps Compose. */
fun interface BlockMeasurer {
    /**
     * @param block block to lay out; a [Block.Separator] is one line without text (offsets 0..0).
     * @param availableWidth width of the reading column in pixels, greater than 0.
     * @return the block layout; offsets must lie inside the block text.
     */
    fun measure(
        block: Block,
        availableWidth: Int,
    ): BlockLayout
}

/** Size of the page area in pixels; only created through [create]. */
@ConsistentCopyVisibility
data class Viewport private constructor(
    val width: Int,
    val height: Int,
) {
    companion object {
        /** @return the viewport, or [PaginationError.InvalidViewport] if a side is not positive. */
        fun create(
            width: Int,
            height: Int,
        ): Result<Viewport> =
            if (width > 0 && height > 0) {
                Result.success(Viewport(width, height))
            } else {
                Result.failure(PaginationError.InvalidViewport(width, height))
            }
    }
}

/**
 * The part of one block shown on a page.
 *
 * @property blockIndex block position in the chapter.
 * @property startOffset first `Char` shown, inclusive.
 * @property endOffset end of the shown text, exclusive.
 */
data class PageFragment(
    val blockIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * One screen of text. Pages are derived, never persisted (docs/02-data-formats.md §3).
 *
 * @property startAnchor position of the first character shown.
 * @property endAnchor position right after the last character shown (exclusive).
 * @property fragments block parts shown, top to bottom.
 */
data class Page(
    val startAnchor: Anchor,
    val endAnchor: Anchor,
    val fragments: List<PageFragment>,
)

/** Why a chapter could not be paginated. */
sealed class PaginationError(
    message: String,
) : IllegalArgumentException(message) {
    class InvalidViewport(
        val width: Int,
        val height: Int,
    ) : PaginationError("Viewport must be positive, was ${width}x$height")
}
