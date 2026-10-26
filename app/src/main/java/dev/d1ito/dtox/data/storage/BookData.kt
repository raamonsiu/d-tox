package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * Contents of `books/<bookId>.json` (docs/02-data-formats.md §4): what the user did with a book.
 *
 * @property source where the book came from.
 * @property progress last reading position, `null` until the book is opened.
 * @property annotations kept as read, not interpreted until the annotation model exists (Fase 2).
 * @property preservedFields the JSON object this was read from, or empty for a new file; fields the
 *   app does not know are written back unchanged (forward compatibility).
 */
data class BookData(
    val bookIdentifier: BookIdentifier,
    val title: String,
    val author: String?,
    val source: BookSource,
    val progress: ReadingProgress?,
    val annotations: JsonArray = JsonArray(emptyList()),
    val preservedFields: JsonObject = JsonObject(emptyMap()),
)

/**
 * @property fileName display name of the imported file.
 * @property addedAt when it was imported.
 */
data class BookSource(
    val fileName: String,
    val addedAt: Instant,
)

/**
 * @property anchor position in the document; its quote helps relocate it if the text changes.
 * @property percent fraction read, `0.0..1.0`.
 * @property finished the user marked the book as read.
 */
data class ReadingProgress(
    val anchor: Anchor,
    val percent: Double,
    val updatedAt: Instant,
    val finished: Boolean,
)

/** Why a book data file could not be read. Returned inside [Result.failure], never thrown. */
sealed class BookDataFormatError(
    message: String,
) : IllegalArgumentException(message) {
    /** Not valid JSON, a required field is missing or a value is invalid. */
    class Corrupt(
        val reason: String,
    ) : BookDataFormatError("Corrupt book data: $reason")

    /** Written by a newer app version; reading it could lose data, so it is left untouched. */
    class UnsupportedVersion(
        val foundVersion: Int,
        val supportedVersion: Int,
    ) : BookDataFormatError("Book data version $foundVersion is newer than $supportedVersion")
}
