package dev.d1ito.dtox

import dev.d1ito.dtox.data.importing.BookIdentifierCalculator
import dev.d1ito.dtox.data.importing.BookParser
import dev.d1ito.dtox.data.importing.Sha256BookIdentifierCalculator
import dev.d1ito.dtox.data.importing.TextBookParser
import dev.d1ito.dtox.ui.reader.SampleBookLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
}

/**
 * Production wiring. Dependencies are created lazily, once, on first use.
 *
 * @param openSampleBook opens a new stream over the bundled sample book.
 * @param dispatchers dispatchers given to every dependency.
 */
class DefaultAppContainer(
    openSampleBook: () -> InputStream,
    override val dispatchers: AppDispatchers = AppDispatchers.Production,
) : AppContainer {
    override val bookIdentifierCalculator: BookIdentifierCalculator by lazy {
        Sha256BookIdentifierCalculator(dispatchers.io)
    }

    override val textBookParser: BookParser by lazy { TextBookParser(dispatchers.io) }

    override val sampleBookLoader: SampleBookLoader by lazy {
        SampleBookLoader(openSampleBook, bookIdentifierCalculator, textBookParser)
    }
}
