package dev.d1ito.dtox.pagination

import dev.d1ito.dtox.domain.AnchorRange

/**
 * The parts of [ranges] drawn in each fragment of this page of chapter [chapterIndex], as
 * [drawnRangeOf] finds them: a range may start on an earlier page, end on a later one or lie in
 * another chapter, and its end is exclusive. Overlapping or touching ranges are merged, so each
 * character is shaded once.
 *
 * @return for each fragment index that shows part of some range, its drawn offsets sorted and
 *   disjoint; fragments that show none of them are absent.
 */
fun Page.drawnRangesOf(
    chapterIndex: Int,
    ranges: List<AnchorRange>,
): Map<Int, List<IntRange>> =
    fragments.indices
        .associateWith { fragmentIndex ->
            mergedRanges(
                ranges.mapNotNull { range ->
                    drawnRangeOf(fragmentIndex, chapterIndex, range)
                },
            )
        }.filterValues { drawn -> drawn.isNotEmpty() }

/**
 * What to shade in one fragment that shows saved highlights and maybe part of the live selection,
 * both drawn in the same color: the selection toggles the shade, so where it covers a highlight
 * that text is left unshaded and the selection stays visible over it.
 *
 * @param highlighted drawn offsets of the saved highlights, sorted and disjoint.
 * @param selected drawn offsets of the selection, or `null` when the fragment shows none.
 * @return the offsets to shade, sorted and disjoint.
 */
fun highlightShades(
    highlighted: List<IntRange>,
    selected: IntRange?,
): List<IntRange> {
    if (selected == null || selected.isEmpty()) return highlighted
    val highlightedOutsideSelection = highlighted.flatMap { range -> range.without(selected) }
    val selectedOutsideHighlights =
        highlighted.fold(listOf(selected)) { remaining, range ->
            remaining.flatMap { part -> part.without(range) }
        }
    return mergedRanges(highlightedOutsideSelection + selectedOutsideHighlights)
}

/** @return [ranges] sorted, with the ones that overlap or touch joined into one. */
private fun mergedRanges(ranges: List<IntRange>): List<IntRange> =
    ranges
        .filterNot(IntRange::isEmpty)
        .sortedBy(IntRange::first)
        .fold(emptyList<IntRange>()) { merged, range ->
            val previous = merged.lastOrNull()
            if (previous != null && range.first <= previous.last + 1) {
                merged.dropLast(1) + listOf(previous.first..maxOf(previous.last, range.last))
            } else {
                merged + listOf(range)
            }
        }

/** @return the parts of this range outside [other]: none, one or two. */
private fun IntRange.without(other: IntRange): List<IntRange> =
    listOf(first..minOf(last, other.first - 1), maxOf(first, other.last + 1)..last)
        .filterNot(IntRange::isEmpty)
