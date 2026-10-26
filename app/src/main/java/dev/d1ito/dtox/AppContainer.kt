package dev.d1ito.dtox

import dev.d1ito.dtox.data.importing.BookIdentifierCalculator
import dev.d1ito.dtox.data.importing.BookImporter
import dev.d1ito.dtox.data.importing.BookParser
import dev.d1ito.dtox.data.importing.EpubBookParser
import dev.d1ito.dtox.data.importing.Sha256BookIdentifierCalculator
import dev.d1ito.dtox.data.importing.TextBookParser
import dev.d1ito.dtox.data.storage.AtomicFileWriter
import dev.d1ito.dtox.data.storage.BookDataStore
import dev.d1ito.dtox.data.storage.FileBookDataStore
import dev.d1ito.dtox.data.storage.FileLibraryIndex
import dev.d1ito.dtox.data.storage.FileParsedBookCache
import dev.d1ito.dtox.data.storage.LibraryIndex
import dev.d1ito.dtox.data.storage.ParsedBookCache
import dev.d1ito.dtox.data.storage.SyncedAtomicFileWriter
import dev.d1ito.dtox.ui.reader.SampleBookLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.InputStream

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
    val sampleBookLoader: SampleBookLoader
    val fileWriter: AtomicFileWriter
    val bookDataStore: BookDataStore
    val libraryIndex: LibraryIndex
    val bookImporter: BookImporter
    val parsedBookCache: ParsedBookCache
}

/**
 * Production wiring. Dependencies are created lazily, once, on first use.
 *
 * @param openSampleBook opens a new stream over the bundled sample book.
 * @param filesDirectory private app storage for user data (`files/`); holds `library/`, `books/`
 *   and `library-index.json` (docs/03-architecture.md §Almacenamiento y permisos).
 * @param cacheDirectory private cache (`cache/`) for temporary copies and parsed books.
 * @param dispatchers dispatchers given to every dependency.
 */
class DefaultAppContainer(
    openSampleBook: () -> InputStream,
    filesDirectory: File,
    cacheDirectory: File,
    override val dispatchers: AppDispatchers = AppDispatchers.Production,
) : AppContainer {
    private val libraryDirectory = File(filesDirectory, "library")

    override val bookIdentifierCalculator: BookIdentifierCalculator by lazy {
        Sha256BookIdentifierCalculator(dispatchers.io)
    }

    override val textBookParser: BookParser by lazy { TextBookParser(dispatchers.io) }

    override val sampleBookLoader: SampleBookLoader by lazy {
        SampleBookLoader(openSampleBook, bookIdentifierCalculator, textBookParser)
    }

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
            parsersByExtension =
                mapOf(
                    "epub" to EpubBookParser(dispatchers.io, cacheDirectory),
                    "txt" to textBookParser,
                    "md" to textBookParser,
                    "markdown" to textBookParser,
                ),
            identifierCalculator = bookIdentifierCalculator,
            fileWriter = fileWriter,
            ioDispatcher = dispatchers.io,
        )
    }

    override val parsedBookCache: ParsedBookCache by lazy {
        FileParsedBookCache(File(cacheDirectory, "parsed"), fileWriter, dispatchers.io)
    }
}
