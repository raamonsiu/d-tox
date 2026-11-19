package dev.d1ito.dtox.ui.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PAGINATION_TIMEOUT_MILLIS = 10_000L

/** Horizontal position of the tap, as a share of the screen width. */
private const val RIGHT_EDGE_TAP_FRACTION = 0.9f

/** The faint `12 / 148` progress indicator (RF-18) and its setting. */
@RunWith(AndroidJUnit4::class)
class ReadingProgressIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingOn_showsThePageOfTheChapter_andFollowsPageTurns() {
        showLongChapter(progressIndicator = true)

        composeRule.onNodeWithText("1 / ", substring = true).assertIsDisplayed()
        composeRule.onRoot().performTouchInput {
            click(percentOffset(RIGHT_EDGE_TAP_FRACTION, 0.5f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 / ", substring = true).assertIsDisplayed()
    }

    @Test
    fun settingOff_hidesTheIndicator() {
        showLongChapter(progressIndicator = false)

        val indicators = composeRule.onAllNodes(hasText(" / ", substring = true))
        assertTrue(indicators.fetchSemanticsNodes().isEmpty())
    }

    private fun showLongChapter(progressIndicator: Boolean) {
        val chapter =
            Chapter
                .create(0, null, listOf(Block.Paragraph("Frase larga de prueba. ".repeat(400))))
                .getOrThrow()
        val settings =
            ReadingSettings.Default.edited(progressIndicator = progressIndicator).getOrThrow()
        composeRule.setContent {
            DTOXTheme {
                ReaderScreen(
                    chapter = chapter,
                    position = ReadingPosition(null, onPageSettled = {}),
                    paginationDispatcher = Dispatchers.Default,
                    settings = settings,
                )
            }
        }
        composeRule.waitUntil(PAGINATION_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(hasText("Frase", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
