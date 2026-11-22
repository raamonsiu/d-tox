package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.PageMargins
import dev.d1ito.dtox.domain.ReadingFont
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.domain.ReadingTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.File

private const val READING_SECTION = "reading"

/** The user's reading settings, shared by the reader and the settings screen. */
interface ReadingSettingsStore {
    /** Current settings, then every change; starts with the stored or default values. */
    val settings: Flow<ReadingSettings>

    /** @return success once [settings] are durably stored, or an [AtomicWriteError]. */
    suspend fun update(settings: ReadingSettings): Result<Unit>
}

/**
 * Keeps the reading settings in the `"reading"` section of `settings.json`
 * (docs/02-data-formats.md §5). A missing or invalid value reads as its default.
 *
 * @param document the settings file, shared with the stores of the other sections.
 */
class FileReadingSettingsStore(
    document: SettingsDocument,
) : ReadingSettingsStore {
    /**
     * A store with a settings file of its own; only for when no other section store uses it.
     *
     * @param settingsFile e.g. `files/settings.json`.
     * @param ioDispatcher where file I/O runs; never the main thread.
     */
    constructor(
        settingsFile: File,
        fileWriter: AtomicFileWriter,
        ioDispatcher: CoroutineDispatcher,
    ) : this(SettingsDocument(settingsFile, fileWriter, ioDispatcher))

    private val section =
        SettingsSection(document, READING_SECTION, ::readingSettingsOf, ::readingSettingsJson)

    override val settings: Flow<ReadingSettings> = section.values

    override suspend fun update(settings: ReadingSettings): Result<Unit> = section.update(settings)
}

/** The reading section; any missing or invalid value takes its default. */
internal fun readingSettingsOf(section: JsonObject?): ReadingSettings {
    val defaults = ReadingSettings.Default
    if (section == null) return defaults
    val primitive = { key: String -> section[key] as? JsonPrimitive }
    val textSize =
        primitive("textSizeSp")?.intOrNull?.takeIf { size ->
            size in
                ReadingSettings.TEXT_SIZE_RANGE
        }
    val spacing =
        primitive("lineSpacing")?.doubleOrNull?.takeIf { value ->
            value in
                ReadingSettings.LINE_SPACING_RANGE
        }
    return defaults
        .edited(
            textSizeSp = textSize ?: defaults.textSizeSp,
            lineSpacing = spacing ?: defaults.lineSpacing,
            margins = enumOrNull<PageMargins>(primitive("margins")) ?: defaults.margins,
            font = enumOrNull<ReadingFont>(primitive("font")) ?: defaults.font,
            theme = enumOrNull<ReadingTheme>(primitive("theme")) ?: defaults.theme,
            pageAnimation = primitive("pageAnimation")?.booleanOrNull ?: defaults.pageAnimation,
            progressIndicator =
                primitive("progressIndicator")?.booleanOrNull ?: defaults.progressIndicator,
        ).getOrDefault(defaults) // Every value above is already inside its range.
}

/** The fields of the reading section this version owns. */
internal fun readingSettingsJson(settings: ReadingSettings): JsonObject =
    buildJsonObject {
        put("textSizeSp", settings.textSizeSp)
        put("lineSpacing", settings.lineSpacing)
        put("margins", settings.margins.name.lowercase())
        put("font", settings.font.name.lowercase())
        put("theme", settings.theme.name.lowercase())
        put("pageAnimation", settings.pageAnimation)
        put("progressIndicator", settings.progressIndicator)
    }

/** Enum constant written in lowercase (e.g. `"open_dyslexic"`), or `null` if unknown. */
private inline fun <reified Constant : Enum<Constant>> enumOrNull(
    value: JsonPrimitive?,
): Constant? =
    value?.takeIf { primitive -> primitive.isString }?.content?.let { name ->
        enumValues<Constant>().firstOrNull { constant ->
            constant.name.equals(name, ignoreCase = true)
        }
    }
