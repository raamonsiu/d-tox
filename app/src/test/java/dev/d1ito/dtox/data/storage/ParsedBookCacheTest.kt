package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.data.importing.EpubBookParser
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val BOOK_HEX = "0123456789abcdef0123456789abcdef"
private const val OTHER_HEX = "fedcba9876543210fedcba9876543210"

class ParsedBookCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val cacheDirectory: File by lazy { File(temporaryFolder.root, "parsed") }
    private val cache by lazy {
        FileParsedBookCache(
            cacheDirectory,
            SyncedAtomicFileWriter(Dispatchers.Unconfined),
            Dispatchers.Unconfined,
        )
    }

    @Test
    fun everyBlockKindAndMetadata_roundTrip() {
        val book = sampleBook(BOOK_HEX)

        write(book)

        assertEquals(book, read(BOOK_HEX))
    }

    @Test
    fun realEpub_roundTrips() {
        val source =
            checkNotNull(
                javaClass.getResourceAsStream("/corpus/files/epub/malformed-broken-xhtml.epub"),
            )
        val book =
            source.use { stream ->
                runBlocking {
                    EpubBookParser(Dispatchers.Unconfined, temporaryFolder.newFolder())
                        .parse(stream, identifier(BOOK_HEX), "roto.epub")
                }.getOrThrow()
            }

        write(book)

        assertEquals(book, read(BOOK_HEX))
    }

    @Test
    fun missingEntry_readsAsNull() {
        assertNull(read(BOOK_HEX))
    }

    @Test
    fun unusableEntries_readAsNull_andAreDeleted() {
        val validText = ParsedBookFormat.encode(sampleBook(BOOK_HEX))
        val unusableContents =
            mapOf(
                "corrupt" to "{ not json",
                "another version" to validText.replace("\"version\":1", "\"version\":2"),
                "invalid heading level" to validText.replace("\"level\":1", "\"level\":7"),
                "unknown block type" to validText.replace("\"separator\"", "\"table\""),
                "another book's content" to ParsedBookFormat.encode(sampleBook(OTHER_HEX)),
            )
        unusableContents.forEach { (case, content) ->
            val cacheFile = File(cacheDirectory.apply { mkdirs() }, "$BOOK_HEX.json")
            cacheFile.writeText(content)

            assertNull(case, read(BOOK_HEX))
            assertFalse("$case must be deleted", cacheFile.exists())
        }
    }

    @Test
    fun writingAgain_replacesTheEntry() {
        write(sampleBook(BOOK_HEX))
        val newer =
            Book
                .create(
                    identifier(BOOK_HEX),
                    BookMetadata("Nuevo", null, null),
                    sampleBook(BOOK_HEX).chapters,
                ).getOrThrow()

        write(newer)

        assertEquals("Nuevo", read(BOOK_HEX)?.metadata?.title)
    }

    private fun write(book: Book) = runBlocking { cache.write(book) }.getOrThrow()

    private fun read(hex: String): Book? = runBlocking { cache.read(identifier(hex)) }.getOrThrow()

    private fun identifier(hex: String): BookIdentifier = BookIdentifier.fromHex(hex).getOrThrow()

    private fun sampleBook(hex: String): Book {
        val firstChapter = Chapter.create(0, "Carta I", everyBlockKind()).getOrThrow()
        val untitled = Chapter.create(1, null, listOf(Block.Paragraph("Sin título."))).getOrThrow()
        val metadata = BookMetadata(title = "Cartas a Lucilio", author = "Séneca", language = "es")
        return Book.create(identifier(hex), metadata, listOf(firstChapter, untitled)).getOrThrow()
    }

    private fun everyBlockKind(): List<Block> =
        listOf(
            Block.Heading.create(1, "Carta I").getOrThrow(),
            Block.Paragraph("Reclama tu derecho sobre ti mismo."),
            Block.Quote("Lo que se nos quita."),
            Block.ListItem.create("Uno", ordered = true, depth = 0).getOrThrow(),
            Block.ListItem.create("Dos", ordered = false, depth = 2).getOrThrow(),
            Block.Separator,
        )
}
