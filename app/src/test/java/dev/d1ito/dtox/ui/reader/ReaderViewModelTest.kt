package dev.d1ito.dtox.ui.reader

import androidx.lifecycle.SavedStateHandle
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
                mapOf("reader.chapter" to 9, "reader.block" to 0, "reader.offset" to 0),
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

    /** Unconfined: loading and state updates run right away on the test thread. */
    private fun readerViewModel(
        savedState: SavedStateHandle,
        loadBook: suspend () -> Result<Book>,
    ) = ReaderViewModel(savedState, loadBook, CoroutineScope(Dispatchers.Unconfined))

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
