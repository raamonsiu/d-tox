package dev.d1ito.dtox.domain

/**
 * Fraction of the book read up to [anchor], counted in characters of block text rather than pages,
 * so it does not change with the screen, font or size (docs/03-architecture.md, Paginación).
 * Separators and empty chapters count as zero characters.
 *
 * @return a value in `0.0..1.0`: `0.0` at the start (and for a book without text), `1.0` at the
 *   end of the last block. Positions past the end of a block, chapter or book are clamped.
 */
fun Book.readingPercentAt(anchor: Anchor): Double {
    val chapterLengths = chapters.map { chapter -> chapter.blocks.sumOf(Block::textLength) }
    val totalLength = chapterLengths.sum()
    if (totalLength == 0) return 0.0
    val readLength =
        if (anchor.chapter > chapters.lastIndex) {
            totalLength
        } else {
            chapterLengths.take(anchor.chapter).sum() + lengthReadInChapter(anchor)
        }
    return (readLength.toDouble() / totalLength).coerceIn(0.0, 1.0)
}

/** Characters before [anchor] inside its chapter, which must exist. */
private fun Book.lengthReadInChapter(anchor: Anchor): Int {
    val blocks = chapters[anchor.chapter].blocks
    val blocksBefore = blocks.take(anchor.block).sumOf(Block::textLength)
    val offsetInBlock =
        anchor.offset.coerceAtMost(
            blocks.getOrNull(anchor.block)?.textLength() ?: 0,
        )
    return blocksBefore + offsetInBlock
}

private fun Block.textLength(): Int = textOrNull?.length ?: 0
