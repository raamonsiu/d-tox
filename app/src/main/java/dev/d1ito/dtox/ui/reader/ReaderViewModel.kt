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
import dev.d1ito.dtox.domain.anchorAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

private const val SAVED_CHAPTER_KEY = "reader.chapter"
private const val SAVED_BLOCK_KEY = "reader.block"
private const val SAVED_OFFSET_KEY = "reader.offset"

/** Debounce before saving the position after a page turn (docs/02-data-formats.md §4). */
private val PROGRESS_SAVE_DELAY = 2.seconds

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
 * Holds the open book and the reading position. The position is kept in [savedState] for
 * rotation and process death, and in the book data file through [progressKeeper] so it survives
 * closing the app. On opening, the [savedState] position wins (it is the most recent); otherwise
 * the stored one, relocated by its quote.
 *
 * @param savedState where the position is saved for this task.
 * @param loadBook loads the book to read; it chooses its own dispatcher.
 * @param sourceFileName name of the book file, recorded when its data file is first created.
 * @param progressKeeper restores and saves the position in the book data file.
 * @param scope runs the loading and is cancelled when the ViewModel is cleared.
 */
class ReaderViewModel(
    private val savedState: SavedStateHandle,
    loadBook: suspend () -> Result<Book>,
    sourceFileName: String,
    private val progressKeeper: ReadingProgressKeeper,
    scope: CoroutineScope,
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)

    /** Current state; the UI only reads it. */
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            mutableState.value =
                loadBook().fold(
                    onSuccess = { book ->
                        readingStateOf(book, progressKeeper.open(book, sourceFileName))
                    },
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
                progressKeeper.onPositionChanged(withQuote(current.book, pageStart))
                current.copy(position = pageStart)
            } else {
                current
            }
        }
    }

    /** Saves the reading position now instead of after the debounce; call it on `onStop`. */
    fun flushProgress() = progressKeeper.flush()

    private fun readingStateOf(
        book: Book,
        storedPosition: Anchor?,
    ): ReaderUiState {
        if (book.chapters.isEmpty()) return ReaderUiState.Unavailable
        val restored =
            (restoredPosition() ?: storedPosition)?.takeIf { anchor ->
                anchor.chapter in book.chapters.indices
            }
        return ReaderUiState.Reading(book, restored ?: bookStart)
    }

    /** The same position with the quote that lets it be relocated later. */
    private fun withQuote(
        book: Book,
        position: Anchor,
    ): Anchor =
        book.anchorAt(position.chapter, position.block, position.offset).getOrDefault(position)

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
                    val scope = CoroutineScope(SupervisorJob() + container.dispatchers.main)
                    ReaderViewModel(
                        savedState = createSavedStateHandle(),
                        loadBook = container.sampleBookLoader::load,
                        sourceFileName = SampleBookLoader.SAMPLE_FILE_NAME,
                        progressKeeper =
                            ReadingProgressKeeper(
                                store = container.bookDataStore,
                                clock = Clock.systemUTC(),
                                waitBeforeSaving = { delay(PROGRESS_SAVE_DELAY) },
                                scope = scope,
                            ),
                        scope = scope,
                    )
                }
            }
    }
}

/** The first position of any book. Zeros are never negative, so creating it cannot fail. */
private val bookStart: Anchor = Anchor.create(chapter = 0, block = 0, offset = 0).getOrThrow()
