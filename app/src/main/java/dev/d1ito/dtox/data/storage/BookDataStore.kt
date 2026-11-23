package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.time.Clock

private const val DATA_EXTENSION = ".json"
private const val CORRUPT_EXTENSION = ".corrupt"

/** Per-book user data (progress, annotations), one file per book. */
interface BookDataStore {
    /**
     * @return the stored data; `null` when the book has none yet or its file was corrupt (then
     *   it is set aside, so the book still opens). Fails with
     *   [BookDataFormatError.UnsupportedVersion] for a file from a newer app, which is left
     *   untouched, or with an [IOException] when the file cannot be read or set aside.
     */
    suspend fun read(bookIdentifier: BookIdentifier): Result<BookData?>

    /** @return success once [bookData] is durably stored, or an [AtomicWriteError]. */
    suspend fun write(bookData: BookData): Result<Unit>

    /**
     * Reads, changes and writes the data of one book as a single step: updates and writes run one
     * after another, so a change made by one writer (e.g. progress) never overwrites another's
     * (e.g. annotations).
     *
     * @param transform gets the stored data as [read] returns it (`null` when there is none) and
     *   returns the new data for the same book, or a failure to leave the file untouched.
     * @return the written data, or the failure of the read, of [transform] or of the write.
     */
    suspend fun update(
        bookIdentifier: BookIdentifier,
        transform: (BookData?) -> Result<BookData>,
    ): Result<BookData>
}

/**
 * Stores each book as `<booksDirectory>/<bookId>.json` (docs/02-data-formats.md §4), written
 * atomically. A corrupt file is renamed to `<bookId>.json.<epochMillis>.corrupt`, keeping it for
 * inspection without blocking the book. Reads, writes and updates of one instance are serialized,
 * so the app keeps a single instance.
 *
 * @param booksDirectory e.g. `files/books`; created on the first write.
 * @param clock timestamps the names of set-aside corrupt files.
 * @param ioDispatcher where file I/O runs; never the main thread.
 */
class FileBookDataStore(
    booksDirectory: File,
    fileWriter: AtomicFileWriter,
    ioDispatcher: CoroutineDispatcher,
    clock: Clock = Clock.systemUTC(),
) : BookDataStore by StorageBookDataStore(
        { DirectoryBookFileStorage(booksDirectory, fileWriter, ioDispatcher) },
        clock,
    )

/**
 * Stores each book as `<bookId>.json` in a [BookFileStorage], with the rules of
 * [FileBookDataStore]. The storage can be swapped with [relocate] (RF-27); every read, write,
 * update and relocation runs one after another, so the app keeps a single instance.
 *
 * @param initialStorage gives the storage to use; called once, on the first use.
 * @param clock timestamps the names of set-aside corrupt files.
 */
class StorageBookDataStore(
    private val initialStorage: suspend () -> BookFileStorage,
    private val clock: Clock = Clock.systemUTC(),
) : BookDataStore {
    private val fileLock = Mutex()
    private var currentStorage: BookFileStorage? = null

    override suspend fun read(bookIdentifier: BookIdentifier): Result<BookData?> =
        fileLock.withLock { readUnlocked(bookIdentifier) }

    override suspend fun write(bookData: BookData): Result<Unit> =
        fileLock.withLock { writeUnlocked(bookData) }

    override suspend fun update(
        bookIdentifier: BookIdentifier,
        transform: (BookData?) -> Result<BookData>,
    ): Result<BookData> =
        fileLock.withLock {
            val stored =
                readUnlocked(bookIdentifier).getOrElse { failure ->
                    return Result.failure(failure)
                }
            val updated = transform(stored).getOrElse { failure -> return Result.failure(failure) }
            writeUnlocked(updated).map { updated }
        }

    /**
     * Moves to [target] while no read or write runs: [prepare] gets the current and the target
     * storage (e.g. to copy the files and remember the choice), and only when it succeeds do later
     * calls use [target].
     *
     * @return the failure of [prepare]; then the current storage stays in use.
     */
    suspend fun relocate(
        target: BookFileStorage,
        prepare: suspend (current: BookFileStorage, target: BookFileStorage) -> Result<Unit>,
    ): Result<Unit> =
        fileLock.withLock {
            prepare(storage(), target).onSuccess { currentStorage = target }
        }

    /** The storage in use; the lock must be held. */
    private suspend fun storage(): BookFileStorage =
        currentStorage ?: initialStorage().also { storage -> currentStorage = storage }

    private suspend fun readUnlocked(bookIdentifier: BookIdentifier): Result<BookData?> {
        val name = dataFileNameOf(bookIdentifier)
        val content =
            storage().read(name).getOrElse { failure -> return Result.failure(failure) }
                ?: return Result.success(null)
        return decodedOrSetAside(name, content)
    }

    private suspend fun writeUnlocked(bookData: BookData): Result<Unit> =
        storage().write(
            dataFileNameOf(bookData.bookIdentifier),
            BookDataFormat.encode(bookData).toByteArray(Charsets.UTF_8),
        )

    /** @return the decoded data, or `null` once a corrupt file was set aside. */
    private suspend fun decodedOrSetAside(
        name: String,
        content: ByteArray,
    ): Result<BookData?> {
        val decoded = BookDataFormat.decode(content.toString(Charsets.UTF_8))
        if (decoded.exceptionOrNull() !is BookDataFormatError.Corrupt) return decoded
        val corruptName = "$name.${clock.millis()}$CORRUPT_EXTENSION"
        return storage().rename(name, corruptName).map { null }
    }
}

/** `<bookId>.json`, the name of a book's data file. */
internal fun dataFileNameOf(bookIdentifier: BookIdentifier): String =
    bookIdentifier.value + DATA_EXTENSION
