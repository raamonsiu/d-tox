package dev.d1ito.dtox.ui.reader

import androidx.lifecycle.SavedStateHandle
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.BookIdentifier

private const val BOOK_KEY = "reader.book"
private const val CHAPTER_KEY = "reader.chapter"
private const val BLOCK_KEY = "reader.block"
private const val OFFSET_KEY = "reader.offset"

/**
 * The open book and its position in a [SavedStateHandle], so they survive rotation and process
 * death. Only plain values are stored: the identifier text and the three position numbers.
 */
internal class SavedReaderState(
    private val savedState: SavedStateHandle,
) {
    /** @return the book that was open, or `null` if none or the saved value is invalid. */
    fun bookIdentifier(): BookIdentifier? =
        savedState.get<String>(BOOK_KEY)?.let { value -> BookIdentifier.parse(value).getOrNull() }

    fun saveBook(bookIdentifier: BookIdentifier) {
        savedState[BOOK_KEY] = bookIdentifier.value
    }

    /** @return the saved position, or `null` if any part is missing or invalid. */
    fun position(): Anchor? {
        val chapter = savedState.get<Int>(CHAPTER_KEY)
        val block = savedState.get<Int>(BLOCK_KEY)
        val offset = savedState.get<Int>(OFFSET_KEY)
        if (chapter == null || block == null || offset == null) return null
        return Anchor.create(chapter, block, offset).getOrNull()
    }

    fun savePosition(anchor: Anchor) {
        savedState[CHAPTER_KEY] = anchor.chapter
        savedState[BLOCK_KEY] = anchor.block
        savedState[OFFSET_KEY] = anchor.offset
    }

    fun clearPosition() {
        listOf(CHAPTER_KEY, BLOCK_KEY, OFFSET_KEY).forEach { key -> savedState.remove<Int>(key) }
    }

    /** Forgets the book and its position. */
    fun clear() {
        savedState.remove<String>(BOOK_KEY)
        clearPosition()
    }
}
