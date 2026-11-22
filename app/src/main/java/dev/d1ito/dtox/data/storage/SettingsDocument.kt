package dev.d1ito.dtox.data.storage

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

private const val SETTINGS_FORMAT_VERSION = 1
private val prettyJson = Json { prettyPrint = true }

/**
 * `settings.json` (docs/02-data-formats.md §5), one object with a section per kind of setting.
 * Every section store shares one instance, so writing a section never loses another. The file is
 * read once and then kept in memory; each write replaces it atomically, keeping other sections and
 * unknown fields. Settings must never stop the app: a missing or corrupt file reads as empty.
 *
 * @param settingsFile e.g. `files/settings.json`.
 * @param ioDispatcher where file I/O runs; never the main thread.
 */
class SettingsDocument(
    private val settingsFile: File,
    private val fileWriter: AtomicFileWriter,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val lock = Mutex()
    private var fileContent: JsonObject? = null

    /** @return the section called [name], or `null` when it is missing or not an object. */
    suspend fun section(name: String): JsonObject? =
        lock.withLock { loadedContent()[name] as? JsonObject }

    /**
     * Merges [section] into the section called [name] and rewrites the file.
     *
     * @return success once durably stored, or an [AtomicWriteError]; then nothing has changed.
     */
    suspend fun writeSection(
        name: String,
        section: JsonObject,
    ): Result<Unit> =
        lock.withLock {
            val known =
                buildJsonObject {
                    put("version", SETTINGS_FORMAT_VERSION)
                    put(name, section)
                }
            val updated = merged(loadedContent(), known)
            val text = prettyJson.encodeToString(JsonElement.serializer(), updated)
            fileWriter.write(settingsFile, text.toByteArray(Charsets.UTF_8)).onSuccess {
                fileContent = updated
            }
        }

    /** The file content, read the first time it is needed; the lock must be held. */
    private suspend fun loadedContent(): JsonObject =
        fileContent ?: withContext(ioDispatcher) { readFileContent() }.also { fileContent = it }

    private fun readFileContent(): JsonObject =
        try {
            if (settingsFile.isFile) {
                parsedObject(settingsFile.readText(Charsets.UTF_8))
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

/**
 * One section of a [SettingsDocument] seen as a value.
 *
 * @param name key of the section, e.g. `"reading"`.
 * @param decode the value of a section, `null` when missing; any missing or invalid field must take
 *   its default, so decoding never fails.
 * @param encode the fields of the section this version owns.
 */
class SettingsSection<Value : Any>(
    private val document: SettingsDocument,
    private val name: String,
    private val decode: (JsonObject?) -> Value,
    private val encode: (Value) -> JsonObject,
) {
    private val current = MutableStateFlow<Value?>(null)
    private val lock = Mutex()

    /** The stored or default value, then every change. */
    val values: Flow<Value> =
        flow {
            lock.withLock {
                if (current.value == null) current.value = decode(document.section(name))
            }
            emitAll(current.filterNotNull())
        }

    /** @return success once [value] is durably stored, or an [AtomicWriteError]. */
    suspend fun update(value: Value): Result<Unit> =
        lock.withLock {
            document.writeSection(name, encode(value)).onSuccess { current.value = value }
        }
}
