package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import java.io.InputStream

/** Converts one imported file format into the internal document model. */
interface BookParser {
    /**
     * Reads [source] to its end, off the main thread.
     *
     * @param source the original file bytes; the caller keeps ownership and must close it.
     * @param identifier the book identity, already computed from the same bytes.
     * @param fileName the display name of the file, used to pick the format and as fallback title.
     * @return the book, or a failure: an [java.io.IOException] while reading or a
     *   [BookParseError] when the content cannot become a book.
     */
    suspend fun parse(
        source: InputStream,
        identifier: BookIdentifier,
        fileName: String,
    ): Result<Book>
}

/** Why a readable file could not become a book. */
sealed class BookParseError(
    message: String,
) : IllegalArgumentException(message) {
    /** The file is empty or only contains whitespace or markup without text. */
    class NoReadableText(
        val fileName: String,
    ) : BookParseError("No readable text in '$fileName'")
}
