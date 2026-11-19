package dev.d1ito.dtox.pagination

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.BookIdentifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

private const val VIEWPORT_WIDTH = 300
private const val VIEWPORT_HEIGHT = 500
private const val CAPACITY = 2

class PageCacheTest {
    private val book = BookIdentifier.fromHex("0".repeat(32)).getOrThrow()
    private val viewport = Viewport.create(VIEWPORT_WIDTH, VIEWPORT_HEIGHT).getOrThrow()
    private val rotated = Viewport.create(VIEWPORT_HEIGHT, VIEWPORT_WIDTH).getOrThrow()
    private val style = Any()
    private val otherStyle = Any()

    @Test
    fun keys_areEqualOnlyForTheSameBookChapterViewportAndStyle() {
        val key = PageCacheKey(book, 1, viewport, style)
        assertEquals(key, PageCacheKey(book, 1, viewport, style))
        assertNotEquals(key, PageCacheKey(book, 2, viewport, style))
        assertNotEquals(key, PageCacheKey(book, 1, rotated, style))
        assertNotEquals(key, PageCacheKey(book, 1, viewport, otherStyle))
        val otherBook = BookIdentifier.fromHex("1".repeat(32)).getOrThrow()
        assertNotEquals(key, PageCacheKey(otherBook, 1, viewport, style))
    }

    @Test
    fun storedPages_areFoundUnderTheirKey() {
        val cache = PageCache()
        cache.put(PageCacheKey(book, 0, viewport, style), pagesOfChapter(0))
        assertEquals(pagesOfChapter(0), cache.get(PageCacheKey(book, 0, viewport, style)))
    }

    @Test
    fun changingStyleOrViewport_missesThePagesOfTheOldLayout() {
        val cache = PageCache()
        cache.put(PageCacheKey(book, 0, viewport, style), pagesOfChapter(0))
        assertNull(cache.get(PageCacheKey(book, 0, viewport, otherStyle)))
        assertNull(cache.get(PageCacheKey(book, 0, rotated, style)))
    }

    @Test
    fun storingOverCapacity_dropsTheLeastRecentlyUsed() {
        val cache = PageCache(capacity = CAPACITY)
        cache.put(key(0), pagesOfChapter(0))
        cache.put(key(1), pagesOfChapter(1))
        cache.get(key(0)) // Chapter 0 is now more recent than chapter 1.
        cache.put(key(2), pagesOfChapter(2))
        assertNull(cache.get(key(1)))
        assertEquals(pagesOfChapter(0), cache.get(key(0)))
        assertEquals(pagesOfChapter(2), cache.get(key(2)))
    }

    @Test
    fun nonPositiveCapacity_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { PageCache(capacity = 0) }
    }

    private fun key(chapterIndex: Int) = PageCacheKey(book, chapterIndex, viewport, style)

    private fun pagesOfChapter(chapterIndex: Int): List<Page> {
        val start = Anchor.create(chapterIndex, 0, 0).getOrThrow()
        return listOf(Page(start, start, emptyList()))
    }
}
