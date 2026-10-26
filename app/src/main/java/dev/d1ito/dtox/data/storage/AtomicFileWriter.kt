package dev.d1ito.dtox.data.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TEMPORARY_SUFFIX = ".tmp"

/** Replaces user data files so that readers see either the old or the new content, never a mix. */
interface AtomicFileWriter {
    /**
     * Writes a new version of [target]. Creates the parent directory when missing.
     *
     * @param target the file to create or replace.
     * @param writeContent writes the whole new content to the given stream; it must not close it.
     *   An [IOException] thrown here aborts the write.
     * @return success once the new content is durable and in place, or an [AtomicWriteError];
     *   on failure [target] keeps its previous content and no temporary file is left behind.
     */
    suspend fun write(
        target: File,
        writeContent: (OutputStream) -> Unit,
    ): Result<Unit>
}

/** Writes [content] as the new version of [target]; see [AtomicFileWriter.write]. */
suspend fun AtomicFileWriter.write(
    target: File,
    content: ByteArray,
): Result<Unit> = write(target) { output -> output.write(content) }

/** Why an atomic write failed. [target] is untouched in every case. */
sealed class AtomicWriteError(
    message: String,
    cause: Throwable?,
) : IOException(message, cause) {
    /** The parent directory does not exist and cannot be created. */
    class DirectoryUnavailable(
        val directory: File,
    ) : AtomicWriteError("Cannot create directory '$directory'", null)

    /** Creating, writing or syncing the temporary file failed. */
    class WriteFailed(
        val target: File,
        cause: IOException,
    ) : AtomicWriteError("Cannot write a new version of '$target'", cause)

    /** The finished temporary file could not atomically replace the target. */
    class ReplaceFailed(
        val target: File,
        cause: IOException,
    ) : AtomicWriteError("Cannot replace '$target'", cause)
}

/**
 * Writes to a uniquely named `*.tmp` file next to the target, `fsync`s it and renames it over the
 * target with an atomic move, so a crash at any point leaves either the old or the new file.
 * The directory entry itself is not synced: after a power loss the rename may be lost, which
 * still leaves the old, complete version.
 *
 * @param ioDispatcher where file I/O runs; never the main thread.
 */
class SyncedAtomicFileWriter(
    private val ioDispatcher: CoroutineDispatcher,
) : AtomicFileWriter {
    override suspend fun write(
        target: File,
        writeContent: (OutputStream) -> Unit,
    ): Result<Unit> =
        withContext(ioDispatcher) { writeAtomically(target.absoluteFile, writeContent) }

    private fun writeAtomically(
        target: File,
        writeContent: (OutputStream) -> Unit,
    ): Result<Unit> {
        val directory = target.parentFile ?: File(".").absoluteFile
        if (!directory.isDirectory && !directory.mkdirs()) {
            return Result.failure(AtomicWriteError.DirectoryUnavailable(directory))
        }
        val temporary =
            try {
                File.createTempFile("${target.name}.", TEMPORARY_SUFFIX, directory)
            } catch (creationFailure: IOException) {
                return Result.failure(AtomicWriteError.WriteFailed(target, creationFailure))
            }
        return try {
            writeSynced(temporary, target, writeContent)
            moveIntoPlace(temporary, target)
            Result.success(Unit)
        } catch (writeError: AtomicWriteError) {
            Result.failure(writeError)
        } finally {
            // Already moved on success; after a failure this removes the partial file.
            temporary.delete()
        }
    }

    /** @throws AtomicWriteError.WriteFailed if writing or syncing fails. */
    private fun writeSynced(
        temporary: File,
        target: File,
        writeContent: (OutputStream) -> Unit,
    ) {
        try {
            FileOutputStream(temporary).use { output ->
                writeContent(output)
                output.flush()
                output.fd.sync()
            }
        } catch (writeFailure: IOException) {
            throw AtomicWriteError.WriteFailed(target, writeFailure)
        }
    }

    /** @throws AtomicWriteError.ReplaceFailed if the atomic move fails. */
    private fun moveIntoPlace(
        temporary: File,
        target: File,
    ) {
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (moveFailure: IOException) {
            throw AtomicWriteError.ReplaceFailed(target, moveFailure)
        }
    }
}
