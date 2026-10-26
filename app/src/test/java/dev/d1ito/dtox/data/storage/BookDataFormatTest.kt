package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val IDENTIFIER = "sha256:0123456789abcdef0123456789abcdef"

/** The example of docs/02-data-formats.md §4, plus fields this version does not know. */
private val documentedFile =
    """
    {
      "version": 1,
      "bookId": "$IDENTIFIER",
      "title": "Cartas a Lucilio",
      "author": "Séneca",
      "source": { "name": "cartas-lucilio.epub", "addedAt": "2026-10-10T18:20:00Z", "origin": "saf" },
      "progress": {
        "anchor": { "chapter": 4, "block": 12, "offset": 87, "quote": "ser feliz no es otra cosa que", "hint": 3 },
        "percent": 0.31,
        "updatedAt": "2026-10-11T07:45:12Z",
        "finished": false
      },
      "annotations": [{ "id": "01JAB7Z", "type": "highlight", "future": true }],
      "readingTimeSeconds": 5400
    }
    """.trimIndent()

class BookDataFormatTest {
    @Test
    fun decode_readsEveryKnownField() {
        val bookData = BookDataFormat.decode(documentedFile).getOrThrow()

        assertEquals(IDENTIFIER, bookData.bookIdentifier.value)
        assertEquals("Cartas a Lucilio", bookData.title)
        assertEquals("Séneca", bookData.author)
        assertEquals(
            BookSource("cartas-lucilio.epub", Instant.parse("2026-10-10T18:20:00Z")),
            bookData.source,
        )
        val progress = requireNotNull(bookData.progress)
        assertEquals(
            Anchor.create(4, 12, 87, "ser feliz no es otra cosa que").getOrThrow(),
            progress.anchor,
        )
        assertEquals(0.31, progress.percent, 0.0)
        assertEquals(Instant.parse("2026-10-11T07:45:12Z"), progress.updatedAt)
        assertEquals(1, bookData.annotations.size)
    }

    @Test
    fun roundTrip_keepsKnownAndUnknownFieldsAtEveryLevel() {
        val original = Json.parseToJsonElement(documentedFile)

        val rewritten =
            Json.parseToJsonElement(
                BookDataFormat.encode(BookDataFormat.decode(documentedFile).getOrThrow()),
            )

        assertEquals(original, rewritten)
    }

    @Test
    fun encode_writesChangesOverPreservedFields() {
        val bookData = BookDataFormat.decode(documentedFile).getOrThrow()
        val newAnchor = Anchor.create(5, 0, 0).getOrThrow()
        val moved =
            bookData.copy(
                progress =
                    bookData.progress?.copy(
                        anchor = newAnchor,
                        updatedAt = Instant.parse("2026-10-12T09:00:00Z"),
                    ),
            )

        val written = Json.parseToJsonElement(BookDataFormat.encode(moved)).jsonObject

        assertEquals("5", written.valueAt("progress", "anchor", "chapter"))
        assertEquals("null", written.valueAt("progress", "anchor", "quote"))
        assertEquals("3", written.valueAt("progress", "anchor", "hint"))
        assertEquals("2026-10-12T09:00:00Z", written.valueAt("progress", "updatedAt"))
        assertEquals("5400", written.valueAt("readingTimeSeconds"))
    }

    @Test
    fun encode_newFileWritesVersionAndUtcDatesWithoutProgress() {
        val bookData =
            BookData(
                bookIdentifier = BookIdentifier.parse(IDENTIFIER).getOrThrow(),
                title = "Niebla",
                author = null,
                source = BookSource("niebla.epub", Instant.parse("2026-10-10T18:20:00Z")),
                progress = null,
            )

        val written = Json.parseToJsonElement(BookDataFormat.encode(bookData)).jsonObject

        assertEquals("1", written.getValue("version").jsonPrimitive.content)
        assertEquals(
            "2026-10-10T18:20:00Z",
            written
                .getValue("source")
                .jsonObject
                .getValue("addedAt")
                .jsonPrimitive.content,
        )
        assertEquals("null", written.getValue("progress").toString())
        assertTrue(written.getValue("annotations").jsonArray.isEmpty())
        assertNull(BookDataFormat.decode(BookDataFormat.encode(bookData)).getOrThrow().progress)
    }

    @Test
    fun newerVersion_isUnsupportedVersion() {
        val error =
            BookDataFormat
                .decode(
                    documentedFile.replace("\"version\": 1", "\"version\": 2"),
                ).exceptionOrNull()

        assertTrue(error is BookDataFormatError.UnsupportedVersion)
        assertEquals(2, (error as BookDataFormatError.UnsupportedVersion).foundVersion)
    }

    @Test
    fun corruptFiles_areCorrupt() {
        val corruptTexts =
            listOf(
                "",
                "{ not json",
                "[]",
                "{}",
                """{ "version": "one" }""",
                """{ "version": 0 }""",
                documentedFile.replace("\"title\": \"Cartas a Lucilio\",", ""),
                documentedFile.replace("\"title\": \"Cartas a Lucilio\"", "\"title\": {}"),
                documentedFile.replace("2026-10-11T07:45:12Z", "ayer"),
                documentedFile.replace("0.31", "1.5"),
                documentedFile.replace("\"chapter\": 4", "\"chapter\": -1"),
                documentedFile.replace(IDENTIFIER, "md5:abc"),
            )

        corruptTexts.forEach { text ->
            val error = BookDataFormat.decode(text).exceptionOrNull()
            assertTrue(
                "Expected Corrupt for <$text>, got $error",
                error is BookDataFormatError.Corrupt,
            )
        }
    }

    @Test
    fun preservedFields_areTheOriginalObject() {
        val bookData = BookDataFormat.decode(documentedFile).getOrThrow()

        assertEquals(
            Json.parseToJsonElement(documentedFile) as JsonObject,
            bookData.preservedFields,
        )
    }

    /** Text of the JSON value at [path] (`null` for JSON null), following nested objects. */
    private fun JsonObject.valueAt(vararg path: String): String {
        val parent = path.dropLast(1).fold(this) { node, key -> node.getValue(key).jsonObject }
        val value = parent.getValue(path.last())
        return if (value is JsonNull) "null" else value.jsonPrimitive.content
    }
}
