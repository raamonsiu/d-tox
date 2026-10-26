package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.data.storage.FileParsedBookCache
import dev.d1ito.dtox.data.storage.SyncedAtomicFileWriter
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream

private const val BOOK_HEX = "0123456789abcdef0123456789abcdef"

class LibraryBookLoaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val libraryDirectory: File by lazy { temporaryFolder.newFolder("library") }
    private val cacheDirectory: File by lazy { File(temporaryFolder.root, "parsed") }
    private val cache by lazy {
        FileParsedBookCache(
            cacheDirectory,
            SyncedAtomicFileWriter(Dispatchers.Unconfined),
            Dispatchers.Unconfined,
        )
    }
    private var parses = 0
    private val countingTextParser =
        object : BookParser {
            override suspend fun parse(
                source: InputStream,
                identifier: BookIdentifier,
                fileName: String,
            ): Result<Book> {
                parses++
                return TextBookParser(Dispatchers.Unconfined).parse(source, identifier, fileName)
            }
        }
    private val loader by lazy {
        LibraryBookLoader(
            libraryDirectory,
            mapOf("md" to countingTextParser),
            cache,
            Dispatchers.Unconfined,
        )
    }

    @Test
    fun firstOpen_parsesTheLibraryCopy_andTheNextOneUsesTheCache() {
        File(libraryDirectory, "$BOOK_HEX.md").writeText("# Cartas\n\nTexto.")

        val first = load().getOrThrow()
        val second = load().getOrThrow()

        assertEquals("Cartas", first.book.metadata.title)
        assertEquals("$BOOK_HEX.md", first.fileName)
        assertEquals(first, second)
        assertEquals("Parsed only the first time", 1, parses)
        assertTrue(File(cacheDirectory, "$BOOK_HEX.json").isFile)
    }

    @Test
    fun aBookWithoutLibraryFile_isNotInLibrary_evenIfCached() {
        File(libraryDirectory, "$BOOK_HEX.md").writeText("# Cartas\n\nTexto.")
        load().getOrThrow()
        File(libraryDirectory, "$BOOK_HEX.md").delete()

        assertTrue(load().exceptionOrNull() is LibraryBookError.NotInLibrary)
    }

    @Test
    fun aFileWithoutParser_isNotInLibrary() {
        File(libraryDirectory, "$BOOK_HEX.pdf").writeText("%PDF")

        assertTrue(load().exceptionOrNull() is LibraryBookError.NotInLibrary)
    }

    @Test
    fun parseFailures_arePassedOn_andNothingIsCached() {
        File(libraryDirectory, "$BOOK_HEX.md").writeText("   ")

        assertTrue(load().exceptionOrNull() is BookParseError.NoReadableText)
        assertEquals(emptyList<String>(), cacheDirectory.list()?.toList().orEmpty())
    }

    private fun load() = runBlocking { loader.load(BookIdentifier.fromHex(BOOK_HEX).getOrThrow()) }
}
