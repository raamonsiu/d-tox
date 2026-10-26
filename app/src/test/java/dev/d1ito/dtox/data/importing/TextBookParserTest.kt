package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

private const val IDENTIFIER = "sha256:0123456789abcdef0123456789abcdef"

class TextBookParserTest {
    private val parser = TextBookParser(Dispatchers.Unconfined)

    @Test
    fun markdown_opensAChapterAtEachTopLevelHeading() {
        val book =
            parse("cartas.md", "# Carta I\nTexto uno.\n## Nota\nMás.\n# Carta II\nTexto dos.")
        assertEquals(listOf("Carta I", "Carta II"), book.chapters.map { chapter -> chapter.title })
        assertEquals(listOf(0, 1), book.chapters.map { chapter -> chapter.index })
        assertEquals(4, book.chapters[0].blocks.size)
    }

    @Test
    fun markdown_keepsTextBeforeTheFirstHeadingAsUntitledChapter() {
        val book = parse("libro.md", "Prólogo breve.\n\n# Uno\nTexto.")
        assertEquals(listOf(null, "Uno"), book.chapters.map { chapter -> chapter.title })
    }

    @Test
    fun markdown_splitsOnTheHighestLevelPresent() {
        val book = parse("notas.md", "## A\nuno\n### A.1\ndos\n## B\ntres")
        assertEquals(listOf("A", "B"), book.chapters.map { chapter -> chapter.title })
    }

    @Test
    fun withoutHeadings_theWholeTextIsOneUntitledChapter() {
        val book = parse("notas.md", "Uno.\n\nDos.")
        assertEquals(1, book.chapters.size)
        assertNull(book.chapters.single().title)
    }

    @Test
    fun plainText_isOneChapterEvenWithMarkdownLikeLines() {
        val book = parse("diario.txt", "# No es un título\n\nTexto.")
        assertEquals(1, book.chapters.size)
        assertEquals(
            Block.Paragraph("# No es un título"),
            book.chapters
                .single()
                .blocks
                .first(),
        )
    }

    @Test
    fun extension_isCaseInsensitive() {
        assertEquals("Uno", parse("NOTAS.MD", "# Uno\nTexto.").chapters.single().title)
    }

    @Test
    fun title_isFirstLevelOneHeadingOrFileNameWithoutExtension() {
        assertEquals(
            "De la brevedad",
            parse("seneca.md", "Intro.\n# De la brevedad\nTexto.").metadata.title,
        )
        assertEquals("seneca", parse("seneca.md", "## Solo nivel dos\nTexto.").metadata.title)
        assertEquals("cartas.a.lucilio", parse("cartas.a.lucilio.txt", "Texto.").metadata.title)
        assertEquals("LEEME", parse("LEEME", "Texto.").metadata.title)
        assertEquals(".md", parse(".md", "Texto.").metadata.title)
    }

    @Test
    fun metadata_hasNoAuthorOrLanguage() {
        val metadata = parse("a.txt", "Texto.").metadata
        assertNull(metadata.author)
        assertNull(metadata.language)
    }

    @Test
    fun byteOrderMark_isRemoved() {
        val bytes =
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "# Uno\nTexto.".toByteArray()
        assertEquals("Uno", parse("a.md", bytes).chapters.single().title)
    }

    @Test
    fun malformedUtf8_isReplacedInsteadOfFailing() {
        val bytes = "Caf".toByteArray() + byteArrayOf(0xFF.toByte()) + " solo.".toByteArray()
        assertEquals(
            Block.Paragraph("Caf\uFFFD solo."),
            parse("a.txt", bytes)
                .chapters
                .single()
                .blocks
                .single(),
        )
    }

    @Test
    fun emptyOrMarkupOnlyFile_failsWithNoReadableText() {
        listOf("" to "a.txt", " \n\n " to "a.txt", "---\n\n***" to "a.md").forEach { (text, name) ->
            val error =
                parseResult(
                    name,
                    ByteArrayInputStream(text.toByteArray()),
                ).exceptionOrNull()
            assertTrue(
                "Expected NoReadableText for '$text', got $error",
                error is BookParseError.NoReadableText,
            )
        }
    }

    @Test
    fun readFailure_isReturnedAsFailure() {
        val failingSource =
            object : InputStream() {
                override fun read(): Int = throw IOException("disk removed")
            }
        assertTrue(parseResult("a.txt", failingSource).exceptionOrNull() is IOException)
    }

    @Test
    fun source_isNotClosed() {
        var closed = false
        val source =
            object : FilterInputStream(ByteArrayInputStream("Texto.".toByteArray())) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        parseResult("a.txt", source).getOrThrow()
        assertFalse(closed)
    }

    private fun parse(
        fileName: String,
        text: String,
    ): Book = parse(fileName, text.toByteArray())

    private fun parse(
        fileName: String,
        bytes: ByteArray,
    ): Book = parseResult(fileName, ByteArrayInputStream(bytes)).getOrThrow()

    private fun parseResult(
        fileName: String,
        source: InputStream,
    ): Result<Book> =
        runBlocking {
            parser.parse(source, BookIdentifier.parse(IDENTIFIER).getOrThrow(), fileName)
        }
}
