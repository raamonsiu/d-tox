package dev.d1ito.dtox.pagination

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Chapter

/**
 * Splits a chapter into pages that fit a viewport (docs/03-architecture.md §Pagination). Lines
 * are placed top to bottom; a block that does not fit continues on the next page.
 */
object Paginator {
    /**
     * @param chapter chapter to paginate; an empty chapter gives one empty page.
     * @param viewport page area; its width is the measuring width.
     * @param measurer lays out each block with the current typography.
     * @return pages in reading order, never empty; or the failure raised when the measurer
     *   returns offsets that cannot form a valid [Anchor].
     */
    fun paginate(
        chapter: Chapter,
        viewport: Viewport,
        measurer: BlockMeasurer,
    ): Result<List<Page>> {
        val builder = PageBuilder(chapter.index, viewport.height)
        chapter.blocks.forEachIndexed { blockIndex, block ->
            val layout = measurer.measure(block, viewport.width)
            layout.lines.forEachIndexed { lineIndex, line ->
                val spacing = if (lineIndex == 0) layout.spacingBefore else 0
                builder.place(blockIndex, line, spacing)
            }
        }
        return builder.finish()
    }
}

/**
 * @return index of the page showing [anchor]: the last page starting at or before it, so an
 *   anchor before the first page maps to 0 and one past the end maps to the last page.
 */
fun pageIndexOf(
    pages: List<Page>,
    anchor: Anchor,
): Int = pages.indexOfLast { page -> page.startAnchor <= anchor }.coerceAtLeast(0)

/** Fills pages line by line and closes a page when the next line does not fit. */
private class PageBuilder(
    private val chapterIndex: Int,
    private val pageHeight: Int,
) {
    private val pages = mutableListOf<Page>()
    private val fragments = mutableListOf<PageFragment>()
    private var usedHeight = 0
    private var failure: Throwable? = null

    fun place(
        blockIndex: Int,
        line: LineLayout,
        spacingBefore: Int,
    ) {
        val pageHasContent = fragments.isNotEmpty()
        val spacing = if (pageHasContent) spacingBefore else 0
        // A line taller than the page still gets a page of its own instead of looping forever.
        if (pageHasContent && usedHeight + spacing + line.height > pageHeight) {
            closePage()
            usedHeight = line.height
        } else {
            usedHeight += spacing + line.height
        }
        appendToFragments(blockIndex, line)
    }

    fun finish(): Result<List<Page>> {
        if (fragments.isNotEmpty() || pages.isEmpty()) closePage()
        return failure?.let { error -> Result.failure(error) } ?: Result.success(pages.toList())
    }

    private fun appendToFragments(
        blockIndex: Int,
        line: LineLayout,
    ) {
        val last = fragments.lastOrNull()
        if (last != null && last.blockIndex == blockIndex) {
            fragments[fragments.lastIndex] = last.copy(endOffset = line.endOffset)
        } else {
            fragments.add(PageFragment(blockIndex, line.startOffset, line.endOffset))
        }
    }

    private fun closePage() {
        val first = fragments.firstOrNull()
        val last = fragments.lastOrNull()
        val start = Anchor.create(chapterIndex, first?.blockIndex ?: 0, first?.startOffset ?: 0)
        val end = Anchor.create(chapterIndex, last?.blockIndex ?: 0, last?.endOffset ?: 0)
        val startAnchor = start.getOrElse { error -> return recordFailure(error) }
        val endAnchor = end.getOrElse { error -> return recordFailure(error) }
        pages.add(Page(startAnchor, endAnchor, fragments.toList()))
        fragments.clear()
    }

    private fun recordFailure(error: Throwable) {
        if (failure == null) failure = error
        fragments.clear()
    }
}
