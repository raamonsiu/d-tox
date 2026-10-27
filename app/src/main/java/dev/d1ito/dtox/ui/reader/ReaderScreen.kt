package dev.d1ito.dtox.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.domain.PageMargins
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.pagination.Page
import dev.d1ito.dtox.pagination.Paginator
import dev.d1ito.dtox.pagination.Viewport
import dev.d1ito.dtox.pagination.pageIndexOf
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext

/** Page turn duration from docs/04-design.md (~200–250 ms). */
internal const val PAGE_TURN_MILLIS = 225

private val pageVerticalMargin = 32.dp

/** Keeps the reading column narrow on wide screens (docs/04-design.md: ≈ 55–70 characters). */
private val readingColumnMaxWidth = 600.dp

/**
 * Full screen reader for one chapter: only text, paginated off the main thread, turned by swipe
 * or by tapping the left/right edges. It opens at [position] and reports every page turned to,
 * so whoever owns the position keeps it across rotation and new paginations.
 *
 * @param chapter chapter to read.
 * @param position where to open, inside [chapter], and who receives the page turns.
 * @param paginationDispatcher where pages are measured; never the main thread.
 * @param settings text size, line spacing and margins (changing them repaginates and reopens
 *   at the page holding [ReadingPosition.restoredAnchor]) and whether page turns slide.
 */
@Suppress("LongParameterList") // Four inputs plus the conventional Compose modifier.
@Composable
fun ReaderScreen(
    chapter: Chapter,
    position: ReadingPosition,
    paginationDispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier,
    settings: ReadingSettings = ReadingSettings.Default,
) {
    val styles = rememberReadingStyles(settings)
    BoxWithConstraints(
        modifier = modifier.readerPageArea(horizontalMarginOf(settings.margins)),
        contentAlignment = Alignment.TopCenter,
    ) {
        val columnWidth = min(maxWidth, readingColumnMaxWidth)
        val pages =
            viewportOf(columnWidth)?.let { size ->
                paginatedPages(chapter, size, styles, paginationDispatcher)
                    .value
                    ?.pagesFor(chapter, size, styles)
            }
        when {
            pages == null -> Unit // Still paginating: keep the empty page background.
            pages.isFailure -> ReaderMessage(stringResource(R.string.reader_chapter_unavailable))
            else ->
                ChapterPager(
                    PaginatedChapter(chapter, pages.getOrThrow(), columnWidth, styles),
                    position,
                    settings.pageAnimation,
                )
        }
    }
}

/** Side margin of the page for each [PageMargins] choice. */
private fun horizontalMarginOf(margins: PageMargins): Dp =
    when (margins) {
        PageMargins.NARROW -> 16.dp
        PageMargins.NORMAL -> 24.dp
        PageMargins.WIDE -> 40.dp
    }

/** The page area in pixels for a column of [columnWidth], or `null` if it is empty. */
@Composable
private fun BoxWithConstraintsScope.viewportOf(columnWidth: Dp): Viewport? =
    with(LocalDensity.current) {
        Viewport.create(columnWidth.roundToPx(), maxHeight.roundToPx()).getOrNull()
    }

/** Page background, room for system insets and the reading margins. */
@Composable
private fun Modifier.readerPageArea(horizontalMargin: Dp): Modifier =
    fillMaxSize()
        .background(DTOXTheme.palette.background)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = horizontalMargin, vertical = pageVerticalMargin)

/** A chapter with its pages for one column width. */
private class PaginatedChapter(
    val chapter: Chapter,
    val pages: List<Page>,
    val columnWidth: Dp,
    val styles: ReadingStyles,
)

/**
 * @property restoredAnchor position to open at, e.g. after rotation; `null` opens the first page.
 * @property onPageSettled receives the start anchor of every page the reader turns to.
 * @property onNextChapter called when the reader turns forward from the last page.
 * @property onPreviousChapter called when the reader turns back from the first page.
 * @property onCenterTap called for a tap in the center of the page (between the edge zones).
 */
class ReadingPosition(
    val restoredAnchor: Anchor?,
    val onPageSettled: (Anchor) -> Unit,
    val onNextChapter: () -> Unit = {},
    val onPreviousChapter: () -> Unit = {},
    val onCenterTap: () -> Unit = {},
)

/**
 * Pages computed for one chapter and viewport. Kept together because a new chapter or viewport
 * is paginated in the background: until then the previous result is still there, and its pages
 * must never be drawn with the new chapter's text (their offsets would not match).
 */
private data class ChapterPagination(
    private val chapter: Chapter,
    private val viewport: Viewport,
    private val styles: ReadingStyles,
    private val pages: Result<List<Page>>,
) {
    /** @return the pages if computed for this chapter, viewport and styles, else `null`. */
    fun pagesFor(
        current: Chapter,
        currentViewport: Viewport,
        currentStyles: ReadingStyles,
    ): Result<List<Page>>? =
        pages.takeIf {
            chapter === current && viewport == currentViewport && styles === currentStyles
        }
}

/** Paginates [chapter] on [dispatcher]; `null` until the first result is ready. */
@Composable
private fun paginatedPages(
    chapter: Chapter,
    viewport: Viewport,
    styles: ReadingStyles,
    dispatcher: CoroutineDispatcher,
): State<ChapterPagination?> {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return produceState<ChapterPagination?>(
        initialValue = null,
        chapter,
        viewport,
        styles,
        density,
        dispatcher,
    ) {
        val measurer = ComposeBlockMeasurer(textMeasurer, styles, density)
        val pages = withContext(dispatcher) { Paginator.paginate(chapter, viewport, measurer) }
        value = ChapterPagination(chapter, viewport, styles, pages)
    }
}

@Composable
private fun ChapterPager(
    paginated: PaginatedChapter,
    position: ReadingPosition,
    pageAnimationSetting: Boolean,
) {
    val pages = paginated.pages
    // New pages (rotation, font change) need a new pager opened at the anchor's page. The pager
    // state is not saveable on purpose: the anchor, not a stale page number, decides the page.
    key(pages) {
        val initialPage = position.restoredAnchor?.let { anchor -> pageIndexOf(pages, anchor) } ?: 0
        val pagerState = remember { PagerState(currentPage = initialPage) { pages.size } }
        val animationsEnabled = rememberPageTurnAnimates(pageAnimationSetting)
        ReportSettledPages(pagerState, pages, position.onPageSettled)
        HorizontalPager(
            state = pagerState,
            flingBehavior =
                PagerDefaults.flingBehavior(
                    pagerState,
                    snapAnimationSpec = pageTurnSpec(animationsEnabled),
                ),
            modifier =
                Modifier.fillMaxSize().pageTurnGestures(
                    pagerState,
                    animationsEnabled,
                    position,
                ),
        ) { pageIndex -> PageAt(paginated, pageIndex) }
    }
}

/** One page of [paginated], centered in the reading column. */
@Composable
private fun PageAt(
    paginated: PaginatedChapter,
    pageIndex: Int,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        PageContent(
            paginated.pages[pageIndex],
            paginated.chapter,
            paginated.styles,
            Modifier.width(paginated.columnWidth),
        )
    }
}

/** Calls [onPageSettled] with the start anchor of each page the reader turns to after opening. */
@Composable
private fun ReportSettledPages(
    pagerState: PagerState,
    pages: List<Page>,
    onPageSettled: (Anchor) -> Unit,
) {
    LaunchedEffect(pagerState, pages) {
        // The first value is the page just restored, not a page turn: reporting it would move the
        // anchor to that page's start and drift a little on every rotation.
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            onPageSettled(pages[page].startAnchor)
        }
    }
}

/** A short centered message in the reading style, e.g. when a chapter cannot be shown. */
@Composable
fun ReaderMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = DTOXTheme.palette.textMuted)
    }
}
