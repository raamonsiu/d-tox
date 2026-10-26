package dev.d1ito.dtox.ui.reader

import android.content.res.Resources
import dev.d1ito.dtox.R
import dev.d1ito.dtox.data.importing.Sha256BookIdentifierCalculator
import dev.d1ito.dtox.data.importing.TextBookParser
import dev.d1ito.dtox.domain.Book
import kotlinx.coroutines.CoroutineDispatcher

private const val SAMPLE_FILE_NAME = "sample_quijote_chapter_one.md"

/**
 * Loads the bundled public domain sample chapter (res/raw) through the real import pipeline:
 * identifier hash, then the TXT/MD parser. Used by the reader prototype until importing exists.
 *
 * @param resources source of the raw resource.
 * @param ioDispatcher where reading and parsing run; never the main thread.
 */
class SampleBookLoader(
    private val resources: Resources,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /** @return the sample book, or the failure raised while hashing or parsing it. */
    suspend fun load(): Result<Book> {
        val identifier =
            resources
                .openRawResource(R.raw.sample_quijote_chapter_one)
                .use { source -> Sha256BookIdentifierCalculator(ioDispatcher).calculate(source) }
                .getOrElse { error -> return Result.failure(error) }
        return resources
            .openRawResource(R.raw.sample_quijote_chapter_one)
            .use { source ->
                TextBookParser(ioDispatcher).parse(source, identifier, SAMPLE_FILE_NAME)
            }
    }
}
