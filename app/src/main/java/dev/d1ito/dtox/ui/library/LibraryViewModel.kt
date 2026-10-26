package dev.d1ito.dtox.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.d1ito.dtox.AppContainer
import dev.d1ito.dtox.data.importing.BookImportError
import dev.d1ito.dtox.data.importing.BookImporter
import dev.d1ito.dtox.data.importing.BookParseError
import dev.d1ito.dtox.data.importing.ImportedBook
import dev.d1ito.dtox.data.storage.LibraryEntry
import dev.d1ito.dtox.data.storage.LibraryIndex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * A file the user picked in the system document picker. Every function blocks (it asks the
 * document provider), so the ViewModel only calls them off the main thread; they may throw
 * [IOException] or [SecurityException].
 *
 * @property displayName the name shown by the provider, e.g. `cartas.epub`.
 * @property mimeType the type reported by the provider, `null` if unknown.
 * @property openStream opens the content.
 */
class PickedDocument(
    val displayName: () -> String,
    val mimeType: () -> String?,
    val openStream: () -> InputStream,
)

/** Feedback after an import or load attempt; the screen turns it into a sentence. */
enum class LibraryMessage {
    UnsupportedFormat,
    NoReadableText,
    ImportFailed,
    LibraryUnavailable,
}

/**
 * @property books the library list in display order.
 * @property isImporting an import is running; the add action is disabled meanwhile.
 * @property message what went wrong last, `null` when nothing to report; the next import clears
 *   it.
 */
data class LibraryUiState(
    val books: List<LibraryEntry> = emptyList(),
    val isImporting: Boolean = false,
    val message: LibraryMessage? = null,
)

/**
 * Shows the library index and imports picked files into it (RF-01, RF-02).
 *
 * @param ioDispatcher where the picked file is opened; never the main thread.
 * @param scope runs the work and is cancelled when the ViewModel is cleared.
 */
class LibraryViewModel(
    private val libraryIndex: LibraryIndex,
    private val bookImporter: BookImporter,
    private val ioDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
) : ViewModel(scope) {
    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<LibraryMessage?>(null)

    val state: StateFlow<LibraryUiState> =
        combine(libraryIndex.entries, importing, message, ::LibraryUiState)
            .stateIn(scope, SharingStarted.Eagerly, LibraryUiState())

    init {
        scope.launch {
            libraryIndex.load().onFailure { message.value = LibraryMessage.LibraryUnavailable }
        }
    }

    /** Copies [document] into the library and lists it; ignored while another import runs. */
    fun importBook(document: PickedDocument) {
        if (importing.value) return
        importing.value = true
        message.value = null
        scope.launch {
            importedBookOf(document)
                .fold(onSuccess = { book -> addToIndex(book) }, onFailure = { Result.failure(it) })
                .onFailure { failure -> message.value = messageFor(failure) }
            importing.value = false
        }
    }

    private suspend fun importedBookOf(document: PickedDocument): Result<ImportedBook> =
        withContext(ioDispatcher) {
            try {
                val displayName = document.displayName()
                val mimeType = document.mimeType()
                document.openStream().use { source ->
                    bookImporter.import(source, displayName, mimeType)
                }
            } catch (unreadable: IOException) {
                Result.failure(unreadable)
            } catch (accessRevoked: SecurityException) {
                Result.failure(accessRevoked)
            }
        }

    /** Lists [imported] unless it already is; an index write failure fails the import. */
    private suspend fun addToIndex(imported: ImportedBook): Result<Unit> {
        val identifier = imported.book.identifier
        // A book already listed keeps its entry, and with it the time it was last read.
        if (libraryIndex.entries.value.any { entry -> entry.bookIdentifier == identifier }) {
            return Result.success(Unit)
        }
        return libraryIndex
            .add(
                LibraryEntry(
                    bookIdentifier = identifier,
                    title = imported.book.metadata.title,
                    author = imported.book.metadata.author,
                    fileName = imported.file.name,
                    lastOpenedAt = null,
                    finished = false,
                ),
            )
    }

    companion object {
        /** @return a factory that builds the library ViewModel from the app dependencies. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    LibraryViewModel(
                        libraryIndex = container.libraryIndex,
                        bookImporter = container.bookImporter,
                        ioDispatcher = container.dispatchers.io,
                        scope = CoroutineScope(SupervisorJob() + container.dispatchers.main),
                    )
                }
            }
    }
}

private fun messageFor(failure: Throwable): LibraryMessage =
    when (failure) {
        is BookImportError.UnsupportedFormat -> LibraryMessage.UnsupportedFormat
        is BookParseError.NoReadableText -> LibraryMessage.NoReadableText
        else -> LibraryMessage.ImportFailed
    }
