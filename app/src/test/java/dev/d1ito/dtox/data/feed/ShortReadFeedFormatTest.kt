package dev.d1ito.dtox.data.feed

import dev.d1ito.dtox.domain.feed.ShortRead
import dev.d1ito.dtox.domain.feed.ShortReadCategory
import dev.d1ito.dtox.domain.feed.ShortReadFeed
import dev.d1ito.dtox.domain.feed.ShortReadSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The `index.json` example of docs/02-data-formats.md §6, verbatim. */
private val documentedIndex =
    """
    {
      "version": 1,
      "generatedAt": "2026-10-10T04:00:00Z",
      "language": "es",
      "items": [
        {
          "id": "seneca-cartas-lucilio-001",
          "title": "Sobre el buen uso del tiempo",
          "author": "Séneca",
          "category": "filosofia",
          "tags": ["estoicismo", "tiempo"],
          "minutes": 8,
          "words": 1850,
          "license": "PD",
          "source": { "name": "Wikisource", "url": "https://es.wikisource.org/…" },
          "translator": "…",
          "path": "items/seneca-cartas-lucilio-001.md",
          "sha256": "…",
          "publishedAt": "2026-10-10"
        }
      ]
    }
    """.trimIndent()

/** A minimal valid item with only the required fields, plus fields this version does not know. */
private fun minimalItem(
    id: String,
    path: String = "items/$id.md",
): String =
    """
    {
      "id": "$id", "title": "Título", "category": "historia", "minutes": 5, "words": 1000,
      "license": "CC-BY-SA", "source": { "name": "Wikipedia", "url": "https://es.wikipedia.org/" },
      "path": "$path", "sha256": "abc", "publishedAt": "2026-10-11", "audio": "future"
    }
    """.trimIndent()

private fun indexWithItems(vararg items: String): String =
    """
    { "version": 1, "generatedAt": "2026-10-11T04:00:00Z", "language": "es",
      "items": [${items.joinToString(",")}], "nextUpdate": "future" }
    """.trimIndent()

class ShortReadFeedFormatTest {
    @Test
    fun decode_readsTheDocumentedExample() {
        val feed = ShortReadFeedFormat.decode(documentedIndex).getOrThrow()

        val expectedItem =
            ShortRead(
                id = "seneca-cartas-lucilio-001",
                title = "Sobre el buen uso del tiempo",
                author = "Séneca",
                category = ShortReadCategory.PHILOSOPHY,
                tags = listOf("estoicismo", "tiempo"),
                minutes = 8,
                words = 1850,
                license = "PD",
                source = ShortReadSource("Wikisource", "https://es.wikisource.org/…"),
                translator = "…",
                path = "items/seneca-cartas-lucilio-001.md",
                sha256 = "…",
                publishedAt = LocalDate.of(2026, 10, 10),
            )
        assertEquals(
            ShortReadFeed(Instant.parse("2026-10-10T04:00:00Z"), "es", listOf(expectedItem)),
            feed,
        )
    }

    @Test
    fun decode_acceptsMissingOptionalFieldsUnknownFieldsAndUnknownCategories() {
        val item =
            ShortReadFeedFormat
                .decode(indexWithItems(minimalItem("wikipedia-agua")))
                .getOrThrow()
                .items
                .single()

        assertNull(item.author)
        assertNull(item.translator)
        assertEquals(emptyList<String>(), item.tags)
        assertEquals(ShortReadCategory("historia"), item.category)
    }

    @Test
    fun decode_acceptsAnEmptyFeed() {
        assertEquals(emptyList<ShortRead>(), decodeItems(indexWithItems()))
    }

    @Test
    fun decode_rejectsEveryVersionButTheSupportedOne() {
        listOf(0, 2, -1).forEach { version ->
            val error =
                ShortReadFeedFormat
                    .decode(documentedIndex.replace("\"version\": 1", "\"version\": $version"))
                    .exceptionOrNull()

            assertTrue(error is ShortReadFeedFormatError.UnsupportedVersion)
            assertEquals(
                version,
                (error as ShortReadFeedFormatError.UnsupportedVersion).foundVersion,
            )
        }
    }

    @Test
    fun decode_reportsAnUnknownVersionEvenWhenItsShapeIsUnknown() {
        val futureIndex = """{ "version": 2, "readings": {} }"""

        val error = ShortReadFeedFormat.decode(futureIndex).exceptionOrNull()

        assertTrue(error is ShortReadFeedFormatError.UnsupportedVersion)
    }

    @Test
    fun decode_rejectsInvalidDocumentsAsCorrupt() {
        val invalidDocuments =
            listOf(
                "not json",
                "[]",
                """{ "generatedAt": "2026-10-10T04:00:00Z", "language": "es", "items": [] }""",
                documentedIndex.replace("\"version\": 1", "\"version\": \"1\""),
                documentedIndex.replace("\"sha256\": \"…\",", ""),
                documentedIndex.replace("2026-10-10T04:00:00Z", "2026-10-10"),
                documentedIndex.replace(
                    "\"publishedAt\": \"2026-10-10\"",
                    "\"publishedAt\": \"10/10\"",
                ),
                documentedIndex.replace("\"minutes\": 8", "\"minutes\": 0"),
                documentedIndex.replace("\"words\": 1850", "\"words\": -1"),
                documentedIndex.replace(
                    "\"title\": \"Sobre el buen uso del tiempo\"",
                    "\"title\": \" \"",
                ),
                documentedIndex.replace("\"language\": \"es\"", "\"language\": \"\""),
                indexWithItems(minimalItem("repetido"), minimalItem("repetido")),
            )

        invalidDocuments.forEach { document ->
            val error = ShortReadFeedFormat.decode(document).exceptionOrNull()

            assertTrue("accepted: $document", error is ShortReadFeedFormatError.Corrupt)
        }
    }

    @Test
    fun decode_rejectsPathsOutsideTheFeed() {
        listOf(
            "/items/a.md",
            "../a.md",
            "items/../../a.md",
            "https://example.org/a.md",
            "items\\a.md",
        ).forEach { unsafePath ->
            val error =
                ShortReadFeedFormat
                    .decode(
                        indexWithItems(minimalItem("lectura", unsafePath.replace("\\", "\\\\"))),
                    ).exceptionOrNull()

            assertTrue("accepted: $unsafePath", error is ShortReadFeedFormatError.Corrupt)
        }
    }

    private fun decodeItems(text: String): List<ShortRead> =
        ShortReadFeedFormat.decode(text).getOrThrow().items
}
