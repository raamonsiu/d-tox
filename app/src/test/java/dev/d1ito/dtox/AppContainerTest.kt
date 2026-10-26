package dev.d1ito.dtox

import dev.d1ito.dtox.ui.reader.SampleBookLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.Executors

private const val IO_THREAD_NAME = "test-io"
private const val SAMPLE_RESOURCE = "/sample_quijote_chapter_one.md"

class AppContainerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val ioExecutor =
        Executors.newSingleThreadExecutor { task -> Thread(task, IO_THREAD_NAME) }
    private val ioDispatcher = ioExecutor.asCoroutineDispatcher()
    private val readingThreadNames = mutableListOf<String>()
    private val cacheDirectory: File by lazy { temporaryFolder.newFolder("cache") }

    private val container by lazy {
        DefaultAppContainer(
            filesDirectory = temporaryFolder.newFolder("files"),
            cacheDirectory = cacheDirectory,
            dispatchers =
                AppDispatchers(
                    io = ioDispatcher,
                    computation = Dispatchers.Unconfined,
                    main = Dispatchers.Unconfined,
                ),
        )
    }

    @After
    fun closeDispatcher() = ioDispatcher.close()

    @Test
    fun dependencies_areCreatedOnce_andShared() {
        assertSame(container.textBookParser, container.textBookParser)
        assertSame(container.bookIdentifierCalculator, container.bookIdentifierCalculator)
        assertSame(container.libraryIndex, container.libraryIndex)
        assertSame(container.bookImporter, container.bookImporter)
        assertSame(container.parsedBookCache, container.parsedBookCache)
        assertSame(container.libraryBookLoader, container.libraryBookLoader)
        assertSame(container.readingSettingsStore, container.readingSettingsStore)
    }

    @Test
    fun hashingAndTextParsing_readOnlyOnTheInjectedIoDispatcher() {
        val loader =
            SampleBookLoader(
                openSample = { ThreadRecordingStream(sampleBytes(), readingThreadNames) },
                identifierCalculator = container.bookIdentifierCalculator,
                parser = container.textBookParser,
            )

        val book = runBlocking { loader.load() }.getOrThrow()

        assertEquals("Capítulo primero", book.metadata.title)
        assertTrue("No reads recorded", readingThreadNames.isNotEmpty())
        // Coroutine debug mode appends " @coroutine#N" to the thread name.
        assertTrue(
            "Read on $readingThreadNames",
            readingThreadNames.all { name -> name.startsWith(IO_THREAD_NAME) },
        )
    }

    @Test
    fun anImportedBook_opensFromTheLibrary_andIsCachedForTheNextTime() {
        val imported =
            runBlocking {
                container.bookImporter.import(
                    ByteArrayInputStream(sampleBytes()),
                    "quijote.md",
                    null,
                )
            }.getOrThrow()

        val opened =
            runBlocking {
                container.libraryBookLoader.load(
                    imported.book.identifier,
                )
            }.getOrThrow()

        assertEquals(imported.book, opened.book)
        assertEquals(imported.file.name, opened.fileName)
        assertTrue(File(cacheDirectory, "parsed/${imported.book.identifier.hex}.json").isFile)
    }

    private fun sampleBytes(): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(SAMPLE_RESOURCE)) { "Missing $SAMPLE_RESOURCE" }
            .use { stream -> stream.readBytes() }

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
