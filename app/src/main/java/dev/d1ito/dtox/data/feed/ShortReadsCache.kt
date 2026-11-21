package dev.d1ito.dtox.data.feed

import dev.d1ito.dtox.data.storage.AtomicFileWriter
import dev.d1ito.dtox.data.storage.AtomicWriteError
import dev.d1ito.dtox.data.storage.write
import dev.d1ito.dtox.domain.feed.ShortRead
import dev.d1ito.dtox.domain.feed.ShortReadFeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val STATE_FILE_NAME = "state.json"
private const val CORRUPT_STATE_FILE_NAME = "state.json.corrupt"
private const val ITEMS_DIRECTORY_NAME = "items"
private const val BODY_EXTENSION = ".md"

/** Identifiers usable as file names: no separators, no leading dot. */
private val safeIdentifier = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

/**
 * Downloaded short readings and which ones were read (D-36: a reading stays cached until it is
 * read or withdrawn from the feed). Every operation fails with an [IOException] (an
 * [AtomicWriteError] when writing) or with [ShortReadsCacheFormatError.UnsupportedVersion] when
 * the stored state comes from a newer app; that state is then left untouched.
 */
interface ShortReadsCache {
    /** @return the cached readings not read yet, in feed order. */
    suspend fun unreadItems(): Result<List<ShortRead>>

    /** @return the Markdown body of the cached [identifier], or `null` when it is not cached. */
    suspend fun readBody(identifier: String): Result<String?>

    /**
     * Records [identifier] as read for good and drops its cached reading, if any. Idempotent;
     * also accepts identifiers that were never cached.
     */
    suspend fun markAsRead(identifier: String): Result<Unit>

    /**
     * @return the [feed] readings to download: neither read nor cached with the same `sha256`,
     *   and with an identifier usable as a file name; in feed order.
     */
    suspend fun itemsToFetch(feed: ShortReadFeed): Result<List<ShortRead>>

    /**
     * Reconciles the cache with a freshly parsed [feed] (no network here): caches the bodies in
     * [fetchedBodies] (identifier → Markdown) of feed readings not read yet, replacing older
     * versions; deletes the cached readings that left the feed (withdrawal, docs/05 §Retirada);
     * keeps the rest. Read state is never dropped, so a withdrawn reading that comes back does
     * not repeat. Bodies of read, unknown or unsafe identifiers are ignored.
     */
    suspend fun synchronize(
        feed: ShortReadFeed,
        fetchedBodies: Map<String, String>,
    ): Result<Unit>
}

/**
 * Keeps the state in `<directory>/state.json` and each body in `<directory>/items/<id>.md`, all
 * written atomically. The state only names bodies already on disk: bodies are written before
 * the state that lists them and deleted after the state that drops them, so a crash leaves at
 * most unlisted files, deleted on the next [synchronize]. A corrupt state is renamed to
 * `state.json.corrupt` and the cache starts empty.
 *
 * @param directory e.g. `files/shortreads`; created on the first write.
 * @param ioDispatcher where file I/O runs; never the main thread.
 */
class FileShortReadsCache(
    private val directory: File,
    private val fileWriter: AtomicFileWriter,
    private val ioDispatcher: CoroutineDispatcher,
) : ShortReadsCache {
    private val mutex = Mutex()
    private val stateFile = File(directory, STATE_FILE_NAME)
    private val itemsDirectory = File(directory, ITEMS_DIRECTORY_NAME)

    override suspend fun unreadItems(): Result<List<ShortRead>> =
        locked {
            Result.success(readState().items)
        }

    override suspend fun readBody(identifier: String): Result<String?> =
        locked {
            val isCached = readState().items.any { it.id == identifier }
            Result.success(if (isCached) bodyFileOf(identifier).readText(Charsets.UTF_8) else null)
        }

    override suspend fun markAsRead(identifier: String): Result<Unit> =
        locked {
            val state = readState()
            val newState =
                ShortReadsCacheState(
                    items = state.items.filterNot { it.id == identifier },
                    readIdentifiers = state.readIdentifiers + identifier,
                )
            replaceState(newState)
        }

    override suspend fun itemsToFetch(feed: ShortReadFeed): Result<List<ShortRead>> =
        locked {
            val state = readState()
            val cachedDigests = state.items.associate { it.id to it.sha256 }
            Result.success(
                feed.items.filter { item ->
                    item.id !in state.readIdentifiers &&
                        cachedDigests[item.id] != item.sha256 &&
                        safeIdentifier.matches(item.id)
                },
            )
        }

    override suspend fun synchronize(
        feed: ShortReadFeed,
        fetchedBodies: Map<String, String>,
    ): Result<Unit> =
        locked {
            val state = readState()
            val cached = state.items.associateBy { it.id }
            val fetched =
                feed.items.filter { item ->
                    item.id in fetchedBodies &&
                        item.id !in state.readIdentifiers &&
                        safeIdentifier.matches(item.id)
                }
            val fetchedIdentifiers = fetched.map { it.id }.toSet()
            val newState =
                ShortReadsCacheState(
                    items =
                        feed.items.mapNotNull { item ->
                            if (item.id in fetchedIdentifiers) item else cached[item.id]
                        },
                    readIdentifiers = state.readIdentifiers,
                )
            writeBodies(fetched, fetchedBodies).onFailure { return@locked Result.failure(it) }
            replaceState(newState)
        }

    /** Runs [operation] on the I/O dispatcher, one at a time; I/O errors become failures. */
    private suspend fun <Value> locked(operation: suspend () -> Result<Value>): Result<Value> =
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    operation()
                } catch (ioFailure: IOException) {
                    Result.failure(ioFailure)
                } catch (newerState: ShortReadsCacheFormatError.UnsupportedVersion) {
                    Result.failure(newerState)
                }
            }
        }

    /**
     * @return the stored state; empty when missing or corrupt (then set aside).
     * @throws IOException if the file cannot be read or set aside.
     * @throws ShortReadsCacheFormatError.UnsupportedVersion for a state from a newer app.
     */
    private fun readState(): ShortReadsCacheState {
        if (!stateFile.isFile) return ShortReadsCacheState.EMPTY
        val decoded = ShortReadsCacheFormat.decode(stateFile.readText(Charsets.UTF_8))
        val error = decoded.exceptionOrNull()
        if (error is ShortReadsCacheFormatError.Corrupt) {
            val corruptFile = File(directory, CORRUPT_STATE_FILE_NAME)
            Files.move(
                stateFile.toPath(),
                corruptFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            return ShortReadsCacheState.EMPTY
        }
        return decoded.getOrThrow()
    }

    /**
     * Stores [state], then deletes the bodies it no longer lists.
     *
     * @throws IOException if a body cannot be deleted; the new state is already stored.
     */
    private suspend fun replaceState(state: ShortReadsCacheState): Result<Unit> =
        fileWriter
            .write(stateFile, ShortReadsCacheFormat.encode(state).toByteArray(Charsets.UTF_8))
            .onSuccess { deleteUnlistedBodies(state) }

    /** @return success once every body of [items] is on disk, or the first [AtomicWriteError]. */
    private suspend fun writeBodies(
        items: List<ShortRead>,
        bodies: Map<String, String>,
    ): Result<Unit> {
        for (item in items) {
            val body = bodies.getValue(item.id)
            fileWriter.write(bodyFileOf(item.id), body.toByteArray(Charsets.UTF_8)).onFailure {
                return Result.failure(it)
            }
        }
        return Result.success(Unit)
    }

    /** Deletes every file in the items directory that [state] does not list. */
    private fun deleteUnlistedBodies(state: ShortReadsCacheState) {
        val listedNames = state.items.map { bodyFileOf(it.id).name }.toSet()
        val unlisted = itemsDirectory.listFiles()?.filter { it.name !in listedNames }.orEmpty()
        unlisted.forEach { file ->
            if (!file.delete()) throw IOException("Cannot delete ${file.name}")
        }
    }

    private fun bodyFileOf(identifier: String): File =
        File(
            itemsDirectory,
            identifier + BODY_EXTENSION,
        )
}
