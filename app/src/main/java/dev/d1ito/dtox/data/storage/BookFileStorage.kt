package dev.d1ito.dtox.data.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A file of a [BookFileStorage].
 *
 * @property name e.g. `sha256:….json`.
 * @property lastModifiedMillis when it last changed, or `null` when the storage cannot tell.
 */
data class StoredBookFile(
    val name: String,
    val lastModifiedMillis: Long?,
)

/**
 * The flat folder that holds the per-book data files (docs/02-data-formats.md §4), wherever it
 * lives: the app's private storage or a folder the user chose (RF-27). Failures are
 * [IOException]s returned inside [Result], never thrown.
 */
interface BookFileStorage {
    /** @return the content of the file called [name], or `null` when there is no such file. */
    suspend fun read(name: String): Result<ByteArray?>

    /**
     * Creates or replaces the file called [name] so that a reader sees either the old or the new
     * content, as far as the storage allows (see each implementation).
     */
    suspend fun write(
        name: String,
        content: ByteArray,
    ): Result<Unit>

    /** @return every file, in no particular order; empty when the folder does not exist yet. */
    suspend fun list(): Result<List<StoredBookFile>>

    /** Renames the file [name] to [newName], which must not exist yet. */
    suspend fun rename(
        name: String,
        newName: String,
    ): Result<Unit>
}

/**
 * [BookFileStorage] in a directory of the app's own storage, e.g. `files/books`. Writes go
 * through [fileWriter] (temporary file, `fsync`, atomic rename); renames are atomic moves.
 *
 * @param directory created on the first write.
 * @param ioDispatcher where file I/O runs; never the main thread.
 */
class DirectoryBookFileStorage(
    private val directory: File,
    private val fileWriter: AtomicFileWriter,
    private val ioDispatcher: CoroutineDispatcher,
) : BookFileStorage {
    override suspend fun read(name: String): Result<ByteArray?> =
        fileCall { File(directory, name).takeIf { file -> file.isFile }?.readBytes() }

    override suspend fun write(
        name: String,
        content: ByteArray,
    ): Result<Unit> = fileWriter.write(File(directory, name), content)

    override suspend fun list(): Result<List<StoredBookFile>> =
        fileCall {
            directory
                .listFiles { file -> file.isFile }
                .orEmpty()
                .map { file -> StoredBookFile(file.name, file.lastModified()) }
        }

    override suspend fun rename(
        name: String,
        newName: String,
    ): Result<Unit> =
        fileCall {
            Files.move(
                File(directory, name).toPath(),
                File(directory, newName).toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
            Unit
        }

    /** Runs [call] on [ioDispatcher], returning its [IOException] as a failure. */
    private suspend fun <T> fileCall(call: () -> T): Result<T> =
        withContext(ioDispatcher) {
            try {
                Result.success(call())
            } catch (fileFailure: IOException) {
                Result.failure(fileFailure)
            }
        }
}
