package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Removes a book from the library (RF-04): the library's own copy, its parsed cache and its index
 * entry. The user's original file is never touched (the library only ever held a copy), and the
 * reading data in `books/<bookId>.json` is kept, so importing the book again restores the
 * position. Files go first and the index last, and every step is idempotent: if a step fails the
 * book stays listed (opening it may then report it unavailable) and removing it again finishes
 * the job; the index is never left without an entry for a copy that is still in the library.
 *
 * @param libraryDirectory e.g. `files/library`.
 * @param ioDispatcher where the library copy is deleted; never the main thread.
 */
class LibraryBookRemover(
    private val libraryDirectory: File,
    private val parsedBookCache: ParsedBookCache,
    private val libraryIndex: LibraryIndex,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * @return success once the book is gone from the library (also when it was never there), or
     *   the first failure: an [IOException] (an [AtomicWriteError] for the index write).
     */
    suspend fun remove(bookIdentifier: BookIdentifier): Result<Unit> {
        deleteLibraryCopies(bookIdentifier).onFailure { failure -> return Result.failure(failure) }
        parsedBookCache.remove(bookIdentifier).onFailure { failure ->
            return Result.failure(failure)
        }
        return libraryIndex.remove(bookIdentifier)
    }

    private suspend fun deleteLibraryCopies(bookIdentifier: BookIdentifier): Result<Unit> =
        withContext(ioDispatcher) {
            val copies =
                libraryDirectory.listFiles { file ->
                    file.isFile && file.nameWithoutExtension == bookIdentifier.hex
                }
            if (copies == null) {
                // No directory: nothing was imported yet. One that cannot be listed: an error.
                return@withContext if (libraryDirectory.exists()) {
                    Result.failure(IOException("Cannot list $libraryDirectory"))
                } else {
                    Result.success(Unit)
                }
            }
            val undeleted = copies.filterNot { copy -> copy.delete() }
            if (undeleted.isEmpty()) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Cannot delete ${undeleted.joinToString()}"))
            }
        }
}
