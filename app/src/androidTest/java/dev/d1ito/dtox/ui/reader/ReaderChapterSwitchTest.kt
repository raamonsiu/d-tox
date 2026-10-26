package dev.d1ito.dtox.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private const val PAGINATION_TIMEOUT_MILLIS = 10_000L

/**
 * Regression: after a chapter change the previous chapter's pages stay until the new ones are
 * computed in the background; drawing them with the new chapter's shorter text crashed with
 * StringIndexOutOfBoundsException.
 */
@RunWith(AndroidJUnit4::class)
class ReaderChapterSwitchTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val paginationThread = Executors.newSingleThreadExecutor()

    @After
    fun stopPaginationThread() {
        paginationThread.shutdownNow()
    }

    @Test
    fun whileTheNewChapterIsPaginated_theOldPagesAreNotDrawnWithItsText() {
        val longChapter = chapter(0, "Larga frase del primer capítulo. ".repeat(400))
        var shown by mutableStateOf(longChapter)
        composeRule.setContent {
            DTOXTheme {
                ReaderScreen(
                    chapter = shown,
                    position = ReadingPosition(restoredAnchor = null, onPageSettled = {}),
                    paginationDispatcher = paginationThread.asCoroutineDispatcher(),
                )
            }
        }
        composeRule.waitUntil(PAGINATION_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(
                    hasText("Larga", substring = true),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Hold the pagination thread so the short chapter is shown before its pages exist.
        val release = CountDownLatch(1)
        paginationThread.execute { release.await() }
        shown = chapter(1, "Breve.")
        composeRule.waitForIdle()
        release.countDown()

        composeRule.waitUntil(PAGINATION_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText("Breve.")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Breve.").assertIsDisplayed()
    }

    private fun chapter(
        index: Int,
        text: String,
    ): Chapter = Chapter.create(index, null, listOf(Block.Paragraph(text))).getOrThrow()
}
