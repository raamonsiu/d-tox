package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.PageMargins
import dev.d1ito.dtox.domain.ReadingFont
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.domain.ReadingTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

private const val SETTINGS_FORMAT_VERSION = 1
private const val READING_SECTION = "reading"
private val prettyJson = Json { prettyPrint = true }

/** The user's reading settings, shared by the reader and the settings screen. */
interface ReadingSettingsStore {
    /** Current settings, then every change; starts with the stored or default values. */
    val settings: Flow<ReadingSettings>

    /** @return success once [settings] are durably stored, or an [AtomicWriteError]. */
    suspend fun update(settings: ReadingSettings): Result<Unit>
}

/**
 * Keeps the reading settings in the `"reading"` section of `settings.json`
 * (docs/02-data-formats.md §5), written atomically. Other sections and unknown fields are kept.
 * Settings must never stop the app: a missing or corrupt file reads as [ReadingSettings.Default],
 * and a missing or invalid value as its default.
 *
 * @param settingsFile e.g. `files/settings.json`.
 * @param ioDispatcher where file I/O runs; never the main thread.
 */
class FileReadingSettingsStore(
    private val settingsFile: File,
    private val fileWriter: AtomicFileWriter,
    private val ioDispatcher: CoroutineDispatcher,
) : ReadingSettingsStore {
    private val current = MutableStateFlow<ReadingSettings?>(null)
    private val lock = Mutex()
    private var fileContent = JsonObject(emptyMap())

    override val settings: Flow<ReadingSettings> =
        flow {
            lock.withLock { loadOnce() }
            emitAll(current.filterNotNull())
        }

    override suspend fun update(settings: ReadingSettings): Result<Unit> =
        lock.withLock {
            loadOnce()
            val updated = merged(fileContent, readingSettingsJson(settings))
            val text = prettyJson.encodeToString(JsonElement.serializer(), updated)
            fileWriter.write(settingsFile, text.toByteArray(Charsets.UTF_8)).onSuccess {
                fileContent = updated
                current.value = settings
            }
        }

    /** Reads the file the first time it is needed; the lock must be held. */
    private suspend fun loadOnce() {
        if (current.value != null) return
        fileContent = withContext(ioDispatcher) { readFileContent() }
        current.value = readingSettingsOf(fileContent[READING_SECTION] as? JsonObject)
    }

    private fun readFileContent(): JsonObject =
        try {
            if (settingsFile.isFile) {
                parsedObject(
                    settingsFile.readText(Charsets.UTF_8),
                )
            } else {
                JsonObject(emptyMap())
            }
        } catch (expectedUnreadable: IOException) {
            // Unreadable settings must not block reading: use the defaults.
            JsonObject(emptyMap())
        }

    private fun parsedObject(text: String): JsonObject =
        try {
            Json.parseToJsonElement(text) as? JsonObject ?: JsonObject(emptyMap())
        } catch (expectedCorrupt: SerializationException) {
            JsonObject(emptyMap())
        }
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

/** The fields this version owns: the format version and the reading section. */
internal fun readingSettingsJson(settings: ReadingSettings): JsonObject =
    buildJsonObject {
        put("version", SETTINGS_FORMAT_VERSION)
        put(
            READING_SECTION,
            buildJsonObject {
                put("textSizeSp", settings.textSizeSp)
                put("lineSpacing", settings.lineSpacing)
                put("margins", settings.margins.name.lowercase())
                put("font", settings.font.name.lowercase())
                put("theme", settings.theme.name.lowercase())
                put("pageAnimation", settings.pageAnimation)
                put("progressIndicator", settings.progressIndicator)
            },
        )
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
