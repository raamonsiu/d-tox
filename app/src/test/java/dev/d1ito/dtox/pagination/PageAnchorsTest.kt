package dev.d1ito.dtox.pagination

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CHAPTER_INDEX = 2
private const val FIRST = "Nadie te devolverá los años, nadie te restituirá a ti mismo."
private const val SECOND = "Seguirá su curso la vida y no volverá atrás."

class PageAnchorsTest {
    private val chapter =
        Chapter
            .create(
                CHAPTER_INDEX,
                null,
                listOf(
                    Block.Paragraph("Antes."),
                    Block.Paragraph(FIRST),
                    Block.Separator,
                    Block.Paragraph(SECOND),
                ),
            ).getOrThrow()

    // The page starts in the middle of block 1 ("nadie te restituirá…"), then shows the separator
    // and the start of block 3.
    private val firstShown = FIRST.indexOf("nadie")
    private val secondShownEnd = SECOND.indexOf(" y no")
    private val page =
        Page(
            startAnchor = anchor(1, firstShown),
            endAnchor = anchor(3, secondShownEnd),
            fragments =
                listOf(
                    PageFragment(1, firstShown, FIRST.length),
                    PageFragment(2, 0, 0),
                    PageFragment(3, 0, secondShownEnd),
                ),
        )

    @Test
    fun positionInAFragment_mapsToTheBlockOffset_withAQuote() {
        val localOffset = "nadie te ".length

        val anchor = page.anchorAt(chapter, PagePosition(0, localOffset)).getOrThrow()

        val blockOffset = FIRST.indexOf("restituirá")
        assertEquals(anchor(1, blockOffset), anchor.withoutQuote())
        assertEquals(FIRST.substring(blockOffset), anchor.quote)
    }

    @Test
    fun endOfAFragment_isAValidPosition_withoutQuoteAtTheEndOfTheBlock() {
        val anchor = page.anchorAt(chapter, PagePosition(0, FIRST.length - firstShown))

        assertEquals(anchor(1, FIRST.length), anchor.getOrThrow())
        assertNull(anchor.getOrThrow().quote)
    }

    @Test
    fun positionsOutsideThePage_fail() {
        val outside =
            listOf(
                PagePosition(fragmentIndex = 3, localOffset = 0),
                PagePosition(fragmentIndex = -1, localOffset = 0),
                PagePosition(fragmentIndex = 1, localOffset = 1),
                PagePosition(fragmentIndex = 2, localOffset = secondShownEnd + 1),
                PagePosition(fragmentIndex = 0, localOffset = -1),
            )

        outside.forEach { position ->
            assertTrue(
                "$position",
                page.anchorAt(chapter, position).exceptionOrNull() is
                    PagePositionError.OutsidePage,
            )
        }
    }

    @Test
    fun rangeAcrossBlocks_isOrdered_whicheverPositionComesFirst() {
        val inFirst = PagePosition(0, "nadie te ".length)
        val inLast = PagePosition(2, "Seguirá".length)

        val forward = page.anchorRangeBetween(chapter, inFirst, inLast).getOrThrow()
        val backward = page.anchorRangeBetween(chapter, inLast, inFirst).getOrThrow()

        assertEquals(forward, backward)
        assertEquals(anchor(1, FIRST.indexOf("restituirá")), forward.start.withoutQuote())
        assertEquals("restituirá", forward.start.quote?.take("restituirá".length))
        assertEquals(anchor(3, "Seguirá".length), forward.end)
        assertNull(forward.end.quote)
    }

    @Test
    fun rangeWithAPositionOutsideThePage_fails() {
        val range = page.anchorRangeBetween(chapter, PagePosition(0, 0), PagePosition(7, 0))

        assertTrue(range.exceptionOrNull() is PagePositionError.OutsidePage)
    }

    @Test
    fun extendedRange_keepsTheFirstSelection_inBothDirections() {
        val word = range(anchor(1, 20), anchor(1, 25))

        assertEquals(range(anchor(1, 20), anchor(3, 4)), word.extendedTo(anchor(3, 4)).getOrThrow())
        assertEquals(range(anchor(1, 2), anchor(1, 25)), word.extendedTo(anchor(1, 2)).getOrThrow())
        assertEquals(word, word.extendedTo(anchor(1, 22)).getOrThrow())
    }

    @Test
    fun drawnRange_coversEachFragmentOfACrossBlockSelection() {
        val selection = range(anchor(1, FIRST.indexOf("restituirá")), anchor(3, "Seguirá".length))

        val drawn =
            page.fragments.indices.map { index ->
                page.drawnRangeOf(index, CHAPTER_INDEX, selection)
            }

        val restOfFirst = "nadie te ".length until FIRST.length - firstShown
        assertEquals(listOf(restOfFirst, null, 0 until "Seguirá".length), drawn)
    }

    @Test
    fun drawnRange_isNull_forFragmentsOutsideTheRangeOrAnotherChapter() {
        val inLastBlock = range(anchor(3, 2), anchor(3, 5))

        assertNull(page.drawnRangeOf(0, CHAPTER_INDEX, inLastBlock))
        assertNull(page.drawnRangeOf(2, CHAPTER_INDEX + 1, inLastBlock))
        assertNull(page.drawnRangeOf(9, CHAPTER_INDEX, inLastBlock))
        assertEquals(2 until 5, page.drawnRangeOf(2, CHAPTER_INDEX, inLastBlock))
    }

    @Test
    fun drawnRange_mapsBackToTheSamePositions() {
        val start = PagePosition(0, 3)
        val end = PagePosition(0, 12)
        val selection = page.anchorRangeBetween(chapter, start, end).getOrThrow()

        assertEquals(3 until 12, page.drawnRangeOf(0, CHAPTER_INDEX, selection))
    }

    private fun anchor(
        block: Int,
        offset: Int,
    ): Anchor = Anchor.create(CHAPTER_INDEX, block, offset).getOrThrow()

    private fun range(
        start: Anchor,
        end: Anchor,
    ): AnchorRange = AnchorRange.create(start, end).getOrThrow()
}
