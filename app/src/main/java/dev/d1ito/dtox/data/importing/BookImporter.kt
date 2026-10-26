package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.data.storage.AtomicFileWriter
import dev.d1ito.dtox.domain.Book
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

private const val STAGING_PREFIX = ".importing-"
private const val IDENTIFIER_SEPARATOR = ':'

/** MIME types accepted when the file name has no supported extension, and their extension. */
private val extensionsByMimeType =
    mapOf(
        "application/epub+zip" to "epub",
        "text/plain" to "txt",
        "text/markdown" to "md",
        "text/x-markdown" to "md",
    )

/**
 * A book copied into the library and parsed.
 *
 * @property book the parsed document model.
 * @property file the library's own copy, named after the book identifier.
 * @property wasAlreadyInLibrary `true` when the same bytes had been imported before.
 */
data class ImportedBook(
    val book: Book,
    val file: File,
    val wasAlreadyInLibrary: Boolean,
)

/** Why a file could not be imported (besides the [BookParseError]s of the parsers). */
sealed class BookImportError(
    message: String,
) : IllegalArgumentException(message) {
    /** Neither the extension nor the MIME type is EPUB, TXT or Markdown. */
    class UnsupportedFormat(
        val fileName: String,
        val mimeType: String?,
    ) : BookImportError("Unsupported format: '$fileName' ($mimeType)")
}

/**
 * Imports a book chosen by the user (RF-01, docs/03-architecture.md §Flujo de importación):
 * copies it into [libraryDirectory] as `<sha256 hex>.<extension>`, so the same bytes are stored
 * once (RF-03), and parses that copy with the parser of its format.
 *
 * The source is read once, into a hidden staging file written atomically; the identifier is
 * computed from that durable copy and the staging file is then renamed atomically to its final
 * name. When parsing fails, a copy made by this import is removed again. Staging files left by a
 * crash start with `.importing-` and are never valid library entries.
 *
 * @param libraryDirectory `files/library/` in production.
 * @param parsersByExtension parser for each supported lowercase extension (`epub`, `txt`, `md`…).
 * @param identifierCalculator computes the book identifier from the copy.
 * @param fileWriter writes the staging copy durably.
 * @param ioDispatcher where file operations run; never the main thread.
 */
class BookImporter(
    private val libraryDirectory: File,
    private val parsersByExtension: Map<String, BookParser>,
    private val identifierCalculator: BookIdentifierCalculator,
    private val fileWriter: AtomicFileWriter,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * @param source the chosen file; read to its end, the caller keeps ownership and closes it.
     * @param fileName display name of the file, used to pick the format and as fallback title.
     * @param mimeType type reported by the document provider, used when the extension is unknown.
     * @return the imported book, or a failure: [BookImportError.UnsupportedFormat], a
     *   [BookParseError] from the parser, an [IOException] while copying or reading, or a
     *   [SecurityException] if access to the source is revoked.
     */
    suspend fun import(
        source: InputStream,
        fileName: String,
        mimeType: String?,
    ): Result<ImportedBook> {
        val extension =
            supportedExtensionOf(fileName, mimeType)
                ?: return Result.failure(BookImportError.UnsupportedFormat(fileName, mimeType))
        return withContext(ioDispatcher) {
            val staging = File(libraryDirectory, "$STAGING_PREFIX${UUID.randomUUID()}")
            try {
                fileWriter.write(staging) { output -> source.copyTo(output) }.getOrThrow()
                storeAndParse(staging, extension, fileName)
            } catch (copyFailure: IOException) {
                Result.failure(copyFailure)
            } catch (accessRevoked: SecurityException) {
                Result.failure(accessRevoked)
            } finally {
                staging.delete() // Already renamed after a successful import.
            }
        }
    }

    /** @throws IOException if the staging copy cannot be read or renamed. */
    private suspend fun storeAndParse(
        staging: File,
        extension: String,
        fileName: String,
    ): Result<ImportedBook> {
        val identifier =
            staging
                .inputStream()
                .use { input ->
                    identifierCalculator.calculate(input)
                }.getOrThrow()
        val libraryFile =
            File(
                libraryDirectory,
                "${identifier.value.substringAfter(IDENTIFIER_SEPARATOR)}.$extension",
            )
        val wasAlreadyInLibrary = libraryFile.exists()
        if (!wasAlreadyInLibrary) {
            // Replacing is safe: a concurrent import of the same identifier wrote the same bytes.
            Files.move(
                staging.toPath(),
                libraryFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        // The extension was checked against the map before copying.
        val parser = parsersByExtension.getValue(extension)
        val parsed =
            libraryFile.inputStream().use { input ->
                parser.parse(input, identifier, fileName)
            }
        if (parsed.isFailure && !wasAlreadyInLibrary) libraryFile.delete()
        return parsed.map { book -> ImportedBook(book, libraryFile, wasAlreadyInLibrary) }
    }

    /** The lowercase extension of [fileName] if supported, otherwise the one of [mimeType]. */
    private fun supportedExtensionOf(
        fileName: String,
        mimeType: String?,
    ): String? {
        val fromName = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val fromMimeType = mimeType?.let { type -> extensionsByMimeType[type.lowercase()] }
        return fromName.takeIf { extension -> extension in parsersByExtension }
            ?: fromMimeType?.takeIf { extension -> extension in parsersByExtension }
    }
}
