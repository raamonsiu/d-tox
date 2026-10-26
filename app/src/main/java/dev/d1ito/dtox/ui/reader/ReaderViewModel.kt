package dev.d1ito.dtox.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.d1ito.dtox.AppContainer
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SAVED_CHAPTER_KEY = "reader.chapter"
private const val SAVED_BLOCK_KEY = "reader.block"
private const val SAVED_OFFSET_KEY = "reader.offset"

/** What the reader shows. */
sealed interface ReaderUiState {
    /** The book is still loading. */
    data object Loading : ReaderUiState

    /** The book could not be loaded or has nothing to show. */
    data object Unavailable : ReaderUiState

    /**
     * @property book the open book.
     * @property position start of the page being read; its chapter is always one of [book]'s.
     */
    data class Reading(
        val book: Book,
        val position: Anchor,
    ) : ReaderUiState {
        /** The chapter that contains [position]. */
        val chapter: Chapter get() = book.chapters[position.chapter]
    }
}

/**
 * Holds the open book and the reading position. The position is kept in [savedState], so it
 * survives rotation and process death; writing it to disk belongs to another issue.
 *
 * @param savedState where the position is saved.
 * @param loadBook loads the book to read; it chooses its own dispatcher.
 * @param scope runs the loading and is cancelled when the ViewModel is cleared.
 */
class ReaderViewModel(
    private val savedState: SavedStateHandle,
    loadBook: suspend () -> Result<Book>,
    scope: CoroutineScope,
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)

    /** Current state; the UI only reads it. */
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            mutableState.value =
                loadBook().fold(
                    onSuccess = ::readingStateOf,
                    onFailure = { ReaderUiState.Unavailable },
                )
        }
    }

    /**
     * Records that the reader stopped on the page starting at [pageStart].
     * Ignored while nothing is open or when [pageStart] is outside the open book.
     */
    fun onPageSettled(pageStart: Anchor) {
        mutableState.update { current ->
            if (current is ReaderUiState.Reading &&
                pageStart.chapter in current.book.chapters.indices
            ) {
                savePosition(pageStart)
                current.copy(position = pageStart)
            } else {
                current
            }
        }
    }

    private fun readingStateOf(book: Book): ReaderUiState {
        if (book.chapters.isEmpty()) return ReaderUiState.Unavailable
        val restored =
            restoredPosition()?.takeIf { anchor ->
                anchor.chapter in
                    book.chapters.indices
            }
        return ReaderUiState.Reading(book, restored ?: bookStart)
    }

    private fun restoredPosition(): Anchor? {
        val chapter = savedState.get<Int>(SAVED_CHAPTER_KEY)
        val block = savedState.get<Int>(SAVED_BLOCK_KEY)
        val offset = savedState.get<Int>(SAVED_OFFSET_KEY)
        if (chapter == null || block == null || offset == null) return null
        return Anchor.create(chapter, block, offset).getOrNull()
    }

    private fun savePosition(anchor: Anchor) {
        savedState[SAVED_CHAPTER_KEY] = anchor.chapter
        savedState[SAVED_BLOCK_KEY] = anchor.block
        savedState[SAVED_OFFSET_KEY] = anchor.offset
    }

    companion object {
        /**
         * @param container source of the book loader and the main dispatcher.
         * @return a factory that builds the reader ViewModel with its saved state.
         */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ReaderViewModel(
                        savedState = createSavedStateHandle(),
                        loadBook = container.sampleBookLoader::load,
                        scope = CoroutineScope(SupervisorJob() + container.dispatchers.main),
                    )
                }
            }
    }
}

/** The first position of any book. Zeros are never negative, so creating it cannot fail. */
private val bookStart: Anchor = Anchor.create(chapter = 0, block = 0, offset = 0).getOrThrow()
