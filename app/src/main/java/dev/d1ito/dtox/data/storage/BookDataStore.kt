package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
}

/**
 * Stores each book as `<booksDirectory>/<bookId>.json` (docs/02-data-formats.md §4), written
 * atomically. A corrupt file is renamed to `<bookId>.json.<epochMillis>.corrupt`, keeping it for
 * inspection without blocking the book.
 *
 * @param booksDirectory e.g. `files/books`; created on the first write.
 * @param clock timestamps the names of set-aside corrupt files.
 * @param ioDispatcher where file I/O runs; never the main thread.
 */
class FileBookDataStore(
    private val booksDirectory: File,
    private val fileWriter: AtomicFileWriter,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.systemUTC(),
) : BookDataStore {
    override suspend fun read(bookIdentifier: BookIdentifier): Result<BookData?> =
        withContext(ioDispatcher) {
            val dataFile = dataFileOf(bookIdentifier)
            try {
                if (dataFile.isFile) decodedOrSetAside(dataFile) else Result.success(null)
            } catch (readFailure: IOException) {
                Result.failure(readFailure)
            }
        }

    override suspend fun write(bookData: BookData): Result<Unit> =
        fileWriter.write(
            dataFileOf(bookData.bookIdentifier),
            BookDataFormat.encode(bookData).toByteArray(Charsets.UTF_8),
        )

    private fun dataFileOf(bookIdentifier: BookIdentifier): File =
        File(booksDirectory, bookIdentifier.value + DATA_EXTENSION)

    /** @throws IOException if the file cannot be read or renamed. */
    private fun decodedOrSetAside(dataFile: File): Result<BookData?> {
        val decoded = BookDataFormat.decode(dataFile.readText(Charsets.UTF_8))
        return if (decoded.exceptionOrNull() is BookDataFormatError.Corrupt) {
            setAside(dataFile)
            Result.success(null)
        } else {
            decoded
        }
    }

    /** @throws IOException if the rename fails; the corrupt file then stays where it was. */
    private fun setAside(dataFile: File) {
        val corruptFile =
            File(dataFile.parentFile, "${dataFile.name}.${clock.millis()}$CORRUPT_EXTENSION")
        Files.move(dataFile.toPath(), corruptFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}
