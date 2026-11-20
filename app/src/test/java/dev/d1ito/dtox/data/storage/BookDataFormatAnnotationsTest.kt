package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.BookAnnotation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val HIGHLIGHT_ID = "01JAB7ZQ8K4M2N6P9R3S5T7V1W"
private const val NOTE_ID = "01JAB80ZQ8K4M2N6P9R3S5T7V1"
private const val BOOKMARK_ID = "3b241101-e2bb-4255-8caf-4136c566a962"
private const val CREATED_AT = "2026-10-11T07:50:00Z"
private const val NOTE_UPDATED_AT = "2026-10-11T08:02:00Z"
private const val HIGHLIGHT_POSITION = 0
private const val NOTE_POSITION = 1
private const val BOOKMARK_POSITION = 2

/** An `end` before the start of every annotation of [annotatedFile]. */
private const val EARLY_END = """{ "chapter": 0, "block": 0, "offset": 0 }"""

/** The annotations of docs/02-data-formats.md §4, plus fields this version does not know. */
private val annotatedFile =
    """
    {
      "version": 1,
      "bookId": "sha256:0123456789abcdef0123456789abcdef",
      "title": "Cartas a Lucilio",
      "author": "Séneca",
      "source": { "name": "cartas-lucilio.epub", "addedAt": "2026-10-10T18:20:00Z" },
      "progress": null,
      "annotations": [
        {
          "id": "$HIGHLIGHT_ID",
          "type": "highlight",
          "start": { "chapter": 2, "block": 3, "offset": 10, "quote": "el tiempo es lo único" },
          "end": { "chapter": 2, "block": 3, "offset": 64, "hint": 2 },
          "text": "el tiempo es lo único que es nuestro",
          "note": null,
          "createdAt": "$CREATED_AT",
          "updatedAt": "$CREATED_AT",
          "device": "tablet"
        },
        {
          "id": "$NOTE_ID",
          "type": "note",
          "start": { "chapter": 2, "block": 5, "offset": 0, "quote": "Guarda el tiempo" },
          "end": null,
          "text": null,
          "note": "Relacionar con la carta 12",
          "createdAt": "$CREATED_AT",
          "updatedAt": "$NOTE_UPDATED_AT"
        },
        {
          "id": "$BOOKMARK_ID",
          "type": "bookmark",
          "start": { "chapter": 6, "block": 0, "offset": 0, "quote": "Carta VI" },
          "end": null,
          "text": null,
          "note": null,
          "createdAt": "$CREATED_AT",
          "updatedAt": "$CREATED_AT"
        }
      ]
    }
    """.trimIndent()

class BookDataFormatAnnotationsTest {
    @Test
    fun decode_readsEveryAnnotationType() {
        val range =
            AnchorRange
                .create(anchor(2, 3, 10, "el tiempo es lo único"), anchor(2, 3, 64))
                .getOrThrow()
        val expected =
            listOf(
                BookAnnotation.Highlight
                    .create(metadata(HIGHLIGHT_ID), range, "el tiempo es lo único que es nuestro")
                    .getOrThrow(),
                BookAnnotation.Note
                    .create(
                        metadata(NOTE_ID, NOTE_UPDATED_AT),
                        anchor(2, 5, 0, "Guarda el tiempo"),
                        "Relacionar con la carta 12",
                    ).getOrThrow(),
                BookAnnotation.Bookmark(metadata(BOOKMARK_ID), anchor(6, 0, 0, "Carta VI")),
            )

        assertEquals(expected, BookDataFormat.decode(annotatedFile).getOrThrow().annotations)
    }

    @Test
    fun roundTrip_keepsTheDocumentedShapeAndUnknownFields() {
        val rewritten =
            BookDataFormat.encode(BookDataFormat.decode(annotatedFile).getOrThrow())

        assertEquals(Json.parseToJsonElement(annotatedFile), Json.parseToJsonElement(rewritten))
    }

    @Test
    fun encode_mergesPreservedFieldsByIdentifierAndDropsRemovedAnnotations() {
        val bookData = BookDataFormat.decode(annotatedFile).getOrThrow()
        val highlight = bookData.annotations.first() as BookAnnotation.Highlight
        val annotated =
            BookAnnotation.Highlight
                .create(highlight.metadata, highlight.range, highlight.text, "Nota nueva")
                .getOrThrow()
        val added =
            BookAnnotation.Bookmark(metadata(AnnotationIdentifier.random().value), anchor(1, 0, 0))
        val changed = bookData.copy(annotations = listOf(added, annotated))

        val written = writtenAnnotations(changed)

        assertEquals(added.metadata.identifier.value, written[0].textAt("id"))
        assertEquals(KNOWN_KEYS, written[0].keys)
        assertEquals(HIGHLIGHT_ID, written[1].textAt("id"))
        assertEquals("Nota nueva", written[1].textAt("note"))
        assertEquals("tablet", written[1].textAt("device"))
        assertEquals("2", written[1].getValue("end").jsonObject.textAt("hint"))
        assertNull(written[1].getValue("end").jsonObject["quote"])
        assertEquals(
            changed.annotations,
            BookDataFormat.decode(BookDataFormat.encode(changed)).getOrThrow().annotations,
        )
    }

    @Test
    fun invalidAnnotations_areCorrupt() {
        invalidAnnotationFiles().forEach { text ->
            val error = BookDataFormat.decode(text).exceptionOrNull()
            assertTrue(
                "Expected Corrupt for <$text>, got $error",
                error is BookDataFormatError.Corrupt,
            )
        }
    }

    private fun invalidAnnotationFiles(): List<String> =
        listOf(
            annotatedFile.replace(HIGHLIGHT_ID, "01JAB7Z"),
            annotatedFile.replace(NOTE_ID, HIGHLIGHT_ID),
            annotatedFile.replace(NOTE_UPDATED_AT, "ayer"),
            withField(HIGHLIGHT_POSITION, "type", JsonPrimitive("underline")),
            withField(HIGHLIGHT_POSITION, "end", JsonNull),
            withField(HIGHLIGHT_POSITION, "end", Json.parseToJsonElement(EARLY_END)),
            withField(HIGHLIGHT_POSITION, "text", JsonNull),
            withField(NOTE_POSITION, "note", JsonPrimitive(" ")),
            withField(NOTE_POSITION, "note", JsonNull),
            withField(NOTE_POSITION, "text", JsonPrimitive("texto")),
            withField(BOOKMARK_POSITION, "end", Json.parseToJsonElement(EARLY_END)),
            withField(BOOKMARK_POSITION, "note", JsonPrimitive("nota")),
        )

    /** [annotatedFile] with [field] of the annotation at [position] set to [value]. */
    private fun withField(
        position: Int,
        field: String,
        value: JsonElement,
    ): String {
        val root = Json.parseToJsonElement(annotatedFile).jsonObject
        val annotations = root.getValue("annotations").jsonArray.toMutableList()
        annotations[position] = JsonObject(annotations[position].jsonObject + (field to value))
        return JsonObject(root + ("annotations" to JsonArray(annotations))).toString()
    }

    private fun writtenAnnotations(bookData: BookData): List<JsonObject> =
        Json
            .parseToJsonElement(BookDataFormat.encode(bookData))
            .jsonObject
            .getValue("annotations")
            .jsonArray
            .map { annotation -> annotation.jsonObject }

    private fun JsonObject.textAt(key: String): String = getValue(key).jsonPrimitive.content

    private fun anchor(
        chapter: Int,
        block: Int,
        offset: Int,
        quote: String? = null,
    ): Anchor = Anchor.create(chapter, block, offset, quote).getOrThrow()

    private fun metadata(
        identifier: String,
        updatedAt: String = CREATED_AT,
    ): AnnotationMetadata =
        AnnotationMetadata(
            AnnotationIdentifier.parse(identifier).getOrThrow(),
            Instant.parse(CREATED_AT),
            Instant.parse(updatedAt),
        )

    private companion object {
        val KNOWN_KEYS =
            setOf("id", "type", "start", "end", "text", "note", "createdAt", "updatedAt")
    }
}
