package dev.d1ito.dtox.domain

import java.text.Normalizer
import java.util.Locale

/** Most results one search returns, so a common word cannot flood the list (RF-19). */
const val MAXIMUM_SEARCH_RESULTS = 200

/** Characters of the block shown before the occurrence in an excerpt. */
private const val EXCERPT_CHARACTERS_BEFORE = 30

/** Characters of the block shown after the occurrence in an excerpt. */
private const val EXCERPT_CHARACTERS_AFTER = 60

/** Marks where an excerpt cuts the text of its block. */
private const val EXCERPT_CUT_MARK = "…"

/** Code points below this are ASCII: folding them is only lowercasing. */
private const val ASCII_LIMIT = 0x80

/**
 * One occurrence of the searched text.
 *
 * @property anchor where the occurrence starts, with its quote (D-07).
 * @property chapterTitle title of its chapter, `null` when the chapter has none.
 * @property excerpt the occurrence with some text of its block around it; [EXCERPT_CUT_MARK]
 *   marks where the block's text is cut.
 * @property occurrenceInExcerpt the characters of the occurrence inside [excerpt].
 */
data class SearchResult(
    val anchor: Anchor,
    val chapterTitle: String?,
    val excerpt: String,
    val occurrenceInExcerpt: IntRange,
)

/**
 * Finds [query] in the text of every block (RF-19), ignoring case and accents: "corazon" finds
 * "Corazón", "pinguino" finds "pingüino" and "fin" finds the ligature "ﬁn". No index is kept;
 * the text is folded block by block until enough results are found. CPU-bound: call it off the
 * main thread.
 *
 * @param query text to find; surrounding whitespace is ignored.
 * @param maximumResults the search stops after this many results.
 * @return non overlapping occurrences in reading order, with positions in the original text;
 *   empty for a blank query.
 */
fun Book.search(
    query: String,
    maximumResults: Int = MAXIMUM_SEARCH_RESULTS,
): List<SearchResult> {
    val foldedQuery = FoldedText.foldedFrom(query.trim()).text
    if (foldedQuery.isEmpty()) return emptyList()
    return chapters
        .asSequence()
        .flatMap { chapter ->
            chapter.blocks.asSequence().withIndex().flatMap { (blockIndex, block) ->
                block.textOrNull
                    ?.let { text -> resultsInBlock(chapter, blockIndex, text, foldedQuery) }
                    .orEmpty()
            }
        }.take(maximumResults)
        .toList()
}

private fun Book.resultsInBlock(
    chapter: Chapter,
    blockIndex: Int,
    text: String,
    foldedQuery: String,
): Sequence<SearchResult> {
    val folded = FoldedText.foldedFrom(text)
    val starts =
        generateSequence(folded.text.indexOf(foldedQuery).takeIf { start -> start >= 0 }) { start ->
            folded.text.indexOf(foldedQuery, start + foldedQuery.length).takeIf { next ->
                next >= 0
            }
        }
    return starts.mapNotNull { start ->
        val occurrence = folded.originalRange(start, start + foldedQuery.length)
        anchorAt(chapter.index, blockIndex, occurrence.first).getOrNull()?.let { anchor ->
            resultOf(anchor, chapter.title, text, occurrence)
        }
    }
}

/** The result for [occurrence] of [text], with an excerpt that never splits a surrogate pair. */
private fun resultOf(
    anchor: Anchor,
    chapterTitle: String?,
    text: String,
    occurrence: IntRange,
): SearchResult {
    var start = (occurrence.first - EXCERPT_CHARACTERS_BEFORE).coerceAtLeast(0)
    if (start > 0 && text[start].isLowSurrogate()) start--
    var end = (occurrence.last + 1 + EXCERPT_CHARACTERS_AFTER).coerceAtMost(text.length)
    if (end < text.length && text[end].isLowSurrogate()) end++
    val prefix = if (start > 0) EXCERPT_CUT_MARK else ""
    val suffix = if (end < text.length) EXCERPT_CUT_MARK else ""
    val shift = prefix.length - start
    return SearchResult(
        anchor = anchor,
        chapterTitle = chapterTitle,
        excerpt = prefix + text.substring(start, end) + suffix,
        occurrenceInExcerpt = (occurrence.first + shift)..(occurrence.last + shift),
    )
}

/**
 * Text folded for search: compatibility decomposition (NFKD, which also splits ligatures such as
 * "ﬁ"), combining marks removed and lowercased without locale rules. Folding can change the
 * length, so every folded character keeps the span of original characters it comes from.
 *
 * @property text the folded text.
 */
private class FoldedText(
    val text: String,
    private val originalStarts: IntArray,
    private val originalEnds: IntArray,
) {
    /**
     * @param start first folded character, inclusive; [endExclusive] must be greater.
     * @return the original characters that fold into `text[start until endExclusive]`.
     */
    fun originalRange(
        start: Int,
        endExclusive: Int,
    ): IntRange = originalStarts[start] until originalEnds[endExclusive - 1]

    companion object {
        /** @return [original] folded, with the original span of every folded character. */
        fun foldedFrom(original: String): FoldedText {
            val buffer = FoldingBuffer(original.length)
            var index = 0
            while (index < original.length) {
                val codePoint = original.codePointAt(index)
                val next = index + Character.charCount(codePoint)
                val piece = fold(codePoint)
                // A combining mark on its own belongs to the character before it.
                if (piece.isEmpty()) {
                    buffer.extendLastPiece(
                        next,
                    )
                } else {
                    buffer.append(piece, index, next)
                }
                index = next
            }
            return buffer.toFoldedText()
        }

        private fun fold(codePoint: Int): String {
            if (codePoint < ASCII_LIMIT) return Character.toLowerCase(codePoint).toChar().toString()
            val decomposed =
                Normalizer
                    .normalize(String(Character.toChars(codePoint)), Normalizer.Form.NFKD)
                    .lowercase(Locale.ROOT)
            val withoutMarks = StringBuilder(decomposed.length)
            decomposed
                .codePoints()
                .filter { part -> Character.getType(part) != Character.NON_SPACING_MARK.toInt() }
                .forEach { part -> withoutMarks.appendCodePoint(part) }
            return withoutMarks.toString()
        }
    }
}

/** Collects folded characters and the span of original characters each one comes from. */
private class FoldingBuffer(
    capacity: Int,
) {
    private val folded = StringBuilder(capacity)
    private val originalStarts = ArrayList<Int>(capacity)
    private val originalEnds = ArrayList<Int>(capacity)
    private var lastPieceStart = 0

    /** Appends [piece], folded from the original characters [originalStart] until [originalEnd]. */
    fun append(
        piece: String,
        originalStart: Int,
        originalEnd: Int,
    ) {
        lastPieceStart = folded.length
        folded.append(piece)
        repeat(piece.length) {
            originalStarts += originalStart
            originalEnds += originalEnd
        }
    }

    /** Makes the last appended piece also cover the original characters until [originalEnd]. */
    fun extendLastPiece(originalEnd: Int) {
        for (position in lastPieceStart until originalEnds.size) {
            originalEnds[position] =
                originalEnd
        }
    }

    fun toFoldedText(): FoldedText =
        FoldedText(folded.toString(), originalStarts.toIntArray(), originalEnds.toIntArray())
}
