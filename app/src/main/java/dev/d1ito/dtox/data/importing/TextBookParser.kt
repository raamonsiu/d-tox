package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

private val markdownExtensions = setOf("md", "markdown")
private const val BYTE_ORDER_MARK = '\uFEFF'

/**
 * Parses TXT and Markdown files (RF-01). The format is chosen by extension: `.md`/`.markdown`
 * are Markdown, anything else is plain text. Text is decoded as UTF-8; malformed bytes become
 * U+FFFD so a single bad byte does not make the whole book unreadable.
 *
 * @param ioDispatcher where reading and parsing run; never the main thread.
 */
class TextBookParser(
    private val ioDispatcher: CoroutineDispatcher,
) : BookParser {
    override suspend fun parse(
        source: InputStream,
        identifier: BookIdentifier,
        fileName: String,
    ): Result<Book> =
        withContext(ioDispatcher) {
            try {
                bookFrom(decodedText(source), identifier, fileName)
            } catch (readFailure: IOException) {
                Result.failure(readFailure)
            } catch (accessRevoked: SecurityException) {
                Result.failure(accessRevoked)
            }
        }

    /** @throws IOException if [source] fails while reading. */
    private fun decodedText(source: InputStream): String =
        // Not closed on purpose: closing the reader would close the caller's stream.
        source.reader(Charsets.UTF_8).readText().removePrefix(BYTE_ORDER_MARK.toString())

    private fun bookFrom(
        text: String,
        identifier: BookIdentifier,
        fileName: String,
    ): Result<Book> {
        val isMarkdown =
            fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase() in
                markdownExtensions
        val blocks = (if (isMarkdown) MarkdownBlockReader else PlainTextBlockReader).read(text)
        if (blocks.none { block -> block !is Block.Separator }) {
            return Result.failure(BookParseError.NoReadableText(fileName))
        }
        val metadata =
            BookMetadata(title = titleOf(blocks, fileName), author = null, language = null)
        return Book.create(identifier, metadata, chaptersOf(blocks))
    }
}

/**
 * Splits blocks into chapters (decision D-29): each heading of the highest level present opens a
 * chapter titled with it; blocks before the first one form an untitled chapter. Without headings
 * the whole text is a single untitled chapter.
 *
 * @return chapters numbered by position; empty only if [blocks] is empty.
 */
internal fun chaptersOf(blocks: List<Block>): List<Chapter> {
    val chapterLevel =
        blocks.filterIsInstance<Block.Heading>().minOfOrNull { heading ->
            heading.level
        }
    val chapterBlocks =
        blocks.fold(mutableListOf<MutableList<Block>>()) { chapters, block ->
            val opensChapter = block is Block.Heading && block.level == chapterLevel
            if (opensChapter || chapters.isEmpty()) chapters.add(mutableListOf())
            chapters.last().add(block)
            chapters
        }
    return chapterBlocks.mapIndexed { index, contents ->
        val title =
            (contents.first() as? Block.Heading)
                ?.takeIf { heading ->
                    heading.level ==
                        chapterLevel
                }?.text
        // index comes from the list position, so it is never negative.
        Chapter.create(index, title, contents).getOrThrow()
    }
}

/** First level 1 heading, otherwise the file name without its extension. */
internal fun titleOf(
    blocks: List<Block>,
    fileName: String,
): String {
    val firstTopHeading =
        blocks.filterIsInstance<Block.Heading>().firstOrNull { heading ->
            heading.level ==
                1
        }
    return firstTopHeading?.text ?: fileName.substringBeforeLast('.').ifBlank { fileName }
}
