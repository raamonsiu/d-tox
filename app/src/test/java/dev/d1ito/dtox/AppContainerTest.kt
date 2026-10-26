package dev.d1ito.dtox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.concurrent.Executors

private const val IO_THREAD_NAME = "test-io"

private val SAMPLE_MARKDOWN =
    """
    # Capítulo primero

    En un lugar de la Mancha.
    """.trimIndent()

class AppContainerTest {
    private val ioExecutor =
        Executors.newSingleThreadExecutor { task -> Thread(task, IO_THREAD_NAME) }
    private val ioDispatcher = ioExecutor.asCoroutineDispatcher()
    private val readingThreadNames = mutableListOf<String>()
    private var timesTheSampleWasOpened = 0

    private val container =
        DefaultAppContainer(
            openSampleBook = {
                timesTheSampleWasOpened++
                ThreadRecordingStream(SAMPLE_MARKDOWN.toByteArray(), readingThreadNames)
            },
            booksDirectory = Files.createTempDirectory("books").toFile(),
            dispatchers =
                AppDispatchers(
                    io = ioDispatcher,
                    computation = Dispatchers.Unconfined,
                    main = Dispatchers.Unconfined,
                ),
        )

    @After
    fun closeDispatcher() = ioDispatcher.close()

    @Test
    fun building_theContainer_opensNothingUntilFirstUse() {
        assertEquals(0, timesTheSampleWasOpened)
    }

    @Test
    fun dependencies_areCreatedOnce_andShared() {
        assertSame(container.textBookParser, container.textBookParser)
        assertSame(container.bookIdentifierCalculator, container.bookIdentifierCalculator)
        assertSame(container.sampleBookLoader, container.sampleBookLoader)
    }

    @Test
    fun sampleBookLoader_hashesAndParsesTheSample() {
        val book = runBlocking { container.sampleBookLoader.load() }.getOrThrow()

        assertEquals("Capítulo primero", book.metadata.title)
        assertEquals(1, book.chapters.size)
        assertTrue(book.identifier.value.startsWith("sha256:"))
        assertEquals("Opened once to hash and once to parse", 2, timesTheSampleWasOpened)
    }

    @Test
    fun sampleBookLoader_readsOnlyOnTheInjectedIoDispatcher() {
        runBlocking { container.sampleBookLoader.load() }.getOrThrow()

        assertTrue("No reads recorded", readingThreadNames.isNotEmpty())
        // Coroutine debug mode appends " @coroutine#N" to the thread name.
        assertTrue(
            "Read on $readingThreadNames",
            readingThreadNames.all { name -> name.startsWith(IO_THREAD_NAME) },
        )
    }

    /** Records the name of every thread that reads from it. */
    private class ThreadRecordingStream(
        bytes: ByteArray,
        private val readingThreadNames: MutableList<String>,
    ) : ByteArrayInputStream(bytes) {
        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            synchronized(readingThreadNames) { readingThreadNames += Thread.currentThread().name }
            return super.read(buffer, offset, length)
        }

        override fun read(): Int {
            synchronized(readingThreadNames) { readingThreadNames += Thread.currentThread().name }
            return super.read()
        }
    }
}
