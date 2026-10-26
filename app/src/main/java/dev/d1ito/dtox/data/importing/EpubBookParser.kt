package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

private const val TEMPORARY_FILE_PREFIX = "import-"
private const val TEMPORARY_FILE_SUFFIX = ".epub"
private const val AUTHOR_SEPARATOR = ", "
private const val BYTE_ORDER_MARK = '\uFEFF'

/** Media types read as content documents; anything else in the spine (images, SVG) is skipped. */
private val contentMediaTypes = setOf("application/xhtml+xml", "text/html")

/**
 * Parses EPUB 2 and 3 files (D-09): the package structure with [EpubPackageReader] and every
 * content document with [XhtmlBlockReader]. Each linear spine document with readable text becomes
 * a chapter, titled from the table of contents or else with its first heading. Documents that are
 * missing, unreadable, not XHTML, non-linear (covers, pop-up notes) or without text are skipped
 * instead of failing the book.
 *
 * The stream is copied to a temporary file first, because a zip archive is read through its
 * central directory; the copy is always deleted before returning.
 *
 * @param ioDispatcher where reading and parsing run; never the main thread.
 * @param temporaryDirectory where the temporary copy is written (the app cache in production).
 * @param packageReader reads the container and package document.
 */
class EpubBookParser(
    private val ioDispatcher: CoroutineDispatcher,
    private val temporaryDirectory: File,
    private val packageReader: EpubPackageReader = EpubPackageReader(),
) : BookParser {
    override suspend fun parse(
        source: InputStream,
        identifier: BookIdentifier,
        fileName: String,
    ): Result<Book> =
        withContext(ioDispatcher) {
            try {
                val copy =
                    File.createTempFile(
                        TEMPORARY_FILE_PREFIX,
                        TEMPORARY_FILE_SUFFIX,
                        temporaryDirectory,
                    )
                try {
                    // The caller owns source and closes it.
                    copy.outputStream().use { output -> source.copyTo(output) }
                    bookFrom(copy, identifier, fileName)
                } finally {
                    copy.delete()
                }
            } catch (readFailure: IOException) {
                Result.failure(readFailure)
            } catch (accessRevoked: SecurityException) {
                Result.failure(accessRevoked)
            }
        }

    /** @throws IOException if the archive cannot be opened. */
    private fun bookFrom(
        epubFile: File,
        identifier: BookIdentifier,
        fileName: String,
    ): Result<Book> {
        val epubPackage =
            packageReader.read(epubFile).getOrElse { failure ->
                return Result.failure(failure)
            }
        val readableItems =
            epubPackage.spine.filter { item ->
                item.isLinear && item.mediaType in contentMediaTypes
            }
        val (documents, tableOfContentsTitles) =
            ZipFile(epubFile).use { archive ->
                val readDocuments =
                    readableItems.mapNotNull { item ->
                        blocksOf(archive, item.path)?.let { blocks -> item.path to blocks }
                    }
                val titles =
                    epubPackage.tableOfContents
                        ?.let { tableOfContents -> titlesOrEmpty(archive, tableOfContents) }
                        .orEmpty()
                readDocuments to titles
            }
        if (documents.isEmpty()) return Result.failure(BookParseError.NoReadableText(fileName))
        return Book.create(
            identifier,
            metadataOf(epubPackage.metadata, fileName),
            chaptersFromDocuments(documents, tableOfContentsTitles),
        )
    }

    /** TOC titles by document path; a damaged TOC only loses titles, never the book. */
    private fun titlesOrEmpty(
        archive: ZipFile,
        tableOfContents: EpubTableOfContentsDocument,
    ): Map<String, String> =
        try {
            titlesFromTableOfContents(archive, tableOfContents)
        } catch (expectedDamagedTableOfContents: IOException) {
            emptyMap()
        }

    /** @return the document's blocks, or `null` if it is missing, unreadable or has no text. */
    private fun blocksOf(
        archive: ZipFile,
        path: String,
    ): List<Block>? {
        val entry = archive.getEntry(path) ?: return null
        val text =
            try {
                archive.getInputStream(entry).use { input -> input.readBytes().decodeToString() }
            } catch (expectedDamagedDocument: IOException) {
                return null // One damaged document must not make the whole book unreadable.
            }
        val blocks = XhtmlBlockReader.read(text.removePrefix(BYTE_ORDER_MARK.toString()))
        return blocks.takeIf { readBlocks -> readBlocks.any { block -> block !is Block.Separator } }
    }
}

/** Title from the OPF, otherwise the file name without its extension; authors joined. */
private fun metadataOf(
    metadata: EpubMetadata,
    fileName: String,
): BookMetadata =
    BookMetadata(
        title = metadata.title ?: fileName.substringBeforeLast('.').ifBlank { fileName },
        author = metadata.authors.joinToString(AUTHOR_SEPARATOR).ifEmpty { null },
        language = metadata.language,
    )

/**
 * One chapter per document (`path to blocks`), numbered by position and titled from the table of
 * contents, or with its first heading when the TOC does not link it (RF-16).
 */
private fun chaptersFromDocuments(
    documents: List<Pair<String, List<Block>>>,
    tableOfContentsTitles: Map<String, String>,
): List<Chapter> =
    documents.mapIndexed { index, (path, blocks) ->
        val title =
            tableOfContentsTitles[path]
                ?: blocks.filterIsInstance<Block.Heading>().firstOrNull()?.text
        // index comes from the list position, so it is never negative.
        Chapter.create(index, title, blocks).getOrThrow()
    }
