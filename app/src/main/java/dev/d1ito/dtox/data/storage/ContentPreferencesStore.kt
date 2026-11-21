package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.feed.ContentPreferences
import dev.d1ito.dtox.domain.feed.ShortReadCategory
import dev.d1ito.dtox.domain.feed.ShortReadDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.putJsonArray

private const val CONTENT_SECTION = "content"
private const val CATEGORIES_KEY = "categories"
private const val DURATIONS_KEY = "durations"

/** The user's choice of short reads, shared by the settings screen and the reading selection. */
interface ContentPreferencesStore {
    /** Current preferences, then every change; starts with the stored or default values. */
    val preferences: Flow<ContentPreferences>

    /** @return success once [preferences] are durably stored, or an [AtomicWriteError]. */
    suspend fun update(preferences: ContentPreferences): Result<Unit>
}

/**
 * Keeps the content preferences in the `"content"` section of `settings.json`
 * (docs/02-data-formats.md §5).
 *
 * @param document the settings file, shared with the stores of the other sections.
 */
class FileContentPreferencesStore(
    document: SettingsDocument,
) : ContentPreferencesStore {
    private val section =
        SettingsSection(document, CONTENT_SECTION, ::contentPreferencesOf, ::contentPreferencesJson)

    override val preferences: Flow<ContentPreferences> = section.values

    override suspend fun update(preferences: ContentPreferences): Result<Unit> =
        section.update(preferences)
}

/**
 * The content section. Unknown categories and durations are skipped; a list that is missing,
 * invalid or left empty takes its default.
 */
internal fun contentPreferencesOf(section: JsonObject?): ContentPreferences {
    val defaults = ContentPreferences.Default
    val categories =
        primitivesOf(section, CATEGORIES_KEY)
            .filter { primitive -> primitive.isString }
            .map { primitive -> ShortReadCategory(primitive.content) }
            .filter { category -> category in ContentPreferences.AVAILABLE_CATEGORIES }
            .toSet()
    val durations =
        primitivesOf(section, DURATIONS_KEY)
            .filterNot { primitive -> primitive.isString }
            .mapNotNull { primitive -> primitive.intOrNull?.let(ShortReadDuration::ofMinutes) }
            .toSet()
    return defaults
        .edited(
            categories = categories.ifEmpty { defaults.categories },
            durations = durations.ifEmpty { defaults.durations },
        ).getOrDefault(defaults) // Both sets are already valid and not empty.
}

/** The fields of the content section this version owns, in the order they are shown. */
internal fun contentPreferencesJson(preferences: ContentPreferences): JsonObject =
    buildJsonObject {
        putJsonArray(CATEGORIES_KEY) {
            ContentPreferences.AVAILABLE_CATEGORIES
                .filter { category -> category in preferences.categories }
                .forEach { category -> add(JsonPrimitive(category.value)) }
        }
        putJsonArray(DURATIONS_KEY) {
            ShortReadDuration.entries
                .filter { duration -> duration in preferences.durations }
                .forEach { duration -> add(JsonPrimitive(duration.minutes)) }
        }
    }

/** The primitive items of the array at [key], or none when it is missing or not an array. */
private fun primitivesOf(
    section: JsonObject?,
    key: String,
): List<JsonPrimitive> =
    (section?.get(key) as? JsonArray).orEmpty().filterIsInstance<JsonPrimitive>()
