package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.format.DateTimeParseException

private const val ANNOTATIONS_KEY = "annotations"
private const val ANNOTATION_IDENTIFIER_KEY = "id"
private const val HIGHLIGHT_TYPE = "highlight"
private const val NOTE_TYPE = "note"
private const val BOOKMARK_TYPE = "bookmark"

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
        val preserved = bookData.preservedFields
        val known = json.encodeToJsonElement(BookDataDocument.from(bookData)).jsonObject
        val annotations = annotationsOver(preserved, known.getValue(ANNOTATIONS_KEY).jsonArray)
        return json.encodeToString(
            JsonElement.serializer(),
            merged(preserved, JsonObject(known + (ANNOTATIONS_KEY to annotations))),
        )
    }
}

/**
 * Each [known] annotation [merged] over the preserved one with the same identifier, so unknown
 * fields inside annotations survive; preserved annotations missing from [known] are dropped.
 */
private fun annotationsOver(
    preserved: JsonObject,
    known: JsonArray,
): JsonArray {
    val preservedByIdentifier =
        (preserved[ANNOTATIONS_KEY] as? JsonArray)
            .orEmpty()
            .filterIsInstance<JsonObject>()
            .associateBy(::identifierOf)
    return JsonArray(
        known.map { annotation ->
            val knownAnnotation = annotation.jsonObject
            val previous = preservedByIdentifier[identifierOf(knownAnnotation)]
            if (previous == null) knownAnnotation else merged(previous, knownAnnotation)
        },
    )
}

private fun identifierOf(annotation: JsonObject): String? =
    (annotation[ANNOTATION_IDENTIFIER_KEY] as? JsonPrimitive)?.contentOrNull

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

private fun corrupt(reason: String): Nothing = throw BookDataFormatError.Corrupt(reason)

/** @throws BookDataFormatError.Corrupt with the model error's message on failure. */
private fun <T> Result<T>.orCorrupt(): T = getOrElse { error -> corrupt(error.message.orEmpty()) }

/** @throws BookDataFormatError.Corrupt for an invalid date. */
private fun instantOf(text: String): Instant =
    try {
        Instant.parse(text)
    } catch (invalidDate: DateTimeParseException) {
        corrupt("invalid date '${invalidDate.parsedString}'")
    }

@Serializable
private data class BookDataDocument(
    val version: Int,
    val bookId: String,
    val title: String,
    val author: String? = null,
    val source: SourceDocument,
    val progress: ProgressDocument? = null,
    val annotations: List<AnnotationDocument> = emptyList(),
) {
    /**
     * @throws BookDataFormatError.Corrupt for an invalid identifier, anchor, percent, date or
     *   annotation, or two annotations with the same identifier.
     */
    fun toBookData(root: JsonObject): BookData {
        if (annotations.distinctBy(AnnotationDocument::id).size != annotations.size) {
            corrupt("duplicate annotation identifier")
        }
        return BookData(
            bookIdentifier = BookIdentifier.parse(bookId).orCorrupt(),
            title = title,
            author = author,
            source = BookSource(source.name, instantOf(source.addedAt)),
            progress = progress?.toReadingProgress(),
            annotations = annotations.map(AnnotationDocument::toAnnotation),
            preservedFields = root,
        )
    }

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
                annotations = bookData.annotations.map(AnnotationDocument::from),
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
        if (percent !in 0.0..1.0) corrupt("percent $percent")
        return ReadingProgress(anchor.toAnchor(), percent, instantOf(updatedAt), finished)
    }

    companion object {
        fun from(progress: ReadingProgress): ProgressDocument =
            ProgressDocument(
                anchor = AnchorDocument.from(progress.anchor),
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
) {
    /** @throws BookDataFormatError.Corrupt for a negative position. */
    fun toAnchor(): Anchor = Anchor.create(chapter, block, offset, quote).orCorrupt()

    companion object {
        fun from(anchor: Anchor): AnchorDocument =
            AnchorDocument(anchor.chapter, anchor.block, anchor.offset, anchor.quote)
    }
}

/** The end of a range: an anchor written without quote (docs/02-data-formats.md §3). */
@Serializable
private data class RangeEndDocument(
    val chapter: Int,
    val block: Int,
    val offset: Int,
) {
    /** @throws BookDataFormatError.Corrupt for a negative position. */
    fun toAnchor(): Anchor = Anchor.create(chapter, block, offset).orCorrupt()

    companion object {
        fun from(anchor: Anchor): RangeEndDocument =
            RangeEndDocument(anchor.chapter, anchor.block, anchor.offset)
    }
}

/** One element of `annotations`; `end` and `text` only exist for highlights. */
@Serializable
private data class AnnotationDocument(
    val id: String,
    val type: String,
    val start: AnchorDocument,
    val end: RangeEndDocument? = null,
    val text: String? = null,
    val note: String? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    /** @throws BookDataFormatError.Corrupt for an invalid value or a field its type lacks. */
    fun toAnnotation(): BookAnnotation {
        val metadata =
            AnnotationMetadata(
                AnnotationIdentifier.parse(id).orCorrupt(),
                instantOf(createdAt),
                instantOf(updatedAt),
            )
        return when (type) {
            HIGHLIGHT_TYPE -> toHighlight(metadata)
            NOTE_TYPE -> toNote(metadata)
            BOOKMARK_TYPE -> toBookmark(metadata)
            else -> corrupt("unknown annotation type '$type'")
        }
    }

    private fun toNote(metadata: AnnotationMetadata): BookAnnotation.Note {
        requireNoRange()
        return BookAnnotation.Note
            .create(metadata, start.toAnchor(), note ?: corrupt("note without text"))
            .orCorrupt()
    }

    private fun toBookmark(metadata: AnnotationMetadata): BookAnnotation.Bookmark {
        requireNoRange()
        if (note != null) corrupt("bookmark with note")
        return BookAnnotation.Bookmark(metadata, start.toAnchor())
    }

    /** @throws BookDataFormatError.Corrupt when a type other than highlight has a range. */
    private fun requireNoRange() {
        if (end != null || text != null) corrupt("$type with end or text")
    }

    private fun toHighlight(metadata: AnnotationMetadata): BookAnnotation.Highlight {
        val rangeEnd = end?.toAnchor() ?: corrupt("highlight without end")
        val range = AnchorRange.create(start.toAnchor(), rangeEnd).orCorrupt()
        return BookAnnotation.Highlight
            .create(metadata, range, text ?: corrupt("highlight without text"), note)
            .orCorrupt()
    }

    companion object {
        fun from(annotation: BookAnnotation): AnnotationDocument {
            val highlight = annotation as? BookAnnotation.Highlight
            return AnnotationDocument(
                id = annotation.metadata.identifier.value,
                type =
                    when (annotation) {
                        is BookAnnotation.Highlight -> HIGHLIGHT_TYPE
                        is BookAnnotation.Note -> NOTE_TYPE
                        is BookAnnotation.Bookmark -> BOOKMARK_TYPE
                    },
                start = AnchorDocument.from(annotation.start),
                end = highlight?.range?.end?.let(RangeEndDocument::from),
                text = highlight?.text,
                note = annotation.note,
                createdAt = annotation.metadata.createdAt.toString(),
                updatedAt = annotation.metadata.updatedAt.toString(),
            )
        }
    }
}
