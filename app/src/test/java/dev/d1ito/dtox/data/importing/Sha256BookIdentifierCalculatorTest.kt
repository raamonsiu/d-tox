package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.random.Random

// First 32 hex characters of well-known SHA-256 test vectors.
private const val EMPTY_INPUT_IDENTIFIER = "sha256:e3b0c44298fc1c149afbf4c8996fb924"
private const val ABC_INPUT_IDENTIFIER = "sha256:ba7816bf8f01cfea414140de5dae2223"
private const val LARGE_INPUT_BYTES = 8 * 1024 * 1024
private const val RANDOM_SEED = 42
private const val IO_THREAD_NAME = "dtox-test-io"

class Sha256BookIdentifierCalculatorTest {
    private val calculator = Sha256BookIdentifierCalculator(Dispatchers.Unconfined)

    @Test
    fun emptyFile_matchesKnownVector() {
        assertEquals(EMPTY_INPUT_IDENTIFIER, identifierOf(ByteArray(0)).value)
    }

    @Test
    fun abc_matchesKnownVector() {
        assertEquals(ABC_INPUT_IDENTIFIER, identifierOf("abc".toByteArray()).value)
    }

    @Test
    fun sameBytes_giveSameIdentifierRegardlessOfReadChunking() {
        val bytes = Random(RANDOM_SEED).nextBytes(LARGE_INPUT_BYTES / 2)
        val wholeReads = identifierOf(bytes)
        val oneByteReads =
            calculate(
                OneByteAtATimeInputStream(ByteArrayInputStream(bytes)),
            ).getOrThrow()
        assertEquals(wholeReads, oneByteReads)
    }

    @Test
    fun largeFile_hashesTheWholeContent() {
        val bytes = Random(RANDOM_SEED).nextBytes(LARGE_INPUT_BYTES)
        val expected =
            BookIdentifier
                .fromSha256Digest(
                    MessageDigest.getInstance("SHA-256").digest(bytes),
                ).getOrThrow()
        assertEquals(expected, identifierOf(bytes))
    }

    @Test
    fun readFailure_isReturnedAsFailure() {
        val failingSource =
            object : InputStream() {
                override fun read(): Int = throw IOException("disk removed")
            }
        val error = calculate(failingSource).exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test
    fun revokedAccess_isReturnedAsFailure() {
        val revokedSource =
            object : InputStream() {
                override fun read(): Int = throw SecurityException("permission revoked")
            }
        assertTrue(calculate(revokedSource).exceptionOrNull() is SecurityException)
    }

    @Test
    fun cancellation_stopsReadingAfterTheCurrentChunk() {
        var readCount = 0
        lateinit var calculationJob: Job
        val cancellingSource =
            object : InputStream() {
                override fun read(): Int = 0

                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    readCount++
                    calculationJob.cancel()
                    return length
                }
            }
        runBlocking {
            calculationJob =
                launch(Dispatchers.Unconfined, start = CoroutineStart.LAZY) {
                    calculator.calculate(cancellingSource)
                }
            calculationJob.join()
        }
        assertTrue(calculationJob.isCancelled)
        assertEquals(1, readCount)
    }

    @Test
    fun reading_runsOnTheInjectedDispatcher() {
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, IO_THREAD_NAME) }
        try {
            var readingThreadName: String? = null
            val recordingSource =
                object : InputStream() {
                    override fun read(): Int {
                        readingThreadName = Thread.currentThread().name
                        return -1
                    }
                }
            val dispatchedCalculator =
                Sha256BookIdentifierCalculator(executor.asCoroutineDispatcher())
            runBlocking { dispatchedCalculator.calculate(recordingSource) }.getOrThrow()
            // Coroutine debug mode appends " @coroutine#N" to the thread name.
            assertTrue(
                "Read on $readingThreadName",
                readingThreadName?.startsWith(IO_THREAD_NAME) == true,
            )
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun source_isNotClosed() {
        val source = CloseTrackingInputStream(ByteArrayInputStream("abc".toByteArray()))
        calculate(source).getOrThrow()
        assertFalse(source.closed)
    }

    private fun identifierOf(bytes: ByteArray): BookIdentifier =
        calculate(ByteArrayInputStream(bytes)).getOrThrow()

    private fun calculate(source: InputStream): Result<BookIdentifier> =
        runBlocking {
            calculator.calculate(source)
        }

    /** Returns at most one byte per bulk read, like a slow or chunked source. */
    private class OneByteAtATimeInputStream(
        source: InputStream,
    ) : FilterInputStream(source) {
        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = super.read(buffer, offset, minOf(length, 1))
    }

    private class CloseTrackingInputStream(
        source: InputStream,
    ) : FilterInputStream(source) {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
