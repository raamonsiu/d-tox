package dev.d1ito.dtox.data.export

import dev.d1ito.dtox.data.storage.BookData
import dev.d1ito.dtox.data.storage.BookDataFormat
import dev.d1ito.dtox.data.storage.BookSource
import dev.d1ito.dtox.data.storage.LibraryEntry
import dev.d1ito.dtox.data.storage.LibraryIndex
import dev.d1ito.dtox.data.storage.ReadingProgress
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.ui.reader.InMemoryBookDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Sample books, annotations and zips for the import tests. */
internal object ImportFixtures {
    const val HEX_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    const val HEX_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    const val BOOK_A = "sha256:$HEX_A"
    const val BOOK_B = "sha256:$HEX_B"
    const val MANIFEST =
        """{"format":"dtox-export","version":1,"appVersion":"0.42.0",""" +
            """"exportedAt":"2026-11-21T10:00:00Z","includesBooks":true}"""
    val EARLIER: Instant = Instant.parse("2026-10-11T07:00:00Z")
    val LATER: Instant = Instant.parse("2026-10-12T07:00:00Z")

    fun identifier(text: String) = BookIdentifier.parse(text).getOrThrow()

    fun note(
        identifier: String,
        text: String,
    ): BookAnnotation =
        BookAnnotation.Note
            .create(
                AnnotationMetadata(
                    AnnotationIdentifier.parse(identifier).getOrThrow(),
                    EARLIER,
                    EARLIER,
                ),
                Anchor.create(1, 0, 0).getOrThrow(),
                text,
            ).getOrThrow()

    fun bookData(
        bookIdentifier: String,
        progressUpdatedAt: Instant? = null,
        annotations: List<BookAnnotation> = emptyList(),
    ) = BookData(
        bookIdentifier = identifier(bookIdentifier),
        title = "Meditaciones",
        author = "Marco Aurelio",
        source = BookSource("meditaciones.epub", EARLIER),
        progress =
            progressUpdatedAt?.let { updatedAt ->
                ReadingProgress(
                    Anchor.create(2, 0, 0).getOrThrow(),
                    0.4,
                    updatedAt,
                    finished = false,
                )
            },
        annotations = annotations,
    )

    fun bookEntry(bookData: BookData): Pair<String, String> =
        "books/${bookData.bookIdentifier.value}.json" to BookDataFormat.encode(bookData)

    fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}

/**
 * A [UserDataImporter] over an [InMemoryBookDataStore] and a [RecordingLibraryIndex] that records
 * the book files it is given.
 */
internal class ImportHarness(
    private val limits: UserDataImportLimits = UserDataImportLimits(),
) {
    val store = InMemoryBookDataStore()
    val libraryIndex = RecordingLibraryIndex()
    val importedBookFiles = mutableListOf<Pair<String, String>>()

    fun import(zip: ByteArray): Result<Unit> {
        val importer =
            UserDataImporter(
                bookDataStore = store,
                libraryIndex = libraryIndex,
                importLibraryBook = { content, fileName ->
                    importedBookFiles += fileName to content.readBytes().toString(Charsets.UTF_8)
                    Result.success(Unit)
                },
                ioDispatcher = Dispatchers.Unconfined,
                limits = limits,
            )
        return runBlocking { importer.import { ByteArrayInputStream(zip) } }
    }
}

/** [LibraryIndex] that lists the given books and counts rebuilds. */
internal class RecordingLibraryIndex : LibraryIndex {
    override val entries = MutableStateFlow<List<LibraryEntry>>(emptyList())
    var rebuilds = 0

    fun listed(bookIdentifier: BookIdentifier) {
        entries.value +=
            LibraryEntry(bookIdentifier, "Libro", null, "${bookIdentifier.hex}.epub", null, false)
    }

    override suspend fun load(): Result<Unit> = Result.success(Unit)

    override suspend fun add(entry: LibraryEntry): Result<Unit> = Result.success(Unit)

    override suspend fun remove(bookIdentifier: BookIdentifier): Result<Unit> = Result.success(Unit)

    override suspend fun markOpened(bookIdentifier: BookIdentifier): Result<Unit> =
        Result.success(Unit)

    override suspend fun rebuild(): Result<Unit> {
        rebuilds++
        return Result.success(Unit)
    }
}
