package dev.d1ito.dtox.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.ui.theme.DTOXTheme

private val screenMargin = 24.dp
private val rowVerticalPadding = 12.dp

/** Minimum touch target of each row (docs/04-design.md: zonas táctiles ≥ 48 dp). */
private val minimumTouchTarget = 48.dp

/** Marks the current chapter next to its weight, so it is not told by color alone. */
private const val CURRENT_CHAPTER_MARK = "· "

/**
 * The book's chapters as a plain text list (RF-16). Untitled chapters read "Capítulo N". The
 * current one is bold and marked, never colored, and is announced as selected by TalkBack.
 *
 * @param chapters the book's chapters in reading order.
 * @param currentIndex index of the chapter being read.
 * @param onSelect opens the chosen chapter.
 */
@Composable
fun ChapterList(
    chapters: List<Chapter>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(DTOXTheme.palette.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = screenMargin),
    ) {
        Text(
            text = stringResource(R.string.chapter_list_title),
            style = MaterialTheme.typography.titleLarge,
            color = DTOXTheme.palette.text,
            modifier = Modifier.padding(vertical = screenMargin),
        )
        LazyColumn(
            state =
                rememberLazyListState(
                    initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0),
                ),
        ) {
            itemsIndexed(chapters, key = { _, chapter -> chapter.index }) { index, chapter ->
                ChapterRow(chapter, isCurrent = index == currentIndex) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val title = chapter.title ?: stringResource(R.string.chapter_untitled, chapter.index + 1)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minimumTouchTarget)
                .semantics { selected = isCurrent }
                .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = if (isCurrent) CURRENT_CHAPTER_MARK + title else title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = DTOXTheme.palette.text,
            modifier = Modifier.padding(vertical = rowVerticalPadding),
        )
        HorizontalDivider(color = DTOXTheme.palette.divider)
    }
}
