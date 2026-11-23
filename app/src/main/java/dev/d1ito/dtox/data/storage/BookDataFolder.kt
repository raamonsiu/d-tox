package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.time.Clock

private const val DATA_FOLDER_FORMAT_VERSION = 1
private const val FOLDER_FIELD = "folder"
private const val DATA_FILE_EXTENSION = ".json"
private val prettyJson = Json { prettyPrint = true }

/** Where the per-book data files live (RF-27). */
sealed interface BookDataLocation {
    /** The app's private `files/books`, the default. */
    data object Internal : BookDataLocation

    /** The `books` folder inside a folder the user chose, as its SAF tree URI. */
    data class Folder(
        val treeUri: String,
    ) : BookDataLocation
}

/**
 * Owns the app's [BookDataStore] and where it keeps its files (RF-27). The choice is kept in
 * `data-folder.json` (docs/02-data-formats.md §10); a missing, unreadable or corrupt file means
 * [BookDataLocation.Internal].
 *
 * @param locationFile e.g. `files/data-folder.json`.
 * @param internalStorage e.g. `files/books`.
 * @param folderStorageOf the storage inside the folder with the given tree URI.
 * @param ioDispatcher where [locationFile] is read; never the main thread.
 */
@Suppress("LongParameterList") // Two files, two storages and the dispatcher and clock they use.
class BookDataFolder(
    private val locationFile: File,
    private val internalStorage: BookFileStorage,
    private val folderStorageOf: (String) -> BookFileStorage,
    private val fileWriter: AtomicFileWriter,
    private val ioDispatcher: CoroutineDispatcher,
    clock: Clock = Clock.systemUTC(),
) {
    private val locationLock = Mutex()
    private var location: BookDataLocation? = null

    /** The store of the app; it follows every successful [moveTo]. */
    val store = StorageBookDataStore({ storageOf(currentLocation()) }, clock)

    /** @return where the data files live now. */
    suspend fun currentLocation(): BookDataLocation =
        locationLock.withLock {
            location
                ?: withContext(ioDispatcher) { readLocation() }.also { read -> location = read }
        }

    /**
     * Copies the data files to [target] (see [copyNewerBookFiles]), remembers it and switches the
     * store to it, with no read or write of the store in between. Moving to the current location
     * does nothing.
     *
     * @return success once [target] is in use, or the failure of the copy or of storing the
     *   choice; then the current location stays in use, and files already copied stay in [target].
     */
    suspend fun moveTo(target: BookDataLocation): Result<Unit> =
        store.relocate(storageOf(target)) { current, targetStorage ->
            if (target == currentLocation()) return@relocate Result.success(Unit)
            copyNewerBookFiles(current, targetStorage).onFailure { failure ->
                return@relocate Result.failure(failure)
            }
            val text = prettyJson.encodeToString(JsonElement.serializer(), locationJson(target))
            locationLock.withLock {
                fileWriter.write(locationFile, text.toByteArray(Charsets.UTF_8)).onSuccess {
                    location = target
                }
            }
        }

    private fun storageOf(location: BookDataLocation): BookFileStorage =
        when (location) {
            BookDataLocation.Internal -> internalStorage
            is BookDataLocation.Folder -> folderStorageOf(location.treeUri)
        }

    private fun readLocation(): BookDataLocation =
        try {
            if (locationFile.isFile) {
                bookDataLocationOf(locationFile.readText(Charsets.UTF_8))
            } else {
                BookDataLocation.Internal
            }
        } catch (expectedUnreadable: IOException) {
            // An unreadable choice must not stop the app: keep the data in the default place.
            BookDataLocation.Internal
        }
}

/**
 * Copies each `<bookId>.json` of [source] to [destination] unless [destination] has one at least
 * as new (by modification time; when either time is unknown, the destination's file is kept).
 * Other files, e.g. `*.corrupt` or sync conflicts, are not copied.
 *
 * @return success also when [source] cannot be listed (e.g. its access was revoked), as there is
 *   nothing to copy; otherwise the first failure of reading, listing or writing.
 */
internal suspend fun copyNewerBookFiles(
    source: BookFileStorage,
    destination: BookFileStorage,
): Result<Unit> {
    val sourceFiles = source.list().getOrElse { return Result.success(Unit) }
    val destinationFiles =
        destination
            .list()
            .getOrElse { failure -> return Result.failure(failure) }
            .associateBy { file -> file.name }
    return sourceFiles
        .filter { file ->
            isBookDataFileName(file.name) && file.isNewerThan(destinationFiles[file.name])
        }.fold(Result.success(Unit)) { copied, file ->
            if (copied.isFailure) copied else copyFile(file.name, source, destination)
        }
}

/** Copies the file [name] from [source] to [destination]; nothing when it is gone meanwhile. */
private suspend fun copyFile(
    name: String,
    source: BookFileStorage,
    destination: BookFileStorage,
): Result<Unit> =
    source.read(name).fold(
        onSuccess = { content ->
            content?.let { bytes -> destination.write(name, bytes) } ?: Result.success(Unit)
        },
        onFailure = { failure -> Result.failure(failure) },
    )

private fun isBookDataFileName(name: String): Boolean =
    name.endsWith(DATA_FILE_EXTENSION) &&
        BookIdentifier.parse(name.removeSuffix(DATA_FILE_EXTENSION)).isSuccess

private fun StoredBookFile.isNewerThan(other: StoredBookFile?): Boolean {
    if (other == null) return true
    val ownTime = lastModifiedMillis
    val otherTime = other.lastModifiedMillis
    return ownTime != null && otherTime != null && ownTime > otherTime
}

/** The location in [text]; corrupt content or a blank folder reads as the internal storage. */
internal fun bookDataLocationOf(text: String): BookDataLocation {
    val content =
        try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (expectedCorrupt: SerializationException) {
            null
        }
    val folder =
        (content?.get(FOLDER_FIELD) as? JsonPrimitive)
            ?.takeIf { primitive -> primitive.isString }
            ?.content
    return if (folder.isNullOrBlank()) {
        BookDataLocation.Internal
    } else {
        BookDataLocation.Folder(
            folder,
        )
    }
}

/** The whole file content for [location]. */
internal fun locationJson(location: BookDataLocation): JsonObject =
    buildJsonObject {
        put("version", DATA_FOLDER_FORMAT_VERSION)
        when (location) {
            BookDataLocation.Internal -> put(FOLDER_FIELD, JsonNull)
            is BookDataLocation.Folder -> put(FOLDER_FIELD, location.treeUri)
        }
    }
