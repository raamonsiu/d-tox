package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val CACHE_EXTENSION = ".json"

/**
 * Parsed books kept so a book opens without parsing its file again (RNF-04). A cache: anything
 * missing or unusable is simply parsed again by the caller.
 */
interface ParsedBookCache {
    /**
     * @return the cached book, or `null` when there is none or it is unusable (corrupt, another
     *   [ParsedBookFormat.VERSION]); an unusable file is deleted. Fails only with an
     *   [IOException] when the file exists but cannot be read.
     */
    suspend fun read(bookIdentifier: BookIdentifier): Result<Book?>

    /** @return success once [book] is stored, or an [AtomicWriteError]. */
    suspend fun write(book: Book): Result<Unit>

    /** Forgets the cached [bookIdentifier]; succeeds when there was nothing cached. */
    suspend fun remove(bookIdentifier: BookIdentifier): Result<Unit>
}

/**
 * Stores each parsed book as `<cacheDirectory>/<hex identifier>.json`, written atomically so a
 * reader never sees half a file. The system may delete the app cache at any time; that only
 * costs a new parse.
 *
 * @param cacheDirectory e.g. `cache/parsed`; created on the first write.
 * @param ioDispatcher where file I/O runs; never the main thread.
 */
class FileParsedBookCache(
    private val cacheDirectory: File,
    private val fileWriter: AtomicFileWriter,
    private val ioDispatcher: CoroutineDispatcher,
) : ParsedBookCache {
    override suspend fun read(bookIdentifier: BookIdentifier): Result<Book?> =
        withContext(ioDispatcher) {
            val cacheFile = cacheFileOf(bookIdentifier)
            try {
                if (cacheFile.isFile) {
                    Result.success(
                        usableBookOrDelete(cacheFile, bookIdentifier),
                    )
                } else {
                    Result.success(null)
                }
            } catch (readFailure: IOException) {
                Result.failure(readFailure)
            }
        }

    override suspend fun write(book: Book): Result<Unit> =
        fileWriter.write(
            cacheFileOf(book.identifier),
            ParsedBookFormat.encode(book).toByteArray(Charsets.UTF_8),
        )

    override suspend fun remove(bookIdentifier: BookIdentifier): Result<Unit> =
        withContext(ioDispatcher) {
            val cacheFile = cacheFileOf(bookIdentifier)
            if (!cacheFile.exists() || cacheFile.delete()) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Cannot delete $cacheFile"))
            }
        }

    private fun cacheFileOf(bookIdentifier: BookIdentifier): File =
        File(cacheDirectory, bookIdentifier.hex + CACHE_EXTENSION)

    /** @throws IOException if the file cannot be read. */
    private fun usableBookOrDelete(
        cacheFile: File,
        bookIdentifier: BookIdentifier,
    ): Book? {
        val book =
            ParsedBookFormat
                .decode(cacheFile.readText(Charsets.UTF_8))
                .getOrNull()
                ?.takeIf { cached -> cached.identifier == bookIdentifier }
        if (book == null) cacheFile.delete()
        return book
    }
}
