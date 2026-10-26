package dev.d1ito.dtox.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.d1ito.dtox.R
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long the controls stay without being used (docs/04-design.md: "se oculta sola"). */
val READER_CONTROLS_AUTO_HIDE: Duration = 4.seconds

private val barHorizontalPadding = 8.dp

/** Minimum touch target for every control (docs/04-design.md: zonas táctiles ≥ 48 dp). */
private val minimumTouchTarget = 48.dp

/**
 * What the reader controls can do. A `null` action has no button, so the bar never shows a
 * button that does nothing.
 *
 * @property onBack closes the book and returns to the library.
 * @property onChapters opens the chapter list.
 * @property onTextSettings opens the text settings.
 */
class ReaderControlActions(
    val onBack: () -> Unit,
    val onChapters: (() -> Unit)? = null,
    val onTextSettings: (() -> Unit)? = null,
)

/**
 * Minimal bar over the top of the page, shown after a tap in the center of the page. It appears
 * and disappears without animation (only the page turn animates) and hides by itself after
 * [READER_CONTROLS_AUTO_HIDE]. Buttons are text, with the standard 48 dp touch target.
 *
 * @param visible whether the bar is shown.
 * @param actions what each button does.
 * @param onHide asks the owner to hide the bar, after the timeout or once an action runs.
 */
@Composable
fun ReaderControlsBar(
    visible: Boolean,
    actions: ReaderControlActions,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    LaunchedEffect(Unit) {
        delay(READER_CONTROLS_AUTO_HIDE)
        onHide()
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(DTOXTheme.palette.background)
                // Taps on the bar's empty space must not reach the page and turn it.
                .pointerInput(Unit) { detectTapGestures {} }
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = barHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ControlButton(R.string.reader_controls_back, actions.onBack, onHide)
            actions.onChapters?.let { open ->
                ControlButton(R.string.reader_controls_chapters, open, onHide)
            }
            actions.onTextSettings?.let { open ->
                ControlButton(R.string.reader_controls_text, open, onHide)
            }
        }
        HorizontalDivider(color = DTOXTheme.palette.divider)
    }
}

@Composable
private fun ControlButton(
    label: Int,
    action: () -> Unit,
    onHide: () -> Unit,
) {
    TextButton(
        onClick = {
            onHide()
            action()
        },
        modifier = Modifier.sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget),
    ) {
        Text(text = stringResource(label), color = DTOXTheme.palette.text)
    }
}
