package dev.d1ito.dtox.ui.reader

import androidx.lifecycle.SavedStateHandle
import dev.d1ito.dtox.data.importing.LoadedBook
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import java.time.Clock

/**
 * A [ReaderViewModel] for JVM tests: everything runs at once on the test thread (Unconfined),
 * debounced saves only happen on flush, and each opened book is added to [openedBooks].
 */
internal fun testReaderViewModel(
    savedState: SavedStateHandle,
    store: InMemoryBookDataStore = InMemoryBookDataStore(),
    openedBooks: MutableList<BookIdentifier> = mutableListOf(),
    loadBook: suspend (BookIdentifier) -> Result<LoadedBook>,
): ReaderViewModel {
    val scope = CoroutineScope(Dispatchers.Unconfined)
    return ReaderViewModel(
        savedState = savedState,
        loadBook = loadBook,
        newProgressKeeper = {
            ReadingProgressKeeper(
                store = store,
                clock = Clock.systemUTC(),
                waitBeforeSaving = { awaitCancellation() },
                scope = scope,
            )
        },
        onBookOpened = { bookIdentifier -> openedBooks += bookIdentifier },
        scope = scope,
    )
}
