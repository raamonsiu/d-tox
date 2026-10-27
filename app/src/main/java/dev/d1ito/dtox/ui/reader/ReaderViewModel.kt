package dev.d1ito.dtox.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.d1ito.dtox.AppContainer
import dev.d1ito.dtox.data.importing.LoadedBook
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.domain.anchorAt
import dev.d1ito.dtox.domain.textOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Debounce before saving the position after a page turn (docs/02-data-formats.md §4). */
private val PROGRESS_SAVE_DELAY = 2.seconds

/** What the reader shows. */
sealed interface ReaderUiState {
    /** No book is open: the app shows the library. */
    data object Closed : ReaderUiState

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
 * Holds the open book and the reading position. Which book is open and where is kept in
 * [savedState] for rotation and process death, and the position also in the book data file
 * through a [ReadingProgressKeeper] so it survives closing the app. On opening, the [savedState]
 * position wins (it is the most recent); otherwise the stored one, relocated by its quote.
 *
 * @param savedState where the open book and position are saved for this task.
 * @param loadBook loads a library book; it chooses its own dispatcher.
 * @param newProgressKeeper creates the keeper of one open book; each book gets its own, so a
 *   save still pending for the previous book never lands in the next one.
 * @param onBookOpened called once a book has loaded, e.g. to stamp it as last opened.
 * @param scope runs the loading and is cancelled when the ViewModel is cleared.
 */
class ReaderViewModel(
    savedState: SavedStateHandle,
    private val loadBook: suspend (BookIdentifier) -> Result<LoadedBook>,
    private val newProgressKeeper: () -> ReadingProgressKeeper,
    private val onBookOpened: suspend (BookIdentifier) -> Unit,
    private val scope: CoroutineScope,
) : ViewModel(scope) {
    private val saved = SavedReaderState(savedState)
    private val mutableState = MutableStateFlow<ReaderUiState>(ReaderUiState.Closed)
    private var openBookIdentifier: BookIdentifier? = null
    private var progressKeeper: ReadingProgressKeeper? = null
    private var loading: Job? = null

    /** Current state; the UI only reads it. */
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()

    init {
        saved.bookIdentifier()?.let(::open)
    }

    /**
     * Opens [bookIdentifier] at its saved or stored position. Opening the book that is already
     * open does nothing; opening another one saves and closes the current one first.
     */
    fun open(bookIdentifier: BookIdentifier) {
        if (bookIdentifier == openBookIdentifier) return
        releaseOpenBook()
        // The saved position belongs to the saved book: keep it only when reopening that one.
        if (bookIdentifier != saved.bookIdentifier()) saved.clearPosition()
        saved.saveBook(bookIdentifier)
        openBookIdentifier = bookIdentifier
        val keeper = newProgressKeeper().also { progressKeeper = it }
        mutableState.value = ReaderUiState.Loading
        loading =
            scope.launch {
                mutableState.value =
                    loadBook(bookIdentifier).fold(
                        onSuccess = { loaded ->
                            onBookOpened(bookIdentifier)
                            readingStateOf(loaded.book, keeper.open(loaded.book, loaded.fileName))
                        },
                        onFailure = { ReaderUiState.Unavailable },
                    )
            }
    }

    /** Saves the position and goes back to the library. */
    fun close() {
        releaseOpenBook()
        saved.clear()
        mutableState.value = ReaderUiState.Closed
    }

    /** Saves the open book's position and stops loading it; the saved state is left as it is. */
    private fun releaseOpenBook() {
        flushProgress()
        loading?.cancel()
        loading = null
        progressKeeper = null
        openBookIdentifier = null
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
                saved.savePosition(pageStart)
                progressKeeper?.onPositionChanged(withQuote(current.book, pageStart))
                current.copy(position = pageStart)
            } else {
                current
            }
        }
    }

    /** Opens the first page of chapter [index], chosen in the chapter list; ignored if invalid. */
    fun onChapterSelected(index: Int) {
        val reading = mutableState.value as? ReaderUiState.Reading ?: return
        if (index !in reading.book.chapters.indices) return
        onPageSettled(chapterStart(index))
    }

    /** Turning past the last page: opens the first page of the next chapter, if there is one. */
    fun onNextChapter() {
        val reading = mutableState.value as? ReaderUiState.Reading ?: return
        val next = reading.position.chapter + 1
        if (next > reading.book.chapters.lastIndex) return
        onPageSettled(chapterStart(next))
    }

    /** Turning back from the first page: opens the last page of the previous chapter, if any. */
    fun onPreviousChapter() {
        val reading = mutableState.value as? ReaderUiState.Reading ?: return
        val previous = reading.position.chapter - 1
        if (previous < 0) return
        onPageSettled(chapterEnd(reading.book.chapters[previous]))
    }

    /** Saves the reading position now instead of after the debounce; call it on `onStop`. */
    fun flushProgress() {
        progressKeeper?.flush()
    }

    private fun readingStateOf(
        book: Book,
        storedPosition: Anchor?,
    ): ReaderUiState {
        if (book.chapters.isEmpty()) return ReaderUiState.Unavailable
        val restored =
            (saved.position() ?: storedPosition)?.takeIf { anchor ->
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

    companion object {
        /**
         * @param container source of the book loader, the stores and the main dispatcher.
         * @return a factory that builds the reader ViewModel with its saved state.
         */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val scope = CoroutineScope(SupervisorJob() + container.dispatchers.main)
                    ReaderViewModel(
                        savedState = createSavedStateHandle(),
                        loadBook = container.libraryBookLoader::load,
                        newProgressKeeper = {
                            ReadingProgressKeeper(
                                store = container.bookDataStore,
                                clock = Clock.systemUTC(),
                                waitBeforeSaving = { delay(PROGRESS_SAVE_DELAY) },
                                scope = scope,
                            )
                        },
                        // Failing to stamp the time only affects the library order.
                        onBookOpened = { bookIdentifier ->
                            container.libraryIndex.markOpened(bookIdentifier)
                        },
                        scope = scope,
                    )
                }
            }
    }
}

/** The first position of any book. Zeros are never negative, so creating it cannot fail. */
private val bookStart: Anchor = chapterStart(0)

/** First position of chapter [index], which is never negative. */
private fun chapterStart(index: Int): Anchor = Anchor.create(index, 0, 0).getOrThrow()

/**
 * Position right after the last character of [chapter]: the paginator maps it to the last page.
 * Every index and length is non-negative, so creating it cannot fail.
 */
private fun chapterEnd(chapter: Chapter): Anchor {
    val lastBlock = chapter.blocks.lastIndex.coerceAtLeast(0)
    val textLength =
        chapter.blocks
            .getOrNull(lastBlock)
            ?.textOrNull
            ?.length ?: 0
    return Anchor.create(chapter.index, lastBlock, textLength).getOrThrow()
}
