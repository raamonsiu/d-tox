package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Reads and writes `library-index.json` version [SUPPORTED_VERSION]. Pure: no disk access.
 * The index is a cache, so any invalid content is reported as a failure and simply rebuilt.
 */
object LibraryIndexFormat {
    const val SUPPORTED_VERSION = 1

    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

    /** @return the entries, or a failure for invalid JSON, invalid values or another version. */
    fun decode(text: String): Result<List<LibraryEntry>> =
        try {
            val document = json.decodeFromString<IndexDocument>(text)
            if (document.version == SUPPORTED_VERSION) {
                Result.success(document.books.map(EntryDocument::toEntry))
            } else {
                Result.failure(
                    IllegalArgumentException("Unsupported index version ${document.version}"),
                )
            }
        } catch (invalidJson: SerializationException) {
            Result.failure(invalidJson)
        } catch (invalidValue: IllegalArgumentException) {
            Result.failure(invalidValue)
        } catch (invalidDate: DateTimeParseException) {
            Result.failure(invalidDate)
        }

    fun encode(entries: List<LibraryEntry>): String =
        json.encodeToString(
            IndexDocument.serializer(),
            IndexDocument(SUPPORTED_VERSION, entries.map(EntryDocument::from)),
        )
}

@Serializable
private data class IndexDocument(
    val version: Int,
    val books: List<EntryDocument>,
)

@Serializable
private data class EntryDocument(
    val bookId: String,
    val title: String,
    val author: String? = null,
    val file: String,
    val lastOpenedAt: String? = null,
    val finished: Boolean = false,
) {
    /**
     * @throws IllegalArgumentException for an invalid identifier.
     * @throws DateTimeParseException for an invalid date.
     */
    fun toEntry(): LibraryEntry =
        LibraryEntry(
            bookIdentifier = BookIdentifier.parse(bookId).getOrThrow(),
            title = title,
            author = author,
            fileName = file,
            lastOpenedAt = lastOpenedAt?.let(Instant::parse),
            finished = finished,
        )

    companion object {
        fun from(entry: LibraryEntry): EntryDocument =
            EntryDocument(
                bookId = entry.bookIdentifier.value,
                title = entry.title,
                author = entry.author,
                file = entry.fileName,
                lastOpenedAt = entry.lastOpenedAt?.toString(),
                finished = entry.finished,
            )
    }
}
