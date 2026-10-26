package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.data.importing.BookIdentifierCalculator
import dev.d1ito.dtox.data.importing.BookParser
import dev.d1ito.dtox.domain.Book
import java.io.InputStream

/**
 * Loads the public domain sample chapter (test resources) through the real import pipeline:
 * identifier hash, then the TXT/MD parser. Test fixture since the app opens library books.
 *
 * @param openSample opens a new stream over the sample file each time it is called.
 * @param identifierCalculator computes the book identifier.
 * @param parser turns the sample into a [Book]; it chooses its own dispatcher.
 */
class SampleBookLoader(
    private val openSample: () -> InputStream,
    private val identifierCalculator: BookIdentifierCalculator,
    private val parser: BookParser,
) {
    companion object {
        /** File name of the bundled sample, used to pick its parser and as its source name. */
        const val SAMPLE_FILE_NAME = "sample_quijote_chapter_one.md"
    }

    /** @return the sample book, or the failure raised while hashing or parsing it. */
    suspend fun load(): Result<Book> {
        val identifier =
            openSample()
                .use { source -> identifierCalculator.calculate(source) }
                .getOrElse { error -> return Result.failure(error) }
        return openSample().use { source -> parser.parse(source, identifier, SAMPLE_FILE_NAME) }
    }
}
