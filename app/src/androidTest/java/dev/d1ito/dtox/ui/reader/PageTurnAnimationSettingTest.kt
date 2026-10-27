package dev.d1ito.dtox.ui.reader

import android.provider.Settings
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PAGINATION_TIMEOUT_MILLIS = 10_000L

/** Frames that fit well inside the page turn slide ([PAGE_TURN_MILLIS]). */
private const val FRAMES_BEFORE_THE_SLIDE_ENDS = 3

/** Horizontal position of the tap, as a share of the screen width. */
private const val RIGHT_EDGE_TAP_FRACTION = 0.9f

/** The "Animación al pasar página" setting (RF-12): off, an edge tap turns the page at once. */
@RunWith(AndroidJUnit4::class)
class PageTurnAnimationSettingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val settledPages = mutableListOf<Anchor>()

    @Test
    fun animationOff_edgeTapTurnsThePageAtOnce() {
        showLongChapter(pageAnimation = false)

        tapRightEdgeAndWaitAFewFrames()

        assertEquals(1, settledPages.size)
    }

    @Test
    fun animationOn_edgeTapSlides() {
        assumeTrue("System animations are removed on this device", systemAnimatorScale() > 0f)
        showLongChapter(pageAnimation = true)

        tapRightEdgeAndWaitAFewFrames()
        assertEquals(0, settledPages.size)

        composeRule.mainClock.advanceTimeBy(PAGE_TURN_MILLIS * 2L)
        assertEquals(1, settledPages.size)
    }

    private fun showLongChapter(pageAnimation: Boolean) {
        val chapter =
            Chapter
                .create(0, null, listOf(Block.Paragraph("Frase larga de prueba. ".repeat(400))))
                .getOrThrow()
        val settings = ReadingSettings.Default.edited(pageAnimation = pageAnimation).getOrThrow()
        composeRule.setContent {
            DTOXTheme {
                ReaderScreen(
                    chapter = chapter,
                    position = ReadingPosition(null, onPageSettled = { settledPages += it }),
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

    private fun tapRightEdgeAndWaitAFewFrames() {
        composeRule.mainClock.autoAdvance = false
        composeRule.onRoot().performTouchInput {
            // Past the page margin, inside the right tap zone of the pager.
            click(percentOffset(RIGHT_EDGE_TAP_FRACTION, 0.5f))
        }
        repeat(FRAMES_BEFORE_THE_SLIDE_ENDS) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun systemAnimatorScale(): Float =
        Settings.Global.getFloat(
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
}
