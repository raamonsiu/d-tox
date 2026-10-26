package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant

/**
 * One book in the library list (docs/02-data-formats.md §7).
 *
 * @property fileName name of the book's copy inside the library directory.
 * @property lastOpenedAt when the reader last opened it; `null` if never.
 * @property finished whether the reader marked it as finished.
 */
data class LibraryEntry(
    val bookIdentifier: BookIdentifier,
    val title: String,
    val author: String?,
    val fileName: String,
    val lastOpenedAt: Instant?,
    val finished: Boolean,
)

/**
 * The list of imported books, a cache that can always be rebuilt from the library files and the
 * per-book data. Every change is durable before [entries] shows it.
 */
interface LibraryIndex {
    /** Books ordered for the library (RF-02): last opened first, never opened last, by title. */
    val entries: StateFlow<List<LibraryEntry>>

    /** Loads the stored index, or rebuilds it when it is missing, corrupt or from a newer app. */
    suspend fun load(): Result<Unit>

    /** Adds [entry], replacing any entry with the same identifier. */
    suspend fun add(entry: LibraryEntry): Result<Unit>

    /** Removes the entry of [bookIdentifier]; succeeds when there is none. */
    suspend fun remove(bookIdentifier: BookIdentifier): Result<Unit>

    /** Sets [LibraryEntry.lastOpenedAt] of [bookIdentifier] to now; succeeds when there is none. */
    suspend fun markOpened(bookIdentifier: BookIdentifier): Result<Unit>

    /** Replaces the index with one rebuilt from the library files and the per-book data. */
    suspend fun rebuild(): Result<Unit>
}

/**
 * [LibraryIndex] stored as one JSON file (D-34), written atomically. Rebuilding lists the files of
 * [libraryDirectory] named `<hex identifier>.<extension>`; any other name, such as the
 * `.importing-*` leftovers of interrupted imports, is skipped. Title, author, last reading time and
 * finished come from [bookDataStore]; without stored data the file name stands in for the title.
 *
 * @param indexFile e.g. `files/library-index.json`.
 * @param libraryDirectory e.g. `files/library`.
 * @param clock gives the time for [markOpened].
 * @param ioDispatcher where file I/O runs; never the main thread.
 */
class FileLibraryIndex(
    private val indexFile: File,
    private val libraryDirectory: File,
    private val bookDataStore: BookDataStore,
    private val fileWriter: AtomicFileWriter,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.systemUTC(),
) : LibraryIndex {
    private val mutex = Mutex()
    private val mutableEntries = MutableStateFlow<List<LibraryEntry>>(emptyList())

    override val entries: StateFlow<List<LibraryEntry>> = mutableEntries.asStateFlow()

    override suspend fun load(): Result<Unit> =
        locked {
            val stored =
                if (indexFile.isFile) {
                    LibraryIndexFormat.decode(
                        indexFile.readText(),
                    )
                } else {
                    null
                }
            stored?.getOrNull()?.let { loaded ->
                mutableEntries.value = libraryOrder(loaded)
                Result.success(Unit)
            } ?: replaceWith(rebuiltEntries())
        }

    override suspend fun add(entry: LibraryEntry): Result<Unit> =
        locked {
            replaceWith(current().filterNot { it.bookIdentifier == entry.bookIdentifier } + entry)
        }

    override suspend fun remove(bookIdentifier: BookIdentifier): Result<Unit> =
        locked { replaceWith(current().filterNot { it.bookIdentifier == bookIdentifier }) }

    override suspend fun markOpened(bookIdentifier: BookIdentifier): Result<Unit> =
        locked {
            val now = clock.instant()
            replaceWith(
                current().map { entry ->
                    if (entry.bookIdentifier ==
                        bookIdentifier
                    ) {
                        entry.copy(lastOpenedAt = now)
                    } else {
                        entry
                    }
                },
            )
        }

    override suspend fun rebuild(): Result<Unit> = locked { replaceWith(rebuiltEntries()) }

    private fun current(): List<LibraryEntry> = mutableEntries.value

    /** Runs [change] on the I/O dispatcher, one change at a time; I/O errors become failures. */
    private suspend fun locked(change: suspend () -> Result<Unit>): Result<Unit> =
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    change()
                } catch (readFailure: IOException) {
                    Result.failure(readFailure)
                }
            }
        }

    /** Stores [newEntries] durably, then publishes them. */
    private suspend fun replaceWith(newEntries: List<LibraryEntry>): Result<Unit> =
        fileWriter
            .write(indexFile, LibraryIndexFormat.encode(newEntries).toByteArray(Charsets.UTF_8))
            .onSuccess { mutableEntries.value = libraryOrder(newEntries) }

    /** @throws IOException if the library directory cannot be listed. */
    private suspend fun rebuiltEntries(): List<LibraryEntry> {
        val files =
            libraryDirectory.listFiles { file -> file.isFile }
                ?: if (libraryDirectory.exists()) {
                    throw IOException(
                        "Cannot list $libraryDirectory",
                    )
                } else {
                    emptyArray()
                }
        return files.mapNotNull { file -> rebuiltEntryOf(file) }
    }

    private suspend fun rebuiltEntryOf(file: File): LibraryEntry? {
        val identifier =
            BookIdentifier.fromHex(file.nameWithoutExtension).getOrNull() ?: return null
        // Unreadable or newer-format data still lists the book, with what the file name tells.
        val bookData = bookDataStore.read(identifier).getOrNull()
        return LibraryEntry(
            bookIdentifier = identifier,
            title = bookData?.title ?: file.name,
            author = bookData?.author,
            fileName = file.name,
            lastOpenedAt = bookData?.progress?.updatedAt,
            finished = bookData?.progress?.finished ?: false,
        )
    }
}

/** RF-02: last opened first; books never opened after them, alphabetically. */
private fun libraryOrder(entries: List<LibraryEntry>): List<LibraryEntry> =
    entries.sortedWith(
        compareByDescending<LibraryEntry, Instant?>(nullsFirst()) { it.lastOpenedAt }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
