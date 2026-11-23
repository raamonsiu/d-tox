package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.data.storage.AnnotationReadingOrder
import dev.d1ito.dtox.data.storage.AnnotationRepository
import dev.d1ito.dtox.data.storage.BookSource
import dev.d1ito.dtox.data.storage.NewBookDetails
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.anchorAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * The page being read, as the paginator laid it out.
 *
 * @property start first position shown, inclusive; a bookmark made on the page is stored here.
 * @property end position right after the last one shown, exclusive.
 */
private data class ShownPage(
    val start: Anchor,
    val end: Anchor,
) {
    /**
     * @return whether [anchor] is in `[start, end)`; a page without text (`start == end`) still
     *   holds its own start, so it can be marked too.
     */
    fun contains(anchor: Anchor): Boolean =
        anchor.compareTo(start) == 0 || (anchor > start && anchor < end)
}

/**
 * The book open in the reader, with what a new data file needs if it has none yet.
 */
private class AnnotatedBook(
    val book: Book,
    val newBookDetails: NewBookDetails,
)

/**
 * Annotations of the book open in the reader (RF-20–24) and the bookmark of the page being read
 * (RF-22). Call it from the main thread; the repository moves file work off it.
 *
 * A bookmark is stored at the start anchor of the page it was made on. The page being read "has
 * a bookmark" when a bookmark's anchor falls in `[page start, page end)`: after repaginating
 * (another font size, a rotation) that anchor may land in the middle of a page, which still
 * counts as marked, and removing the mark then removes every bookmark on the page.
 *
 * A change that fails (e.g. the disk is full) leaves the stored annotations as they were, so
 * [annotations] and [pageBookmarked] keep showing what is really stored.
 *
 * @param repository where annotations are read and changed.
 * @param clock dates new annotations and new data files.
 * @param scope reads the annotations and runs the changes; cancelled when the reader is cleared.
 */
class ReaderAnnotations(
    private val repository: AnnotationRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val storedAnnotations = MutableStateFlow<List<BookAnnotation>?>(null)
    private val shownPage = MutableStateFlow<ShownPage?>(null)
    private var openBook: AnnotatedBook? = null
    private var reading: Job? = null
    private var pendingChange: Job? = null

    /**
     * Annotations of the open book in [AnnotationReadingOrder], updated after each change. `null`
     * while no book is open, before they are read, or when the book's data file cannot be read
     * (e.g. it comes from a newer app); then nothing can be annotated, so offer no annotation
     * action.
     */
    val annotations: StateFlow<List<BookAnnotation>?> = storedAnnotations.asStateFlow()

    /**
     * Whether the page being read has a bookmark; `null` while that cannot be known or changed
     * (no page shown yet, or [annotations] is `null`).
     */
    val pageBookmarked: StateFlow<Boolean?> =
        combine(storedAnnotations, shownPage) { stored, page ->
            if (stored == null || page == null) null else stored.bookmarksOn(page).isNotEmpty()
        }.stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    /**
     * Starts following the annotations of [book], replacing the previous book's.
     *
     * @param sourceFileName recorded in a new data file when the book has none yet.
     */
    fun open(
        book: Book,
        sourceFileName: String,
    ) {
        close()
        val details =
            NewBookDetails(
                title = book.metadata.title,
                author = book.metadata.author,
                source = BookSource(sourceFileName, clock.instant()),
            )
        openBook = AnnotatedBook(book, details)
        reading =
            scope.launch {
                repository.annotations(book.identifier).collect { result ->
                    storedAnnotations.value = result.getOrNull()
                }
            }
    }

    /** Stops following the open book; a change already started still completes. */
    fun close() {
        reading?.cancel()
        reading = null
        openBook = null
        storedAnnotations.value = null
        shownPage.value = null
    }

    /**
     * Records the page being read, the restored one included.
     *
     * @param pageStart first position shown.
     * @param pageEnd position right after the last one shown, never before [pageStart].
     */
    fun onPageShown(
        pageStart: Anchor,
        pageEnd: Anchor,
    ) {
        shownPage.value = ShownPage(pageStart, pageEnd)
    }

    /**
     * Marks the page being read at its start, or removes every bookmark on it if it is marked.
     * Ignored while [pageBookmarked] is `null` or the previous change is still being stored, so
     * a quick second tap never adds a second bookmark.
     */
    fun toggleBookmark() {
        if (pendingChange?.isActive == true) return
        val annotated = openBook
        val page = shownPage.value
        val stored = storedAnnotations.value
        if (annotated != null && page != null && stored != null) {
            pendingChange =
                scope.launch { toggleBookmark(annotated, page, stored.bookmarksOn(page)) }
        }
    }

    /** Adds a bookmark at the start of [page] if [bookmarksOnPage] is empty, else removes them. */
    private suspend fun toggleBookmark(
        annotated: AnnotatedBook,
        page: ShownPage,
        bookmarksOnPage: List<BookAnnotation.Bookmark>,
    ) {
        val bookIdentifier = annotated.book.identifier
        if (bookmarksOnPage.isEmpty()) {
            val bookmark = newBookmark(annotated.book, page.start)
            repository.add(bookIdentifier, bookmark, annotated.newBookDetails)
        } else {
            bookmarksOnPage.forEach { bookmark ->
                repository.remove(bookIdentifier, bookmark.metadata.identifier)
            }
        }
    }

    /** A new bookmark at [pageStart], with the quote that lets it be relocated later. */
    private fun newBookmark(
        book: Book,
        pageStart: Anchor,
    ): BookAnnotation.Bookmark {
        val now = clock.instant()
        val anchor =
            book
                .anchorAt(
                    pageStart.chapter,
                    pageStart.block,
                    pageStart.offset,
                ).getOrDefault(pageStart)
        return BookAnnotation.Bookmark(
            metadata = AnnotationMetadata(AnnotationIdentifier.random(), now, now),
            start = anchor,
        )
    }
}

/** @return the bookmarks whose anchor falls on [page]. */
private fun List<BookAnnotation>.bookmarksOn(page: ShownPage): List<BookAnnotation.Bookmark> =
    filterIsInstance<BookAnnotation.Bookmark>().filter { bookmark -> page.contains(bookmark.start) }
