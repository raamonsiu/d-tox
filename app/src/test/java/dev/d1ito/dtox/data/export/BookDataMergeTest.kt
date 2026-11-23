package dev.d1ito.dtox.data.export

import dev.d1ito.dtox.data.storage.BookData
import dev.d1ito.dtox.data.storage.BookDataFormat
import dev.d1ito.dtox.data.storage.BookSource
import dev.d1ito.dtox.data.storage.ReadingProgress
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

private const val BOOK = "sha256:0123456789abcdef0123456789abcdef"
private const val FIRST_ANNOTATION = "11111111-1111-4111-8111-111111111111"
private const val SECOND_ANNOTATION = "22222222-2222-4222-8222-222222222222"
private val EARLIER: Instant = Instant.parse("2026-10-11T07:00:00Z")
private val LATER: Instant = Instant.parse("2026-10-12T07:00:00Z")

class BookDataMergeTest {
    private fun anchor(chapter: Int): Anchor = Anchor.create(chapter, 0, 0).getOrThrow()

    private fun progress(
        chapter: Int,
        updatedAt: Instant,
    ) = ReadingProgress(anchor(chapter), percent = 0.5, updatedAt = updatedAt, finished = false)

    private fun note(
        identifier: String,
        text: String,
        updatedAt: Instant,
    ): BookAnnotation =
        BookAnnotation.Note
            .create(
                AnnotationMetadata(
                    AnnotationIdentifier.parse(identifier).getOrThrow(),
                    EARLIER,
                    updatedAt,
                ),
                anchor(chapter = 1),
                text,
            ).getOrThrow()

    private fun bookData(
        title: String = "Cartas a Lucilio",
        progress: ReadingProgress? = null,
        annotations: List<BookAnnotation> = emptyList(),
    ) = BookData(
        bookIdentifier = BookIdentifier.parse(BOOK).getOrThrow(),
        title = title,
        author = "Séneca",
        source = BookSource("cartas.epub", EARLIER),
        progress = progress,
        annotations = annotations,
    )

    /** [bookData] as read from a file whose JSON was changed by [edit], with its unknown fields. */
    private fun readBack(
        bookData: BookData,
        edit: (String) -> String,
    ): BookData = BookDataFormat.decode(edit(BookDataFormat.encode(bookData))).getOrThrow()

    @Test
    fun withoutLocalData_theImportIsTaken() {
        val imported = bookData(progress = progress(chapter = 3, LATER))

        assertEquals(imported, mergedBookData(local = null, imported))
    }

    @Test
    fun progress_theLaterOneWins_andATieKeepsTheLocalOne() {
        val earlier = bookData(progress = progress(chapter = 1, EARLIER))
        val later = bookData(progress = progress(chapter = 5, LATER))
        val tied = bookData(progress = progress(chapter = 9, EARLIER))

        assertEquals(later.progress, mergedBookData(earlier, later).progress)
        assertEquals(later.progress, mergedBookData(later, earlier).progress)
        assertEquals(earlier.progress, mergedBookData(earlier, tied).progress)
    }

    @Test
    fun progress_missingOnOneSide_takesTheOther() {
        val withProgress = bookData(progress = progress(chapter = 2, EARLIER))

        assertEquals(withProgress.progress, mergedBookData(bookData(), withProgress).progress)
        assertEquals(withProgress.progress, mergedBookData(withProgress, bookData()).progress)
    }

    @Test
    fun annotations_areTheUnionById_keepingTheLaterVersion() {
        val localOnly = note(FIRST_ANNOTATION, "Solo aquí", EARLIER)
        val olderCopy = note(SECOND_ANNOTATION, "Primera versión", EARLIER)
        val newerCopy = note(SECOND_ANNOTATION, "Versión corregida", LATER)
        val local = bookData(annotations = listOf(localOnly, newerCopy))
        val imported = bookData(annotations = listOf(olderCopy))

        assertEquals(listOf(localOnly, newerCopy), mergedBookData(local, imported).annotations)
        val reversed = mergedBookData(bookData(annotations = listOf(olderCopy)), local)
        assertEquals(setOf(localOnly, newerCopy), reversed.annotations.toSet())
    }

    @Test
    fun annotations_withTheSameUpdatedAt_keepTheLocalVersion() {
        val localCopy = note(FIRST_ANNOTATION, "Local", EARLIER)
        val importedCopy = note(FIRST_ANNOTATION, "Importada", EARLIER)

        val merged =
            mergedBookData(
                bookData(annotations = listOf(localCopy)),
                bookData(annotations = listOf(importedCopy)),
            )

        assertEquals(listOf(localCopy), merged.annotations)
    }

    @Test
    fun titleAndSource_stayLocal() {
        val merged = mergedBookData(bookData(title = "Local"), bookData(title = "Importado"))

        assertEquals("Local", merged.title)
        assertNull(merged.progress)
    }

    @Test
    fun unknownFields_ofTheWinningSide_arePreserved() {
        val local =
            readBack(
                bookData(annotations = listOf(note(FIRST_ANNOTATION, "Local", EARLIER))),
            ) { json ->
                json.replaceFirst("{", """{"localExtra": 1,""")
            }
        val imported =
            readBack(
                bookData(annotations = listOf(note(FIRST_ANNOTATION, "Nueva", LATER))),
            ) { json ->
                json
                    .replaceFirst("{", """{"importedExtra": 2,""")
                    .replace(""""type": "note",""", """"type": "note", "color": "gris",""")
            }

        val written =
            Json
                .parseToJsonElement(
                    BookDataFormat.encode(mergedBookData(local, imported)),
                ).jsonObject

        assertEquals(JsonPrimitive(1), written["localExtra"])
        assertEquals(JsonPrimitive(2), written["importedExtra"])
        val annotation = written.getValue("annotations").jsonArray.single() as JsonObject
        assertEquals(JsonPrimitive("gris"), annotation["color"])
        assertEquals(JsonPrimitive("Nueva"), annotation["note"])
    }
}
