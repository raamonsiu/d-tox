package dev.d1ito.dtox.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** Text of the faint progress indicator (RF-18). */
class ChapterPageLabelTest {
    @Test
    fun showsOneBasedPageAndChapterPageCount() {
        assertEquals("12 / 148", chapterPageLabel(pageIndex = 11, pageCount = 148))
    }

    @Test
    fun firstAndLastPage() {
        assertEquals("1 / 3", chapterPageLabel(pageIndex = 0, pageCount = 3))
        assertEquals("3 / 3", chapterPageLabel(pageIndex = 2, pageCount = 3))
    }

    @Test
    fun indexOutsideTheChapter_isClamped() {
        assertEquals("1 / 3", chapterPageLabel(pageIndex = -1, pageCount = 3))
        assertEquals("3 / 3", chapterPageLabel(pageIndex = 7, pageCount = 3))
    }

    @Test
    fun emptyPageCount_countsAsOnePage() {
        assertEquals("1 / 1", chapterPageLabel(pageIndex = 0, pageCount = 0))
    }
}
