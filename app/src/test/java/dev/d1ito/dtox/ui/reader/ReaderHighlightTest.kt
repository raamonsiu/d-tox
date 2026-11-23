package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.data.storage.BookSource
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.BookAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

private const val IDENTIFIER = "sha256:0123456789abcdef0123456789abcdef"
private const val FILE_NAME = "libro.txt"
private val NOW: Instant = Instant.parse("2026-11-23T10:00:00Z")

/** The «Resaltar» use case (RF-20): [ReaderAnnotations.highlight] over a book data store. */
class ReaderHighlightTest {
    private val book = twoParagraphBook(IDENTIFIER)

    @Test
    fun highlight_ofABookWithoutDataFile_storesTheExactSelectedText_andShowsIt() {
        val store = InMemoryBookDataStore()
        val annotations = readerAnnotationsOver(store, NOW)
        annotations.open(book, FILE_NAME)
        val selected = range(anchor(0, 7), anchor(1, 4))

        annotations.highlight(selected)

        val stored = store.writes.single()
        assertEquals(FILE_NAME, stored.source.fileName)
        val highlight = stored.annotations.single() as BookAnnotation.Highlight
        assertEquals("párrafo.\nOtro", highlight.text)
        assertEquals(selected, range(highlight.range.start.withoutQuote(), highlight.range.end))
        assertEquals("párrafo.", highlight.range.start.quote)
        assertNull(highlight.note)
        assertEquals(NOW, highlight.metadata.createdAt)
        assertEquals(NOW, highlight.metadata.updatedAt)
        assertEquals(listOf(highlight.range), annotations.highlights.value)
    }

    @Test
    fun highlight_insideAStoredHighlight_addsNothing_butOneGoingPastItDoes() {
        val metadata = AnnotationMetadata(AnnotationIdentifier.random(), NOW, NOW)
        val existing =
            BookAnnotation.Highlight
                .create(metadata, range(anchor(0, 0), anchor(1, 4)), "Primer párrafo.\nOtro")
                .getOrThrow()
        val store = storeHolding(book, listOf(existing), BookSource(FILE_NAME, NOW))
        val annotations = readerAnnotationsOver(store, NOW)
        annotations.open(book, FILE_NAME)

        annotations.highlight(range(anchor(0, 7), anchor(1, 4)))
        assertTrue(store.writes.isEmpty())

        annotations.highlight(range(anchor(1, 0), anchor(1, 13)))
        val added =
            store.writes
                .single()
                .annotations
                .last() as BookAnnotation.Highlight
        assertEquals("Otro párrafo.", added.text)
    }

    @Test
    fun highlight_withAnUnreadableDataFile_orNoBookOpen_isNeverWritten() {
        val unreadable = InMemoryBookDataStore(readFailure = IOException("unreadable"))
        val annotations = readerAnnotationsOver(unreadable, NOW)
        annotations.open(book, FILE_NAME)
        annotations.highlight(range(anchor(0, 0), anchor(0, 5)))
        assertTrue(unreadable.writes.isEmpty())

        val store = InMemoryBookDataStore()
        readerAnnotationsOver(store, NOW).highlight(range(anchor(0, 0), anchor(0, 5)))
        assertTrue(store.writes.isEmpty())
    }

    private fun range(
        start: Anchor,
        end: Anchor,
    ): AnchorRange = AnchorRange.create(start, end).getOrThrow()

    private fun anchor(
        block: Int,
        offset: Int,
    ): Anchor = Anchor.create(0, block, offset).getOrThrow()
}
