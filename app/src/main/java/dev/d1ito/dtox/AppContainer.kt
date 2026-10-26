package dev.d1ito.dtox

import dev.d1ito.dtox.data.importing.BookIdentifierCalculator
import dev.d1ito.dtox.data.importing.BookImporter
import dev.d1ito.dtox.data.importing.BookParser
import dev.d1ito.dtox.data.importing.EpubBookParser
import dev.d1ito.dtox.data.importing.LibraryBookLoader
import dev.d1ito.dtox.data.importing.Sha256BookIdentifierCalculator
import dev.d1ito.dtox.data.importing.TextBookParser
import dev.d1ito.dtox.data.storage.AtomicFileWriter
import dev.d1ito.dtox.data.storage.BookDataStore
import dev.d1ito.dtox.data.storage.FileBookDataStore
import dev.d1ito.dtox.data.storage.FileLibraryIndex
import dev.d1ito.dtox.data.storage.FileParsedBookCache
import dev.d1ito.dtox.data.storage.FileReadingSettingsStore
import dev.d1ito.dtox.data.storage.LibraryIndex
import dev.d1ito.dtox.data.storage.ParsedBookCache
import dev.d1ito.dtox.data.storage.ReadingSettingsStore
import dev.d1ito.dtox.data.storage.SyncedAtomicFileWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Coroutine dispatchers used by the app, injected so tests can replace them.
 *
 * @property io blocking file and stream work.
 * @property computation CPU-bound work such as pagination.
 * @property main UI state updates, e.g. in ViewModels.
 */
data class AppDispatchers(
    val io: CoroutineDispatcher,
    val computation: CoroutineDispatcher,
    val main: CoroutineDispatcher,
) {
    companion object {
        /** The production dispatchers. */
        val Production =
            AppDispatchers(
                io = Dispatchers.IO,
                computation = Dispatchers.Default,
                main = Dispatchers.Main.immediate,
            )
    }
}

/**
 * Every app-wide dependency, created in one place (manual dependency injection, D-32).
 * Classes receive what they need through their constructors, never from this container.
 */
interface AppContainer {
    val dispatchers: AppDispatchers
    val bookIdentifierCalculator: BookIdentifierCalculator
    val textBookParser: BookParser
    val fileWriter: AtomicFileWriter
    val bookDataStore: BookDataStore
    val libraryIndex: LibraryIndex
    val bookImporter: BookImporter
    val parsedBookCache: ParsedBookCache
    val libraryBookLoader: LibraryBookLoader
    val readingSettingsStore: ReadingSettingsStore
}

/**
 * Production wiring. Dependencies are created lazily, once, on first use.
 *
 * @param filesDirectory private app storage for user data (`files/`); holds `library/`, `books/`,
 *   `library-index.json` and `settings.json` (docs/03-architecture.md §Almacenamiento y permisos).
 * @param cacheDirectory private cache (`cache/`) for temporary copies and parsed books.
 * @param dispatchers dispatchers given to every dependency.
 */
class DefaultAppContainer(
    filesDirectory: File,
    cacheDirectory: File,
    override val dispatchers: AppDispatchers = AppDispatchers.Production,
) : AppContainer {
    private val libraryDirectory = File(filesDirectory, "library")

    /** Parser of each supported lowercase extension, shared by importing and opening. */
    private val parsersByExtension: Map<String, BookParser> by lazy {
        mapOf(
            "epub" to EpubBookParser(dispatchers.io, cacheDirectory),
            "txt" to textBookParser,
            "md" to textBookParser,
            "markdown" to textBookParser,
        )
    }

    override val bookIdentifierCalculator: BookIdentifierCalculator by lazy {
        Sha256BookIdentifierCalculator(dispatchers.io)
    }

    override val textBookParser: BookParser by lazy { TextBookParser(dispatchers.io) }

    override val fileWriter: AtomicFileWriter by lazy { SyncedAtomicFileWriter(dispatchers.io) }

    override val bookDataStore: BookDataStore by lazy {
        FileBookDataStore(File(filesDirectory, "books"), fileWriter, dispatchers.io)
    }

    override val libraryIndex: LibraryIndex by lazy {
        FileLibraryIndex(
            indexFile = File(filesDirectory, "library-index.json"),
            libraryDirectory = libraryDirectory,
            bookDataStore = bookDataStore,
            fileWriter = fileWriter,
            ioDispatcher = dispatchers.io,
        )
    }

    override val bookImporter: BookImporter by lazy {
        BookImporter(
            libraryDirectory = libraryDirectory,
            parsersByExtension = parsersByExtension,
            identifierCalculator = bookIdentifierCalculator,
            fileWriter = fileWriter,
            ioDispatcher = dispatchers.io,
        )
    }

    override val parsedBookCache: ParsedBookCache by lazy {
        FileParsedBookCache(File(cacheDirectory, "parsed"), fileWriter, dispatchers.io)
    }

    override val libraryBookLoader: LibraryBookLoader by lazy {
        LibraryBookLoader(libraryDirectory, parsersByExtension, parsedBookCache, dispatchers.io)
    }

    override val readingSettingsStore: ReadingSettingsStore by lazy {
        FileReadingSettingsStore(File(filesDirectory, "settings.json"), fileWriter, dispatchers.io)
    }
}
