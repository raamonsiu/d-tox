package dev.d1ito.dtox.domain

import org.junit.Assert.assertEquals
import org.junit.Test

private const val TOLERANCE = 1e-9

class ReadingPercentTest {
    // 10 + 10 characters in chapter 0, an empty chapter 1, 20 characters in chapter 2: 40 in total.
    private val book =
        book(
            listOf(
                Block.Paragraph("a".repeat(10)),
                Block.Separator,
                Block.Paragraph("b".repeat(10)),
            ),
            emptyList(),
            listOf(Block.Paragraph("c".repeat(20))),
        )

    @Test
    fun start_isZero() {
        assertEquals(0.0, book.readingPercentAt(anchor(0, 0, 0)), TOLERANCE)
    }

    @Test
    fun endOfTheLastBlock_isOne() {
        assertEquals(1.0, book.readingPercentAt(anchor(2, 0, 20)), TOLERANCE)
    }

    @Test
    fun middle_countsCharactersOfPreviousChaptersAndBlocks() {
        assertEquals(0.5, book.readingPercentAt(anchor(2, 0, 0)), TOLERANCE)
        assertEquals(0.375, book.readingPercentAt(anchor(0, 2, 5)), TOLERANCE)
    }

    @Test
    fun emptyChaptersAndSeparators_addNothing() {
        assertEquals(0.5, book.readingPercentAt(anchor(1, 0, 0)), TOLERANCE)
        assertEquals(0.25, book.readingPercentAt(anchor(0, 1, 0)), TOLERANCE)
    }

    @Test
    fun positionsPastTheEnd_areClamped() {
        assertEquals(0.25, book.readingPercentAt(anchor(0, 0, 99)), TOLERANCE)
        assertEquals(0.5, book.readingPercentAt(anchor(0, 9, 0)), TOLERANCE)
        assertEquals(1.0, book.readingPercentAt(anchor(5, 0, 0)), TOLERANCE)
    }

    @Test
    fun bookWithoutText_isZero() {
        val empty = book(emptyList(), listOf(Block.Separator))

        assertEquals(0.0, empty.readingPercentAt(anchor(1, 0, 0)), TOLERANCE)
    }

    private fun anchor(
        chapter: Int,
        block: Int,
        offset: Int,
    ): Anchor = Anchor.create(chapter, block, offset).getOrThrow()

    private fun book(vararg chapterBlocks: List<Block>): Book =
        Book
            .create(
                BookIdentifier.parse("sha256:0123456789abcdef0123456789abcdef").getOrThrow(),
                BookMetadata("Libro", null, "es"),
                chapterBlocks.withIndex().map { (index, blocks) -> chapter(index, blocks) },
            ).getOrThrow()

    private fun chapter(
        index: Int,
        blocks: List<Block>,
    ): Chapter = Chapter.create(index, null, blocks).getOrThrow()
}
