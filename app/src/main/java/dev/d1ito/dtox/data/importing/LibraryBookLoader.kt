package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.data.storage.ParsedBookCache
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * A library book ready to read.
 *
 * @property fileName name of its copy in the library directory.
 */
data class LoadedBook(
    val book: Book,
    val fileName: String,
)

/** Why a library book could not be opened (besides read and parse failures). */
sealed class LibraryBookError(
    message: String,
) : IllegalStateException(message) {
    /** No file named after [bookIdentifier] is in the library, or its format has no parser. */
    class NotInLibrary(
        val bookIdentifier: BookIdentifier,
    ) : LibraryBookError("No readable library file for $bookIdentifier")
}

/**
 * Opens an imported book: from [parsedBookCache] when possible, otherwise by parsing its copy
 * `<libraryDirectory>/<hex>.<extension>` and caching the result (RNF-04). A failed cache write
 * only costs a new parse next time, so it does not fail the load.
 *
 * @param parsersByExtension the same parsers the importer uses, by lowercase extension.
 * @param ioDispatcher where the library directory is listed; never the main thread.
 */
class LibraryBookLoader(
    private val libraryDirectory: File,
    private val parsersByExtension: Map<String, BookParser>,
    private val parsedBookCache: ParsedBookCache,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * @return the book, or a failure: [LibraryBookError.NotInLibrary], a [BookParseError], or an
     *   [IOException] while reading.
     */
    suspend fun load(bookIdentifier: BookIdentifier): Result<LoadedBook> {
        val file =
            withContext(ioDispatcher) { libraryFileOf(bookIdentifier) }
                ?: return Result.failure(LibraryBookError.NotInLibrary(bookIdentifier))
        val cached = parsedBookCache.read(bookIdentifier).getOrNull()
        val book =
            if (cached !=
                null
            ) {
                Result.success(cached)
            } else {
                parsedAndCached(file, bookIdentifier)
            }
        return book.map { loaded -> LoadedBook(loaded, file.name) }
    }

    private suspend fun parsedAndCached(
        file: File,
        bookIdentifier: BookIdentifier,
    ): Result<Book> {
        val parser =
            parsersByExtension[file.extension.lowercase()]
                ?: return Result.failure(LibraryBookError.NotInLibrary(bookIdentifier))
        return parsed(parser, file, bookIdentifier).onSuccess { book ->
            parsedBookCache.write(book)
        }
    }

    private fun libraryFileOf(bookIdentifier: BookIdentifier): File? =
        libraryDirectory
            .listFiles { file -> file.isFile && file.nameWithoutExtension == bookIdentifier.hex }
            ?.firstOrNull()

    private suspend fun parsed(
        parser: BookParser,
        file: File,
        bookIdentifier: BookIdentifier,
    ): Result<Book> =
        withContext(ioDispatcher) {
            try {
                file.inputStream().use { input -> parser.parse(input, bookIdentifier, file.name) }
            } catch (readFailure: IOException) {
                Result.failure(readFailure)
            }
        }
}
