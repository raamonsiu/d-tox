package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val IDENTIFIER = "sha256:0123456789abcdef0123456789abcdef"
private const val FIXED_MILLIS = 1_760_000_000_000L

class FileBookDataStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val booksDirectory by lazy { File(temporaryFolder.root, "books") }
    private val store by lazy {
        FileBookDataStore(
            booksDirectory = booksDirectory,
            fileWriter = SyncedAtomicFileWriter(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            clock = Clock.fixed(Instant.ofEpochMilli(FIXED_MILLIS), ZoneOffset.UTC),
        )
    }
    private val identifier = BookIdentifier.parse(IDENTIFIER).getOrThrow()
    private val dataFile by lazy { File(booksDirectory, "$IDENTIFIER.json") }

    @Test
    fun missingFile_readsAsNoData() {
        assertNull(runBlocking { store.read(identifier) }.getOrThrow())
    }

    @Test
    fun writtenData_readsBackEqualAndLivesInBookIdFile() {
        val bookData = sampleBookData()

        runBlocking { store.write(bookData) }.getOrThrow()
        val readBack = runBlocking { store.read(identifier) }.getOrThrow()

        assertTrue(dataFile.isFile)
        assertEquals(
            bookData.copy(preservedFields = requireNotNull(readBack).preservedFields),
            readBack,
        )
    }

    @Test
    fun corruptFile_isSetAsideAndReadsAsNoData() {
        booksDirectory.mkdirs()
        dataFile.writeText("{ not json")

        val readBack = runBlocking { store.read(identifier) }.getOrThrow()

        assertNull(readBack)
        assertFalse(dataFile.exists())
        val setAside = File(booksDirectory, "$IDENTIFIER.json.$FIXED_MILLIS.corrupt")
        assertEquals("{ not json", setAside.readText())
    }

    @Test
    fun fileFromNewerVersion_failsAndIsLeftUntouched() {
        runBlocking { store.write(sampleBookData()) }.getOrThrow()
        val newer = dataFile.readText().replace("\"version\": 1", "\"version\": 2")
        dataFile.writeText(newer)

        val result = runBlocking { store.read(identifier) }

        assertTrue(result.exceptionOrNull() is BookDataFormatError.UnsupportedVersion)
        assertEquals(newer, dataFile.readText())
    }

    @Test
    fun write_replacesThePreviousVersion() {
        val first = sampleBookData()
        val finished = first.copy(progress = first.progress?.copy(finished = true))

        runBlocking { store.write(first) }.getOrThrow()
        runBlocking { store.write(finished) }.getOrThrow()

        assertEquals(true, runBlocking { store.read(identifier) }.getOrThrow()?.progress?.finished)
    }

    private fun sampleBookData(): BookData =
        BookData(
            bookIdentifier = identifier,
            title = "Cartas a Lucilio",
            author = "Séneca",
            source = BookSource("cartas.epub", Instant.parse("2026-10-10T18:20:00Z")),
            progress =
                ReadingProgress(
                    anchor = Anchor.create(4, 12, 87, "ser feliz").getOrThrow(),
                    percent = 0.31,
                    updatedAt = Instant.parse("2026-10-11T07:45:12Z"),
                    finished = false,
                ),
        )
}
