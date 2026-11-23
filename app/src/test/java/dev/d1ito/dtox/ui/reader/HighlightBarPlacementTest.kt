package dev.d1ito.dtox.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

private const val PAGE_HEIGHT = 1000f
private const val BAR_HEIGHT = 100f

class HighlightBarPlacementTest {
    @Test
    fun selectionAboveTheBottomBand_keepsTheBarAtTheBottom() {
        assertEquals(HighlightBarPlacement.BOTTOM, placementFor(0f..900f))
        assertEquals(HighlightBarPlacement.BOTTOM, placementFor(null))
    }

    @Test
    fun selectionReachingTheBottomBand_movesTheBarToTheTop() {
        assertEquals(HighlightBarPlacement.TOP, placementFor(100f..901f))
    }

    @Test
    fun selectionReachingBothBands_keepsTheBarAtTheBottom() {
        assertEquals(HighlightBarPlacement.BOTTOM, placementFor(99f..950f))
    }

    private fun placementFor(selectedSpan: ClosedFloatingPointRange<Float>?) =
        highlightBarPlacement(selectedSpan, PAGE_HEIGHT, BAR_HEIGHT)
}
