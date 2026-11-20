package dev.d1ito.dtox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val UUID_TEXT = "3b241101-e2bb-4255-8caf-4136c566a962"
private const val ULID_TEXT = "01JAB7ZQ8K4M2N6P9R3S5T7V1W"

class BookAnnotationTest {
    @Test
    fun identifier_acceptsUuidAndUlidInAnyCase() {
        listOf(UUID_TEXT, UUID_TEXT.uppercase(), ULID_TEXT, ULID_TEXT.lowercase()).forEach { text ->
            assertEquals(text, AnnotationIdentifier.parse(text).getOrThrow().value)
        }
    }

    @Test
    fun identifier_rejectsOtherText() {
        listOf(
            "",
            "01JAB7Z",
            "81JAB7ZQ8K4M2N6P9R3S5T7V1W",
            "01JAB7ZQ8K4M2N6P9R3S5T7V1U",
            "3b241101e2bb42558caf4136c566a962",
            "sha256:0123456789abcdef0123456789abcdef",
        ).forEach { text ->
            assertFailsWith<DocumentModelError.InvalidAnnotationIdentifier>(
                AnnotationIdentifier.parse(text),
            )
        }
    }

    @Test
    fun identifier_randomIsAParseableUniqueUuid() {
        val first = AnnotationIdentifier.random()
        assertEquals(first, AnnotationIdentifier.parse(first.value).getOrThrow())
        assertNotEquals(first, AnnotationIdentifier.random())
    }

    @Test
    fun highlight_startsAtItsRangeAndMayHaveANote() {
        val range = AnchorRange.create(anchor(10, "ser feliz"), anchor(64)).getOrThrow()

        val plain = BookAnnotation.Highlight.create(metadata(), range, "ser feliz").getOrThrow()
        val withNote =
            BookAnnotation.Highlight.create(metadata(), range, "ser feliz", "Carta 12").getOrThrow()

        assertEquals(range.start, plain.start)
        assertNull(plain.note)
        assertEquals("Carta 12", withNote.note)
    }

    @Test
    fun blankNotes_areRejected() {
        val range = AnchorRange.create(anchor(10), anchor(64)).getOrThrow()
        assertFailsWith<DocumentModelError.BlankNote>(
            BookAnnotation.Highlight.create(metadata(), range, "texto", " "),
        )
        assertFailsWith<DocumentModelError.BlankNote>(
            BookAnnotation.Note.create(metadata(), anchor(0), ""),
        )
    }

    @Test
    fun noteAndBookmark_pointAtTheirStart() {
        val note = BookAnnotation.Note.create(metadata(), anchor(5), "Relacionar").getOrThrow()
        val bookmark = BookAnnotation.Bookmark(metadata(), anchor(0))

        assertEquals(anchor(5), note.start)
        assertEquals("Relacionar", note.note)
        assertNull(bookmark.note)
    }

    private inline fun <reified E : DocumentModelError> assertFailsWith(result: Result<*>) {
        val error = result.exceptionOrNull()
        assertTrue("Expected ${E::class.simpleName}, got $error", error is E)
    }

    private fun anchor(
        offset: Int,
        quote: String? = null,
    ): Anchor = Anchor.create(chapter = 2, block = 3, offset = offset, quote = quote).getOrThrow()

    private fun metadata(): AnnotationMetadata =
        AnnotationMetadata(
            AnnotationIdentifier.parse(ULID_TEXT).getOrThrow(),
            Instant.parse("2026-10-11T07:50:00Z"),
            Instant.parse("2026-10-11T07:50:00Z"),
        )
}
