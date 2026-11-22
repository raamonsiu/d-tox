package dev.d1ito.dtox.pagination

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.domain.quoteAt

/**
 * A character position on a drawn page, as text selection finds it under a finger.
 *
 * @property fragmentIndex index in [Page.fragments].
 * @property localOffset `Char` offset in the text drawn for that fragment: 0 is the fragment's
 *   [PageFragment.startOffset] and the fragment length is its end.
 */
data class PagePosition(
    val fragmentIndex: Int,
    val localOffset: Int,
)

/** Why a [PagePosition] does not point into its page. */
sealed class PagePositionError(
    message: String,
) : IllegalArgumentException(message) {
    class OutsidePage(
        val position: PagePosition,
    ) : PagePositionError("$position is not on the page")
}

/** Number of `Char`s of the block this fragment shows. */
val PageFragment.length: Int
    get() = endOffset - startOffset

/**
 * Translates a position on this page of [chapter] to an anchor in the block text (D-31: drawn
 * offsets and block offsets differ only by the fragment start).
 *
 * @return the anchor with its quote, or [PagePositionError.OutsidePage] when the fragment does
 *   not exist or the offset is past its end.
 */
fun Page.anchorAt(
    chapter: Chapter,
    position: PagePosition,
): Result<Anchor> {
    val fragment =
        fragments
            .getOrNull(position.fragmentIndex)
            ?.takeIf { shown -> position.localOffset in 0..shown.length }
            ?: return Result.failure(PagePositionError.OutsidePage(position))
    val blockOffset = fragment.startOffset + position.localOffset
    return Anchor.create(
        chapter.index,
        fragment.blockIndex,
        blockOffset,
        chapter.quoteAt(fragment.blockIndex, blockOffset),
    )
}

/**
 * The text between two positions on this page, given in either order, possibly in different
 * blocks. The range end is exclusive: the position right after the last selected character, as
 * in [Page.endAnchor].
 *
 * @return the range, or [PagePositionError.OutsidePage] if either position is not on the page.
 */
fun Page.anchorRangeBetween(
    chapter: Chapter,
    first: PagePosition,
    second: PagePosition,
): Result<AnchorRange> {
    val firstAnchor = anchorAt(chapter, first).getOrElse { error -> return Result.failure(error) }
    val secondAnchor = anchorAt(chapter, second).getOrElse { error -> return Result.failure(error) }
    return AnchorRange.create(minOf(firstAnchor, secondAnchor), maxOf(firstAnchor, secondAnchor))
}

/**
 * This range grown to also cover [position], as when dragging after a long press: the text first
 * selected stays selected whichever way the finger moves.
 *
 * @return the grown range; its start keeps the quote of the anchor it comes from.
 */
fun AnchorRange.extendedTo(position: Anchor): Result<AnchorRange> =
    AnchorRange.create(minOf(start, position), maxOf(end, position))

/**
 * The part of [range] drawn in one fragment of this page of chapter [chapterIndex]; the range may
 * start or end on other pages.
 *
 * @return offsets in the fragment's drawn text, or `null` when the fragment shows none of it.
 */
fun Page.drawnRangeOf(
    fragmentIndex: Int,
    chapterIndex: Int,
    range: AnchorRange,
): IntRange? {
    val fragment = fragments.getOrNull(fragmentIndex) ?: return null
    val block = fragment.blockIndex
    val fragmentStart = Anchor.create(chapterIndex, block, fragment.startOffset).getOrNull()
    val fragmentEnd = Anchor.create(chapterIndex, block, fragment.endOffset).getOrNull()
    if (fragmentStart == null || fragmentEnd == null) return null
    val drawnStart = maxOf(range.start, fragmentStart)
    val drawnEnd = minOf(range.end, fragmentEnd)
    return (localOffsetOf(drawnStart, fragment) until localOffsetOf(drawnEnd, fragment))
        .takeIf { drawnStart < drawnEnd }
}

/** Offset of [anchor], which lies inside [fragment], in the fragment's drawn text. */
private fun localOffsetOf(
    anchor: Anchor,
    fragment: PageFragment,
): Int = anchor.offset - fragment.startOffset
