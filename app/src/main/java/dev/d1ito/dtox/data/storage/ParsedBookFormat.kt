package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reads and writes a parsed [Book] for the parsed book cache. Pure: no disk access.
 *
 * Bump [VERSION] whenever this format **or the output of any parser** changes (new block type,
 * different chapter splitting…), so caches written by an older app are parsed again.
 */
object ParsedBookFormat {
    const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = false }

    /**
     * @return the book; a failure for invalid JSON, another [VERSION] or values the document
     *   model rejects. Every failure means "parse the book again".
     */
    fun decode(text: String): Result<Book> =
        try {
            val document = json.decodeFromString<ParsedBookDocument>(text)
            if (document.version == VERSION) {
                document.toBook()
            } else {
                Result.failure(IllegalArgumentException("Parsed book version ${document.version}"))
            }
        } catch (invalid: SerializationException) {
            Result.failure(invalid)
        } catch (invalidValue: IllegalArgumentException) {
            Result.failure(invalidValue)
        }

    fun encode(book: Book): String =
        json.encodeToString(ParsedBookDocument.serializer(), ParsedBookDocument.from(book))
}

@Serializable
private data class ParsedBookDocument(
    val version: Int,
    val bookId: String,
    val title: String,
    val author: String?,
    val language: String?,
    val chapters: List<ChapterDocument>,
) {
    /** @throws IllegalArgumentException when a value is rejected by the document model. */
    fun toBook(): Result<Book> =
        Book.create(
            BookIdentifier.parse(bookId).getOrThrow(),
            BookMetadata(title, author, language),
            chapters.mapIndexed { index, chapter -> chapter.toChapter(index) },
        )

    companion object {
        fun from(book: Book) =
            ParsedBookDocument(
                version = ParsedBookFormat.VERSION,
                bookId = book.identifier.value,
                title = book.metadata.title,
                author = book.metadata.author,
                language = book.metadata.language,
                chapters = book.chapters.map(ChapterDocument::from),
            )
    }
}

@Serializable
private data class ChapterDocument(
    val title: String?,
    val blocks: List<BlockDocument>,
) {
    fun toChapter(index: Int): Chapter =
        Chapter.create(index, title, blocks.map(BlockDocument::toBlock)).getOrThrow()

    companion object {
        fun from(chapter: Chapter) =
            ChapterDocument(chapter.title, chapter.blocks.map(BlockDocument::from))
    }
}

/** One [Block], tagged by its kind in a `type` field. */
@Serializable
private sealed interface BlockDocument {
    /** @throws IllegalArgumentException when the model rejects a value (heading level, depth). */
    fun toBlock(): Block

    @Serializable
    @SerialName("heading")
    data class Heading(
        val level: Int,
        val text: String,
    ) : BlockDocument {
        override fun toBlock(): Block = Block.Heading.create(level, text).getOrThrow()
    }

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        val text: String,
    ) : BlockDocument {
        override fun toBlock(): Block = Block.Paragraph(text)
    }

    @Serializable
    @SerialName("quote")
    data class Quote(
        val text: String,
    ) : BlockDocument {
        override fun toBlock(): Block = Block.Quote(text)
    }

    @Serializable
    @SerialName("listItem")
    data class ListItem(
        val text: String,
        val ordered: Boolean,
        val depth: Int,
    ) : BlockDocument {
        override fun toBlock(): Block = Block.ListItem.create(text, ordered, depth).getOrThrow()
    }

    @Serializable
    @SerialName("separator")
    data object Separator : BlockDocument {
        override fun toBlock(): Block = Block.Separator
    }

    companion object {
        fun from(block: Block): BlockDocument =
            when (block) {
                is Block.Heading -> Heading(block.level, block.text)
                is Block.Paragraph -> Paragraph(block.text)
                is Block.Quote -> Quote(block.text)
                is Block.ListItem -> ListItem(block.text, block.ordered, block.depth)
                Block.Separator -> Separator
            }
    }
}
