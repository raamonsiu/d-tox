package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

private const val IO_THREAD_NAME = "test-epub-io"

/** Failures, cleanup, threading and the malformed corpus EPUBs. */
class EpubBookParserRobustnessTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val workDirectory: File by lazy { temporaryFolder.newFolder("work") }
    private val parser by lazy { EpubBookParser(Dispatchers.Unconfined, workDirectory) }

    @Test
    fun packageErrors_arePropagated() {
        val withoutContainer = temporaryFolder.epubFile(container = null, "a.xhtml" to TEST_CHAPTER)
        val notAZip = temporaryFolder.newFile().apply { writeText("not a zip") }

        assertTrue(
            parser
                .parseFile(
                    withoutContainer,
                ).exceptionOrNull() is EpubPackageError.MissingContainer,
        )
        assertTrue(parser.parseFile(notAZip).exceptionOrNull() is EpubPackageError.NotAnArchive)
    }

    @Test
    fun temporaryCopy_isDeleted_afterSuccessAndAfterFailure() {
        parser
            .parseFile(
                epubWith(documents = listOf("a.xhtml" to chapterDocument("<p>Texto.</p>"))),
            ).getOrThrow()
        parser.parseFile(temporaryFolder.newFile().apply { writeText("not a zip") })

        assertEquals(emptyList<String>(), workDirectory.list()?.toList())
    }

    @Test
    fun readFailure_isReturnedAsFailure_andTheCopyIsDeleted() {
        val failingSource =
            object : InputStream() {
                override fun read(): Int = throw java.io.IOException("disk gone")
            }

        val result = runBlocking { parser.parse(failingSource, testBookIdentifier(), "roto.epub") }

        assertTrue("Got $result", result.exceptionOrNull() is java.io.IOException)
        assertEquals(emptyList<String>(), workDirectory.list()?.toList())
    }

    @Test
    fun reading_runsOnTheInjectedDispatcher() {
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, IO_THREAD_NAME) }
        try {
            var readingThread: String? = null
            val bytes =
                epubWith(
                    documents = listOf("a.xhtml" to chapterDocument("<p>T.</p>")),
                ).readBytes()
            val recordingSource =
                object : ByteArrayInputStream(bytes) {
                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        readingThread = Thread.currentThread().name
                        return super.read(buffer, offset, length)
                    }
                }
            val dispatchedParser = EpubBookParser(executor.asCoroutineDispatcher(), workDirectory)

            runBlocking {
                dispatchedParser.parse(
                    recordingSource,
                    testBookIdentifier(),
                    "a.epub",
                )
            }.getOrThrow()

            // Coroutine debug mode appends " @coroutine#N" to the thread name.
            assertTrue("Read on $readingThread", readingThread?.startsWith(IO_THREAD_NAME) == true)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun corpusEpubWithBrokenXhtml_isReadLeniently() {
        val book = parseCorpus("malformed-broken-xhtml.epub").getOrThrow()

        assertTrue(book.chapters.isNotEmpty())
        assertTrue(book.chapters.all { chapter -> chapter.blocks.isNotEmpty() })
    }

    @Test
    fun corpusEpubWithMissingSpineItem_keepsTheReadableChapters() {
        val book = parseCorpus("malformed-missing-spine-item.epub").getOrThrow()

        assertTrue(book.chapters.isNotEmpty())
    }

    @Test
    fun corpusEpubWithoutContainer_failsWithMissingContainer() {
        assertTrue(
            parseCorpus("malformed-missing-container.epub").exceptionOrNull() is
                EpubPackageError.MissingContainer,
        )
    }

    private fun epubWith(documents: List<Pair<String, String>>): File =
        temporaryFolder.epubWithDocuments(documents = documents)

    private fun parseCorpus(name: String): Result<Book> {
        val resource = "/corpus/files/epub/$name"
        val source = checkNotNull(javaClass.getResourceAsStream(resource)) { "Missing $resource" }
        return source.use { stream ->
            runBlocking { parser.parse(stream, testBookIdentifier(), name) }
        }
    }
}
