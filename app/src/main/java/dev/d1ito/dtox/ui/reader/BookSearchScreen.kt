package dev.d1ito.dtox.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.MAXIMUM_SEARCH_RESULTS
import dev.d1ito.dtox.domain.SearchResult
import dev.d1ito.dtox.domain.search
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private val screenMargin = 24.dp
private val rowVerticalPadding = 12.dp
private val fieldVerticalPadding = 8.dp

/** Minimum touch target of each row (docs/04-design.md: zonas táctiles ≥ 48 dp). */
private val minimumTouchTarget = 48.dp

/** Pause after the last keystroke before searching, so typing does not search every letter. */
private val TYPING_PAUSE = 300.milliseconds

/**
 * The results of searching [query], kept together so results of an older query are never
 * described as the current one's.
 */
private class SearchOutcome(
    val query: String,
    val results: List<SearchResult>,
)

/**
 * Plain search inside the open book (RF-19): a text field and the occurrences found, each with
 * its chapter and an excerpt where the occurrence is bold, never colored. Searching runs on
 * [searchDispatcher] while the reader types.
 *
 * @param book the open book.
 * @param searchDispatcher where the text is searched; never the main thread.
 * @param onSelect opens the reader at the chosen occurrence.
 */
@Composable
fun BookSearchScreen(
    book: Book,
    searchDispatcher: CoroutineDispatcher,
    onSelect: (Anchor) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val outcome by produceState<SearchOutcome?>(initialValue = null, book, query) {
        if (query.isNotBlank()) delay(TYPING_PAUSE)
        value = SearchOutcome(query, withContext(searchDispatcher) { book.search(query) })
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
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.titleLarge,
            color = DTOXTheme.palette.text,
            modifier = Modifier.padding(vertical = screenMargin),
        )
        SearchField(query, onChange = { text -> query = text })
        // While the reader types, the previous results stay until the new ones arrive.
        outcome?.let { found -> SearchResults(found, isCurrent = found.query == query, onSelect) }
    }
}

/** A single line field with an underline, focused on opening; "search" hides the keyboard. */
@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
) {
    val hint = stringResource(R.string.search_field_hint)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = DTOXTheme.palette.text),
        cursorBrush = SolidColor(DTOXTheme.palette.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { contentDescription = hint },
        decorationBox = { field -> UnderlinedField(hint.takeIf { query.isEmpty() }, field) },
    )
}

/** [field] over an underline in the text color, with [hint] muted behind it while non-null. */
@Composable
private fun UnderlinedField(
    hint: String?,
    field: @Composable () -> Unit,
) {
    Column {
        Box(modifier = Modifier.padding(vertical = fieldVerticalPadding)) {
            hint?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DTOXTheme.palette.textMuted,
                )
            }
            field()
        }
        HorizontalDivider(color = DTOXTheme.palette.text)
    }
}

/**
 * The occurrences, and a short note when there are none or only the first ones are shown.
 *
 * @param isCurrent whether [outcome] answers the query in the field; only then the note shows.
 */
@Composable
private fun SearchResults(
    outcome: SearchOutcome,
    isCurrent: Boolean,
    onSelect: (Anchor) -> Unit,
) {
    val results = outcome.results
    val note =
        when {
            !isCurrent || outcome.query.isBlank() -> null
            results.isEmpty() -> stringResource(R.string.search_no_results)
            results.size >= MAXIMUM_SEARCH_RESULTS ->
                stringResource(R.string.search_results_capped, MAXIMUM_SEARCH_RESULTS)
            else -> null
        }
    note?.let { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = DTOXTheme.palette.textMuted,
            modifier = Modifier.padding(vertical = rowVerticalPadding),
        )
    }
    LazyColumn {
        items(results) { result -> ResultRow(result) { onSelect(result.anchor) } }
    }
}

@Composable
private fun ResultRow(
    result: SearchResult,
    onClick: () -> Unit,
) {
    val chapterTitle =
        result.chapterTitle
            ?: stringResource(R.string.chapter_untitled, result.anchor.chapter + 1)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minimumTouchTarget)
                .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(modifier = Modifier.padding(vertical = rowVerticalPadding)) {
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = DTOXTheme.palette.textMuted,
            )
            Text(
                text = excerptWithOccurrenceInBold(result),
                style = MaterialTheme.typography.bodyLarge,
                color = DTOXTheme.palette.text,
            )
        }
        HorizontalDivider(color = DTOXTheme.palette.divider)
    }
}

private fun excerptWithOccurrenceInBold(result: SearchResult) =
    buildAnnotatedString {
        val occurrence = result.occurrenceInExcerpt
        append(result.excerpt.substring(0, occurrence.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(result.excerpt.substring(occurrence))
        }
        append(result.excerpt.substring(occurrence.last + 1))
    }
