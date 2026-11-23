package dev.d1ito.dtox.pagination

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import org.junit.Assert.assertEquals
import org.junit.Test

private const val CHAPTER_INDEX = 2
private const val FIRST_BLOCK_LENGTH = 60
private const val FIRST_SHOWN = 20
private const val LAST_SHOWN_END = 30
private const val LAST_BLOCK_LENGTH = 45

class PageHighlightsTest {
    // Block 1 starts on the previous page, block 2 is a separator and block 3 goes on to the next.
    private val page =
        Page(
            startAnchor = anchor(1, FIRST_SHOWN),
            endAnchor = anchor(3, LAST_SHOWN_END),
            fragments =
                listOf(
                    PageFragment(1, FIRST_SHOWN, FIRST_BLOCK_LENGTH),
                    PageFragment(2, 0, 0),
                    PageFragment(3, 0, LAST_SHOWN_END),
                ),
        )
    private val firstFragmentLength = FIRST_BLOCK_LENGTH - FIRST_SHOWN

    @Test
    fun rangeStartingOnThePreviousPage_isClippedToThePageStart() {
        val fromPreviousPage = range(anchor(1, 5), anchor(1, FIRST_SHOWN + 7))

        assertEquals(mapOf(0 to listOf(0 until 7)), drawn(fromPreviousPage))
    }

    @Test
    fun rangeEndingOnTheNextPage_isClippedToThePageEnd() {
        val toNextPage = range(anchor(3, 12), anchor(3, LAST_BLOCK_LENGTH))

        assertEquals(mapOf(2 to listOf(12 until LAST_SHOWN_END)), drawn(toNextPage))
    }

    @Test
    fun rangeAcrossThePage_shadesEveryFragmentWithText() {
        val acrossChapters = range(anchor(1, 0, CHAPTER_INDEX - 1), anchor(0, 0, CHAPTER_INDEX + 1))

        val expected =
            mapOf(0 to listOf(0 until firstFragmentLength), 2 to listOf(0 until LAST_SHOWN_END))
        assertEquals(expected, drawn(acrossChapters))
    }

    @Test
    fun rangeEnds_areExclusive() {
        val endingAtThePageStart = range(anchor(1, 0), anchor(1, FIRST_SHOWN))
        val startingAtThePageEnd = range(anchor(3, LAST_SHOWN_END), anchor(3, LAST_BLOCK_LENGTH))
        val endingOneCharacterIn = range(anchor(3, 0), anchor(3, 1))

        assertEquals(
            mapOf(2 to listOf(0..0)),
            drawn(endingAtThePageStart, startingAtThePageEnd, endingOneCharacterIn),
        )
    }

    @Test
    fun rangesInOtherChapters_areNotDrawn() {
        val previousChapter =
            range(anchor(1, 25, CHAPTER_INDEX - 1), anchor(3, 5, CHAPTER_INDEX - 1))
        val nextChapter = range(anchor(1, 25, CHAPTER_INDEX + 1), anchor(3, 5, CHAPTER_INDEX + 1))

        assertEquals(emptyMap<Int, List<IntRange>>(), drawn(previousChapter, nextChapter))
    }

    @Test
    fun overlappingAndTouchingHighlights_areMergedAndSorted() {
        val later = range(anchor(3, 20), anchor(3, 25))
        val overlapping = range(anchor(3, 5), anchor(3, 12))
        val inside = range(anchor(3, 6), anchor(3, 8))
        val touching = range(anchor(3, 12), anchor(3, 14))

        assertEquals(
            mapOf(2 to listOf(5 until 14, 20 until 25)),
            drawn(later, overlapping, inside, touching),
        )
    }

    @Test
    fun withoutSelection_theHighlightsAreShadedAsTheyAre() {
        val highlighted = listOf(2 until 6, 10 until 12)

        assertEquals(highlighted, highlightShades(highlighted, selected = null))
        assertEquals(highlighted, highlightShades(highlighted, selected = IntRange.EMPTY))
    }

    @Test
    fun selectionOutsideHighlights_isShadedToo() {
        val shades = highlightShades(listOf(2 until 6), selected = 6 until 9)

        assertEquals(listOf(2 until 9), shades)
        assertEquals(listOf(4 until 7), highlightShades(emptyList(), selected = 4 until 7))
    }

    @Test
    fun selectionOverAHighlight_leavesTheCoveredTextUnshaded() {
        val inside = highlightShades(listOf(0 until 10), selected = 3 until 5)
        val overlapping = highlightShades(listOf(0 until 10), selected = 8 until 14)
        val exact = highlightShades(listOf(0 until 10), selected = 0 until 10)

        assertEquals(listOf(0 until 3, 5 until 10), inside)
        assertEquals(listOf(0 until 8, 10 until 14), overlapping)
        assertEquals(emptyList<IntRange>(), exact)
    }

    @Test
    fun selectionAcrossSeveralHighlights_shadesOnlyTheGapsBetweenThem() {
        val shades = highlightShades(listOf(2 until 4, 6 until 8), selected = 0 until 10)

        assertEquals(listOf(0 until 2, 4 until 6, 8 until 10), shades)
    }

    private fun drawn(vararg ranges: AnchorRange): Map<Int, List<IntRange>> =
        page.drawnRangesOf(CHAPTER_INDEX, ranges.toList())

    private fun anchor(
        block: Int,
        offset: Int,
        chapter: Int = CHAPTER_INDEX,
    ): Anchor = Anchor.create(chapter, block, offset).getOrThrow()

    private fun range(
        start: Anchor,
        end: Anchor,
    ): AnchorRange = AnchorRange.create(start, end).getOrThrow()
}
