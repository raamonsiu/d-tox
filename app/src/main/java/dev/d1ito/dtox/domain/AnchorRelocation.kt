package dev.d1ito.dtox.domain

import kotlin.math.abs

/** Characters kept in [Anchor.quote] (docs/02-data-formats.md §3). */
const val ANCHOR_QUOTE_LENGTH = 40

/**
 * Creates the anchor for a position, with the quote that later lets [relocate] find it again.
 *
 * @return the anchor, or [DocumentModelError.NegativeValue]; the quote is `null` when the
 *   position has no text (a separator, or outside the book).
 */
fun Book.anchorAt(
    chapter: Int,
    block: Int,
    offset: Int,
): Result<Anchor> = Anchor.create(chapter, block, offset, quoteAt(chapter, block, offset))

/**
 * Up to [ANCHOR_QUOTE_LENGTH] characters of text starting at the position, or `null` when the
 * position has no text.
 */
fun Book.quoteAt(
    chapter: Int,
    block: Int,
    offset: Int,
): String? =
    textAt(chapter, block)
        ?.takeIf { text -> offset in 0..text.lastIndex }
        ?.let { text -> text.substring(offset, minOf(text.length, offset + ANCHOR_QUOTE_LENGTH)) }

/**
 * Finds where [anchor] points in this book after the extracted text may have changed
 * (docs/02-data-formats.md §3). If the quote no longer matches at the stored position, it is
 * searched in the same block, then in the same chapter, choosing the occurrence closest to the
 * stored position; otherwise the anchor falls back to the start of its block, of its chapter or,
 * when the chapter no longer exists, of the last chapter.
 *
 * @return an anchor inside the book with a quote taken from the new position; [anchor] itself
 *   when it still matches. An anchor without quote is only clamped to the book.
 */
fun Book.relocate(anchor: Anchor): Anchor {
    val quote = anchor.quote?.takeIf { text -> text.isNotEmpty() }
    val text = textAt(anchor.chapter, anchor.block)
    return when {
        quote == null -> clamped(anchor)
        text?.startsWith(quote, anchor.offset) == true -> anchor
        else -> quoteInBlock(anchor, quote) ?: quoteInChapter(anchor, quote) ?: fallbackFor(anchor)
    }
}

private fun Book.textAt(
    chapter: Int,
    block: Int,
): String? =
    chapters
        .getOrNull(chapter)
        ?.blocks
        ?.getOrNull(block)
        ?.textOrNull

private fun Book.quoteInBlock(
    anchor: Anchor,
    quote: String,
): Anchor? {
    val text = textAt(anchor.chapter, anchor.block) ?: return null
    return closestOccurrence(text, quote, anchor.offset)?.let { offset ->
        anchorWithQuote(anchor.chapter, anchor.block, offset)
    }
}

/** Searches every block of the chapter, nearest block first. */
private fun Book.quoteInChapter(
    anchor: Anchor,
    quote: String,
): Anchor? {
    val blocks = chapters.getOrNull(anchor.chapter)?.blocks ?: return null
    return blocks.indices
        .sortedBy { block -> abs(block - anchor.block) }
        .firstNotNullOfOrNull { block ->
            blocks[block]
                .textOrNull
                ?.let { text ->
                    closestOccurrence(text, quote, 0)
                }?.let { offset ->
                    anchorWithQuote(anchor.chapter, block, offset)
                }
        }
}

private fun Book.blockExists(anchor: Anchor): Boolean =
    chapters
        .getOrNull(anchor.chapter)
        ?.blocks
        ?.indices
        ?.contains(anchor.block) == true

private fun Book.fallbackFor(anchor: Anchor): Anchor =
    when {
        blockExists(anchor) -> anchorWithQuote(anchor.chapter, anchor.block, 0)
        anchor.chapter in chapters.indices -> anchorWithQuote(anchor.chapter, 0, 0)
        else -> anchorWithQuote(maxOf(chapters.lastIndex, 0), 0, 0)
    }

/** An existing block keeps its position, offset limited to the text; otherwise [fallbackFor]. */
private fun Book.clamped(anchor: Anchor): Anchor {
    if (!blockExists(anchor)) return fallbackFor(anchor)
    val textLength = textAt(anchor.chapter, anchor.block)?.length ?: 0
    return anchorWithQuote(anchor.chapter, anchor.block, anchor.offset.coerceAtMost(textLength))
}

/** @return the start of the occurrence of [quote] in [text] nearest to [offset], or `null`. */
private fun closestOccurrence(
    text: String,
    quote: String,
    offset: Int,
): Int? =
    generateSequence(text.indexOf(quote).takeIf { start -> start >= 0 }) { previous ->
        text.indexOf(quote, previous + 1).takeIf { start -> start >= 0 }
    }.minByOrNull { start -> abs(start - offset) }

private fun Book.anchorWithQuote(
    chapter: Int,
    block: Int,
    offset: Int,
): Anchor =
    // Callers only pass indices derived from the book or clamped to zero, never negative values.
    anchorAt(chapter, block, offset).getOrThrow()
