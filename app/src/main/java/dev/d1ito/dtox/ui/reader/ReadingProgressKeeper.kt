package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.data.storage.BookData
import dev.d1ito.dtox.data.storage.BookDataStore
import dev.d1ito.dtox.data.storage.BookSource
import dev.d1ito.dtox.data.storage.ReadingProgress
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.readingPercentAt
import dev.d1ito.dtox.domain.relocate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/**
 * Restores and saves the reading position of one open book in its data file (RF-17). Positions
 * are saved after [waitBeforeSaving] without further changes (debounce) or at once on [flush].
 * Call it from a single thread (the main one); the store moves file work off it.
 *
 * If the stored file cannot be read (I/O error, newer format) the position is not saved at all,
 * so a file this version does not understand is never overwritten. A failed write is retried on
 * the next save, which always writes the latest position. Each save changes only the progress of
 * the file as stored at that moment, so annotations written meanwhile are kept.
 *
 * @param waitBeforeSaving suspends for the debounce time, e.g. `delay(2.seconds)`.
 * @param scope runs the saves; cancelling it drops a pending save, so [flush] first.
 */
class ReadingProgressKeeper(
    private val store: BookDataStore,
    private val clock: Clock,
    private val waitBeforeSaving: suspend () -> Unit,
    private val scope: CoroutineScope,
) {
    private val saveLock = Mutex()
    private var openBook: Book? = null

    /** Contents for a new data file; `null` until [open] could read the stored one. */
    private var dataForNewFile: BookData? = null
    private var unsavedPosition: Anchor? = null
    private var pendingSave: Job? = null

    /**
     * Loads the stored progress of [book] and prepares saving. A stored anchor is relocated with
     * its quote; if that moved it, the new position (with updated percent and date) is saved.
     *
     * @param sourceFileName recorded in a new data file when the book has none yet.
     * @return the relocated stored position, or `null` when there is none or it is unreadable.
     */
    suspend fun open(
        book: Book,
        sourceFileName: String,
    ): Anchor? {
        val stored = store.read(book.identifier).getOrElse { return null }
        openBook = book
        dataForNewFile = newBookData(book, sourceFileName)
        val storedAnchor = stored?.progress?.anchor ?: return null
        val relocated = book.relocate(storedAnchor)
        if (relocated != storedAnchor) {
            unsavedPosition = relocated
            flush()
        }
        return relocated
    }

    /** Records the new reading position and saves it once the reader stays there. */
    fun onPositionChanged(position: Anchor) {
        if (dataForNewFile == null) return
        unsavedPosition = position
        pendingSave?.cancel()
        pendingSave =
            scope.launch {
                waitBeforeSaving()
                saveUnsavedPosition()
            }
    }

    /** Saves the latest position now, e.g. when the app goes to the background. */
    fun flush() {
        if (unsavedPosition == null) return
        pendingSave?.cancel()
        pendingSave = scope.launch { saveUnsavedPosition() }
    }

    private suspend fun saveUnsavedPosition() =
        saveLock.withLock {
            val position = unsavedPosition ?: return@withLock
            val book = openBook ?: return@withLock
            val newFileData = dataForNewFile ?: return@withLock
            store
                .update(book.identifier) { stored ->
                    val current = stored ?: newFileData
                    Result.success(current.copy(progress = progressAt(position, book, current)))
                }.onSuccess { if (unsavedPosition == position) unsavedPosition = null }
        }

    private fun progressAt(
        position: Anchor,
        book: Book,
        current: BookData,
    ): ReadingProgress =
        ReadingProgress(
            anchor = position,
            percent = book.readingPercentAt(position),
            updatedAt = clock.instant(),
            finished = current.progress?.finished ?: false,
        )

    private fun newBookData(
        book: Book,
        sourceFileName: String,
    ): BookData =
        BookData(
            bookIdentifier = book.identifier,
            title = book.metadata.title,
            author = book.metadata.author,
            source = BookSource(sourceFileName, clock.instant()),
            progress = null,
        )
}
