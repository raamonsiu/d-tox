package dev.d1ito.dtox.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PAGINATION_TIMEOUT_MILLIS = 10_000L

@RunWith(AndroidJUnit4::class)
class ReaderControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    @Test
    fun hiddenBar_showsNothing_andAVisibleOneShowsOnlyActionsItHas() {
        var visible by mutableStateOf(false)
        composeRule.setContent {
            DTOXTheme { ReaderControlsBar(visible, ReaderControlActions(onBack = {}), onHide = {}) }
        }
        composeRule.onNodeWithText(text(R.string.reader_controls_back)).assertDoesNotExist()

        visible = true

        composeRule.onNodeWithText(text(R.string.reader_controls_back)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.reader_controls_chapters)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.reader_controls_text)).assertDoesNotExist()
    }

    @Test
    fun buttons_haveTouchTargets_runTheirAction_andHideTheBar() {
        val calls = mutableListOf<String>()
        var visible by mutableStateOf(true)
        composeRule.setContent {
            DTOXTheme {
                ReaderControlsBar(
                    visible,
                    ReaderControlActions(
                        onBack = { calls += "back" },
                        onChapters = { calls += "chapters" },
                        onTextSettings = { calls += "text" },
                    ),
                    onHide = { visible = false },
                )
            }
        }

        composeRule
            .onNodeWithText(text(R.string.reader_controls_chapters))
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(listOf("chapters"), calls)
        composeRule.onNodeWithText(text(R.string.reader_controls_back)).assertDoesNotExist()
    }

    @Test
    fun bar_hidesByItself_afterTheTimeout() {
        var visible by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            DTOXTheme {
                ReaderControlsBar(visible, ReaderControlActions(onBack = {}), onHide = {
                    visible =
                        false
                })
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText(text(R.string.reader_controls_back)).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(READER_CONTROLS_AUTO_HIDE.inWholeMilliseconds + 100)

        composeRule.onNodeWithText(text(R.string.reader_controls_back)).assertDoesNotExist()
    }

    @Test
    fun aTapInTheCenterOfThePage_reachesOnCenterTap_butEdgeTapsDoNot() {
        var centerTaps = 0
        val chapter =
            Chapter
                .create(
                    0,
                    null,
                    listOf(Block.Paragraph("Una página corta.")),
                ).getOrThrow()
        composeRule.setContent {
            DTOXTheme {
                ReaderScreen(
                    chapter = chapter,
                    position =
                        ReadingPosition(
                            null,
                            onPageSettled = {},
                            onCenterTap = { centerTaps++ },
                        ),
                    paginationDispatcher = Dispatchers.Default,
                )
            }
        }
        composeRule.waitUntil(PAGINATION_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText("Una página corta.")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onRoot().performTouchInput { click(center) }
        // Inside the page but within the left and right edge zones: these turn pages instead.
        composeRule.onRoot().performTouchInput { click(Offset(width * 0.15f, centerY)) }
        composeRule.onRoot().performTouchInput { click(Offset(width * 0.85f, centerY)) }
        composeRule.waitForIdle()

        assertEquals(1, centerTaps)
    }
}
