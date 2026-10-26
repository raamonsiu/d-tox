package dev.d1ito.dtox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val VALID_IDENTIFIER = "sha256:0123456789abcdef0123456789abcdef"

class DocumentModelTest {
    @Test
    fun bookIdentifier_acceptsCanonicalForm() {
        assertEquals(VALID_IDENTIFIER, BookIdentifier.parse(VALID_IDENTIFIER).getOrThrow().value)
    }

    @Test
    fun bookIdentifier_rejectsMalformedValues() {
        listOf(
            "md5:0123456789abcdef0123456789abcdef",
            "sha256:0123456789abcdef",
            "sha256:0123456789abcdef0123456789abcdef00",
            "sha256:0123456789ABCDEF0123456789ABCDEF",
            "sha256:0123456789abcdef0123456789abcdeg",
            "",
        ).forEach { malformed ->
            assertFailsWith<DocumentModelError.InvalidBookIdentifier>(
                BookIdentifier.parse(malformed),
            )
        }
    }

    @Test
    fun heading_acceptsLevelsOneToThree() {
        (1..3).forEach { level ->
            assertEquals(
                level,
                Block.Heading
                    .create(level, "Title")
                    .getOrThrow()
                    .level,
            )
        }
    }

    @Test
    fun heading_rejectsLevelsOutsideRange() {
        listOf(0, 4, -1).forEach { level ->
            assertFailsWith<DocumentModelError.HeadingLevelOutOfRange>(
                Block.Heading.create(level, "Title"),
            )
        }
    }

    @Test
    fun listItem_rejectsNegativeDepth() {
        assertFailsWith<DocumentModelError.NegativeValue>(
            Block.ListItem.create("Item", ordered = false, depth = -1),
        )
        assertEquals(
            0,
            Block.ListItem
                .create("Item", ordered = true, depth = 0)
                .getOrThrow()
                .depth,
        )
    }

    @Test
    fun anchor_rejectsNegativePositionNamingTheField() {
        val error = Anchor.create(chapter = 0, block = -2, offset = 5).exceptionOrNull()
        assertTrue(error is DocumentModelError.NegativeValue)
        assertEquals("block", (error as DocumentModelError.NegativeValue).fieldName)
    }

    @Test
    fun anchor_ordersByChapterThenBlockThenOffsetIgnoringQuote() {
        val early = anchor(chapter = 1, block = 9, offset = 99, quote = "zzz")
        val middle = anchor(chapter = 2, block = 0, offset = 50)
        val late = anchor(chapter = 2, block = 0, offset = 51, quote = "aaa")
        assertEquals(listOf(early, middle, late), listOf(late, early, middle).sorted())
    }

    @Test
    fun anchorRange_acceptsEmptyAndForwardRanges() {
        val start = anchor(chapter = 0, block = 3, offset = 10)
        assertEquals(start, AnchorRange.create(start, start).getOrThrow().end)
        AnchorRange.create(start, anchor(chapter = 0, block = 3, offset = 64)).getOrThrow()
    }

    @Test
    fun anchorRange_rejectsEndBeforeStart() {
        val start = anchor(chapter = 1, block = 0, offset = 0)
        val end = anchor(chapter = 0, block = 5, offset = 0)
        assertFailsWith<DocumentModelError.RangeEndsBeforeStart>(AnchorRange.create(start, end))
    }

    @Test
    fun chapter_rejectsNegativeIndex() {
        assertFailsWith<DocumentModelError.NegativeValue>(Chapter.create(-1, null, emptyList()))
    }

    @Test
    fun book_acceptsChaptersNumberedByPosition() {
        val chapters = listOf(chapter(0), chapter(1))
        val book =
            Book
                .create(
                    identifier(),
                    BookMetadata("Cartas a Lucilio", "Séneca", "es"),
                    chapters,
                ).getOrThrow()
        assertEquals(chapters, book.chapters)
    }

    @Test
    fun book_rejectsMisnumberedChapters() {
        val error =
            Book
                .create(
                    identifier(),
                    BookMetadata("Title", null, null),
                    listOf(chapter(0), chapter(2)),
                ).exceptionOrNull()
        assertTrue(error is DocumentModelError.ChapterIndexMismatch)
        assertEquals(1, (error as DocumentModelError.ChapterIndexMismatch).position)
    }

    @Test
    fun book_isNotAffectedByLaterChangesToTheSourceList() {
        val chapters = mutableListOf(chapter(0))
        val book =
            Book
                .create(
                    identifier(),
                    BookMetadata("Title", null, null),
                    chapters,
                ).getOrThrow()
        chapters.add(chapter(1))
        assertEquals(1, book.chapters.size)
    }

    private inline fun <reified E : DocumentModelError> assertFailsWith(result: Result<*>) {
        val error = result.exceptionOrNull()
        assertTrue("Expected ${E::class.simpleName}, got $error", error is E)
    }

    private fun anchor(
        chapter: Int,
        block: Int,
        offset: Int,
        quote: String? = null,
    ): Anchor = Anchor.create(chapter, block, offset, quote).getOrThrow()

    private fun chapter(index: Int): Chapter =
        Chapter.create(index, "Chapter $index", listOf(Block.Paragraph("Text"))).getOrThrow()

    private fun identifier(): BookIdentifier = BookIdentifier.parse(VALID_IDENTIFIER).getOrThrow()
}
