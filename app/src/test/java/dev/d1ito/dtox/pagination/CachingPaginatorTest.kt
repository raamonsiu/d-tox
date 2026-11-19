package dev.d1ito.dtox.pagination

import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

private const val VIEWPORT_WIDTH = 300
private const val VIEWPORT_HEIGHT = 500
private const val LINE_HEIGHT = 20
private const val CHAPTER_COUNT = 4
private const val LAST_CHAPTER = CHAPTER_COUNT - 1

class CachingPaginatorTest {
    private val book = BookIdentifier.fromHex("0".repeat(32)).getOrThrow()
    private val viewport = Viewport.create(VIEWPORT_WIDTH, VIEWPORT_HEIGHT).getOrThrow()
    private val chapters =
        (0 until CHAPTER_COUNT).map { index ->
            Chapter.create(index, null, listOf(Block.Paragraph("Capítulo $index"))).getOrThrow()
        }
    private val measurer = CountingMeasurer()
    private val paginator = CachingPaginator(Dispatchers.Unconfined)

    @Test
    fun chapterAskedAgain_isNotMeasuredAgain() =
        runBlocking {
            val layout = layoutOf(style = Any())
            val first = paginator.pagesOf(chapters[0], layout)
            val second = paginator.pagesOf(chapters[0], layout)
            assertEquals(first, second)
            assertEquals(1, measurer.measuredBlocks)
        }

    @Test
    fun newStyleOrViewport_paginatesAgain() =
        runBlocking {
            paginator.pagesOf(chapters[0], layoutOf(style = Any()))
            paginator.pagesOf(chapters[0], layoutOf(style = Any()))
            val rotated = Viewport.create(VIEWPORT_HEIGHT, VIEWPORT_WIDTH).getOrThrow()
            paginator.pagesOf(chapters[0], PageLayout(book, rotated, Any(), measurer))
            assertEquals(3, measurer.measuredBlocks)
        }

    @Test
    fun withoutBook_nothingIsCached() =
        runBlocking {
            val layout = PageLayout(null, viewport, Any(), measurer)
            paginator.pagesOf(chapters[0], layout)
            paginator.pagesOf(chapters[0], layout)
            assertEquals(2, measurer.measuredBlocks)
        }

    @Test
    fun prefetch_paginatesTheNeighboursIntoTheCache() =
        runBlocking {
            val layout = layoutOf(style = Any())
            paginator.prefetchAround(chapters, chapterIndex = 1, layout)
            assertEquals(listOf(2, 0), measurer.measuredChapters)
            paginator.pagesOf(chapters[0], layout)
            paginator.pagesOf(chapters[2], layout)
            assertEquals(2, measurer.measuredBlocks)
        }

    @Test
    fun prefetchAtTheFirstChapter_skipsTheMissingPreviousOne() =
        runBlocking {
            paginator.prefetchAround(chapters, chapterIndex = 0, layoutOf(style = Any()))
            assertEquals(listOf(1), measurer.measuredChapters)
        }

    @Test
    fun prefetchWithoutBook_doesNothing() =
        runBlocking {
            paginator.prefetchAround(chapters, 1, PageLayout(null, viewport, Any(), measurer))
            assertEquals(0, measurer.measuredBlocks)
        }

    @Test
    fun adjacentChapters_nextFirst_andOnlyInsideTheBook() {
        assertEquals(listOf(2, 0), adjacentChapterIndices(1, CHAPTER_COUNT))
        assertEquals(listOf(1), adjacentChapterIndices(0, CHAPTER_COUNT))
        assertEquals(listOf(LAST_CHAPTER - 1), adjacentChapterIndices(LAST_CHAPTER, CHAPTER_COUNT))
        assertEquals(emptyList<Int>(), adjacentChapterIndices(0, 1))
    }

    private fun layoutOf(style: Any) = PageLayout(book, viewport, style, measurer)

    /** One line per block; records the chapter of each block it measures. */
    private class CountingMeasurer : BlockMeasurer {
        val measuredChapters = mutableListOf<Int>()
        val measuredBlocks: Int get() = measuredChapters.size

        override fun measure(
            block: Block,
            availableWidth: Int,
        ): BlockLayout {
            val text = (block as Block.Paragraph).text
            measuredChapters += text.substringAfterLast(' ').toInt()
            return BlockLayout(0, listOf(LineLayout(0, text.length, LINE_HEIGHT)))
        }
    }
}
