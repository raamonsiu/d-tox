package dev.d1ito.dtox.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.domain.ReadingFont
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PAGINATION_TIMEOUT_MILLIS = 10_000L
private const val WORD_COUNT = 800
private const val ANCHORED_WORD_NUMBER = 500
private const val WORD_NUMBER_DIGITS = 4

/** Changing the font repaginates and reopens at the page holding the reading anchor (RF-14). */
@RunWith(AndroidJUnit4::class)
class ReaderFontChangeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var settings by mutableStateOf(ReadingSettings.Default)

    @Test
    fun changingTheFont_keepsThePageHoldingTheAnchor() {
        val anchoredWord = wordNumbered(ANCHORED_WORD_NUMBER)
        val text = (1..WORD_COUNT).joinToString(" ", transform = ::wordNumbered)
        val chapter = Chapter.create(0, null, listOf(Block.Paragraph(text))).getOrThrow()
        val anchor = Anchor.create(0, 0, text.indexOf(anchoredWord)).getOrThrow()
        composeRule.setContent {
            DTOXTheme(font = settings.font) {
                ReaderScreen(
                    chapter = chapter,
                    position = ReadingPosition(anchor, onPageSettled = {}),
                    paginationDispatcher = Dispatchers.Default,
                    settings = settings,
                )
            }
        }
        val progressBefore = waitForProgressText { true }
        composeRule.onNodeWithText(anchoredWord, substring = true).assertIsDisplayed()

        settings = settings.edited(font = ReadingFont.IBM_PLEX_MONO).getOrThrow()

        val progressAfter = waitForProgressText { progress -> progress != progressBefore }
        assertNotEquals(progressBefore, progressAfter)
        composeRule.onNodeWithText(anchoredWord, substring = true).assertIsDisplayed()
    }

    /** A word that appears once in the chapter, so the page showing it is unambiguous. */
    private fun wordNumbered(number: Int): String =
        "Palabra" + number.toString().padStart(WORD_NUMBER_DIGITS, '0')

    /**
     * Waits until the `12 / 148` indicator shows a text accepted by [isExpected].
     *
     * @return the indicator text.
     */
    private fun waitForProgressText(isExpected: (String) -> Boolean): String {
        var progress = ""
        composeRule.waitUntil(PAGINATION_TIMEOUT_MILLIS) {
            val texts =
                composeRule
                    .onAllNodes(hasText(" / ", substring = true))
                    .fetchSemanticsNodes()
                    .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            progress = texts.firstOrNull()?.text.orEmpty()
            progress.isNotEmpty() && isExpected(progress)
        }
        return progress
    }
}
