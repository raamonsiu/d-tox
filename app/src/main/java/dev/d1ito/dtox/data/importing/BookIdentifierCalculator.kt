package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Computes the [BookIdentifier] of a book from the bytes of its original file (RF-03). */
interface BookIdentifierCalculator {
    /**
     * Reads [source] to its end. Cancelling the calling coroutine stops the reading.
     *
     * @param source the original file bytes from its current position; the caller keeps
     *   ownership and must close it (it is not closed here).
     * @return the identifier, or a failure with the [IOException] raised while reading or the
     *   [SecurityException] raised when access to the file is revoked. Other exceptions propagate.
     */
    suspend fun calculate(source: InputStream): Result<BookIdentifier>
}

private const val READ_BUFFER_BYTES = 64 * 1024
private const val SHA_256_ALGORITHM = "SHA-256"
private const val END_OF_STREAM = -1

/**
 * SHA-256 of the whole file, read in fixed-size chunks so memory use does not grow with the book.
 *
 * @param ioDispatcher where the reading runs; never the main thread (inject a test one in tests).
 */
class Sha256BookIdentifierCalculator(
    private val ioDispatcher: CoroutineDispatcher,
) : BookIdentifierCalculator {
    override suspend fun calculate(source: InputStream): Result<BookIdentifier> =
        withContext(ioDispatcher) {
            try {
                BookIdentifier.fromSha256Digest(digestOf(source))
            } catch (readFailure: IOException) {
                Result.failure(readFailure)
            } catch (accessRevoked: SecurityException) {
                Result.failure(accessRevoked)
            }
        }

    /**
     * @param source stream read until its end, in [READ_BUFFER_BYTES] chunks.
     * @return the full 32-byte SHA-256 digest of the bytes read.
     * @throws IOException if [source] fails while reading.
     * @throws kotlinx.coroutines.CancellationException if the coroutine is cancelled mid-read.
     */
    private suspend fun digestOf(source: InputStream): ByteArray {
        val digest = MessageDigest.getInstance(SHA_256_ALGORITHM)
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var bytesRead = source.read(buffer)
        while (bytesRead != END_OF_STREAM) {
            currentCoroutineContext().ensureActive()
            digest.update(buffer, 0, bytesRead)
            bytesRead = source.read(buffer)
        }
        return digest.digest()
    }
}
