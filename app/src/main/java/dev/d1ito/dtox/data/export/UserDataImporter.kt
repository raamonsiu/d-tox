package dev.d1ito.dtox.data.export

import dev.d1ito.dtox.data.storage.BookData
import dev.d1ito.dtox.data.storage.BookDataStore
import dev.d1ito.dtox.data.storage.LibraryIndex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Imports a zip written by [UserDataExporter] (docs/02-data-formats.md §10). The whole zip is
 * validated first ([validateUserDataZip]); only then is anything written. Each book data file is
 * merged with [mergedBookData] and written atomically; book files missing from the library are
 * added through [importLibraryBook]; the library index is rebuilt at the end. Importing the same
 * zip again changes nothing, so an import stopped by a write failure can simply be repeated.
 *
 * @param importLibraryBook adds one book file to the library, deduplicated by its content (e.g.
 *   the app's book importer); gets the bytes, which it must not close, and the file name.
 * @param ioDispatcher where the zip is read; never the main thread.
 */
class UserDataImporter(
    private val bookDataStore: BookDataStore,
    private val libraryIndex: LibraryIndex,
    private val importLibraryBook: suspend (InputStream, String) -> Result<Unit>,
    private val ioDispatcher: CoroutineDispatcher,
    private val limits: UserDataImportLimits = UserDataImportLimits(),
) {
    /**
     * @param openSource opens the zip chosen by the user; called on [ioDispatcher], once to
     *   validate and once more to copy book files when there are any. It throws an [IOException]
     *   or a [SecurityException] when the zip cannot be opened.
     * @return success once everything is merged; a [UserDataImportError] when the zip is not
     *   importable (then nothing was written), or the failure that stopped the writing.
     */
    suspend fun import(openSource: () -> InputStream): Result<Unit> {
        val export =
            readingFailuresCaught {
                openSource().use { input ->
                    validateUserDataZip(input, limits)
                }
            }.getOrElse { failure -> return Result.failure(failure) }
        val written =
            writeBookData(export.bookData).andThen {
                importLibraryFiles(openSource, export.libraryFiles)
            }
        val indexed = libraryIndex.rebuild()
        return written.andThen { indexed }
    }

    /** Merges and writes each book's data, stopping at the first failure. */
    private suspend fun writeBookData(bookData: List<BookData>): Result<Unit> {
        bookData.forEach { imported ->
            bookDataStore
                .update(
                    imported.bookIdentifier,
                ) { local -> Result.success(mergedBookData(local, imported)) }
                .onFailure { failure -> return Result.failure(failure) }
        }
        return Result.success(Unit)
    }

    /** Adds the book files of [libraryFiles] the library does not list yet. */
    private suspend fun importLibraryFiles(
        openSource: () -> InputStream,
        libraryFiles: List<ExportedLibraryFile>,
    ): Result<Unit> {
        val listed =
            libraryIndex.entries.value
                .map { entry -> entry.bookIdentifier }
                .toSet()
        val pending =
            libraryFiles
                .filterNot { file -> file.bookIdentifier in listed }
                .associateBy(ExportedLibraryFile::entryName)
        if (pending.isEmpty()) return Result.success(Unit)
        return readingFailuresCaught {
            openSource().use { input ->
                ZipInputStream(input).use { zip -> importPending(zip, pending) }
            }
        }
    }

    /** Imports each entry of [zip] named in [pending], stopping at the first failure. */
    private suspend fun importPending(
        zip: ZipInputStream,
        pending: Map<String, ExportedLibraryFile>,
    ): Result<Unit> {
        generateSequence { zip.nextEntry }.forEach { entry ->
            val file = pending[entry.name] ?: return@forEach
            val content = BoundedInputStream(zip, limits.maximumBookFileBytes)
            importLibraryBook(content, file.fileName).onFailure { failure ->
                return Result.failure(failure)
            }
        }
        return Result.success(Unit)
    }

    /** Runs [read] on [ioDispatcher], turning I/O and access failures into results. */
    private suspend fun <Value> readingFailuresCaught(
        read: suspend () -> Result<Value>,
    ): Result<Value> =
        withContext(ioDispatcher) {
            try {
                read()
            } catch (readFailure: IOException) {
                Result.failure(readFailure)
            } catch (accessRevoked: SecurityException) {
                Result.failure(accessRevoked)
            }
        }
}

/** [next] when this succeeded, otherwise this failure. */
private inline fun Result<Unit>.andThen(next: () -> Result<Unit>): Result<Unit> =
    if (isSuccess) next() else this
