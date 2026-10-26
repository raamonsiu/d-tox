package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookMetadata
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A stylesheet wrongly listed in the spine: its text must never become a chapter. */
private const val STYLESHEET_SPINE_ITEM = "p { text-indent: 1em; }"

/** How EPUB content becomes chapters and metadata; see also [EpubBookParserRobustnessTest]. */
class EpubBookParserTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val workDirectory: File by lazy { temporaryFolder.newFolder("work") }
    private val parser by lazy { EpubBookParser(Dispatchers.Unconfined, workDirectory) }

    @Test
    fun chapters_followTheSpine_withTitlesFromTheirFirstHeading() {
        val book =
            parse(
                epubWith(
                    metadata =
                        "<dc:title>Cartas a Lucilio</dc:title><dc:creator>Séneca</dc:creator>" +
                            "<dc:creator>Otro autor</dc:creator><dc:language>es</dc:language>",
                    documents =
                        listOf(
                            "carta2.xhtml" to chapterDocument("<h2>Carta II</h2><p>Segunda.</p>"),
                            "carta1.xhtml" to
                                chapterDocument("<p>Sin título.</p><h3>Más tarde</h3>"),
                        ),
                ),
            ).getOrThrow()

        assertEquals(BookMetadata("Cartas a Lucilio", "Séneca, Otro autor", "es"), book.metadata)
        assertEquals(listOf(0, 1), book.chapters.map { chapter -> chapter.index })
        assertEquals(
            listOf("Carta II", "Más tarde"),
            book.chapters.map { chapter ->
                chapter.title
            },
        )
        assertEquals(
            listOf(heading(2, "Carta II"), Block.Paragraph("Segunda.")),
            book.chapters[0].blocks,
        )
    }

    @Test
    fun chapterWithoutHeadings_hasNoTitle() {
        val book =
            parse(epubWith(documents = listOf("a.xhtml" to chapterDocument("<p>Solo texto.</p>"))))
                .getOrThrow()

        assertNull(book.chapters.single().title)
    }

    @Test
    fun missingTitleAndAuthors_fallBackToFileNameAndNoAuthor() {
        val book =
            parse(
                epubWith(
                    metadata = "",
                    documents =
                        listOf(
                            "a.xhtml" to chapterDocument("<p>Texto.</p>"),
                        ),
                ),
                fileName = "mi libro.epub",
            ).getOrThrow()

        assertEquals(BookMetadata("mi libro", null, null), book.metadata)
    }

    @Test
    fun documentsWithoutText_nonLinearOrNotXhtml_areSkipped_andChaptersStayNumbered() {
        val manifest =
            xhtmlItem("cover", "cover.xhtml") + xhtmlItem("empty", "empty.xhtml") +
                xhtmlItem("notes", "notes.xhtml") + xhtmlItem("one", "one.xhtml") +
                """<item id="style" href="style.css" media-type="text/css"/>""" +
                xhtmlItem("two", "two.xhtml")
        val spine =
            """<itemref idref="cover"/><itemref idref="empty"/>""" +
                """<itemref idref="notes" linear="no"/><itemref idref="one"/>""" +
                """<itemref idref="style"/><itemref idref="two"/>"""
        val epub =
            temporaryFolder.epubFile(
                containerFor("content.opf"),
                "content.opf" to packageDocument(manifest = manifest, spine = spine),
                "cover.xhtml" to chapterDocument("<img src=\"cover.jpg\" alt=\"Portada\"/>"),
                "empty.xhtml" to chapterDocument("<p> </p><hr/>"),
                "notes.xhtml" to chapterDocument("<p>Nota emergente.</p>"),
                "one.xhtml" to chapterDocument("<p>Uno.</p>"),
                "style.css" to STYLESHEET_SPINE_ITEM,
                "two.xhtml" to chapterDocument("<p>Dos.</p>"),
            )

        val book = parse(epub).getOrThrow()

        assertEquals(listOf(0, 1), book.chapters.map { chapter -> chapter.index })
        assertEquals(
            listOf(listOf(Block.Paragraph("Uno.")), listOf(Block.Paragraph("Dos."))),
            book.chapters.map { chapter -> chapter.blocks },
        )
    }

    @Test
    fun damagedContentDocument_isSkipped_andTheRestIsRead() {
        val epub =
            epubWith(
                documents =
                    listOf(
                        "one.xhtml" to chapterDocument("<p>Uno.</p>"),
                        "two.xhtml" to chapterDocument("<p>${"Texto dañado. ".repeat(200)}</p>"),
                    ),
            )
        corruptCompressedData(epub, entryName = "OEBPS/two.xhtml")

        val book = parse(epub).getOrThrow()

        assertEquals(
            listOf(listOf(Block.Paragraph("Uno."))),
            book.chapters.map { chapter -> chapter.blocks },
        )
    }

    @Test
    fun epubWithoutReadableText_failsWithNoReadableText() {
        val result =
            parse(
                epubWith(documents = listOf("a.xhtml" to chapterDocument("<img src=\"a.png\"/>"))),
                fileName = "imagenes.epub",
            )

        val failure = result.exceptionOrNull()
        assertTrue("Got $result", failure is BookParseError.NoReadableText)
        assertEquals("imagenes.epub", (failure as BookParseError.NoReadableText).fileName)
    }

    private fun parse(
        epub: File,
        fileName: String = "libro.epub",
    ): Result<Book> = parser.parseFile(epub, fileName)

    private fun epubWith(
        metadata: String = "<dc:title>Título</dc:title>",
        documents: List<Pair<String, String>>,
    ): File = temporaryFolder.epubWithDocuments(metadata, documents)

    /** Overwrites the middle of an entry's deflated bytes so reading it fails. */
    private fun corruptCompressedData(
        epub: File,
        entryName: String,
    ) {
        val bytes = epub.readBytes()
        val nameStart = String(bytes, Charsets.ISO_8859_1).indexOf(entryName)
        val dataStart = nameStart + entryName.length
        for (offset in dataStart + 4 until dataStart + 40) bytes[offset] = 0x7F
        epub.writeBytes(bytes)
    }

    private fun heading(
        level: Int,
        text: String,
    ) = Block.Heading.create(level, text).getOrThrow()
}
