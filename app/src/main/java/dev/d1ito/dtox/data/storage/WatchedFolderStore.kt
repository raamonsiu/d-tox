package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.WatchedFolder
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.IOException

private const val WATCHED_FOLDERS_FORMAT_VERSION = 1
private const val FOLDERS_FIELD = "folders"
private val prettyJson = Json { prettyPrint = true }

/** The folders the user chose to watch for books (RF-05); only the list, not their access. */
interface WatchedFolderStore {
    /** Current folders in the order they were added, then every change. */
    val folders: Flow<List<WatchedFolder>>

    /**
     * Appends [folder]; a folder already in the list is left where it is.
     *
     * @return success once the list is durably stored, or an [AtomicWriteError].
     */
    suspend fun add(folder: WatchedFolder): Result<Unit>

    /**
     * Removes [folder]; one not in the list is ignored.
     *
     * @return success once the list is durably stored, or an [AtomicWriteError].
     */
    suspend fun remove(folder: WatchedFolder): Result<Unit>
}

/**
 * Keeps the list in `watched-folders.json` (docs/02-data-formats.md §8), written atomically.
 * The list must never stop the app: a missing, unreadable or corrupt file reads as no folders, and
 * entries that are not strings are skipped.
 *
 * @param foldersFile e.g. `files/watched-folders.json`.
 * @param ioDispatcher where file I/O runs; never the main thread.
 */
class FileWatchedFolderStore(
    private val foldersFile: File,
    private val fileWriter: AtomicFileWriter,
    private val ioDispatcher: CoroutineDispatcher,
) : WatchedFolderStore {
    private val current = MutableStateFlow<List<WatchedFolder>?>(null)
    private val lock = Mutex()

    override val folders: Flow<List<WatchedFolder>> =
        flow {
            lock.withLock { loadOnce() }
            emitAll(current.filterNotNull())
        }

    override suspend fun add(folder: WatchedFolder): Result<Unit> =
        change { stored -> if (folder in stored) stored else stored + folder }

    override suspend fun remove(folder: WatchedFolder): Result<Unit> =
        change { stored -> stored - folder }

    /** Writes [edit] of the stored list, and publishes it only once it is durable. */
    private suspend fun change(edit: (List<WatchedFolder>) -> List<WatchedFolder>): Result<Unit> =
        lock.withLock {
            val stored = loadOnce()
            val edited = edit(stored)
            if (edited == stored) return@withLock Result.success(Unit)
            val text =
                prettyJson.encodeToString(
                    JsonElement.serializer(),
                    watchedFoldersJson(edited),
                )
            fileWriter.write(foldersFile, text.toByteArray(Charsets.UTF_8)).onSuccess {
                current.value = edited
            }
        }

    /** @return the stored list, read from the file the first time; the lock must be held. */
    private suspend fun loadOnce(): List<WatchedFolder> =
        current.value
            ?: withContext(ioDispatcher) { readFolders() }.also { read -> current.value = read }

    private fun readFolders(): List<WatchedFolder> =
        try {
            if (foldersFile.isFile) {
                watchedFoldersOf(foldersFile.readText(Charsets.UTF_8))
            } else {
                emptyList()
            }
        } catch (expectedUnreadable: IOException) {
            // An unreadable list must not block the app: start with no folders.
            emptyList()
        }
}

/** The folders in [text]; corrupt content or entries that are not strings are left out. */
internal fun watchedFoldersOf(text: String): List<WatchedFolder> {
    val content =
        try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (expectedCorrupt: SerializationException) {
            null
        }
    val entries = content?.get(FOLDERS_FIELD) as? JsonArray ?: return emptyList()
    return entries
        .mapNotNull { entry ->
            (entry as? JsonPrimitive)
                ?.takeIf { primitive ->
                    primitive.isString
                }?.content
        }.filter { treeUri -> treeUri.isNotBlank() }
        .distinct()
        .map(::WatchedFolder)
}

/** The whole file content for [folders]. */
internal fun watchedFoldersJson(folders: List<WatchedFolder>): JsonObject =
    buildJsonObject {
        put("version", WATCHED_FOLDERS_FORMAT_VERSION)
        putJsonArray(FOLDERS_FIELD) { folders.forEach { folder -> add(folder.treeUri) } }
    }
