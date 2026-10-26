package dev.d1ito.dtox.ui.reader

import androidx.lifecycle.SavedStateHandle
import dev.d1ito.dtox.data.importing.LoadedBook
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException

private const val IDENTIFIER = "sha256:0123456789abcdef0123456789abcdef"

class ReaderViewModelTest {
    private val book = bookWithChapters(count = 2)

    @Test
    fun state_isLoading_untilTheBookArrives() {
        val pendingBook = CompletableDeferred<Result<Book>>()
        val viewModel = readerViewModel(SavedStateHandle()) { pendingBook.await() }

        assertSame(ReaderUiState.Loading, viewModel.state.value)

        pendingBook.complete(Result.success(book))
        assertEquals(ReaderUiState.Reading(book, anchor(0, 0, 0)), viewModel.state.value)
    }

    @Test
    fun state_isUnavailable_whenLoadingFails() {
        val viewModel = readerViewModel(SavedStateHandle()) { Result.failure(IOException()) }

        assertSame(ReaderUiState.Unavailable, viewModel.state.value)
    }

    @Test
    fun state_isUnavailable_forABookWithoutChapters() {
        val viewModel =
            readerViewModel(SavedStateHandle()) { Result.success(bookWithChapters(count = 0)) }

        assertSame(ReaderUiState.Unavailable, viewModel.state.value)
    }

    @Test
    fun settledPage_becomesTheCurrentPositionAndChapter() {
        val viewModel = readerViewModel(SavedStateHandle()) { Result.success(book) }

        viewModel.onPageSettled(anchor(1, 2, 30))

        val reading = viewModel.state.value as ReaderUiState.Reading
        assertEquals(anchor(1, 2, 30), reading.position)
        assertSame(book.chapters[1], reading.chapter)
    }

    @Test
    fun position_survivesANewViewModelWithTheSameSavedState() {
        val savedState = SavedStateHandle()
        readerViewModel(savedState) { Result.success(book) }.onPageSettled(anchor(1, 2, 30))

        // Same saved state, new instance: what Android does after rotation or process death.
        val restored = readerViewModel(savedState) { Result.success(book) }

        assertEquals(ReaderUiState.Reading(book, anchor(1, 2, 30)), restored.state.value)
    }

    @Test
    fun savedPosition_inAChapterTheBookDoesNotHave_opensAtTheStart() {
        val savedState =
            SavedStateHandle(
                mapOf(
                    "reader.book" to IDENTIFIER,
                    "reader.chapter" to 9,
                    "reader.block" to 0,
                    "reader.offset" to 0,
                ),
            )

        val viewModel = readerViewModel(savedState) { Result.success(book) }

        assertEquals(ReaderUiState.Reading(book, anchor(0, 0, 0)), viewModel.state.value)
    }

    @Test
    fun settledPage_outsideTheBook_isIgnored() {
        val savedState = SavedStateHandle()
        val viewModel = readerViewModel(savedState) { Result.success(book) }

        viewModel.onPageSettled(anchor(5, 0, 0))

        assertEquals(ReaderUiState.Reading(book, anchor(0, 0, 0)), viewModel.state.value)
        assertEquals(null, savedState.get<Int>("reader.chapter"))
    }

    @Test
    fun settledPage_whileLoading_isIgnored() {
        val savedState = SavedStateHandle()
        val pendingBook = CompletableDeferred<Result<Book>>()
        val viewModel = readerViewModel(savedState) { pendingBook.await() }

        viewModel.onPageSettled(anchor(1, 0, 0))

        assertSame(ReaderUiState.Loading, viewModel.state.value)
        assertEquals(null, savedState.get<Int>("reader.chapter"))
    }

    @Test
    fun storedPosition_isRestoredWhenThereIsNoSavedState() {
        val store = InMemoryBookDataStore()
        readerViewModel(SavedStateHandle(), store) { Result.success(book) }.apply {
            onPageSettled(anchor(1, 0, 3))
            flushProgress()
        }

        // New process: no saved state, only the data file.
        val reopened = readerViewModel(SavedStateHandle(), store) { Result.success(book) }

        val restored = (reopened.state.value as ReaderUiState.Reading).position
        assertEquals(Triple(1, 0, 3), Triple(restored.chapter, restored.block, restored.offset))
        assertEquals("to 1", restored.quote)
    }

    @Test
    fun savedState_winsOverTheStoredPosition() {
        val store = InMemoryBookDataStore()
        val savedState = SavedStateHandle()
        readerViewModel(savedState, store) { Result.success(book) }.apply {
            onPageSettled(anchor(1, 0, 3))
            flushProgress()
            onPageSettled(anchor(0, 0, 2))
        }

        val restored = readerViewModel(savedState, store) { Result.success(book) }

        assertEquals(ReaderUiState.Reading(book, anchor(0, 0, 2)), restored.state.value)
    }

    @Test
    fun nextChapter_opensTheStartOfTheFollowingChapter() {
        val viewModel = readerViewModel(SavedStateHandle()) { Result.success(book) }

        viewModel.onNextChapter()

        assertEquals(anchor(1, 0, 0), (viewModel.state.value as ReaderUiState.Reading).position)
    }

    @Test
    fun previousChapter_opensTheEndOfThePreviousChapter() {
        val viewModel = readerViewModel(SavedStateHandle()) { Result.success(book) }
        viewModel.onPageSettled(anchor(1, 0, 0))

        viewModel.onPreviousChapter()

        // Right after the last character of "Texto 0": the paginator shows its last page.
        val position = (viewModel.state.value as ReaderUiState.Reading).position
        assertEquals(anchor(0, 0, "Texto 0".length), position)
    }

    @Test
    fun chapterTurns_pastTheBookEdges_areIgnored() {
        val viewModel = readerViewModel(SavedStateHandle()) { Result.success(book) }

        viewModel.onPreviousChapter()
        assertEquals(anchor(0, 0, 0), (viewModel.state.value as ReaderUiState.Reading).position)

        viewModel.onNextChapter()
        viewModel.onNextChapter()
        assertEquals(anchor(1, 0, 0), (viewModel.state.value as ReaderUiState.Reading).position)
    }

    @Test
    fun chapterTurn_isSavedLikeAPageTurn() {
        val store = InMemoryBookDataStore()
        val savedState = SavedStateHandle()
        val viewModel = readerViewModel(savedState, store) { Result.success(book) }

        viewModel.onNextChapter()
        viewModel.flushProgress()

        assertEquals(1, savedState.get<Int>("reader.chapter"))
        assertEquals(
            1,
            store.writes
                .single()
                .progress
                ?.anchor
                ?.chapter,
        )
    }

    /**
     * A reader with [IDENTIFIER] open, as after tapping it in the library, unless [savedState]
     * already names an open book (then the ViewModel reopens it by itself). Unconfined: loading and
     * state updates run right away on the test thread.
     */
    private fun readerViewModel(
        savedState: SavedStateHandle,
        store: InMemoryBookDataStore = InMemoryBookDataStore(),
        loadBook: suspend () -> Result<Book>,
    ): ReaderViewModel {
        val viewModel =
            testReaderViewModel(savedState, store) {
                loadBook().map { book ->
                    LoadedBook(book, "libro.md")
                }
            }
        if (savedState.get<String>("reader.book") == null) {
            viewModel.open(BookIdentifier.parse(IDENTIFIER).getOrThrow())
        }
        return viewModel
    }

    private fun anchor(
        chapter: Int,
        block: Int,
        offset: Int,
    ): Anchor = Anchor.create(chapter, block, offset).getOrThrow()

    private fun bookWithChapters(count: Int): Book =
        Book
            .create(
                BookIdentifier.parse(IDENTIFIER).getOrThrow(),
                BookMetadata(title = "Libro", author = null, language = null),
                List(count) { index ->
                    Chapter
                        .create(
                            index,
                            null,
                            listOf(Block.Paragraph("Texto $index")),
                        ).getOrThrow()
                },
            ).getOrThrow()
}
