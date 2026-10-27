package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.data.importing.BookImporter
import dev.d1ito.dtox.data.importing.Sha256BookIdentifierCalculator
import dev.d1ito.dtox.data.importing.TextBookParser
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Instant

private const val CARTAS = "# Cartas\n\nTexto."

/**
 * RF-04 against real stores: books are imported from real original files and cached as the app
 * does, and the whole temporary tree is compared before and after removing.
 */
class LibraryBookRemoverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val writer = SyncedAtomicFileWriter(Dispatchers.Unconfined)
    private val filesDirectory: File by lazy { temporaryFolder.newFolder("files") }
    private val libraryDirectory: File by lazy { File(filesDirectory, "library") }
    private val cacheDirectory: File by lazy { File(temporaryFolder.root, "cache/parsed") }
    private val downloads: File by lazy { temporaryFolder.newFolder("downloads") }
    private val dataStore by lazy {
        FileBookDataStore(File(filesDirectory, "books"), writer, Dispatchers.Unconfined)
    }
    private val cache by lazy {
        FileParsedBookCache(
            cacheDirectory,
            writer,
            Dispatchers.Unconfined,
        )
    }
    private val index by lazy {
        val indexFile = File(filesDirectory, "library-index.json")
        FileLibraryIndex(indexFile, libraryDirectory, dataStore, writer, Dispatchers.Unconfined)
            .also { created -> runBlocking { created.load() }.getOrThrow() }
    }
    private val importer by lazy {
        val parser = TextBookParser(Dispatchers.Unconfined)
        val calculator = Sha256BookIdentifierCalculator(Dispatchers.Unconfined)
        BookImporter(
            libraryDirectory,
            mapOf("md" to parser),
            calculator,
            writer,
            Dispatchers.Unconfined,
        )
    }

    @Test
    fun removing_deletesOnlyTheCopyCacheAndEntry_andKeepsTheOriginalAndEverythingElse() {
        val removed = importFromDownloads("cartas.md", CARTAS)
        val kept = importFromDownloads("niebla.md", "# Niebla\n\nOtro texto.")
        File(filesDirectory, "settings.json").writeText("{\"version\":1}")
        saveProgress(removed)
        val before = treeSnapshot()

        remover().removeNow(removed).getOrThrow()

        val after = treeSnapshot()
        val gone = before.keys - after.keys
        assertEquals(
            setOf("files/library/${removed.hex}.md", "cache/parsed/${removed.hex}.json"),
            gone,
        )
        // Only the index content changes among the files that stay: originals, data, settings…
        val unchanged = (before - gone).filterKeys { path -> path != "files/library-index.json" }
        assertEquals(unchanged, after.filterKeys { path -> path != "files/library-index.json" })
        assertEquals(listOf(kept), listedIdentifiers())
        assertNull(runBlocking { cache.read(removed) }.getOrThrow())
    }

    @Test
    fun importingAgain_findsTheKeptProgress() {
        val book = importFromDownloads("cartas.md", CARTAS)
        saveProgress(book)

        remover().removeNow(book).getOrThrow()
        importFromDownloads("cartas.md", CARTAS)

        val restored = runBlocking { dataStore.read(book) }.getOrThrow()?.progress?.anchor
        assertEquals(Triple(0, 1, 2), restored?.let { Triple(it.chapter, it.block, it.offset) })
    }

    @Test
    fun removingABookThatIsNotThere_succeeds_andChangesNothing() {
        val listed = importFromDownloads("cartas.md", CARTAS)
        val before = treeSnapshot()

        remover().removeNow(identifier("00000000000000000000000000000000")).getOrThrow()

        assertEquals(before, treeSnapshot())
        assertEquals(listOf(listed), listedIdentifiers())
    }

    @Test
    fun aCopyThatCannotBeDeleted_failsWithIOException_andTheBookStaysListedAndCached() {
        val book = importFromDownloads("cartas.md", CARTAS)
        libraryDirectory.setWritable(false)
        try {
            val result = remover().removeNow(book)

            assertTrue("Got $result", result.exceptionOrNull() is IOException)
            assertEquals(listOf(book), listedIdentifiers())
            assertTrue(File(libraryDirectory, "${book.hex}.md").isFile)
            assertEquals(book, runBlocking { cache.read(book) }.getOrThrow()?.identifier)
        } finally {
            libraryDirectory.setWritable(true)
        }
    }

    @Test
    fun aLibraryThatCannotBeListed_failsWithIOException_andTheBookStaysListed() {
        val book = importFromDownloads("cartas.md", CARTAS)
        libraryDirectory.setReadable(false)
        try {
            assertTrue(remover().removeNow(book).exceptionOrNull() is IOException)
        } finally {
            libraryDirectory.setReadable(true)
        }
        assertEquals(listOf(book), listedIdentifiers())
    }

    @Test
    fun afterAFailedIndexStep_removingAgainFinishesTheJob() {
        val book = importFromDownloads("cartas.md", CARTAS)
        val indexFile = File(filesDirectory, "library-index.json")
        indexFile.delete()
        indexFile.mkdir() // A directory where the index file goes: its atomic write fails.

        assertTrue(remover().removeNow(book).isFailure)
        assertEquals("Still listed after the failure", listOf(book), listedIdentifiers())

        indexFile.delete()
        remover().removeNow(book).getOrThrow()

        assertTrue(index.entries.value.isEmpty())
        assertEquals(emptyList<String>(), libraryDirectory.list()?.toList())
    }

    private fun remover() =
        LibraryBookRemover(libraryDirectory, cache, index, Dispatchers.Unconfined)

    private fun LibraryBookRemover.removeNow(bookIdentifier: BookIdentifier) =
        runBlocking { remove(bookIdentifier) }

    private fun listedIdentifiers() = index.entries.value.map { entry -> entry.bookIdentifier }

    /** Imports a real file kept in `downloads/`, then caches and lists it as the app does. */
    private fun importFromDownloads(
        name: String,
        content: String,
    ): BookIdentifier {
        val original = File(downloads, name).apply { writeText(content) }
        val imported =
            original
                .inputStream()
                .use { source ->
                    runBlocking { importer.import(source, name, null) }
                }.getOrThrow()
        val book = imported.book
        runBlocking { cache.write(book) }.getOrThrow()
        val entry =
            LibraryEntry(
                book.identifier,
                book.metadata.title,
                null,
                imported.file.name,
                null,
                false,
            )
        runBlocking { index.add(entry) }.getOrThrow()
        return book.identifier
    }

    private fun saveProgress(book: BookIdentifier) {
        val anchor = Anchor.create(0, 1, 2).getOrThrow()
        val progress = ReadingProgress(anchor, 0.5, Instant.EPOCH, finished = false)
        val data = BookData(book, "Cartas", null, BookSource("cartas.md", Instant.EPOCH), progress)
        runBlocking { dataStore.write(data) }.getOrThrow()
    }

    private fun identifier(hex: String): BookIdentifier = BookIdentifier.fromHex(hex).getOrThrow()

    /** Every file under the temporary root, by relative path, with its bytes. */
    private fun treeSnapshot(): Map<String, List<Byte>> =
        temporaryFolder.root
            .walkTopDown()
            .filter { file -> file.isFile }
            .associate { file ->
                file.relativeTo(temporaryFolder.root).path to
                    file.readBytes().toList()
            }
}
