package dev.d1ito.dtox.domain

/** Joins the text of consecutive blocks in [textIn]: a paragraph break reads as a line break. */
const val BLOCK_TEXT_SEPARATOR = "\n"

/** @return whether [other] lies entirely inside this range, its ends included. */
fun AnchorRange.covers(other: AnchorRange): Boolean = start <= other.start && other.end <= end

/**
 * The text of [range] exactly as the blocks hold it, e.g. the copy a highlight keeps: the part of
 * each block between the range ends (the end is exclusive), joined with [BLOCK_TEXT_SEPARATOR].
 * A separator, or a block the range covers no character of (e.g. when it ends at the start of the
 * next block), adds nothing, so the text never starts or ends with a stray line break.
 *
 * @return the text, empty when the range covers no character; `null` when [range] does not lie
 *   in one chapter of this book.
 */
fun Book.textIn(range: AnchorRange): String? {
    if (range.start.chapter != range.end.chapter) return null
    val chapter = chapters.getOrNull(range.start.chapter) ?: return null
    return (range.start.block..range.end.block)
        .mapNotNull { blockIndex ->
            chapter.blocks.getOrNull(blockIndex)?.textOrNull?.let { text ->
                val partStart = if (blockIndex == range.start.block) range.start.offset else 0
                val partEnd = if (blockIndex == range.end.block) range.end.offset else text.length
                text.partBetween(partStart, partEnd)
            }
        }.filter(String::isNotEmpty)
        .joinToString(BLOCK_TEXT_SEPARATOR)
}

/** @return the characters in `[partStart, partEnd)`, limited to this text; empty if none are. */
private fun String.partBetween(
    partStart: Int,
    partEnd: Int,
): String {
    val start = partStart.coerceIn(0, length)
    return substring(start, partEnd.coerceIn(start, length))
}
