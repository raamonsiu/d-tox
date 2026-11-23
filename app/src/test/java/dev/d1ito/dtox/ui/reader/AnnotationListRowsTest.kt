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
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

private val CREATED: Instant = Instant.parse("2026-11-22T10:00:00Z")
private const val LONG_PARAGRAPH_REPEATS = 20

class AnnotationListRowsTest {
    private val longParagraph = "Palabra larga ".repeat(LONG_PARAGRAPH_REPEATS).trim()

    private val book: Book =
        Book
            .create(
                BookIdentifier.parse("sha256:0123456789abcdef0123456789abcdef").getOrThrow(),
                BookMetadata(title = "Libro", author = null, language = "es"),
                listOf(
                    chapter(0, "Prólogo", Block.Paragraph("Era un día claro."), Block.Separator),
                    chapter(1, null, Block.Paragraph(longParagraph)),
                ),
            ).getOrThrow()

    @Test
    fun eachKind_getsItsTypeChapterExcerptAndNote_inTheGivenOrder() {
        val bookmark = BookAnnotation.Bookmark(metadata(1), anchor(0, 0, 7))
        val highlight =
            BookAnnotation.Highlight
                .create(metadata(2), range(anchor(0, 0, 0), anchor(0, 0, 6)), "Era un", "Inicio")
                .getOrThrow()
        val note = BookAnnotation.Note.create(metadata(3), anchor(0, 0, 11), "Luz").getOrThrow()

        val rows = book.annotationRows(listOf(bookmark, highlight, note))

        assertEquals(
            listOf(AnnotationKind.BOOKMARK, AnnotationKind.HIGHLIGHT, AnnotationKind.NOTE),
            rows.map(AnnotationRow::kind),
        )
        assertEquals(listOf("día claro.", "Era un", "claro."), rows.map(AnnotationRow::excerpt))
        assertEquals(listOf(null, "Inicio", "Luz"), rows.map(AnnotationRow::note))
        assertEquals(listOf("Prólogo"), rows.map(AnnotationRow::chapterTitle).distinct())
        assertEquals(
            listOf(metadata(1), metadata(2), metadata(3)).map(AnnotationMetadata::identifier),
            rows.map(AnnotationRow::identifier),
        )
    }

    @Test
    fun theTarget_isTheStartRelocatedByItsQuote() {
        // Stored at offset 0 with the quote of offset 4: the text moved, the quote finds it.
        val bookmark = BookAnnotation.Bookmark(metadata(1), anchor(0, 0, 0, quote = "un día"))

        val row = book.annotationRows(listOf(bookmark)).single()

        assertEquals(anchor(0, 0, 4), row.target.withoutQuote())
        assertEquals("un día claro.", row.excerpt)
    }

    @Test
    fun aLongBlock_isCutWithAMark_andAPositionWithoutText_hasNoExcerpt() {
        val inLongBlock = BookAnnotation.Bookmark(metadata(1), anchor(1, 0, 0))
        val onSeparator = BookAnnotation.Bookmark(metadata(2), anchor(0, 1, 0))

        val (longRow, separatorRow) = book.annotationRows(listOf(inLongBlock, onSeparator))

        val expected =
            longParagraph.take(ANNOTATION_EXCERPT_LENGTH).trimEnd() + ANNOTATION_EXCERPT_CUT_MARK
        assertEquals(expected, longRow.excerpt)
        assertNull("an untitled chapter keeps no title", longRow.chapterTitle)
        assertNull(separatorRow.excerpt)
    }

    @Test
    fun noAnnotations_giveNoRows() {
        assertEquals(emptyList<AnnotationRow>(), book.annotationRows(emptyList()))
    }

    private fun chapter(
        index: Int,
        title: String?,
        vararg blocks: Block,
    ): Chapter = Chapter.create(index, title, blocks.toList()).getOrThrow()

    private fun anchor(
        chapter: Int,
        block: Int,
        offset: Int,
        quote: String? = null,
    ): Anchor = Anchor.create(chapter, block, offset, quote).getOrThrow()

    private fun range(
        start: Anchor,
        end: Anchor,
    ): AnchorRange = AnchorRange.create(start, end).getOrThrow()

    /** Metadata with a fixed identifier per [number], so rows can be told apart. */
    private fun metadata(number: Int): AnnotationMetadata =
        AnnotationMetadata(
            AnnotationIdentifier
                .parse("00000000-0000-4000-8000-%012d".format(number))
                .getOrThrow(),
            CREATED,
            CREATED,
        )
}
