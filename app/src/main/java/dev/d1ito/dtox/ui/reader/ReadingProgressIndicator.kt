package dev.d1ito.dtox.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.d1ito.dtox.R
import dev.d1ito.dtox.ui.theme.DTOXTheme

/**
 * Text of the faint progress indicator (RF-18, docs/04-design.md): the page of the chapter and
 * the chapter's page count, e.g. `12 / 148`.
 *
 * @param pageIndex zero-based index of the page shown; clamped into `0 until pageCount`.
 * @param pageCount pages in the chapter; at least one page is assumed.
 * @return the one-based page number, a slash and the page count.
 */
fun chapterPageLabel(
    pageIndex: Int,
    pageCount: Int,
): String {
    val count = pageCount.coerceAtLeast(1)
    val pageNumber = pageIndex.coerceIn(0, count - 1) + 1
    return "$pageNumber / $count"
}

/**
 * Shows the settled page of [pagerState] as `12 / 148` in `textMuted`, centered in the bottom
 * margin of the page. It sits below the page area instead of inside it, so the viewport and the
 * pagination do not change; it holds no pointer input, so taps and swipes reach the pager.
 *
 * @param pagerState pager of the chapter being read; its page count is the chapter's.
 * @param bottomMargin height of the empty band below the page area, where the text is centered.
 */
@Composable
fun ChapterPageIndicator(
    pagerState: PagerState,
    bottomMargin: Dp,
) {
    val pageNumber = pagerState.settledPage + 1
    val description =
        stringResource(R.string.reader_progress_description, pageNumber, pagerState.pageCount)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = chapterPageLabel(pagerState.settledPage, pagerState.pageCount),
            color = DTOXTheme.palette.textMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier =
                Modifier
                    .offset(y = bottomMargin)
                    .height(bottomMargin)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .semantics { contentDescription = description },
        )
    }
}
