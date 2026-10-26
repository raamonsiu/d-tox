package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.data.storage.BookData
import dev.d1ito.dtox.data.storage.BookDataFormatError
import dev.d1ito.dtox.data.storage.BookSource
import dev.d1ito.dtox.data.storage.ReadingProgress
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val IDENTIFIER = "sha256:0123456789abcdef0123456789abcdef"
private const val TEXT = "Ser feliz no es otra cosa que vivir conforme a la naturaleza."
private val NOW: Instant = Instant.parse("2026-10-27T10:00:00Z")

class ReadingProgressKeeperTest {
    /** Each element lets one pending debounce finish. */
    private val debounceEnds = Channel<Unit>(Channel.UNLIMITED)
    private val book = book(TEXT)

    @Test
    fun open_withoutStoredData_returnsNothingAndWritesNothing() {
        val store = InMemoryBookDataStore()

        assertNull(runBlocking { keeper(store).open(book, "libro.md") })
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun open_relocatesTheStoredAnchorAndSavesTheMovedPosition() {
        val storedAnchor = Anchor.create(0, 0, 4, "feliz no es otra cosa").getOrThrow()
        val store = InMemoryBookDataStore(listOf(bookData(storedAnchor)))
        val edited = book("Prólogo. $TEXT")

        val restored = runBlocking { keeper(store).open(edited, "libro.md") }

        assertEquals("Prólogo. ".length + 4, restored?.offset)
        val saved = requireNotNull(store.writes.single().progress)
        assertEquals(restored, saved.anchor)
        assertEquals(NOW, saved.updatedAt)
    }

    @Test
    fun positionChanges_areSavedOnceAfterTheDebounceWithTheLatestPosition() {
        val store = InMemoryBookDataStore()
        val keeper = keeper(store)
        runBlocking { keeper.open(book, "libro.md") }

        keeper.onPositionChanged(anchor(10))
        keeper.onPositionChanged(anchor(30))
        assertTrue(store.writes.isEmpty())
        debounceEnds.trySend(Unit)

        val saved = store.writes.single()
        assertEquals(anchor(30), saved.progress?.anchor)
        assertEquals(30.0 / TEXT.length, requireNotNull(saved.progress).percent, 1e-9)
        assertEquals(BookSource("libro.md", NOW), saved.source)
        assertEquals("Libro", saved.title)
    }

    @Test
    fun flush_savesAtOnceAndCancelsThePendingSave() {
        val store = InMemoryBookDataStore()
        val keeper = keeper(store)
        runBlocking { keeper.open(book, "libro.md") }

        keeper.onPositionChanged(anchor(10))
        keeper.flush()
        debounceEnds.trySend(Unit)

        assertEquals(listOf(anchor(10)), store.writes.map { data -> data.progress?.anchor })
    }

    @Test
    fun failedWrite_isRetriedOnTheNextFlush() {
        val store = InMemoryBookDataStore().apply { failWrites = true }
        val keeper = keeper(store)
        runBlocking { keeper.open(book, "libro.md") }

        keeper.onPositionChanged(anchor(10))
        keeper.flush()
        store.failWrites = false
        keeper.flush()

        assertEquals(listOf(anchor(10)), store.writes.map { data -> data.progress?.anchor })
    }

    @Test
    fun unreadableStoredFile_isNeverOverwritten() {
        val store =
            InMemoryBookDataStore(readFailure = BookDataFormatError.UnsupportedVersion(2, 1))
        val keeper = keeper(store)

        assertNull(runBlocking { keeper.open(book, "libro.md") })
        keeper.onPositionChanged(anchor(10))
        keeper.flush()

        assertTrue(store.writes.isEmpty())
    }

    private fun keeper(store: InMemoryBookDataStore) =
        ReadingProgressKeeper(
            store = store,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            waitBeforeSaving = { debounceEnds.receive() },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

    private fun anchor(offset: Int): Anchor =
        Anchor
            .create(
                0,
                0,
                offset,
                TEXT.substring(offset, minOf(TEXT.length, offset + 40)),
            ).getOrThrow()

    private fun bookData(anchor: Anchor): BookData =
        BookData(
            bookIdentifier = book.identifier,
            title = "Libro",
            author = null,
            source = BookSource("libro.md", Instant.parse("2026-10-01T00:00:00Z")),
            progress = ReadingProgress(anchor, 0.1, Instant.parse("2026-10-02T00:00:00Z"), false),
        )

    private fun book(text: String): Book =
        Book
            .create(
                BookIdentifier.parse(IDENTIFIER).getOrThrow(),
                BookMetadata("Libro", null, "es"),
                listOf(Chapter.create(0, null, listOf(Block.Paragraph(text))).getOrThrow()),
            ).getOrThrow()
}
