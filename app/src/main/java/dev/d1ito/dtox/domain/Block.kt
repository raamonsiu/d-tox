package dev.d1ito.dtox.domain

/**
 * One structural unit of a chapter (docs/02-data-formats.md §1). Text is plain, already
 * normalized; positions inside it are UTF-16 `Char` offsets. Images, tables and CSS are dropped
 * by the parsers in the MVP.
 */
sealed interface Block {
    /**
     * A title inside the chapter.
     *
     * @property level 1 (book/chapter title) to 3.
     */
    @ConsistentCopyVisibility
    data class Heading private constructor(
        val level: Int,
        val text: String,
    ) : Block {
        companion object {
            const val HIGHEST_LEVEL = 1
            const val LOWEST_LEVEL = 3

            /** @return the heading, or [DocumentModelError.HeadingLevelOutOfRange]. */
            fun create(
                level: Int,
                text: String,
            ): Result<Heading> =
                if (level in HIGHEST_LEVEL..LOWEST_LEVEL) {
                    Result.success(Heading(level, text))
                } else {
                    Result.failure(DocumentModelError.HeadingLevelOutOfRange(level))
                }
        }
    }

    /** Running text. */
    data class Paragraph(
        val text: String,
    ) : Block

    /** Quoted text, shown indented and muted. */
    data class Quote(
        val text: String,
    ) : Block

    /**
     * One item of a list.
     *
     * @property ordered whether the list is numbered.
     * @property depth nesting level, 0 for a top-level item.
     */
    @ConsistentCopyVisibility
    data class ListItem private constructor(
        val text: String,
        val ordered: Boolean,
        val depth: Int,
    ) : Block {
        companion object {
            /** @return the item, or [DocumentModelError.NegativeValue] if [depth] is negative. */
            fun create(
                text: String,
                ordered: Boolean,
                depth: Int,
            ): Result<ListItem> =
                failureForFirstNegative("depth" to depth)
                    ?: Result.success(ListItem(text, ordered, depth))
        }
    }

    /** A thematic break between sections. */
    data object Separator : Block
}
