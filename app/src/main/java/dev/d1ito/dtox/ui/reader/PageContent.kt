package dev.d1ito.dtox.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.domain.textOrNull
import dev.d1ito.dtox.pagination.Page
import dev.d1ito.dtox.pagination.PageFragment

/**
 * Draws one page of [chapter] with the same [styles] used to measure it. Each fragment shows
 * whole lines of its block, so the text wraps exactly as measured.
 *
 * @param page page produced by the paginator for [chapter].
 * @param chapter the chapter [page] belongs to; its blocks provide the text.
 * @param styles the same styles the [ComposeBlockMeasurer] used to paginate.
 * @param modifier should give the column the width used as the viewport width.
 */
@Composable
fun PageContent(
    page: Page,
    chapter: Chapter,
    styles: ReadingStyles,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        page.fragments.forEachIndexed { position, fragment ->
            FragmentContent(fragment, chapter, styles, isFirstOnPage = position == 0)
        }
    }
}

@Composable
private fun FragmentContent(
    fragment: PageFragment,
    chapter: Chapter,
    styles: ReadingStyles,
    isFirstOnPage: Boolean,
) {
    val block = chapter.blocks[fragment.blockIndex]
    val style = styles.styleOf(block)
    val startsBlock = fragment.startOffset == 0
    if (startsBlock && !isFirstOnPage) Spacer(Modifier.height(style.spacingBefore))
    val text =
        block.textOrNull?.substring(fragment.startOffset, fragment.endOffset) ?: SEPARATOR_GLYPHS
    Box(modifier = Modifier.fillMaxWidth()) {
        if (startsBlock && style.listMarker != null) {
            BasicText(
                text = style.listMarker,
                style = style.textStyle,
                modifier = Modifier.padding(start = style.startIndent - style.listMarkerGutter),
            )
        }
        BasicText(
            text = text,
            style = style.textStyle,
            modifier = Modifier.fillMaxWidth().padding(start = style.startIndent),
        )
    }
}
