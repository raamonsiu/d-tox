package dev.d1ito.dtox.data.feed

import dev.d1ito.dtox.domain.feed.ShortRead
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the short-reads cache holds (docs/02-data-formats.md §6, «Caché local»).
 *
 * @property items cached readings not read yet, in feed order; each has its body on disk.
 * @property readIdentifiers every reading marked as read, kept even after it leaves the feed so
 *   it never repeats.
 */
internal data class ShortReadsCacheState(
    val items: List<ShortRead>,
    val readIdentifiers: Set<String>,
) {
    companion object {
        val EMPTY = ShortReadsCacheState(emptyList(), emptySet())
    }
}

/** Why the cache `state.json` cannot be read. */
sealed class ShortReadsCacheFormatError(
    message: String,
) : IllegalArgumentException(message) {
    /** Not valid JSON, a required field is missing or a value is invalid. */
    class Corrupt(
        val reason: String,
    ) : ShortReadsCacheFormatError("Corrupt short-reads state: $reason")

    /** Any version other than [ShortReadsCacheFormat.SUPPORTED_VERSION], e.g. from a newer app. */
    class UnsupportedVersion(
        val foundVersion: Int,
        val supportedVersion: Int,
    ) : ShortReadsCacheFormatError(
            "Short-reads state version $foundVersion is not $supportedVersion",
        )
}

/** Reads and writes the cache `state.json` version [SUPPORTED_VERSION]. Pure: no disk access. */
internal object ShortReadsCacheFormat {
    const val SUPPORTED_VERSION = 1

    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    /**
     * @return the state, or a [ShortReadsCacheFormatError]: `UnsupportedVersion` for any other
     *   version, `Corrupt` for anything else that is invalid.
     */
    fun decode(text: String): Result<ShortReadsCacheState> =
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val version =
                root["version"]?.jsonPrimitive?.takeUnless { it.isString }?.intOrNull
                    ?: throw ShortReadsCacheFormatError.Corrupt("missing integer version")
            if (version == SUPPORTED_VERSION) {
                Result.success(json.decodeFromJsonElement<StateDocument>(root).toState())
            } else {
                Result.failure(
                    ShortReadsCacheFormatError.UnsupportedVersion(version, SUPPORTED_VERSION),
                )
            }
        } catch (invalidItem: ShortReadFeedFormatError) {
            Result.failure(ShortReadsCacheFormatError.Corrupt(invalidItem.message.orEmpty()))
        } catch (invalid: ShortReadsCacheFormatError) {
            Result.failure(invalid)
        } catch (invalidJson: SerializationException) {
            Result.failure(ShortReadsCacheFormatError.Corrupt(invalidJson.message.orEmpty()))
        } catch (wrongShape: IllegalArgumentException) {
            // jsonObject/jsonPrimitive throw this when an element has another JSON type.
            Result.failure(ShortReadsCacheFormatError.Corrupt(wrongShape.message.orEmpty()))
        }

    fun encode(state: ShortReadsCacheState): String =
        json.encodeToString(
            StateDocument.serializer(),
            StateDocument(
                version = SUPPORTED_VERSION,
                items = state.items.map(ItemDocument::from),
                readIds = state.readIdentifiers.toList(),
            ),
        )
}

@Serializable
private data class StateDocument(
    val version: Int,
    val items: List<ItemDocument>,
    val readIds: List<String>,
) {
    /** @throws ShortReadFeedFormatError.Corrupt for an invalid item. */
    fun toState(): ShortReadsCacheState =
        ShortReadsCacheState(items.map(ItemDocument::toShortRead), readIds.toSet())
}
