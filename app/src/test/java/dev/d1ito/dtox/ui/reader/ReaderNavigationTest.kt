package dev.d1ito.dtox.ui.reader

import androidx.lifecycle.SavedStateHandle
import dev.d1ito.dtox.data.importing.LoadedBook
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

private const val FIRST_HEX = "0123456789abcdef0123456789abcdef"
private const val SECOND_HEX = "fedcba9876543210fedcba9876543210"

/** Opening, closing and switching books, and reopening after process death. */
class ReaderNavigationTest {
    private val books =
        listOf(FIRST_HEX, SECOND_HEX).associate { hex ->
            identifier(hex) to
                book(hex)
        }
    private val loads = mutableListOf<BookIdentifier>()
    private val openedBooks = mutableListOf<BookIdentifier>()
    private val store = InMemoryBookDataStore()

    @Test
    fun withoutASavedBook_theReaderStartsClosed() {
        assertSame(ReaderUiState.Closed, viewModel(SavedStateHandle()).state.value)
    }

    @Test
    fun open_loadsTheBookOnce_andReportsItOpened() {
        val viewModel = viewModel(SavedStateHandle())

        viewModel.open(identifier(FIRST_HEX))
        viewModel.open(identifier(FIRST_HEX))

        assertEquals(ReaderUiState.Reading(book(FIRST_HEX), anchor(0, 0, 0)), viewModel.state.value)
        assertEquals(listOf(identifier(FIRST_HEX)), loads)
        assertEquals(listOf(identifier(FIRST_HEX)), openedBooks)
    }

    @Test
    fun close_goesBackToTheLibrary_andForgetsTheBook() {
        val savedState = SavedStateHandle()
        val viewModel = viewModel(savedState).apply { open(identifier(FIRST_HEX)) }
        viewModel.onPageSettled(anchor(1, 0, 0))

        viewModel.close()

        assertSame(ReaderUiState.Closed, viewModel.state.value)
        assertNull(savedState.get<String>("reader.book"))
        assertNull(savedState.get<Int>("reader.chapter"))
        assertSame(ReaderUiState.Closed, viewModel(savedState).state.value)
    }

    @Test
    fun close_savesThePosition() {
        val viewModel = viewModel(SavedStateHandle()).apply { open(identifier(FIRST_HEX)) }
        viewModel.onPageSettled(anchor(1, 0, 0))

        viewModel.close()

        assertEquals(
            1,
            store.writes
                .last()
                .progress
                ?.anchor
                ?.chapter,
        )
    }

    @Test
    fun theOpenBookAndItsPosition_comeBackAfterProcessDeath() {
        val savedState = SavedStateHandle()
        viewModel(savedState).apply {
            open(identifier(SECOND_HEX))
            onPageSettled(anchor(1, 0, 2))
        }

        // Same saved state, new instance: what Android does after process death.
        val restored = viewModel(savedState)

        assertEquals(ReaderUiState.Reading(book(SECOND_HEX), anchor(1, 0, 2)), restored.state.value)
    }

    @Test
    fun openingAnotherBook_startsAtItsOwnPosition_andSavesThePreviousOne() {
        val savedState = SavedStateHandle()
        val viewModel = viewModel(savedState).apply { open(identifier(FIRST_HEX)) }
        viewModel.onPageSettled(anchor(1, 0, 0))

        viewModel.open(identifier(SECOND_HEX))

        assertEquals(
            ReaderUiState.Reading(book(SECOND_HEX), anchor(0, 0, 0)),
            viewModel.state.value,
        )
        assertEquals(identifier(FIRST_HEX), store.writes.single().bookIdentifier)
        assertEquals(SECOND_HEX, identifier(savedState.get<String>("reader.book").orEmpty()).hex)
    }

    @Test
    fun aBookThatCannotLoad_isUnavailable_andCanBeClosed() {
        val viewModel = viewModel(SavedStateHandle())

        viewModel.open(identifier("00000000000000000000000000000000"))
        assertSame(ReaderUiState.Unavailable, viewModel.state.value)

        viewModel.close()
        assertSame(ReaderUiState.Closed, viewModel.state.value)
    }

    @Test
    fun choosingAChapter_opensItsFirstPage_andSavesItLikeAPageTurn() {
        val savedState = SavedStateHandle()
        val viewModel = viewModel(savedState).apply { open(identifier(FIRST_HEX)) }

        viewModel.onChapterSelected(1)
        viewModel.flushProgress()

        assertEquals(ReaderUiState.Reading(book(FIRST_HEX), anchor(1, 0, 0)), viewModel.state.value)
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

    @Test
    fun choosingAChapterOutsideTheBook_orWithNothingOpen_isIgnored() {
        val viewModel = viewModel(SavedStateHandle())
        viewModel.onChapterSelected(1)
        assertSame(ReaderUiState.Closed, viewModel.state.value)

        viewModel.open(identifier(FIRST_HEX))
        viewModel.onChapterSelected(2)
        viewModel.onChapterSelected(-1)

        assertEquals(ReaderUiState.Reading(book(FIRST_HEX), anchor(0, 0, 0)), viewModel.state.value)
    }

    private fun viewModel(savedState: SavedStateHandle): ReaderViewModel =
        testReaderViewModel(savedState, store, openedBooks) { bookIdentifier ->
            loads += bookIdentifier
            books[bookIdentifier]?.let { book ->
                Result.success(LoadedBook(book, "${bookIdentifier.hex}.md"))
            }
                ?: Result.failure(IllegalStateException("not in library"))
        }

    private fun identifier(text: String): BookIdentifier =
        (BookIdentifier.parse(text).getOrNull() ?: BookIdentifier.fromHex(text).getOrNull())
            ?: error("Invalid identifier $text")

    private fun anchor(
        chapter: Int,
        block: Int,
        offset: Int,
    ): Anchor = Anchor.create(chapter, block, offset).getOrThrow()

    private fun book(hex: String): Book =
        Book
            .create(
                identifier(hex),
                BookMetadata(title = "Libro $hex", author = null, language = null),
                List(
                    2,
                ) { index ->
                    Chapter
                        .create(
                            index,
                            null,
                            listOf(Block.Paragraph("Texto $index")),
                        ).getOrThrow()
                },
            ).getOrThrow()
}
