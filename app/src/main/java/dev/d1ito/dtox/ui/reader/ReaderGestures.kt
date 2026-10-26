package dev.d1ito.dtox.ui.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Share of the page width on each side where a tap turns the page. */
private const val EDGE_TAP_FRACTION = 0.3f

/** Drag past the first or last page needed to change chapter, like a deliberate page turn. */
private val chapterTurnPullDistance = 48.dp

/** Swipes past the chapter edges and edge taps, on top of the pager's own swipes. */
@Composable
internal fun Modifier.pageTurnGestures(
    pagerState: PagerState,
    animationsEnabled: Boolean,
    position: ReadingPosition,
): Modifier =
    pullingPastTheEdgesTurnsChapters(pagerState, position)
        .edgeTapsTurnPages(pagerState, animationsEnabled, position)

/**
 * Tapping the left or right [EDGE_TAP_FRACTION] of the page goes to the previous or next page;
 * past the first or last page it asks [position] for the adjacent chapter.
 */
@Composable
private fun Modifier.edgeTapsTurnPages(
    pagerState: PagerState,
    animationsEnabled: Boolean,
    position: ReadingPosition,
): Modifier {
    val scope = rememberCoroutineScope()
    return pointerInput(pagerState, animationsEnabled, position) {
        detectTapGestures { tap ->
            val step =
                when {
                    tap.x < size.width * EDGE_TAP_FRACTION -> -1
                    tap.x > size.width * (1 - EDGE_TAP_FRACTION) -> 1
                    else -> 0 // The center will open the reading controls (another issue).
                }
            val target = pagerState.currentPage + step
            when {
                step == 0 -> return@detectTapGestures
                target < 0 -> return@detectTapGestures position.onPreviousChapter()
                target >= pagerState.pageCount -> return@detectTapGestures position.onNextChapter()
            }
            scope.launch {
                if (animationsEnabled) {
                    pagerState.animateScrollToPage(
                        target,
                        animationSpec = tween(PAGE_TURN_MILLIS, easing = FastOutSlowInEasing),
                    )
                } else {
                    pagerState.scrollToPage(target)
                }
            }
        }
    }
}

/**
 * Swiping past the last page opens the next chapter and past the first one the previous chapter,
 * with the same gesture as a page turn. The pager cannot move there, so the drag it leaves
 * unconsumed is added up and checked when the finger lifts.
 */
@Composable
private fun Modifier.pullingPastTheEdgesTurnsChapters(
    pagerState: PagerState,
    position: ReadingPosition,
): Modifier {
    val threshold = with(LocalDensity.current) { chapterTurnPullDistance.toPx() }
    val connection =
        remember(pagerState, position, threshold) {
            ChapterTurnConnection(pagerState, position, threshold)
        }
    return nestedScroll(connection)
}

/** Adds up the horizontal drag the pager could not consume at its first or last page. */
private class ChapterTurnConnection(
    private val pagerState: PagerState,
    private val position: ReadingPosition,
    private val threshold: Float,
) : NestedScrollConnection {
    private var unconsumedPull = 0f

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput) unconsumedPull += available.x
        return Offset.Zero
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        val pull = unconsumedPull
        unconsumedPull = 0f
        when {
            // Dragging left (negative x) moves forward, as on any page.
            pull <= -threshold && !pagerState.canScrollForward -> position.onNextChapter()
            pull >= threshold && !pagerState.canScrollBackward -> position.onPreviousChapter()
        }
        return Velocity.Zero
    }
}
