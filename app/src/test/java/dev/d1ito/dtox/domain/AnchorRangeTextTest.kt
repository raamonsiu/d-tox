package dev.d1ito.dtox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FIRST = "Nadie te devolverá los años."
private const val SECOND = "Seguirá su curso la vida."

class AnchorRangeTextTest {
    private val book =
        Book
            .create(
                BookIdentifier.parse("sha256:0123456789abcdef0123456789abcdef").getOrThrow(),
                BookMetadata("Cartas", null, "es"),
                listOf(
                    Chapter
                        .create(
                            0,
                            null,
                            listOf(
                                Block.Paragraph(FIRST),
                                Block.Separator,
                                Block.Paragraph(SECOND),
                            ),
                        ).getOrThrow(),
                ),
            ).getOrThrow()

    @Test
    fun textIn_oneBlock_isTheExactPartBetweenTheEnds() {
        assertEquals("devolverá", book.textIn(range(0, 9, 0, 18)))
        assertEquals("", book.textIn(range(0, 4, 0, 4)))
    }

    @Test
    fun textIn_acrossBlocks_joinsThemWithALineBreak_andSkipsTheSeparator() {
        assertEquals("los años.\nSeguirá", book.textIn(range(0, 19, 2, 7)))
    }

    @Test
    fun textIn_endingAtTheStartOfABlock_addsNoTrailingLineBreak() {
        assertEquals("años.", book.textIn(range(0, 23, 2, 0)))
    }

    @Test
    fun textIn_outsideTheBook_orAcrossChapters_isNull() {
        val otherChapter = Anchor.create(1, 0, 0).getOrThrow()
        val acrossChapters = AnchorRange.create(anchor(0, 0), otherChapter).getOrThrow()

        assertNull(book.textIn(acrossChapters))
        assertNull(book.textIn(AnchorRange.create(otherChapter, otherChapter).getOrThrow()))
    }

    @Test
    fun covers_includesTheEnds_andNotARangeThatGoesPastThem() {
        val highlight = range(0, 4, 2, 7)

        assertTrue(highlight.covers(highlight))
        assertTrue(highlight.covers(range(0, 9, 0, 18)))
        assertFalse(highlight.covers(range(0, 0, 0, 5)))
        assertFalse(highlight.covers(range(2, 0, 2, 8)))
    }

    private fun range(
        startBlock: Int,
        startOffset: Int,
        endBlock: Int,
        endOffset: Int,
    ): AnchorRange =
        AnchorRange
            .create(
                anchor(startBlock, startOffset),
                anchor(endBlock, endOffset),
            ).getOrThrow()

    private fun anchor(
        block: Int,
        offset: Int,
    ): Anchor = Anchor.create(0, block, offset).getOrThrow()
}
