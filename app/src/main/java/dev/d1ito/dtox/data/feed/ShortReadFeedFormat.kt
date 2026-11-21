package dev.d1ito.dtox.data.feed

import dev.d1ito.dtox.domain.feed.ShortRead
import dev.d1ito.dtox.domain.feed.ShortReadCategory
import dev.d1ito.dtox.domain.feed.ShortReadFeed
import dev.d1ito.dtox.domain.feed.ShortReadSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Why a feed `index.json` cannot be read. */
sealed class ShortReadFeedFormatError(
    message: String,
) : IllegalArgumentException(message) {
    /** Not valid JSON, a required field is missing or a value is invalid. */
    class Corrupt(
        val reason: String,
    ) : ShortReadFeedFormatError("Corrupt feed index: $reason")

    /** Any version other than [ShortReadFeedFormat.SUPPORTED_VERSION]; its shape is unknown. */
    class UnsupportedVersion(
        val foundVersion: Int,
        val supportedVersion: Int,
    ) : ShortReadFeedFormatError("Feed index version $foundVersion is not $supportedVersion")
}

/**
 * Reads the feed `index.json` version [SUPPORTED_VERSION] (docs/02-data-formats.md §6). Pure: no
 * network or disk access. Unknown fields are ignored; unknown categories and licenses are kept.
 */
object ShortReadFeedFormat {
    const val SUPPORTED_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param text the downloaded `index.json`.
     * @return the feed, or a [ShortReadFeedFormatError]: `UnsupportedVersion` for any other
     *   version (even if it looks compatible), `Corrupt` for anything else that is invalid.
     */
    fun decode(text: String): Result<ShortReadFeed> =
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val version =
                root["version"]?.jsonPrimitive?.takeUnless { it.isString }?.intOrNull
                    ?: throw ShortReadFeedFormatError.Corrupt("missing integer version")
            if (version == SUPPORTED_VERSION) {
                Result.success(json.decodeFromJsonElement<FeedDocument>(root).toFeed())
            } else {
                Result.failure(
                    ShortReadFeedFormatError.UnsupportedVersion(version, SUPPORTED_VERSION),
                )
            }
        } catch (invalid: ShortReadFeedFormatError) {
            Result.failure(invalid)
        } catch (invalidJson: SerializationException) {
            Result.failure(ShortReadFeedFormatError.Corrupt(invalidJson.message.orEmpty()))
        } catch (wrongShape: IllegalArgumentException) {
            // jsonObject/jsonPrimitive throw this when an element has another JSON type.
            Result.failure(ShortReadFeedFormatError.Corrupt(wrongShape.message.orEmpty()))
        }
}

/** @throws ShortReadFeedFormatError.Corrupt unless [condition] holds. */
private fun requireValid(
    condition: Boolean,
    reason: () -> String,
) {
    if (!condition) throw ShortReadFeedFormatError.Corrupt(reason())
}

/** @throws ShortReadFeedFormatError.Corrupt for a blank [value]. */
private fun nonBlank(
    field: String,
    value: String,
): String = value.also { requireValid(it.isNotBlank()) { "blank $field" } }

/** @throws ShortReadFeedFormatError.Corrupt for text that is not a valid [parse] input. */
private fun <Date> dateOf(
    text: String,
    parse: (String) -> Date,
): Date =
    try {
        parse(text)
    } catch (invalidDate: DateTimeParseException) {
        throw ShortReadFeedFormatError.Corrupt("invalid date '${invalidDate.parsedString}'")
    }

/**
 * @return whether [path] stays inside the feed once resolved against its address: relative, with
 *   no scheme, backslash or `..` segment.
 */
private fun isInsideFeed(path: String): Boolean =
    !path.startsWith("/") && ':' !in path && '\\' !in path && ".." !in path.split("/")

@Serializable
private data class FeedDocument(
    val generatedAt: String,
    val language: String,
    val items: List<ItemDocument>,
) {
    /** @throws ShortReadFeedFormatError.Corrupt for an invalid value or a repeated item id. */
    fun toFeed(): ShortReadFeed {
        val duplicateIdentifier = items.groupBy { it.id }.entries.firstOrNull { it.value.size > 1 }
        requireValid(duplicateIdentifier == null) { "repeated id '${duplicateIdentifier?.key}'" }
        return ShortReadFeed(
            generatedAt = dateOf(generatedAt, Instant::parse),
            language = nonBlank("language", language),
            items = items.map(ItemDocument::toShortRead),
        )
    }
}

/** One feed item as JSON; also the item shape of the local cache (docs/02-data-formats.md §6). */
@Serializable
internal data class ItemDocument(
    val id: String,
    val title: String,
    val author: String? = null,
    val category: String,
    val tags: List<String> = emptyList(),
    val minutes: Int,
    val words: Int,
    val license: String,
    val source: SourceDocument,
    val translator: String? = null,
    val path: String,
    val sha256: String,
    val publishedAt: String,
) {
    /** @throws ShortReadFeedFormatError.Corrupt for a blank, non-positive or unsafe value. */
    fun toShortRead(): ShortRead {
        requireValid(minutes > 0 && words > 0) { "item '$id': minutes $minutes, words $words" }
        requireValid(isInsideFeed(path)) { "unsafe path '$path'" }
        return ShortRead(
            id = nonBlank("id", id),
            title = nonBlank("title", title),
            author = author,
            category = ShortReadCategory(nonBlank("category", category)),
            tags = tags,
            minutes = minutes,
            words = words,
            license = nonBlank("license", license),
            source =
                ShortReadSource(
                    name = nonBlank("source.name", source.name),
                    url = nonBlank("source.url", source.url),
                ),
            translator = translator,
            path = nonBlank("path", path),
            sha256 = nonBlank("sha256", sha256),
            publishedAt = dateOf(publishedAt, LocalDate::parse),
        )
    }

    companion object {
        fun from(item: ShortRead): ItemDocument =
            ItemDocument(
                id = item.id,
                title = item.title,
                author = item.author,
                category = item.category.value,
                tags = item.tags,
                minutes = item.minutes,
                words = item.words,
                license = item.license,
                source = SourceDocument(item.source.name, item.source.url),
                translator = item.translator,
                path = item.path,
                sha256 = item.sha256,
                publishedAt = item.publishedAt.toString(),
            )
    }
}

@Serializable
internal data class SourceDocument(
    val name: String,
    val url: String,
)
