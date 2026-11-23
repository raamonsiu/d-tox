package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.data.storage.BookData
import dev.d1ito.dtox.data.storage.BookDataAnnotationRepository
import dev.d1ito.dtox.data.storage.BookSource
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val IDENTIFIER = "sha256:0123456789abcdef0123456789abcdef"
private const val OTHER_IDENTIFIER = "sha256:fedcba9876543210fedcba9876543210"
private const val FILE_NAME = "libro.txt"
private val NOW: Instant = Instant.parse("2026-11-22T10:00:00Z")

class ReaderAnnotationsTest {
    private val book = bookWith(IDENTIFIER)

    /** First page: block 0 whole and block 1 up to offset 5; second page: the rest. */
    private val firstPageStart = anchor(0, 0, 0)
    private val firstPageEnd = anchor(0, 1, 5)
    private val chapterEnd = anchor(0, 1, 13)

    @Test
    fun pageBookmarked_isUnknown_untilAPageIsShown_thenFalseWithoutBookmarks() {
        val annotations = readerAnnotations()
        annotations.open(book, FILE_NAME)

        assertNull(annotations.pageBookmarked.value)
        assertEquals(emptyList<BookAnnotation>(), annotations.annotations.value)

        annotations.onPageShown(firstPageStart, firstPageEnd)
        assertEquals(false, annotations.pageBookmarked.value)
    }

    @Test
    fun toggle_onAnUnmarkedPage_storesABookmarkAtThePageStart_withItsQuote() {
        val store = InMemoryBookDataStore()
        val annotations = readerAnnotations(store)
        annotations.open(book, FILE_NAME)
        annotations.onPageShown(firstPageEnd, chapterEnd)

        annotations.toggleBookmark()

        assertEquals(true, annotations.pageBookmarked.value)
        val stored = store.writes.last()
        assertEquals(FILE_NAME, stored.source.fileName)
        val bookmark = stored.annotations.single() as BookAnnotation.Bookmark
        assertEquals(0, bookmark.start.compareTo(firstPageEnd))
        assertEquals("párrafo.", bookmark.start.quote)
        assertEquals(NOW, bookmark.metadata.createdAt)
        assertEquals(listOf(bookmark), annotations.annotations.value)
    }

    @Test
    fun toggle_onAMarkedPage_removesEveryBookmarkOnIt_andKeepsTheOthers() {
        val onFirstPage = bookmarkAt(anchor(0, 0, 0))
        val alsoOnFirstPage = bookmarkAt(anchor(0, 0, 7))
        val onSecondPage = bookmarkAt(firstPageEnd)
        val store = storeWith(onFirstPage, alsoOnFirstPage, onSecondPage)
        val annotations = readerAnnotations(store)
        annotations.open(book, FILE_NAME)
        annotations.onPageShown(firstPageStart, firstPageEnd)
        assertEquals(true, annotations.pageBookmarked.value)

        annotations.toggleBookmark()

        assertEquals(false, annotations.pageBookmarked.value)
        assertEquals(listOf(onSecondPage), store.writes.last().annotations)
    }

    @Test
    fun bookmarkInTheMiddleOfThePage_marksIt_butOneAtThePageEndDoesNot() {
        val annotations = readerAnnotations(storeWith(bookmarkAt(anchor(0, 0, 7))))
        annotations.open(book, FILE_NAME)

        annotations.onPageShown(firstPageStart, firstPageEnd)
        assertEquals(true, annotations.pageBookmarked.value)

        annotations.onPageShown(anchor(0, 0, 0), anchor(0, 0, 7))
        assertEquals(false, annotations.pageBookmarked.value)
    }

    @Test
    fun pageWithoutText_canBeMarked() {
        val annotations = readerAnnotations()
        annotations.open(book, FILE_NAME)
        annotations.onPageShown(chapterEnd, chapterEnd)

        annotations.toggleBookmark()

        assertEquals(true, annotations.pageBookmarked.value)
    }

    @Test
    fun unreadableDataFile_offersNoBookmark_andIsNeverWritten() {
        val store = InMemoryBookDataStore(readFailure = IOException("unreadable"))
        val annotations = readerAnnotations(store)
        annotations.open(book, FILE_NAME)
        annotations.onPageShown(firstPageStart, firstPageEnd)

        annotations.toggleBookmark()

        assertNull(annotations.annotations.value)
        assertNull(annotations.pageBookmarked.value)
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun failedWrite_keepsThePageUnmarked() {
        val store = InMemoryBookDataStore().apply { failWrites = true }
        val annotations = readerAnnotations(store)
        annotations.open(book, FILE_NAME)
        annotations.onPageShown(firstPageStart, firstPageEnd)

        annotations.toggleBookmark()

        assertEquals(false, annotations.pageBookmarked.value)
        assertEquals(emptyList<BookAnnotation>(), annotations.annotations.value)
    }

    @Test
    fun close_forgetsTheBook_andAnotherBookShowsOnlyItsOwnAnnotations() {
        val annotations = readerAnnotations(storeWith(bookmarkAt(firstPageStart)))
        annotations.open(book, FILE_NAME)
        annotations.onPageShown(firstPageStart, firstPageEnd)

        annotations.close()
        assertNull(annotations.annotations.value)
        assertNull(annotations.pageBookmarked.value)

        annotations.open(bookWith(OTHER_IDENTIFIER), FILE_NAME)
        annotations.onPageShown(firstPageStart, firstPageEnd)
        assertEquals(emptyList<BookAnnotation>(), annotations.annotations.value)
        assertEquals(false, annotations.pageBookmarked.value)
    }

    @Test
    fun highlights_areTheStoredHighlightRanges_andEmptyOnceClosed() {
        val range = AnchorRange.create(anchor(0, 0, 2), anchor(0, 1, 4)).getOrThrow()
        val highlight =
            BookAnnotation.Highlight
                .create(AnnotationMetadata(AnnotationIdentifier.random(), NOW, NOW), range, "texto")
                .getOrThrow()
        val annotations = readerAnnotations(storeWith(highlight, bookmarkAt(firstPageEnd)))

        annotations.open(book, FILE_NAME)
        assertEquals(listOf(range), annotations.highlights.value)

        annotations.close()
        assertEquals(emptyList<AnchorRange>(), annotations.highlights.value)
    }

    private fun readerAnnotations(store: InMemoryBookDataStore = InMemoryBookDataStore()) =
        ReaderAnnotations(
            BookDataAnnotationRepository(store),
            Clock.fixed(NOW, ZoneOffset.UTC),
            CoroutineScope(Dispatchers.Unconfined),
        )

    private fun storeWith(vararg stored: BookAnnotation): InMemoryBookDataStore =
        InMemoryBookDataStore(
            listOf(
                BookData(
                    bookIdentifier = book.identifier,
                    title = "Libro",
                    author = null,
                    source = BookSource(FILE_NAME, NOW),
                    progress = null,
                    annotations = stored.toList(),
                ),
            ),
        )

    private fun bookmarkAt(start: Anchor): BookAnnotation.Bookmark =
        BookAnnotation.Bookmark(AnnotationMetadata(AnnotationIdentifier.random(), NOW, NOW), start)

    private fun anchor(
        chapter: Int,
        block: Int,
        offset: Int,
    ): Anchor = Anchor.create(chapter, block, offset).getOrThrow()

    private fun bookWith(identifier: String): Book =
        Book
            .create(
                BookIdentifier.parse(identifier).getOrThrow(),
                BookMetadata(title = "Libro", author = null, language = null),
                listOf(
                    Chapter
                        .create(
                            0,
                            null,
                            listOf(
                                Block.Paragraph("Primer párrafo."),
                                Block.Paragraph("Otro párrafo."),
                            ),
                        ).getOrThrow(),
                ),
            ).getOrThrow()
}
