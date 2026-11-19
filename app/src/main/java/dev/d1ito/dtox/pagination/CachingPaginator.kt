package dev.d1ito.dtox.pagination

import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.Chapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What chapters are paginated for.
 *
 * @property bookIdentifier book the chapters belong to; `null` when unknown, and then nothing is
 *   cached.
 * @property viewport page area.
 * @property style identity of the typography [measurer] uses (see [PageCacheKey.style]).
 * @property measurer lays out blocks with that typography.
 */
class PageLayout(
    val bookIdentifier: BookIdentifier?,
    val viewport: Viewport,
    val style: Any,
    val measurer: BlockMeasurer,
)

/**
 * Paginates chapters on [dispatcher], one at a time because measurers may share caches that are
 * not thread-safe, and keeps their pages in [cache], so going back to a chapter or into one
 * paginated ahead does not wait (docs/03-architecture.md §Paginación 4–5).
 *
 * @param dispatcher where pages are measured; never the main thread.
 * @param cache pages already computed, shared by every layout.
 */
class CachingPaginator(
    private val dispatcher: CoroutineDispatcher,
    private val cache: PageCache = PageCache(),
) {
    private val measuring = Mutex()

    /**
     * @return the pages of [chapter] for [layout]: cached ones at once, else paginated on the
     *   dispatcher after any chapter being measured, and cached if that succeeds (even when the
     *   caller is cancelled meanwhile, so the work is not lost). Failures are not cached.
     */
    suspend fun pagesOf(
        chapter: Chapter,
        layout: PageLayout,
    ): Result<List<Page>> {
        val key = cacheKeyOf(chapter, layout)
        key?.let(cache::get)?.let { pages -> return Result.success(pages) }
        return measuring.withLock {
            // Measured while this call waited, e.g. as a neighbour paginated ahead.
            key?.let(cache::get)?.let { pages -> Result.success(pages) }
                ?: withContext(dispatcher) {
                    Paginator
                        .paginate(chapter, layout.viewport, layout.measurer)
                        .onSuccess { pages -> key?.let { cacheKey -> cache.put(cacheKey, pages) } }
                }
        }
    }

    /**
     * Paginates into the cache the chapters right after and before [chapterIndex] in [chapters],
     * the next one first because reading usually goes forward. Does nothing without a book to key
     * the cache with. Cancelling stops before the next chapter.
     */
    suspend fun prefetchAround(
        chapters: List<Chapter>,
        chapterIndex: Int,
        layout: PageLayout,
    ) {
        if (layout.bookIdentifier == null) return
        adjacentChapterIndices(chapterIndex, chapters.size).forEach { index ->
            pagesOf(chapters[index], layout)
        }
    }

    private fun cacheKeyOf(
        chapter: Chapter,
        layout: PageLayout,
    ): PageCacheKey? =
        layout.bookIdentifier?.let { book ->
            PageCacheKey(book, chapter.index, layout.viewport, layout.style)
        }
}

/**
 * @return indices of the chapters next to [chapterIndex] in a book of [chapterCount] chapters,
 *   the next one first; those outside the book are left out.
 */
fun adjacentChapterIndices(
    chapterIndex: Int,
    chapterCount: Int,
): List<Int> =
    listOf(chapterIndex + 1, chapterIndex - 1).filter { index -> index in 0 until chapterCount }
