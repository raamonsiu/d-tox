package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.data.storage.SyncedAtomicFileWriter
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

private const val MARKDOWN_BOOK = "# Cartas a Lucilio\n\nSéneca saluda a su Lucilio."

class BookImporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val libraryDirectory: File by lazy { File(temporaryFolder.root, "library") }

    private val importer by lazy {
        val textParser = TextBookParser(Dispatchers.Unconfined)
        importerWith(
            mapOf(
                "epub" to
                    EpubBookParser(
                        Dispatchers.Unconfined,
                        temporaryFolder.newFolder("cache"),
                    ),
                "txt" to textParser,
                "md" to textParser,
            ),
        )
    }

    private fun importerWith(parsersByExtension: Map<String, BookParser>) =
        BookImporter(
            libraryDirectory = libraryDirectory,
            parsersByExtension = parsersByExtension,
            identifierCalculator = Sha256BookIdentifierCalculator(Dispatchers.Unconfined),
            fileWriter = SyncedAtomicFileWriter(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun markdown_isCopiedUnderItsHash_andParsed() {
        val bytes = MARKDOWN_BOOK.toByteArray()

        val imported = import(bytes, "cartas.md").getOrThrow()

        assertEquals(File(libraryDirectory, "${sha256Prefix(bytes)}.md"), imported.file)
        assertArrayEquals(bytes, imported.file.readBytes())
        assertEquals("Cartas a Lucilio", imported.book.metadata.title)
        assertEquals("sha256:${sha256Prefix(bytes)}", imported.book.identifier.value)
        assertFalse(imported.wasAlreadyInLibrary)
        assertEquals(listOf(imported.file.name), libraryFiles())
    }

    @Test
    fun reimportingTheSameBytes_doesNotDuplicate() {
        val bytes = MARKDOWN_BOOK.toByteArray()
        val first = import(bytes, "cartas.md").getOrThrow()

        val second = import(bytes, "copia de cartas.md").getOrThrow()

        assertTrue(second.wasAlreadyInLibrary)
        assertEquals(first.file, second.file)
        assertEquals(listOf(first.file.name), libraryFiles())
    }

    @Test
    fun epub_isChosenByExtension_inAnyCase() {
        val epub =
            temporaryFolder.epubWithDocuments(
                documents =
                    listOf(
                        "a.xhtml" to chapterDocument("<p>Uno.</p>"),
                    ),
            )

        val imported = import(epub.readBytes(), "LIBRO.EPUB").getOrThrow()

        assertTrue(imported.file.name.endsWith(".epub"))
        assertEquals("Título", imported.book.metadata.title)
    }

    @Test
    fun mimeType_choosesTheFormat_whenTheNameHasNoKnownExtension() {
        val epub =
            temporaryFolder.epubWithDocuments(
                documents =
                    listOf(
                        "a.xhtml" to chapterDocument("<p>Uno.</p>"),
                    ),
            )

        val imported =
            import(
                epub.readBytes(),
                "descarga",
                mimeType = "application/epub+zip",
            ).getOrThrow()

        assertTrue(imported.file.name.endsWith(".epub"))
    }

    @Test
    fun unsupportedFormat_failsWithoutTouchingTheLibrary() {
        val result = import(byteArrayOf(1, 2, 3), "foto.jpg", mimeType = "image/jpeg")

        val failure = result.exceptionOrNull()
        assertTrue("Got $result", failure is BookImportError.UnsupportedFormat)
        assertEquals("foto.jpg", (failure as BookImportError.UnsupportedFormat).fileName)
        assertEquals(emptyList<String>(), libraryFiles())
    }

    @Test
    fun parseFailure_removesTheNewCopy() {
        val result = import("   \n\n ".toByteArray(), "vacio.txt")

        assertTrue("Got $result", result.exceptionOrNull() is BookParseError.NoReadableText)
        assertEquals(emptyList<String>(), libraryFiles())
    }

    @Test
    fun parseFailure_keepsACopyThatWasAlreadyInTheLibrary() {
        val bytes = MARKDOWN_BOOK.toByteArray()
        val first = import(bytes, "cartas.md").getOrThrow()
        val failingParser =
            object : BookParser {
                override suspend fun parse(
                    source: InputStream,
                    identifier: BookIdentifier,
                    fileName: String,
                ) = Result.failure<Book>(BookParseError.NoReadableText(fileName))
            }
        val failingImporter = importerWith(mapOf("md" to failingParser))

        val result =
            runBlocking { failingImporter.import(ByteArrayInputStream(bytes), "cartas.md", null) }

        assertTrue("Got $result", result.isFailure)
        assertTrue("An earlier import's copy must survive", first.file.exists())
        assertEquals(listOf(first.file.name), libraryFiles())
    }

    @Test
    fun readFailure_leavesNothingBehind() {
        val failingSource =
            object : InputStream() {
                override fun read(): Int = throw IOException("disk gone")
            }

        val result = runBlocking { importer.import(failingSource, "roto.md", mimeType = null) }

        assertTrue("Got $result", result.exceptionOrNull() is IOException)
        assertEquals(emptyList<String>(), libraryFiles())
    }

    private fun import(
        bytes: ByteArray,
        fileName: String,
        mimeType: String? = null,
    ): Result<ImportedBook> =
        runBlocking { importer.import(ByteArrayInputStream(bytes), fileName, mimeType) }

    /** Every file in the library, including staging leftovers, sorted. */
    private fun libraryFiles(): List<String> = libraryDirectory.list()?.sorted().orEmpty()

    private fun sha256Prefix(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(32)
}
