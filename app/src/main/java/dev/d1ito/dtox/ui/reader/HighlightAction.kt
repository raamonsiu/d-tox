package dev.d1ito.dtox.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.d1ito.dtox.R
import dev.d1ito.dtox.ui.theme.DTOXTheme

/** Minimum touch target, as every reader control (docs/04-design.md: zonas táctiles ≥ 48 dp). */
private val minimumTouchTarget = 48.dp

private val dividerThickness = 1.dp

/** Height of [HighlightBar]: its touch target and the line between it and the text. */
internal val highlightBarHeight: Dp = minimumTouchTarget + dividerThickness

/** Where [HighlightBar] goes on the page area. */
enum class HighlightBarPlacement { BOTTOM, TOP }

/**
 * Chooses where the «Resaltar» bar goes so it does not cover the selected text: at the bottom of
 * the page area, unless the selection reaches into that band and the band at the top is free. A
 * selection that reaches into both leaves the bar at the bottom.
 *
 * @param selectedSpan top and bottom, in pixels from the top of the page area, of the lines that
 *   show the selection; `null` when unknown, which keeps the bar at the bottom.
 * @param pageHeight height of the page area in pixels.
 * @param barHeight height of the bar in pixels.
 */
fun highlightBarPlacement(
    selectedSpan: ClosedFloatingPointRange<Float>?,
    pageHeight: Float,
    barHeight: Float,
): HighlightBarPlacement {
    val coversBottomBand =
        selectedSpan != null && selectedSpan.endInclusive > pageHeight - barHeight
    val topBandFree = selectedSpan != null && selectedSpan.start >= barHeight
    val movesToTop = coversBottomBand && topBandFree
    return if (movesToTop) HighlightBarPlacement.TOP else HighlightBarPlacement.BOTTOM
}

/**
 * A strip of the page background with one plain text action, «Resaltar», at its end and a
 * divider line on the side of the text (RF-20). No accent color, icon, toolbar or animation: it
 * appears and disappears with the selection. Taps on its empty part do nothing, so they neither
 * clear the selection nor turn the page.
 *
 * @param placement whether it sits at the bottom or at the top of the page area.
 * @param onHighlight highlights the selected text.
 */
@Composable
internal fun HighlightBar(
    placement: HighlightBarPlacement,
    modifier: Modifier = Modifier,
    onHighlight: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(DTOXTheme.palette.background)
                .pointerInput(Unit) { detectTapGestures {} },
    ) {
        if (placement == HighlightBarPlacement.BOTTOM) BarDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = onHighlight,
                modifier =
                    Modifier.sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget),
            ) {
                Text(
                    text = stringResource(R.string.reader_highlight),
                    color = DTOXTheme.palette.text,
                )
            }
        }
        if (placement == HighlightBarPlacement.TOP) BarDivider()
    }
}

@Composable
private fun BarDivider() {
    HorizontalDivider(thickness = dividerThickness, color = DTOXTheme.palette.divider)
}
