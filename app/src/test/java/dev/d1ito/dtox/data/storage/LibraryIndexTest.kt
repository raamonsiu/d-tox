package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-10-26T20:00:00Z")
private const val QUIJOTE_HEX = "0123456789abcdef0123456789abcdef"
private const val NIEBLA_HEX = "fedcba9876543210fedcba9876543210"
private const val SENECA_HEX = "00112233445566778899aabbccddeeff"

class LibraryIndexTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val indexFile: File by lazy { File(temporaryFolder.root, "library-index.json") }
    private val libraryDirectory: File by lazy { temporaryFolder.newFolder("library") }
    private val bookDataStore by lazy {
        FileBookDataStore(File(temporaryFolder.root, "books"), writer, Dispatchers.Unconfined)
    }
    private val writer = SyncedAtomicFileWriter(Dispatchers.Unconfined)

    @Test
    fun emptyLibrary_loadsEmpty() {
        val index = index()

        index.loadNow().getOrThrow()

        assertEquals(emptyList<LibraryEntry>(), index.entries.value)
    }

    @Test
    fun changes_areStored_andANewInstanceLoadsThemWithoutRebuilding() {
        val index = index().apply { loadNow().getOrThrow() }
        index.addNow(entry(QUIJOTE_HEX, "Don Quijote")).getOrThrow()
        index.addNow(entry(NIEBLA_HEX, "Niebla")).getOrThrow()
        index.removeNow(identifier(NIEBLA_HEX)).getOrThrow()

        // The library directory is empty: a rebuild would find nothing.
        val reloaded = index().apply { loadNow().getOrThrow() }

        assertEquals(listOf(entry(QUIJOTE_HEX, "Don Quijote")), reloaded.entries.value)
    }

    @Test
    fun addingTheSameBookAgain_replacesItsEntry() {
        val index = index().apply { loadNow().getOrThrow() }
        index.addNow(entry(QUIJOTE_HEX, "Quijote")).getOrThrow()

        index.addNow(entry(QUIJOTE_HEX, "Don Quijote")).getOrThrow()

        assertEquals(listOf("Don Quijote"), index.entries.value.map { it.title })
    }

    @Test
    fun entries_areOrderedByLastOpened_thenNeverOpenedByTitle() {
        val index = index().apply { loadNow().getOrThrow() }
        index.addNow(entry(QUIJOTE_HEX, "Don Quijote")).getOrThrow()
        index.addNow(entry(NIEBLA_HEX, "Niebla", lastOpenedAt = NOW.minusSeconds(60))).getOrThrow()
        index.addNow(entry(SENECA_HEX, "Cartas", lastOpenedAt = NOW)).getOrThrow()
        index.addNow(entry("ffffffffffffffffffffffffffffffff", "anábasis")).getOrThrow()

        assertEquals(
            listOf("Cartas", "Niebla", "anábasis", "Don Quijote"),
            index.entries.value.map { it.title },
        )
    }

    @Test
    fun markOpened_stampsNow_andMovesTheBookFirst_unknownBooksAreIgnored() {
        val index = index().apply { loadNow().getOrThrow() }
        index
            .addNow(
                entry(QUIJOTE_HEX, "Don Quijote", lastOpenedAt = NOW.minusSeconds(60)),
            ).getOrThrow()
        index.addNow(entry(NIEBLA_HEX, "Niebla")).getOrThrow()

        index.markOpenedNow(identifier(NIEBLA_HEX)).getOrThrow()
        index.markOpenedNow(identifier(SENECA_HEX)).getOrThrow()

        assertEquals(listOf("Niebla", "Don Quijote"), index.entries.value.map { it.title })
        assertEquals(
            NOW,
            index.entries.value
                .first()
                .lastOpenedAt,
        )
    }

    @Test
    fun rebuild_listsVisibleLibraryFiles_withTheirBookData() {
        seedLibraryWithAndWithoutBookData()

        val index = index().apply { loadNow().getOrThrow() }

        val withData =
            LibraryEntry(
                identifier(QUIJOTE_HEX),
                "Don Quijote",
                "Cervantes",
                "$QUIJOTE_HEX.epub",
                NOW,
                true,
            )
        val withoutData =
            LibraryEntry(
                identifier(NIEBLA_HEX),
                "$NIEBLA_HEX.md",
                null,
                "$NIEBLA_HEX.md",
                null,
                false,
            )
        assertEquals(listOf(withData, withoutData), index.entries.value)
    }

    @Test
    fun corruptOrNewerIndex_isRebuilt() {
        writeLibraryFile("$QUIJOTE_HEX.epub")
        for (content in listOf(
            "{ not json",
            """{"version": 99, "books": []}""",
            """{"version": 1, "books": [{"bookId": "x", "title": "t", "file": "f"}]}""",
        )) {
            indexFile.writeText(content)

            val index = index().apply { loadNow().getOrThrow() }

            assertEquals(
                "For $content",
                listOf("$QUIJOTE_HEX.epub"),
                index.entries.value.map { it.fileName },
            )
        }
    }

    @Test
    fun failedWrite_keepsThePreviousEntries() {
        val index = index().apply { loadNow().getOrThrow() }
        index.addNow(entry(QUIJOTE_HEX, "Don Quijote")).getOrThrow()
        val failingIndex = index(fileWriter = FailingWriter).apply { loadNow().getOrThrow() }

        val result = failingIndex.addNow(entry(NIEBLA_HEX, "Niebla"))

        assertTrue("Got $result", result.isFailure)
        assertEquals(listOf("Don Quijote"), failingIndex.entries.value.map { it.title })
    }

    private fun index(fileWriter: AtomicFileWriter = writer) =
        FileLibraryIndex(
            indexFile = indexFile,
            libraryDirectory = libraryDirectory,
            bookDataStore = bookDataStore,
            fileWriter = fileWriter,
            ioDispatcher = Dispatchers.Unconfined,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
        )

    private fun LibraryIndex.loadNow() = runBlocking { load() }

    private fun LibraryIndex.addNow(entry: LibraryEntry) = runBlocking { add(entry) }

    private fun LibraryIndex.removeNow(bookIdentifier: BookIdentifier) =
        runBlocking {
            remove(bookIdentifier)
        }

    private fun LibraryIndex.markOpenedNow(bookIdentifier: BookIdentifier) =
        runBlocking {
            markOpened(bookIdentifier)
        }

    private fun entry(
        hex: String,
        title: String,
        lastOpenedAt: Instant? = null,
    ) = LibraryEntry(
        identifier(hex),
        title,
        author = null,
        fileName = "$hex.epub",
        lastOpenedAt,
        finished = false,
    )

    private fun bookData(
        hex: String,
        title: String,
        openedAt: Instant,
        finished: Boolean,
    ) = BookData(
        bookIdentifier = identifier(hex),
        title = title,
        author = "Cervantes",
        source = BookSource("quijote.epub", addedAt = openedAt),
        progress = ReadingProgress(Anchor.create(1, 2, 3).getOrThrow(), 0.4, openedAt, finished),
    )

    /** Two books (one with stored data), an import leftover and a file not named by an id. */
    private fun seedLibraryWithAndWithoutBookData() {
        writeLibraryFile("$QUIJOTE_HEX.epub")
        writeLibraryFile("$NIEBLA_HEX.md")
        writeLibraryFile(".importing-1234")
        writeLibraryFile("notas.txt")
        runBlocking {
            bookDataStore.write(
                bookData(QUIJOTE_HEX, "Don Quijote", openedAt = NOW, finished = true),
            )
        }.getOrThrow()
    }

    private fun writeLibraryFile(name: String) = File(libraryDirectory, name).writeText("libro")

    private fun identifier(hex: String): BookIdentifier = BookIdentifier.fromHex(hex).getOrThrow()

    /** Reads succeed; every write fails as if the disk were full. */
    private object FailingWriter : AtomicFileWriter {
        override suspend fun write(
            target: File,
            writeContent: (OutputStream) -> Unit,
        ): Result<Unit> =
            Result.failure(AtomicWriteError.WriteFailed(target, IOException("disk full")))
    }
}
