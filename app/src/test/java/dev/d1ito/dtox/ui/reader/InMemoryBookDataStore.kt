package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.data.storage.BookData
import dev.d1ito.dtox.data.storage.BookDataStore
import dev.d1ito.dtox.domain.BookIdentifier
import java.io.IOException

/**
 * [BookDataStore] in memory that records every write.
 *
 * @param readFailure when set, every read fails with it (e.g. an unreadable file).
 */
internal class InMemoryBookDataStore(
    initial: List<BookData> = emptyList(),
    private val readFailure: Throwable? = null,
) : BookDataStore {
    private val stored = initial.associateBy { data -> data.bookIdentifier }.toMutableMap()

    /** Every successful write, oldest first. */
    val writes = mutableListOf<BookData>()

    /** When `true`, writes fail with an [IOException] and store nothing. */
    var failWrites = false

    override suspend fun read(bookIdentifier: BookIdentifier): Result<BookData?> =
        readFailure?.let { failure -> Result.failure(failure) }
            ?: Result.success(stored[bookIdentifier])

    override suspend fun write(bookData: BookData): Result<Unit> {
        if (failWrites) return Result.failure(IOException("disk full"))
        stored[bookData.bookIdentifier] = bookData
        writes += bookData
        return Result.success(Unit)
    }
}
