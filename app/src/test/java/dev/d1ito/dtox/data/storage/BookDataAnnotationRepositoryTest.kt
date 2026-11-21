package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

private const val IDENTIFIER = "sha256:0123456789abcdef0123456789abcdef"
private const val CONCURRENT_CHANGES = 20
private val CREATED_AT: Instant = Instant.parse("2026-10-11T07:50:00Z")
private val LATER: Instant = Instant.parse("2026-10-11T08:00:00Z")

class BookDataAnnotationRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val booksDirectory by lazy { File(temporaryFolder.root, "books") }
    private val store by lazy {
        FileBookDataStore(
            booksDirectory = booksDirectory,
            fileWriter = SyncedAtomicFileWriter(Dispatchers.IO),
            ioDispatcher = Dispatchers.IO,
        )
    }
    private val repository by lazy { BookDataAnnotationRepository(store) }
    private val bookIdentifier = BookIdentifier.parse(IDENTIFIER).getOrThrow()
    private val dataFile by lazy { File(booksDirectory, "$IDENTIFIER.json") }
    private val details =
        NewBookDetails("Cartas a Lucilio", "Séneca", BookSource("cartas.epub", CREATED_AT))

    @Test
    fun add_toBookWithoutData_createsItsFileWithTheDetails() {
        val bookmark = bookmark(anchor(chapter = 1))

        runBlocking { repository.add(bookIdentifier, bookmark, details) }.getOrThrow()

        val stored = requireNotNull(runBlocking { store.read(bookIdentifier) }.getOrThrow())
        assertEquals(listOf(bookmark), stored.annotations)
        assertEquals(details.title, stored.title)
        assertEquals(details.source, stored.source)
    }

    @Test
    fun annotations_areInReadingOrderWhateverTheOrderTheyWereAdded() {
        val late = note(anchor(chapter = 2), "Más adelante")
        val early = highlight(anchor(chapter = 0, offset = 5), anchor(chapter = 0, offset = 9))
        val middle = bookmark(anchor(chapter = 1))

        listOf(late, early, middle).forEach(::add)

        assertEquals(listOf(early, middle, late), currentAnnotations())
    }

    @Test
    fun readingOrder_breaksTiesByEndThenCreationThenIdentifier() {
        val start = anchor(chapter = 0)
        val longHighlight = highlight(start, anchor(chapter = 0, offset = 30))
        val shortHighlight = highlight(start, anchor(chapter = 0, offset = 10))
        val laterBookmark = bookmark(start, createdAt = LATER)
        val firstBookmark = bookmark(start, identifier = "00000000-0000-4000-8000-000000000001")
        val secondBookmark = bookmark(start, identifier = "00000000-0000-4000-8000-000000000002")

        val ordered =
            listOf(longHighlight, laterBookmark, shortHighlight, secondBookmark, firstBookmark)
                .sortedWith(AnnotationReadingOrder)

        assertEquals(
            listOf(firstBookmark, secondBookmark, laterBookmark, shortHighlight, longHighlight),
            ordered,
        )
    }

    @Test
    fun edit_replacesTheAnnotationWithTheSameIdentifier() {
        val original = note(anchor(chapter = 0), "Primera idea")
        add(original)
        val edited = note(anchor(chapter = 0), "Idea revisada", original.metadata.identifier)

        runBlocking { repository.edit(bookIdentifier, edited) }.getOrThrow()

        assertEquals(listOf(edited), currentAnnotations())
    }

    @Test
    fun remove_dropsOnlyThatAnnotation() {
        val kept = bookmark(anchor(chapter = 0))
        val removed = bookmark(anchor(chapter = 1))
        listOf(kept, removed).forEach(::add)

        runBlocking { repository.remove(bookIdentifier, removed.metadata.identifier) }.getOrThrow()

        assertEquals(listOf(kept), currentAnnotations())
    }

    @Test
    fun missingAnnotation_cannotBeEditedOrRemoved() {
        val absent = bookmark(anchor(chapter = 0))

        val edit = runBlocking { repository.edit(bookIdentifier, absent) }
        val remove = runBlocking { repository.remove(bookIdentifier, absent.metadata.identifier) }

        assertTrue(edit.exceptionOrNull() is AnnotationRepositoryError.NotFound)
        assertTrue(remove.exceptionOrNull() is AnnotationRepositoryError.NotFound)
        assertFalse(dataFile.exists())
    }

    @Test
    fun duplicateIdentifier_isRejectedAndTheFileIsUntouched() {
        val bookmark = bookmark(anchor(chapter = 0))
        add(bookmark)
        val before = dataFile.readText()

        val result = runBlocking { repository.add(bookIdentifier, bookmark, details) }

        assertTrue(result.exceptionOrNull() is AnnotationRepositoryError.DuplicateIdentifier)
        assertEquals(before, dataFile.readText())
    }

    @Test
    fun changes_keepTheStoredProgress() {
        val progress = ReadingProgress(anchor(chapter = 3), 0.4, CREATED_AT, finished = false)
        val stored = BookData(bookIdentifier, "Cartas", null, details.source, progress)
        runBlocking { store.write(stored) }.getOrThrow()

        add(bookmark(anchor(chapter = 0)))

        assertEquals(progress, runBlocking { store.read(bookIdentifier) }.getOrThrow()?.progress)
    }

    @Test
    fun concurrentAnnotationAndProgressChanges_areAllKept() {
        runBlocking {
            val annotationChanges =
                (0 until CONCURRENT_CHANGES).map { index ->
                    async(Dispatchers.Default) {
                        repository.add(bookIdentifier, bookmark(anchor(chapter = index)), details)
                    }
                }
            val progressChanges =
                (0 until CONCURRENT_CHANGES).map { index ->
                    async(Dispatchers.Default) {
                        store.update(bookIdentifier) { stored ->
                            val current = stored ?: newData()
                            val progress =
                                ReadingProgress(anchor(index), 0.1, CREATED_AT, finished = false)
                            Result.success(current.copy(progress = progress))
                        }
                    }
                }
            (annotationChanges + progressChanges).awaitAll().forEach { result ->
                result.getOrThrow()
            }
        }

        val stored = requireNotNull(runBlocking { store.read(bookIdentifier) }.getOrThrow())
        assertEquals(CONCURRENT_CHANGES, stored.annotations.size)
        assertTrue(stored.progress != null)
    }

    @Test
    fun annotationsFlow_emitsAgainAfterEachChange() {
        val bookmark = bookmark(anchor(chapter = 0))
        val emissions =
            runBlocking {
                val collected =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        repository.annotations(bookIdentifier).take(2).toList()
                    }
                launch { repository.add(bookIdentifier, bookmark, details).getOrThrow() }
                collected.await()
            }

        assertEquals(
            listOf(emptyList(), listOf(bookmark)),
            emissions.map { emission -> emission.getOrThrow() },
        )
    }

    @Test
    fun fileFromNewerVersion_isReportedAndNeverOverwritten() {
        booksDirectory.mkdirs()
        val newer = """{ "version": 2, "bookId": "$IDENTIFIER" }"""
        dataFile.writeText(newer)

        val listed = runBlocking { repository.annotations(bookIdentifier).first() }
        val added = runBlocking { repository.add(bookIdentifier, bookmark(anchor(0)), details) }

        assertTrue(listed.exceptionOrNull() is BookDataFormatError.UnsupportedVersion)
        assertTrue(added.exceptionOrNull() is BookDataFormatError.UnsupportedVersion)
        assertEquals(newer, dataFile.readText())
    }

    private fun add(annotation: BookAnnotation) =
        runBlocking { repository.add(bookIdentifier, annotation, details) }.getOrThrow()

    private fun currentAnnotations(): List<BookAnnotation> =
        runBlocking { repository.annotations(bookIdentifier).first() }.getOrThrow()

    private fun newData(): BookData =
        BookData(bookIdentifier, details.title, details.author, details.source, progress = null)

    private fun anchor(
        chapter: Int,
        offset: Int = 0,
    ): Anchor = Anchor.create(chapter, 0, offset).getOrThrow()

    private fun metadata(
        identifier: AnnotationIdentifier = AnnotationIdentifier.random(),
        createdAt: Instant = CREATED_AT,
    ) = AnnotationMetadata(identifier, createdAt, createdAt)

    private fun bookmark(
        start: Anchor,
        identifier: String? = null,
        createdAt: Instant = CREATED_AT,
    ): BookAnnotation.Bookmark {
        val parsed = identifier?.let { text -> AnnotationIdentifier.parse(text).getOrThrow() }
        return BookAnnotation.Bookmark(
            metadata(parsed ?: AnnotationIdentifier.random(), createdAt),
            start,
        )
    }

    private fun note(
        start: Anchor,
        text: String,
        identifier: AnnotationIdentifier = AnnotationIdentifier.random(),
    ): BookAnnotation.Note =
        BookAnnotation.Note.create(metadata(identifier), start, text).getOrThrow()

    private fun highlight(
        start: Anchor,
        end: Anchor,
    ): BookAnnotation.Highlight =
        BookAnnotation.Highlight
            .create(metadata(), AnchorRange.create(start, end).getOrThrow(), "texto")
            .getOrThrow()
}
