package dev.d1ito.dtox.data.feed

import dev.d1ito.dtox.domain.feed.ShortRead
import dev.d1ito.dtox.domain.feed.ShortReadCategory
import dev.d1ito.dtox.domain.feed.ShortReadSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The `state.json` example of docs/02-data-formats.md §6 «Caché local», verbatim. */
private val documentedState =
    """
    {
      "version": 1,
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
      ],
      "readIds": ["epicteto-enquiridion-001"]
    }
    """.trimIndent()

private val documentedItem =
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

class ShortReadsCacheFormatTest {
    @Test
    fun decode_readsTheDocumentedExample() {
        assertEquals(
            ShortReadsCacheState(listOf(documentedItem), setOf("epicteto-enquiridion-001")),
            ShortReadsCacheFormat.decode(documentedState).getOrThrow(),
        )
    }

    @Test
    fun encode_roundTrips_withAndWithoutOptionalFields() {
        val minimal =
            documentedItem.copy(
                id = "agua",
                author = null,
                translator = null,
                tags = emptyList(),
            )
        val state = ShortReadsCacheState(listOf(documentedItem, minimal), setOf("b", "a"))

        val decoded = ShortReadsCacheFormat.decode(ShortReadsCacheFormat.encode(state)).getOrThrow()

        assertEquals(state, decoded)
        assertEquals(listOf("b", "a"), decoded.readIdentifiers.toList())
    }

    @Test
    fun decode_rejectsOtherVersions() {
        listOf(0, 2).forEach { version ->
            val error =
                ShortReadsCacheFormat
                    .decode(documentedState.replace("\"version\": 1", "\"version\": $version"))
                    .exceptionOrNull()

            assertTrue("Got $error", error is ShortReadsCacheFormatError.UnsupportedVersion)
        }
    }

    @Test
    fun decode_rejectsInvalidDocumentsAsCorrupt() {
        listOf(
            "not json",
            "[]",
            """{ "items": [], "readIds": [] }""",
            documentedState.replace("\"readIds\"", "\"otherIds\""),
            documentedState.replace("\"minutes\": 8", "\"minutes\": 0"),
            documentedState.replace("\"2026-10-10\"", "\"ayer\""),
        ).forEach { invalid ->
            val error = ShortReadsCacheFormat.decode(invalid).exceptionOrNull()

            assertTrue("Got $error for $invalid", error is ShortReadsCacheFormatError.Corrupt)
        }
    }
}
