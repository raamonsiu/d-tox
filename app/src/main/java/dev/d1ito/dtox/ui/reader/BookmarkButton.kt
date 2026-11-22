package dev.d1ito.dtox.ui.reader

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.d1ito.dtox.R
import dev.d1ito.dtox.ui.theme.DTOXTheme

/** Minimum touch target, as every reader control (docs/04-design.md: zonas táctiles ≥ 48 dp). */
private val minimumTouchTarget = 48.dp

/**
 * The bookmark of the page being read, as the controls bar shows it (RF-22).
 *
 * @property bookmarked whether the page has a bookmark.
 * @property onToggle marks the page, or removes its bookmark.
 */
class BookmarkToggle(
    val bookmarked: Boolean,
    val onToggle: () -> Unit,
)

/**
 * Text button that marks or unmarks the page. The state shows in the text itself, «Marcar» or
 * «Marcada» in bold, in the text color: no accent color, no icon, no animation. Unlike the other
 * controls it leaves the bar open, so the new state can be seen before the bar hides by itself.
 */
@Composable
internal fun BookmarkButton(toggle: BookmarkToggle) {
    val label =
        if (toggle.bookmarked) {
            R.string.reader_controls_bookmarked
        } else {
            R.string.reader_controls_bookmark
        }
    TextButton(
        onClick = toggle.onToggle,
        modifier = Modifier.sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget),
    ) {
        Text(
            text = stringResource(label),
            color = DTOXTheme.palette.text,
            fontWeight = if (toggle.bookmarked) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
