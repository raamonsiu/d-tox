package dev.d1ito.dtox.ui.reader

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.pagination.Page
import dev.d1ito.dtox.pagination.PageFragment
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val FIRST =
    "Nadie te devolverá los años, nadie te restituirá a ti mismo. Seguirá su curso la vida."
private const val SECOND =
    "No volverá atrás ni detendrá su carrera; no hará ruido, no advertirá de su velocidad."
private const val CHAPTER_INDEX = 1
private const val PAGINATION_TIMEOUT_MILLIS = 10_000L
private const val PARAGRAPHS_FOR_SEVERAL_PAGES = 40

/** Long press selection on a drawn page, mapped to anchors, and its coexistence with page turns. */
@RunWith(AndroidJUnit4::class)
class PageTextSelectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val chapter =
        Chapter
            .create(CHAPTER_INDEX, null, listOf(Block.Paragraph(FIRST), Block.Paragraph(SECOND)))
            .getOrThrow()

    private val page =
        Page(
            startAnchor = anchor(0, 0),
            endAnchor = anchor(1, SECOND.length),
            fragments = listOf(PageFragment(0, 0, FIRST.length), PageFragment(1, 0, SECOND.length)),
        )

    @Test
    fun longPress_selectsTheWord_andDraggingExtendsItIntoTheNextBlock() {
        val reported = showSelectablePage()
        val first = boundsOf(FIRST)
        val second = boundsOf(SECOND)
        val onFirstWord = Offset(first.left + 4f, first.top + 4f)

        composeRule.onRoot().performTouchInput { longClick(onFirstWord) }
        composeRule.waitForIdle()
        val word = reported.last()
        assertEquals(anchor(0, 0), word?.start?.withoutQuote())
        assertEquals(anchor(0, "Nadie".length), word?.end)
        assertEquals(FIRST.take(40), word?.start?.quote)

        composeRule.onRoot().performTouchInput {
            longPressAndDrag(onFirstWord, Offset(second.center.x, second.top + 4f))
        }
        composeRule.waitForIdle()

        val crossing = reported.last()
        assertEquals(anchor(0, 0), crossing?.start?.withoutQuote())
        assertEquals(1, crossing?.end?.block)
        assertTrue("${crossing?.end}", (crossing?.end?.offset ?: 0) in 1 until SECOND.length)
    }

    @Test
    fun aTapClearsTheSelection() {
        val reported = showSelectablePage()
        val first = boundsOf(FIRST)
        val onFirstWord = Offset(first.left + 4f, first.top + 4f)
        composeRule.onRoot().performTouchInput { longClick(onFirstWord) }
        composeRule.waitForIdle()

        composeRule.onRoot().performTouchInput { click(first.center) }
        composeRule.waitForIdle()

        assertEquals(2, reported.size)
        assertNull(reported.last())
    }

    @Test
    fun longPress_offersResaltar_whichHighlightsTheSelection_andClearsIt() {
        val highlighted = mutableListOf<AnchorRange>()
        val reported = showSelectablePage(onHighlight = { range -> highlighted += range })
        val first = boundsOf(FIRST)
        composeRule.onNodeWithText(highlightLabel()).assertDoesNotExist()

        composeRule.onRoot().performTouchInput {
            longClick(
                Offset(first.left + 4f, first.top + 4f),
            )
        }
        composeRule.onNodeWithText(highlightLabel()).assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(anchor(0, "Nadie".length), highlighted.single().end)
        assertNull(reported.last())
        composeRule.onNodeWithText(highlightLabel()).assertDoesNotExist()
    }

    @Test
    fun selectionInsideAHighlight_offersNoResaltar() {
        val wholeFirstBlock = AnchorRange.create(anchor(0, 0), anchor(0, FIRST.length)).getOrThrow()
        val reported =
            showSelectablePage(highlights = listOf(wholeFirstBlock), onHighlight = {})
        val first = boundsOf(FIRST)

        composeRule.onRoot().performTouchInput {
            longClick(
                Offset(first.left + 4f, first.top + 4f),
            )
        }
        composeRule.waitForIdle()

        assertEquals(anchor(0, 0), reported.last()?.start?.withoutQuote())
        composeRule.onNodeWithText(highlightLabel()).assertDoesNotExist()
    }

    @Test
    fun selectingInTheReader_neitherTurnsThePageNorTapsTheCenter_andATapOnlyClearsIt() {
        var settledPages = 0
        var centerTaps = 0
        val longChapter =
            Chapter
                .create(0, null, List(PARAGRAPHS_FOR_SEVERAL_PAGES) { Block.Paragraph(FIRST) })
                .getOrThrow()
        composeRule.setContent {
            DTOXTheme {
                ReaderScreen(
                    chapter = longChapter,
                    position =
                        ReadingPosition(
                            null,
                            onPageSettled = { settledPages++ },
                            onCenterTap = { centerTaps++ },
                        ),
                    paginationDispatcher = Dispatchers.Default,
                )
            }
        }
        composeRule.waitUntil(PAGINATION_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(FIRST)).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onRoot().performTouchInput {
            longPressAndDrag(center, Offset(width * 0.05f, centerY))
        }
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(0, settledPages)
        assertEquals(0, centerTaps)

        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(1, centerTaps)
    }

    private fun showSelectablePage(
        highlights: List<AnchorRange> = emptyList(),
        onHighlight: ((AnchorRange) -> Unit)? = null,
    ): List<AnchorRange?> {
        val reported = mutableListOf<AnchorRange?>()
        composeRule.setContent {
            DTOXTheme {
                SelectablePageContent(
                    page,
                    chapter,
                    readingStyles(),
                    Modifier.width(320.dp),
                    highlights,
                    onSelectionChange = { range -> reported += range },
                    onHighlight = onHighlight,
                )
            }
        }
        composeRule.waitForIdle()
        return reported
    }

    private fun highlightLabel(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(
            R.string.reader_highlight,
        )

    private fun boundsOf(text: String): Rect =
        composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    /** Holds still past the long press timeout, then drags to [end] in small steps and lifts. */
    private fun TouchInjectionScope.longPressAndDrag(
        start: Offset,
        end: Offset,
    ) {
        down(start)
        moveTo(start, delayMillis = viewConfiguration.longPressTimeoutMillis * 2)
        val steps = 10
        (1..steps).forEach { step -> moveTo(start + (end - start) * (step / steps.toFloat())) }
        up()
    }

    private fun anchor(
        block: Int,
        offset: Int,
    ): Anchor = Anchor.create(CHAPTER_INDEX, block, offset).getOrThrow()
}
