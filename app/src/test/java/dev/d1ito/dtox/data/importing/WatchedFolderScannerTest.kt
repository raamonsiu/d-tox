package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.data.storage.FileBookDataStore
import dev.d1ito.dtox.data.storage.FileLibraryIndex
import dev.d1ito.dtox.data.storage.FolderSource
import dev.d1ito.dtox.data.storage.LibraryEntry
import dev.d1ito.dtox.data.storage.SyncedAtomicFileWriter
import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

private const val CARTAS = "# Cartas a Lucilio\n\nSéneca saluda a su Lucilio."
private const val NIEBLA = "# Niebla\n\nAugusto salió a la puerta de su casa."

class WatchedFolderScannerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val novels = WatchedFolder("content://provider/tree/primary%3ANovelas")
    private val essays = WatchedFolder("content://provider/tree/primary%3AEnsayos")
    private val documents = FakeWatchedFolderDocuments()
    private val store = FakeWatchedFolderStore(listOf(novels, essays))
    private val writer = SyncedAtomicFileWriter(Dispatchers.Unconfined)
    private val filesDirectory: File by lazy { temporaryFolder.newFolder("files") }
    private val libraryDirectory: File by lazy { File(filesDirectory, "library") }
    private val libraryIndex by lazy {
        FileLibraryIndex(
            indexFile = File(filesDirectory, "library-index.json"),
            libraryDirectory = libraryDirectory,
            bookDataStore =
                FileBookDataStore(
                    File(filesDirectory, "books"),
                    writer,
                    Dispatchers.Unconfined,
                ),
            fileWriter = writer,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }
    private val importer by lazy {
        val textParser = TextBookParser(Dispatchers.Unconfined)
        BookImporter(
            libraryDirectory = libraryDirectory,
            parsersByExtension = mapOf("txt" to textParser, "md" to textParser),
            identifierCalculator = Sha256BookIdentifierCalculator(Dispatchers.Unconfined),
            fileWriter = writer,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }
    private val scanner by lazy { WatchedFolderScanner(store, documents, importer, libraryIndex) }

    @Before
    fun loadIndex() {
        runBlocking { libraryIndex.load() }.getOrThrow()
    }

    @Test
    fun newBooks_areImportedWithTheirSource_andOtherFilesAreSkipped() {
        documents.put(novels, "niebla.md", NIEBLA)
        documents.put(essays, "cartas.md", CARTAS)
        documents.put(essays, "portada.jpg", "x")
        documents.put(essays, "vacio.txt", "  \n ")

        assertEquals(emptyList<WatchedFolder>(), scanNow())

        assertEquals(
            setOf(
                "Niebla" to FolderSource(novels, documents.uriOf(novels, "niebla.md")),
                "Cartas a Lucilio" to FolderSource(essays, documents.uriOf(essays, "cartas.md")),
            ),
            entries().map { entry -> entry.title to entry.source }.toSet(),
        )
        assertTrue(documents.uriOf(essays, "portada.jpg") !in documents.opened)
    }

    @Test
    fun aKnownDocument_isNotReadAgain() {
        documents.put(novels, "niebla.md", NIEBLA)
        scanNow()
        documents.opened.clear()

        scanNow()

        assertEquals(emptyList<String>(), documents.opened)
        assertEquals(1, entries().size)
    }

    @Test
    fun aDisappearedBook_isMarkedMissing_andKeepsItsEntryAndCopy() {
        documents.put(novels, "niebla.md", NIEBLA)
        scanNow()
        val listed = entries().single()
        runBlocking { libraryIndex.markOpened(listed.bookIdentifier) }.getOrThrow()

        documents.delete(novels, "niebla.md")
        scanNow()

        val marked = entries().single()
        assertEquals(true, marked.source?.missing)
        assertNotNull(marked.lastOpenedAt)
        assertTrue(File(libraryDirectory, marked.fileName).isFile)
    }

    @Test
    fun aBookThatComesBack_isNoLongerMissing() {
        documents.put(novels, "niebla.md", NIEBLA)
        scanNow()
        documents.delete(novels, "niebla.md")
        scanNow()

        documents.put(novels, "niebla.md", NIEBLA)
        scanNow()

        assertEquals(false, entries().single().source?.missing)
    }

    @Test
    fun aMovedBook_takesItsNewSource_withoutADuplicate() {
        documents.put(novels, "niebla.md", NIEBLA)
        scanNow()

        documents.delete(novels, "niebla.md")
        documents.put(novels, "unamuno-niebla.md", NIEBLA)
        scanNow()

        assertEquals(
            FolderSource(novels, documents.uriOf(novels, "unamuno-niebla.md")),
            entries().single().source,
        )
    }

    @Test
    fun aBookAddedByHand_isNotListedTwice_andTakesTheSource() {
        val byHand =
            runBlocking {
                importer.import(
                    ByteArrayInputStream(NIEBLA.toByteArray()),
                    "niebla.md",
                    null,
                )
            }.getOrThrow()
        runBlocking { libraryIndex.add(byHand.newLibraryEntry()) }.getOrThrow()
        documents.put(novels, "niebla.md", NIEBLA)

        scanNow()

        assertEquals(
            FolderSource(novels, documents.uriOf(novels, "niebla.md")),
            entries().single().source,
        )
    }

    @Test
    fun anUnreadableFolder_isReported_andItsBooksKeepTheirMarks() {
        documents.put(novels, "niebla.md", NIEBLA)
        documents.put(essays, "cartas.md", CARTAS)
        scanNow()

        documents.unreadableFolders += novels
        documents.delete(essays, "cartas.md")

        assertEquals(listOf(novels), scanNow())
        val missingByTitle = entries().associate { entry -> entry.title to entry.source?.missing }
        assertEquals(mapOf("Niebla" to false, "Cartas a Lucilio" to true), missingByTitle)
    }

    @Test
    fun booksOfAFolderNoLongerWatched_areLeftAsTheyAre() {
        documents.put(novels, "niebla.md", NIEBLA)
        scanNow()

        store.current.value = listOf(essays)
        documents.delete(novels, "niebla.md")
        scanNow()

        assertEquals(false, entries().single().source?.missing)
    }

    private fun scanNow(): List<WatchedFolder> = runBlocking { scanner.scan() }.getOrThrow()

    private fun entries(): List<LibraryEntry> = libraryIndex.entries.value
}
