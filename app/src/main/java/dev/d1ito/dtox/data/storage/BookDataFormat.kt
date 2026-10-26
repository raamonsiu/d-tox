package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Reads and writes the book data file format version [SUPPORTED_VERSION]. Pure: no disk access.
 * Dates are ISO-8601 in UTC (`2026-10-11T07:45:12Z`).
 */
object BookDataFormat {
    const val SUPPORTED_VERSION = 1

    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            // Every known field is written, nulls included, so a cleared value (e.g. a quote set
            // to null) replaces the old one instead of letting the preserved copy survive.
            encodeDefaults = true
            explicitNulls = true
        }

    /**
     * @return the data, or a [BookDataFormatError]: [BookDataFormatError.UnsupportedVersion] for
     *   files from a newer app, [BookDataFormatError.Corrupt] for anything else that is invalid.
     */
    fun decode(text: String): Result<BookData> =
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val version =
                root["version"]?.jsonPrimitive?.intOrNull
                    ?: throw BookDataFormatError.Corrupt("missing integer version")
            when {
                version > SUPPORTED_VERSION ->
                    Result.failure(
                        BookDataFormatError.UnsupportedVersion(version, SUPPORTED_VERSION),
                    )
                version < 1 -> Result.failure(BookDataFormatError.Corrupt("version $version"))
                else ->
                    Result.success(
                        json.decodeFromJsonElement<BookDataDocument>(root).toBookData(root),
                    )
            }
        } catch (invalid: BookDataFormatError) {
            Result.failure(invalid)
        } catch (invalidJson: SerializationException) {
            Result.failure(BookDataFormatError.Corrupt(invalidJson.message.orEmpty()))
        } catch (wrongShape: IllegalArgumentException) {
            // jsonObject/jsonPrimitive throw this when an element has another JSON type.
            Result.failure(BookDataFormatError.Corrupt(wrongShape.message.orEmpty()))
        }

    /** @return the JSON text: known fields from [bookData] over its [BookData.preservedFields]. */
    fun encode(bookData: BookData): String {
        val known = json.encodeToJsonElement(BookDataDocument.from(bookData)).jsonObject
        return json.encodeToString(
            JsonElement.serializer(),
            merged(bookData.preservedFields, known),
        )
    }
}

/**
 * [known] values replace those in [preserved]; nested objects are merged the same way, so unknown
 * fields survive at every level. Keys keep their original order; new keys go last.
 */
internal fun merged(
    preserved: JsonObject,
    known: JsonObject,
): JsonObject {
    val result = LinkedHashMap<String, JsonElement>(preserved)
    known.forEach { (key, value) ->
        val previous = result[key]
        result[key] =
            if (previous is JsonObject && value is JsonObject) merged(previous, value) else value
    }
    return JsonObject(result)
}

/** @throws BookDataFormatError.Corrupt for an invalid date. */
private fun instantOf(text: String): Instant =
    try {
        Instant.parse(text)
    } catch (invalidDate: DateTimeParseException) {
        throw BookDataFormatError.Corrupt("invalid date '${invalidDate.parsedString}'")
    }

@Serializable
private data class BookDataDocument(
    val version: Int,
    val bookId: String,
    val title: String,
    val author: String? = null,
    val source: SourceDocument,
    val progress: ProgressDocument? = null,
    val annotations: JsonArray = JsonArray(emptyList()),
) {
    /** @throws BookDataFormatError.Corrupt for an invalid identifier, anchor, percent or date. */
    fun toBookData(root: JsonObject): BookData =
        BookData(
            bookIdentifier =
                BookIdentifier.parse(bookId).getOrElse { error ->
                    throw BookDataFormatError.Corrupt(error.message.orEmpty())
                },
            title = title,
            author = author,
            source = BookSource(source.name, instantOf(source.addedAt)),
            progress = progress?.toReadingProgress(),
            annotations = annotations,
            preservedFields = root,
        )

    companion object {
        fun from(bookData: BookData): BookDataDocument =
            BookDataDocument(
                version = BookDataFormat.SUPPORTED_VERSION,
                bookId = bookData.bookIdentifier.value,
                title = bookData.title,
                author = bookData.author,
                source =
                    SourceDocument(bookData.source.fileName, bookData.source.addedAt.toString()),
                progress = bookData.progress?.let(ProgressDocument::from),
                annotations = bookData.annotations,
            )
    }
}

@Serializable
private data class SourceDocument(
    val name: String,
    val addedAt: String,
)

@Serializable
private data class ProgressDocument(
    val anchor: AnchorDocument,
    val percent: Double,
    val updatedAt: String,
    val finished: Boolean = false,
) {
    fun toReadingProgress(): ReadingProgress {
        if (percent !in 0.0..1.0) throw BookDataFormatError.Corrupt("percent $percent")
        val position =
            with(anchor) { Anchor.create(chapter, block, offset, quote) }.getOrElse { error ->
                throw BookDataFormatError.Corrupt(error.message.orEmpty())
            }
        return ReadingProgress(position, percent, instantOf(updatedAt), finished)
    }

    companion object {
        fun from(progress: ReadingProgress): ProgressDocument =
            ProgressDocument(
                anchor =
                    AnchorDocument(
                        progress.anchor.chapter,
                        progress.anchor.block,
                        progress.anchor.offset,
                        progress.anchor.quote,
                    ),
                percent = progress.percent,
                updatedAt = progress.updatedAt.toString(),
                finished = progress.finished,
            )
    }
}

@Serializable
private data class AnchorDocument(
    val chapter: Int,
    val block: Int,
    val offset: Int,
    val quote: String? = null,
)
