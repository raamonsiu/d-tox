package dev.d1ito.dtox.pagination

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CHARACTERS_PER_LINE = 10
private const val LINE_HEIGHT = 20
private const val CHAPTER_INDEX = 3
private const val VIEWPORT_WIDTH = 300

class PaginatorTest {
    @Test
    fun paragraphThatDoesNotFit_continuesOnTheNextPage() {
        val pages = paginate(listOf(paragraph(lines = 5)), visibleLines = 3)
        assertEquals(
            listOf(listOf(PageFragment(0, 0, 30)), listOf(PageFragment(0, 30, 50))),
            pages.map(Page::fragments),
        )
        assertEquals(anchor(0, 30), pages[0].endAnchor)
        assertEquals(anchor(0, 30), pages[1].startAnchor)
    }

    @Test
    fun blocksThatFillExactlyOnePage_stayTogether() {
        val pages = paginate(listOf(paragraph(lines = 1), paragraph(lines = 2)), visibleLines = 3)
        assertEquals(1, pages.size)
        assertEquals(
            listOf(PageFragment(0, 0, 10), PageFragment(1, 0, 20)),
            pages.single().fragments,
        )
    }

    @Test
    fun spacingBeforeABlock_isDroppedAtTheTopOfAPage() {
        // Page 1: 2 lines. Block 1 needs spacing 10 + 3 lines = 70 > 60, so it moves to page 2,
        // where without the spacing its 3 lines fit exactly.
        val measurer = FakeMeasurer(spacingBefore = 10)
        val pages =
            paginate(
                listOf(paragraph(lines = 2), paragraph(lines = 3)),
                visibleLines = 3,
                measurer = measurer,
            )
        assertEquals(
            listOf(
                listOf(PageFragment(0, 0, 20)),
                listOf(PageFragment(1, 0, 30)),
            ),
            pages.map(Page::fragments),
        )
    }

    @Test
    fun spacingBeforeTheFirstBlockOfTheChapter_isDropped() {
        val measurer = FakeMeasurer(spacingBefore = 10)
        val pages = paginate(listOf(paragraph(lines = 3)), visibleLines = 3, measurer = measurer)
        assertEquals(1, pages.size)
    }

    @Test
    fun spacingBeforeABlock_countsInsideAPage() {
        val measurer = FakeMeasurer(spacingBefore = 10)
        val pages =
            paginate(
                listOf(paragraph(lines = 1), paragraph(lines = 2)),
                visibleLines = 3,
                measurer = measurer,
            )
        assertEquals(2, pages.size)
    }

    @Test
    fun lineTallerThanThePage_getsAPageOfItsOwn() {
        val measurer =
            FakeMeasurer(lineHeightFor = { block ->
                if (block is Block.Heading) {
                    LINE_HEIGHT *
                        5
                } else {
                    LINE_HEIGHT
                }
            })
        val heading = Block.Heading.create(1, "Giant").getOrThrow()
        val pages =
            paginate(
                listOf(paragraph(lines = 1), heading, paragraph(lines = 1)),
                visibleLines = 3,
                measurer = measurer,
            )
        assertEquals(listOf(0, 1, 2), pages.map { page -> page.fragments.single().blockIndex })
    }

    @Test
    fun chapterStartingWithALineTallerThanThePage_hasNoEmptyPage() {
        val measurer = FakeMeasurer(lineHeightFor = { LINE_HEIGHT * 5 })
        val pages =
            paginate(
                listOf(paragraph(lines = 1), paragraph(lines = 1)),
                visibleLines = 3,
                measurer = measurer,
            )
        assertEquals(listOf(0, 1), pages.map { page -> page.fragments.single().blockIndex })
    }

    @Test
    fun separator_takesOneLine() {
        val pages =
            paginate(
                listOf(paragraph(lines = 2), Block.Separator, paragraph(lines = 1)),
                visibleLines = 3,
            )
        assertEquals(
            listOf(listOf(0, 1), listOf(2)),
            pages.map { page ->
                page.fragments.map(PageFragment::blockIndex)
            },
        )
        assertEquals(PageFragment(1, 0, 0), pages[0].fragments[1])
    }

    @Test
    fun emptyChapter_givesOneEmptyPage() {
        val page = paginate(emptyList(), visibleLines = 3).single()
        assertEquals(emptyList<PageFragment>(), page.fragments)
        assertEquals(anchor(0, 0), page.startAnchor)
    }

    @Test
    fun anchorAndPage_mapBothWays() {
        val pages =
            paginate(
                listOf(paragraph(lines = 4), paragraph(lines = 4), paragraph(lines = 1)),
                visibleLines = 3,
            )
        pages.forEachIndexed {
            index,
            page,
            ->
            assertEquals(index, pageIndexOf(pages, page.startAnchor))
        }
        assertEquals(0, pageIndexOf(pages, anchor(0, 25)))
        assertEquals(1, pageIndexOf(pages, anchor(0, 35)))
        assertEquals(1, pageIndexOf(pages, anchor(0, 40)))
        assertEquals(2, pageIndexOf(pages, anchor(1, 25)))
    }

    @Test
    fun anchorOutsideTheChapter_mapsToFirstOrLastPage() {
        val pages = paginate(listOf(paragraph(lines = 7)), visibleLines = 3)
        val before = Anchor.create(CHAPTER_INDEX - 1, 99, 99).getOrThrow()
        val after = Anchor.create(CHAPTER_INDEX + 1, 0, 0).getOrThrow()
        assertEquals(0, pageIndexOf(pages, before))
        assertEquals(pages.lastIndex, pageIndexOf(pages, after))
    }

    @Test
    fun pages_carryTheChapterIndexInTheirAnchors() {
        val pages = paginate(listOf(paragraph(lines = 5)), visibleLines = 2)
        assertTrue(
            pages.all { page ->
                page.startAnchor.chapter == CHAPTER_INDEX &&
                    page.endAnchor.chapter == CHAPTER_INDEX
            },
        )
    }

    @Test
    fun nonPositiveViewport_isRejected() {
        listOf(0 to 100, 100 to 0, -1 to 100).forEach { (width, height) ->
            assertTrue(
                Viewport.create(width, height).exceptionOrNull() is PaginationError.InvalidViewport,
            )
        }
    }

    @Test
    fun measurerWithNegativeOffsets_failsInsteadOfThrowing() {
        val brokenMeasurer =
            BlockMeasurer {
                _,
                _,
                ->
                BlockLayout(spacingBefore = 0, lines = listOf(LineLayout(-5, 3, LINE_HEIGHT)))
            }
        val chapter = Chapter.create(CHAPTER_INDEX, null, listOf(paragraph(lines = 1))).getOrThrow()
        val viewport = Viewport.create(VIEWPORT_WIDTH, LINE_HEIGHT * 3).getOrThrow()
        assertTrue(Paginator.paginate(chapter, viewport, brokenMeasurer).isFailure)
    }

    private fun paginate(
        blocks: List<Block>,
        visibleLines: Int,
        measurer: BlockMeasurer = FakeMeasurer(),
    ): List<Page> {
        val chapter = Chapter.create(CHAPTER_INDEX, null, blocks).getOrThrow()
        val viewport = Viewport.create(VIEWPORT_WIDTH, LINE_HEIGHT * visibleLines).getOrThrow()
        return Paginator.paginate(chapter, viewport, measurer).getOrThrow()
    }

    private fun paragraph(lines: Int) = Block.Paragraph("x".repeat(CHARACTERS_PER_LINE * lines))

    private fun anchor(
        block: Int,
        offset: Int,
    ) = Anchor.create(CHAPTER_INDEX, block, offset).getOrThrow()

    /** Fixed characters per line; every line [lineHeightFor] pixels high. */
    private class FakeMeasurer(
        private val spacingBefore: Int = 0,
        private val lineHeightFor: (Block) -> Int = { LINE_HEIGHT },
    ) : BlockMeasurer {
        override fun measure(
            block: Block,
            availableWidth: Int,
        ): BlockLayout {
            val height = lineHeightFor(block)
            val text =
                textOf(block) ?: return BlockLayout(spacingBefore, listOf(LineLayout(0, 0, height)))
            val lines =
                (text.indices step CHARACTERS_PER_LINE).map { start ->
                    LineLayout(start, minOf(start + CHARACTERS_PER_LINE, text.length), height)
                }
            return BlockLayout(spacingBefore, lines)
        }

        private fun textOf(block: Block): String? =
            when (block) {
                is Block.Heading -> block.text
                is Block.Paragraph -> block.text
                is Block.Quote -> block.text
                is Block.ListItem -> block.text
                Block.Separator -> null
            }
    }
}
