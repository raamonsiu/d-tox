package dev.d1ito.dtox.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.domain.covers
import dev.d1ito.dtox.pagination.Page
import dev.d1ito.dtox.pagination.PagePosition
import dev.d1ito.dtox.pagination.anchorAt
import dev.d1ito.dtox.pagination.anchorRangeBetween
import dev.d1ito.dtox.pagination.drawnRangeOf
import dev.d1ito.dtox.pagination.drawnRangesOf
import dev.d1ito.dtox.pagination.extendedTo
import dev.d1ito.dtox.pagination.highlightShades
import dev.d1ito.dtox.pagination.length
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

/**
 * [PageContent] where a long press selects the word under the finger and dragging without lifting
 * extends the selection, also across the blocks of the page. The selection is kept as an
 * [AnchorRange] and drawn from it in the theme highlight, without handles, toolbar or animation.
 * While text is selected a tap only clears it; turning the page drops it.
 *
 * @param highlights saved highlights of the book, in any chapter; the parts on this page are
 *   shaded the same way as the selection, which toggles that shade where it covers them.
 * @param onSelectionChange receives each new non-empty selection, or `null` when it is cleared.
 * @param onHighlight highlights a selection; while text is selected it is offered as «Resaltar»
 *   in a [HighlightBar] at the bottom (or top) of the page area, which fills the height given,
 *   unless the selection already lies inside one of [highlights]. Tapping it clears the selection.
 *   `null` offers no action, e.g. while annotations cannot be stored.
 */
@Suppress("LongParameterList") // Six inputs plus the conventional Compose modifier.
@Composable
internal fun SelectablePageContent(
    page: Page,
    chapter: Chapter,
    styles: ReadingStyles,
    modifier: Modifier = Modifier,
    highlights: List<AnchorRange> = emptyList(),
    onSelectionChange: (AnchorRange?) -> Unit = {},
    onHighlight: ((AnchorRange) -> Unit)? = null,
) {
    val selection = remember(page, chapter) { PageSelection(page, chapter) }
    val highlightedOnPage =
        remember(page, chapter, highlights) { page.drawnRangesOf(chapter.index, highlights) }
    val reportSelection by rememberUpdatedState(onSelectionChange)
    LaunchedEffect(selection) {
        snapshotFlow { selection.range?.takeIf { range -> range.start < range.end } }
            .drop(1)
            .collect { range -> reportSelection(range) }
    }
    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        PageContent(
            page,
            chapter,
            styles,
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates -> selection.textLayouts.page = coordinates }
                .drawsShades(selection, highlightedOnPage, DTOXTheme.palette.highlight)
                .tapClearsSelection(selection)
                .longPressSelectsText(selection),
            selection.textLayouts,
        )
        onHighlight?.let { highlight -> HighlightBarFor(selection, highlights, highlight) }
    }
}

/**
 * The «Resaltar» bar of [selection], placed where it does not cover the selected text; nothing
 * while no text is selected or the selection lies inside one of [highlights]. Tapping it passes
 * the selection to [onHighlight] and clears it.
 */
@Composable
private fun BoxWithConstraintsScope.HighlightBarFor(
    selection: PageSelection,
    highlights: List<AnchorRange>,
    onHighlight: (AnchorRange) -> Unit,
) {
    val selected = selection.range
    if (selected == null || highlights.any { highlighted -> highlighted.covers(selected) }) return
    val barHeight = with(LocalDensity.current) { highlightBarHeight.toPx() }
    val placement =
        highlightBarPlacement(selection.selectedSpan(), constraints.maxHeight.toFloat(), barHeight)
    val alignment =
        when (placement) {
            HighlightBarPlacement.BOTTOM -> Alignment.BottomCenter
            HighlightBarPlacement.TOP -> Alignment.TopCenter
        }
    HighlightBar(placement, Modifier.align(alignment)) {
        onHighlight(selected)
        selection.clear()
    }
}

/**
 * Where the text of each fragment of one drawn page was laid out. [PageContent] fills it; it is
 * plain memory, not state, because every new layout is followed by a new draw anyway.
 */
class PageTextLayouts {
    private val layouts = mutableMapOf<Int, TextLayoutResult>()
    private val placements = mutableMapOf<Int, LayoutCoordinates>()

    /** Coordinates of the page itself, the space of touch positions and of the drawing. */
    var page: LayoutCoordinates? = null

    /** @return what records the text layout of the fragment at [fragmentIndex] of the page. */
    fun recorderOf(fragmentIndex: Int): FragmentTextRecorder =
        FragmentTextRecorder(
            onLayout = { layout -> layouts[fragmentIndex] = layout },
            onPlacement = { coordinates -> placements[fragmentIndex] = coordinates },
        )

    /** @return every fragment text laid out and attached, with its origin on the page. */
    fun placedTexts(): List<PlacedText> {
        val pageCoordinates = page?.takeIf { coordinates -> coordinates.isAttached }
        return layouts.mapNotNull { (fragmentIndex, layout) ->
            val placement = placements[fragmentIndex]?.takeIf { it.isAttached }
            val origin = placement?.let { pageCoordinates?.localPositionOf(it, Offset.Zero) }
            origin?.let { PlacedText(fragmentIndex, layout, origin) }
        }
    }
}

/** Receives the layout and the placement of one fragment's text while [PageContent] draws it. */
class FragmentTextRecorder(
    val onLayout: (TextLayoutResult) -> Unit,
    val onPlacement: (LayoutCoordinates) -> Unit,
)

/** Records the placement of a fragment's text, if [recorder] asks for it. */
internal fun Modifier.recordsPlacement(recorder: FragmentTextRecorder?): Modifier =
    if (recorder == null) this else onGloballyPositioned(recorder.onPlacement)

/** A fragment's text layout with its [origin] in page coordinates. */
class PlacedText(
    val fragmentIndex: Int,
    val layout: TextLayoutResult,
    val origin: Offset,
) {
    /** Vertical distance from [point] to this text; 0 inside it. */
    fun verticalDistanceTo(point: Offset): Float {
        val top = origin.y
        val bottom = origin.y + layout.size.height
        return if (point.y in top..bottom) 0f else minOf(abs(point.y - top), abs(point.y - bottom))
    }

    /** Offset in the drawn text of the character boundary nearest to [point]. */
    fun offsetAt(point: Offset): Int = layout.getOffsetForPosition(point - origin)

    /** Top and bottom, in page coordinates, of the lines that show the drawn offsets [drawn]. */
    fun linesSpanOf(drawn: IntRange): ClosedFloatingPointRange<Float> {
        val top = layout.getLineTop(layout.getLineForOffset(drawn.first))
        val bottom = layout.getLineBottom(layout.getLineForOffset(drawn.last))
        return (origin.y + top)..(origin.y + bottom)
    }
}

/**
 * The selection on one page: the word picked by the long press and the range grown from it by
 * dragging. Touch positions go to the nearest fragment text, so a finger in the gap between two
 * blocks still selects.
 */
private class PageSelection(
    val page: Page,
    val chapter: Chapter,
) {
    val textLayouts = PageTextLayouts()
    private var pressedWord: AnchorRange? = null

    /** The selected text; `null` when nothing is selected. */
    var range by mutableStateOf<AnchorRange?>(null)
        private set

    /** Selects the word nearest to [point], or nothing if the page has no text there. */
    fun selectWordAt(point: Offset) {
        val placed = nearestTextTo(point) ?: return
        val length = page.fragments.getOrNull(placed.fragmentIndex)?.length ?: return
        val word = placed.layout.getWordBoundary(placed.offsetAt(point).coerceAtMost(length))
        val start = PagePosition(placed.fragmentIndex, word.start.coerceAtMost(length))
        val end = PagePosition(placed.fragmentIndex, word.end.coerceAtMost(length))
        pressedWord = page.anchorRangeBetween(chapter, start, end).getOrNull()
        range = pressedWord
    }

    /** Grows the word first selected up to the character boundary nearest to [point]. */
    fun extendTo(point: Offset) {
        val word = pressedWord ?: return
        val reached = positionAt(point)?.let { position -> page.anchorAt(chapter, position) }
        val extended = reached?.getOrNull()?.let { anchor -> word.extendedTo(anchor) }
        range = extended?.getOrNull() ?: range
    }

    /**
     * @return the character boundary nearest to [point], limited to the fragment's own text (a
     *   separator draws glyphs that are not in its block), or `null` if the page has no text.
     */
    private fun positionAt(point: Offset): PagePosition? {
        val placed = nearestTextTo(point) ?: return null
        val length = page.fragments.getOrNull(placed.fragmentIndex)?.length ?: return null
        return PagePosition(placed.fragmentIndex, placed.offsetAt(point).coerceAtMost(length))
    }

    fun clear() {
        pressedWord = null
        range = null
    }

    /**
     * @return top and bottom, in page coordinates, of the lines that show the selection; `null`
     *   when nothing is selected or its text is not laid out.
     */
    fun selectedSpan(): ClosedFloatingPointRange<Float>? {
        val selected = range ?: return null
        val spans =
            textLayouts.placedTexts().mapNotNull { placed ->
                page
                    .drawnRangeOf(placed.fragmentIndex, chapter.index, selected)
                    ?.let(placed::linesSpanOf)
            }
        if (spans.isEmpty()) return null
        return spans.minOf { span -> span.start }..spans.maxOf { span -> span.endInclusive }
    }

    private fun nearestTextTo(point: Offset): PlacedText? =
        textLayouts.placedTexts().minByOrNull { placed -> placed.verticalDistanceTo(point) }
}

/** A long press selects a word and the drag that follows extends it; both are consumed. */
private fun Modifier.longPressSelectsText(selection: PageSelection): Modifier =
    pointerInput(selection) {
        detectDragGesturesAfterLongPress(
            onDragStart = selection::selectWordAt,
            onDrag = { change, _ -> selection.extendTo(change.position) },
        )
    }

/**
 * While text is selected, a tap clears it and is consumed, so it neither turns the page nor shows
 * the controls. Placed before [longPressSelectsText], it sees events after it: the release that
 * ends a long press arrives already consumed and does not clear the new selection.
 */
private fun Modifier.tapClearsSelection(selection: PageSelection): Modifier =
    pointerInput(selection) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val release = waitForUpOrCancellation() ?: return@awaitEachGesture
            if (selection.range != null) {
                release.consume()
                selection.clear()
            }
        }
    }

/**
 * Shades, behind the text of every fragment, the saved highlights and the selected part in
 * [highlight], as [highlightShades] combines them.
 *
 * @param highlightedOnPage drawn offsets of the saved highlights, by fragment index.
 */
private fun Modifier.drawsShades(
    selection: PageSelection,
    highlightedOnPage: Map<Int, List<IntRange>>,
    highlight: Color,
): Modifier =
    drawBehind {
        val range = selection.range
        selection.textLayouts.placedTexts().forEach { placed ->
            val selected =
                range?.let { selectedRange ->
                    selection.page.drawnRangeOf(
                        placed.fragmentIndex,
                        selection.chapter.index,
                        selectedRange,
                    )
                }
            val highlighted = highlightedOnPage[placed.fragmentIndex].orEmpty()
            drawShades(placed, highlightShades(highlighted, selected), highlight)
        }
    }

/** Fills the text of [placed] at each of the drawn offsets in [shades]. */
private fun DrawScope.drawShades(
    placed: PlacedText,
    shades: List<IntRange>,
    color: Color,
) = translate(placed.origin.x, placed.origin.y) {
    shades.forEach { shade ->
        drawPath(placed.layout.getPathForRange(shade.first, shade.last + 1), color)
    }
}
