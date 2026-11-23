package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

private val CREATED: Instant = Instant.parse("2026-11-23T10:00:00Z")
private const val LONG_TITLE_REPEATS = 30
private const val FILE_NAME_TITLE_MAXIMUM_LENGTH = 80

class AnnotationMarkdownTest {
    private val untitledChapterTitle = { chapterNumber: Int -> "Capítulo $chapterNumber" }

    @Test
    fun highlightsAndNotes_areGroupedByChapter_inReadingOrder_withoutBookmarks() {
        val book = book(title = "Cuentos", author = "Autora")
        val noteInSecondChapter =
            BookAnnotation.Note.create(metadata(1), anchor(1, 0, 0), "Me gusta").getOrThrow()
        val lastHighlight = highlight(2, range(18, 31), "Luego llovió.")
        val firstHighlight = highlight(3, range(0, 6), "Era un", "Inicio")
        val bookmark = BookAnnotation.Bookmark(metadata(4), anchor(0, 0, 4))

        val markdown =
            book.annotationsMarkdown(
                listOf(noteInSecondChapter, lastHighlight, bookmark, firstHighlight),
                untitledChapterTitle,
            )

        val expected =
            listOf(
                "# Cuentos",
                "Autora",
                "## Primero",
                "> Era un",
                "Inicio",
                "> Luego llovió.",
                "## Capítulo 2",
                "> Otra historia empieza aquí.",
                "Me gusta",
            ).joinToString("\n\n", postfix = "\n")
        assertEquals(expected, markdown)
    }

    @Test
    fun nothingToExport_givesTheHeaderOnly() {
        val bookmark = BookAnnotation.Bookmark(metadata(1), anchor(0, 0, 0))

        assertEquals(
            "# Cuentos\n\nAutora\n",
            book(author = "Autora").annotationsMarkdown(listOf(bookmark), untitledChapterTitle),
        )
        assertEquals(
            "# Cuentos\n",
            book(author = null).annotationsMarkdown(emptyList(), untitledChapterTitle),
        )
    }

    @Test
    fun markdownCharacters_inBookAndUserText_areEscaped() {
        val book = book(title = "C# en 2 días #", author = "*Anónima*")
        val text = "*Hola* _mundo_ [enlace](x) <b> &amp; | ~ `code` \\ 5 + 3 = 8 #tag"
        val note = "# no es título\n- no es lista\n1. no numerada\n> no cita\n+ más\n=== fin"

        val markdown =
            book.annotationsMarkdown(
                listOf(highlight(1, range(0, 6), text, note)),
                untitledChapterTitle,
            )

        val expected =
            listOf(
                "# C\\# en 2 días \\#",
                "\\*Anónima\\*",
                "## Primero",
                "> \\*Hola\\* \\_mundo\\_ \\[enlace\\](x) \\<b> \\&amp; \\| \\~ \\`code\\` \\\\ " +
                    "5 + 3 = 8 #tag",
                "\\# no es título\\\n\\- no es lista\\\n1\\. no numerada\\\n\\> no cita\\\n" +
                    "\\+ más\\\n\\=== fin",
            ).joinToString("\n\n", postfix = "\n")
        assertEquals(expected, markdown)
    }

    @Test
    fun lineBreaks_areNormalized_andKeptAsHardBreaksInsideQuotesAndNotes() {
        val verse = "  verso uno\r\nverso dos  \r\r\n\n\n    estrofa final\n"
        val note = "Primera\rsegunda\n\n\n\ntercera   "

        val markdown =
            book(author = null).annotationsMarkdown(
                listOf(highlight(1, range(0, 6), verse, note)),
                untitledChapterTitle,
            )

        val expected =
            listOf(
                "# Cuentos",
                "## Primero",
                "> verso uno\\\n> verso dos\n>\n> estrofa\\\n> final",
                "Primera\\\nsegunda\n\ntercera",
            ).joinToString("\n\n", postfix = "\n")
        assertEquals(expected, markdown)
    }

    @Test
    fun titlesOnSeveralLines_becomeOneHeadingLine() {
        val book = book(title = "Cuentos\nde la\r\nselva", author = null)

        assertEquals("# Cuentos de la selva\n", book.annotationsMarkdown(emptyList()) { "" })
    }

    @Test
    fun fileName_isTheTitleWithoutUnsafeCharacters_shortened_withTheNotesSuffix() {
        assertEquals("Cuentos «uno» dos-notas.md", annotationsFileName("Cuentos: «uno»/dos?"))
        assertEquals("Un libro-notas.md", annotationsFileName("  Un\n\tlibro. "))
        assertEquals("notas.md", annotationsFileName(" ... "))
        val longTitle = "Palabra ".repeat(LONG_TITLE_REPEATS)
        val shortened = "Palabra ".repeat(LONG_TITLE_REPEATS).take(FILE_NAME_TITLE_MAXIMUM_LENGTH)
        assertEquals(shortened.trimEnd() + "-notas.md", annotationsFileName(longTitle))
    }

    private fun book(
        title: String = "Cuentos",
        author: String?,
    ): Book =
        Book
            .create(
                BookIdentifier.parse("sha256:0123456789abcdef0123456789abcdef").getOrThrow(),
                BookMetadata(title = title, author = author, language = "es"),
                listOf(
                    chapter(0, "Primero", Block.Paragraph("Era un día claro. Luego llovió.")),
                    chapter(1, null, Block.Paragraph("Otra historia empieza aquí.")),
                ),
            ).getOrThrow()

    private fun highlight(
        number: Int,
        range: AnchorRange,
        text: String,
        note: String? = null,
    ): BookAnnotation.Highlight =
        BookAnnotation.Highlight.create(metadata(number), range, text, note).getOrThrow()

    /** A range in the first paragraph of the first chapter. */
    private fun range(
        startOffset: Int,
        endOffset: Int,
    ): AnchorRange =
        AnchorRange.create(anchor(0, 0, startOffset), anchor(0, 0, endOffset)).getOrThrow()

    private fun chapter(
        index: Int,
        title: String?,
        vararg blocks: Block,
    ): Chapter = Chapter.create(index, title, blocks.toList()).getOrThrow()

    private fun anchor(
        chapter: Int,
        block: Int,
        offset: Int,
    ): Anchor = Anchor.create(chapter, block, offset, null).getOrThrow()

    private fun metadata(number: Int): AnnotationMetadata =
        AnnotationMetadata(
            AnnotationIdentifier
                .parse("00000000-0000-4000-8000-%012d".format(number))
                .getOrThrow(),
            CREATED,
            CREATED,
        )
}
