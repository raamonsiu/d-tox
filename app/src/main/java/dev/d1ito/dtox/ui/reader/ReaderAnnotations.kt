package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.data.storage.AnnotationReadingOrder
import dev.d1ito.dtox.data.storage.AnnotationRepository
import dev.d1ito.dtox.data.storage.BookSource
import dev.d1ito.dtox.data.storage.NewBookDetails
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.anchorAt
import dev.d1ito.dtox.domain.covers
import dev.d1ito.dtox.domain.highlightRanges
import dev.d1ito.dtox.domain.textIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
     * Ranges of the open book's highlights in reading order; empty while [annotations] is `null`.
     */
    val highlights: StateFlow<List<AnchorRange>> =
        storedAnnotations
            .map { stored -> stored.orEmpty().highlightRanges() }
            .stateIn(scope, SharingStarted.Eagerly, initialValue = emptyList())

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

    /**
     * Stores a highlight of [range], selected in the open book, with a copy of its text as
     * [textIn] extracts it (RF-20). Ignored while [annotations] is `null`, when [range] already
     * lies inside one stored highlight (it would add nothing visible) or covers no text (e.g. only
     * a separator). Not gated like [toggleBookmark]: the selection is cleared once highlighted, so
     * the same range is not offered twice, and the repository serializes the writes.
     */
    fun highlight(range: AnchorRange) {
        val annotated = openBook
        val stored = storedAnnotations.value
        val alreadyHighlighted =
            stored.orEmpty().highlightRanges().any { highlighted -> highlighted.covers(range) }
        if (annotated == null || stored == null || alreadyHighlighted) return
        val highlight = newHighlight(annotated.book, range) ?: return
        scope.launch {
            repository.add(annotated.book.identifier, highlight, annotated.newBookDetails)
        }
    }

    /**
     * A new highlight of [range] with its text and a start quote that lets it be relocated later.
     *
     * @return the highlight, or `null` when [range] covers no text of [book].
     */
    private fun newHighlight(
        book: Book,
        range: AnchorRange,
    ): BookAnnotation.Highlight? {
        val text = book.textIn(range)?.takeIf(String::isNotBlank) ?: return null
        val quotedRange = AnchorRange.create(withQuote(book, range.start), range.end).getOrNull()
        val now = clock.instant()
        val metadata = AnnotationMetadata(AnnotationIdentifier.random(), now, now)
        return quotedRange?.let { BookAnnotation.Highlight.create(metadata, it, text).getOrNull() }
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
        return BookAnnotation.Bookmark(
            metadata = AnnotationMetadata(AnnotationIdentifier.random(), now, now),
            start = withQuote(book, pageStart),
        )
    }
}

/** The same position in [book] with the quote that lets it be relocated later. */
private fun withQuote(
    book: Book,
    position: Anchor,
): Anchor = book.anchorAt(position.chapter, position.block, position.offset).getOrDefault(position)

/** @return the bookmarks whose anchor falls on [page]. */
private fun List<BookAnnotation>.bookmarksOn(page: ShownPage): List<BookAnnotation.Bookmark> =
    filterIsInstance<BookAnnotation.Bookmark>().filter { bookmark -> page.contains(bookmark.start) }
