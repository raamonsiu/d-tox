package dev.d1ito.dtox.ui.library

import dev.d1ito.dtox.data.importing.BookImporter
import dev.d1ito.dtox.data.importing.Sha256BookIdentifierCalculator
import dev.d1ito.dtox.data.importing.TextBookParser
import dev.d1ito.dtox.data.storage.FileBookDataStore
import dev.d1ito.dtox.data.storage.FileLibraryIndex
import dev.d1ito.dtox.data.storage.SyncedAtomicFileWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

private const val MARKDOWN_BOOK = "# Cartas a Lucilio\n\nSéneca saluda a su Lucilio."

class LibraryViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val filesDirectory: File by lazy { temporaryFolder.newFolder("files") }
    private val libraryDirectory: File by lazy { File(filesDirectory, "library") }
    private val writer = SyncedAtomicFileWriter(Dispatchers.Unconfined)
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

    @Test
    fun emptyLibrary_showsNoBooksAndNoMessage() {
        val state = viewModel().state.value

        assertEquals(LibraryUiState(), state)
    }

    @Test
    fun importingAFile_listsTheBook() {
        val viewModel = viewModel()

        viewModel.importBook(document("cartas.md", MARKDOWN_BOOK))

        val state = viewModel.state.value
        assertEquals(listOf("Cartas a Lucilio"), state.books.map { it.title })
        assertFalse(state.isImporting)
        assertNull(state.message)
    }

    @Test
    fun reimportingAListedBook_keepsItsEntry() {
        val viewModel = viewModel()
        viewModel.importBook(document("cartas.md", MARKDOWN_BOOK))
        val listed =
            viewModel.state.value.books
                .single()
        runBlocking { libraryIndex.markOpened(listed.bookIdentifier) }.getOrThrow()
        val opened =
            viewModel.state.value.books
                .single()

        viewModel.importBook(document("copia.md", MARKDOWN_BOOK))

        assertEquals(listOf(opened), viewModel.state.value.books)
    }

    @Test
    fun failures_becomeMessages_andTheNextImportClearsThem() {
        val viewModel = viewModel()

        viewModel.importBook(document("foto.jpg", "x", mimeType = "image/jpeg"))
        assertEquals(LibraryMessage.UnsupportedFormat, viewModel.state.value.message)

        viewModel.importBook(document("vacio.txt", "  \n "))
        assertEquals(LibraryMessage.NoReadableText, viewModel.state.value.message)

        viewModel.importBook(PickedDocument({ "roto.md" }, { null }, { throw IOException("gone") }))
        assertEquals(LibraryMessage.ImportFailed, viewModel.state.value.message)

        viewModel.importBook(
            PickedDocument({
                throw SecurityException("revoked")
            }, { null }, { error("unused") }),
        )
        assertEquals(LibraryMessage.ImportFailed, viewModel.state.value.message)

        viewModel.importBook(document("cartas.md", MARKDOWN_BOOK))
        assertNull(viewModel.state.value.message)
        assertEquals(1, viewModel.state.value.books.size)
    }

    @Test
    fun unreadableLibrary_showsLibraryUnavailable() {
        // A file where the library directory should be cannot be listed for a rebuild.
        libraryDirectory.writeText("not a directory")

        assertEquals(LibraryMessage.LibraryUnavailable, viewModel().state.value.message)
    }

    private fun viewModel(): LibraryViewModel {
        val textParser = TextBookParser(Dispatchers.Unconfined)
        val importer =
            BookImporter(
                libraryDirectory = libraryDirectory,
                parsersByExtension = mapOf("txt" to textParser, "md" to textParser),
                identifierCalculator = Sha256BookIdentifierCalculator(Dispatchers.Unconfined),
                fileWriter = writer,
                ioDispatcher = Dispatchers.Unconfined,
            )
        return LibraryViewModel(
            libraryIndex,
            importer,
            Dispatchers.Unconfined,
            CoroutineScope(Dispatchers.Unconfined),
        )
    }

    private fun document(
        name: String,
        content: String,
        mimeType: String? = null,
    ) = PickedDocument({ name }, { mimeType }, { ByteArrayInputStream(content.toByteArray()) })
}
