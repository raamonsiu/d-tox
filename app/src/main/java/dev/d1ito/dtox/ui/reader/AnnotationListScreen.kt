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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private val screenMargin = 24.dp
private val rowVerticalPadding = 12.dp

/** Minimum touch target of each row (docs/04-design.md: zonas táctiles ≥ 48 dp). */
private val minimumTouchTarget = 48.dp

/** Lines of a long highlight shown in its row; the whole text is on its page. */
private const val EXCERPT_MAXIMUM_LINES = 3

/**
 * The marks and notes of the open book as a plain text list in reading order (RF-23). Each row
 * shows its type and chapter, the excerpt it points at and the note, if any; tapping it opens
 * that page. No accent color, no animation.
 *
 * @param book the open book.
 * @param annotations its annotations in reading order; `null` while they cannot be read, and
 *   then only the title shows.
 * @param rowsDispatcher where the rows are built, since anchors are relocated in the book's
 *   text; never the main thread.
 * @param onSelect opens the reader at the chosen annotation.
 * @param actions shown under the title, before the list (e.g. exporting the annotations).
 */
@Suppress("LongParameterList") // The list's data and callbacks plus the standard slots.
@Composable
fun AnnotationListScreen(
    book: Book,
    annotations: List<BookAnnotation>?,
    rowsDispatcher: CoroutineDispatcher,
    onSelect: (Anchor) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val rows by produceState<List<AnnotationRow>?>(initialValue = null, book, annotations) {
        value =
            annotations?.let { stored ->
                withContext(rowsDispatcher) { book.annotationRows(stored) }
            }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(DTOXTheme.palette.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = screenMargin),
    ) {
        Text(
            text = stringResource(R.string.annotations_list_title),
            style = MaterialTheme.typography.titleLarge,
            color = DTOXTheme.palette.text,
            modifier = Modifier.padding(vertical = screenMargin),
        )
        actions()
        rows?.let { shown -> AnnotationRows(shown, onSelect) }
    }
}

/** The rows, or one sentence when there are none. */
@Composable
private fun AnnotationRows(
    rows: List<AnnotationRow>,
    onSelect: (Anchor) -> Unit,
) {
    if (rows.isEmpty()) {
        Text(
            text = stringResource(R.string.annotations_list_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = DTOXTheme.palette.textMuted,
            modifier = Modifier.padding(vertical = rowVerticalPadding),
        )
        return
    }
    LazyColumn {
        items(rows, key = { row -> row.identifier.value }) { row ->
            AnnotationRowItem(row) { onSelect(row.target) }
        }
    }
}

@Composable
private fun AnnotationRowItem(
    row: AnnotationRow,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minimumTouchTarget)
                .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(modifier = Modifier.padding(vertical = rowVerticalPadding)) {
            AnnotationHeading(row)
            AnnotationTexts(row)
        }
        HorizontalDivider(color = DTOXTheme.palette.divider)
    }
}

/** The excerpt the annotation points at, in «», and the user's note under it, if any. */
@Composable
private fun AnnotationTexts(row: AnnotationRow) {
    row.excerpt?.let { excerpt ->
        Text(
            text = stringResource(R.string.annotation_row_excerpt, excerpt),
            style = MaterialTheme.typography.bodyLarge,
            color = DTOXTheme.palette.text,
            maxLines = EXCERPT_MAXIMUM_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
    row.note?.let { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.bodyLarge,
            color = DTOXTheme.palette.text,
        )
    }
}

/** The row's type and chapter, muted; untitled chapters read "Capítulo N". */
@Composable
private fun AnnotationHeading(row: AnnotationRow) {
    val chapterTitle =
        row.chapterTitle ?: stringResource(R.string.chapter_untitled, row.target.chapter + 1)
    Text(
        text =
            stringResource(
                R.string.annotation_row_heading,
                stringResource(kindLabel(row.kind)),
                chapterTitle,
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = DTOXTheme.palette.textMuted,
    )
}

private fun kindLabel(kind: AnnotationKind): Int =
    when (kind) {
        AnnotationKind.BOOKMARK -> R.string.annotation_kind_bookmark
        AnnotationKind.HIGHLIGHT -> R.string.annotation_kind_highlight
        AnnotationKind.NOTE -> R.string.annotation_kind_note
    }
