package dev.d1ito.dtox.domain

/**
 * A reading position tied to the content, not to a page (docs/02-data-formats.md §3), so it
 * survives changes of screen, font or size. Anchors are ordered by document position.
 *
 * @property chapter 0-based chapter index.
 * @property block block index inside the chapter.
 * @property offset `Char` offset inside the block text.
 * @property quote up to ~40 characters starting at the position, used to relocate the anchor if
 *   the extracted text changes; `null` when not needed (e.g. the end of a range).
 */
@ConsistentCopyVisibility
data class Anchor private constructor(
    val chapter: Int,
    val block: Int,
    val offset: Int,
    val quote: String?,
) : Comparable<Anchor> {
    /** Document order; [quote] is ignored. */
    override fun compareTo(other: Anchor): Int =
        compareValuesBy(this, other, Anchor::chapter, Anchor::block, Anchor::offset)

    /** @return the same position with no [quote]. */
    fun withoutQuote(): Anchor = copy(quote = null)

    companion object {
        /** @return the anchor, or [DocumentModelError.NegativeValue] for a negative position. */
        fun create(
            chapter: Int,
            block: Int,
            offset: Int,
            quote: String? = null,
        ): Result<Anchor> =
            failureForFirstNegative("chapter" to chapter, "block" to block, "offset" to offset)
                ?: Result.success(Anchor(chapter, block, offset, quote))
    }
}

/**
 * A span of text between two anchors, e.g. a highlight (docs/02-data-formats.md §3).
 *
 * @property start first position, inclusive.
 * @property end last position, never before [start]; always without quote, only [start] needs one.
 */
@ConsistentCopyVisibility
data class AnchorRange private constructor(
    val start: Anchor,
    val end: Anchor,
) {
    companion object {
        /**
         * @param end its quote is dropped.
         * @return the range, or [DocumentModelError.RangeEndsBeforeStart].
         */
        fun create(
            start: Anchor,
            end: Anchor,
        ): Result<AnchorRange> =
            if (end >= start) {
                Result.success(AnchorRange(start, end.withoutQuote()))
            } else {
                Result.failure(DocumentModelError.RangeEndsBeforeStart(start, end))
            }
    }
}
