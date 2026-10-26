package dev.d1ito.dtox.domain

/**
 * Descriptive data a parser extracts from the source file.
 *
 * @property title shown in the library; parsers fall back to the file name.
 * @property author `null` when the source does not declare one.
 * @property language BCP 47 tag (e.g. `es`), `null` when unknown.
 */
data class BookMetadata(
    val title: String,
    val author: String?,
    val language: String?,
)

/**
 * A book converted to the internal document model. Every input format (EPUB, TXT, MD, PDF) is
 * parsed into this; the reader and the paginator only know this model.
 *
 * @property chapters in reading order; `chapters[position].index == position`.
 */
@ConsistentCopyVisibility
data class Book private constructor(
    val identifier: BookIdentifier,
    val metadata: BookMetadata,
    val chapters: List<Chapter>,
) {
    companion object {
        /** @return the book, or [DocumentModelError.ChapterIndexMismatch] if misnumbered. */
        fun create(
            identifier: BookIdentifier,
            metadata: BookMetadata,
            chapters: List<Chapter>,
        ): Result<Book> {
            val misnumbered =
                chapters.withIndex().firstOrNull { (position, chapter) ->
                    chapter.index != position
                }
            return if (misnumbered == null) {
                Result.success(Book(identifier, metadata, chapters.toList()))
            } else {
                Result.failure(
                    DocumentModelError.ChapterIndexMismatch(
                        misnumbered.index,
                        misnumbered.value.index,
                    ),
                )
            }
        }
    }
}

/**
 * One chapter: the unit of pagination and of the table of contents.
 *
 * @property index 0-based position in [Book.chapters].
 * @property title `null` when the source has none (e.g. a plain text file).
 */
@ConsistentCopyVisibility
data class Chapter private constructor(
    val index: Int,
    val title: String?,
    val blocks: List<Block>,
) {
    companion object {
        /** @return the chapter, or [DocumentModelError.NegativeValue] if [index] is negative. */
        fun create(
            index: Int,
            title: String?,
            blocks: List<Block>,
        ): Result<Chapter> =
            failureForFirstNegative("index" to index)
                ?: Result.success(Chapter(index, title, blocks.toList()))
    }
}
