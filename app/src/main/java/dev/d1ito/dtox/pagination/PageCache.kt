package dev.d1ito.dtox.pagination

import dev.d1ito.dtox.domain.BookIdentifier

/** Chapters kept by default: the one being read, its neighbours and a few recently visited. */
const val DEFAULT_PAGE_CACHE_CAPACITY = 8

/**
 * Identifies the pages of one chapter laid out for one viewport and style
 * (docs/03-architecture.md §Paginación 5).
 *
 * @property style equal only while blocks measure the same (font, size, spacing, margins,
 *   density); e.g. a `ReadingStyles` instance, which compares by identity.
 */
data class PageCacheKey(
    val bookIdentifier: BookIdentifier,
    val chapterIndex: Int,
    val viewport: Viewport,
    val style: Any,
)

/**
 * Pages of recently paginated chapters, in memory and bounded to [capacity] chapters: storing one
 * more drops the least recently used. A new style or viewport is a new key, so it never gets the
 * old pages; those just wait to be dropped. Safe to use from several threads.
 *
 * @param capacity chapters kept; must be positive.
 */
class PageCache(
    private val capacity: Int = DEFAULT_PAGE_CACHE_CAPACITY,
) {
    private val lock = Any()

    // Kept in use order: a hit moves to the end, so the first entry is the least recently used.
    private val pagesByKey = LinkedHashMap<PageCacheKey, List<Page>>()

    init {
        require(capacity > 0) { "Page cache capacity must be positive, was $capacity" }
    }

    /** @return the pages stored for [key], or `null`; a hit counts as a use. */
    fun get(key: PageCacheKey): List<Page>? =
        synchronized(lock) {
            pagesByKey.remove(key)?.also { pages -> pagesByKey[key] = pages }
        }

    /** Stores [pages] for [key] as the most recently used, dropping the eldest over [capacity]. */
    fun put(
        key: PageCacheKey,
        pages: List<Page>,
    ) {
        synchronized(lock) {
            pagesByKey.remove(key)
            pagesByKey[key] = pages
            if (pagesByKey.size > capacity) pagesByKey.remove(pagesByKey.keys.first())
        }
    }
}
