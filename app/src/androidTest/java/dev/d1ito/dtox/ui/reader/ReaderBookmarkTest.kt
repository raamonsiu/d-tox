package dev.d1ito.dtox.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.pagination.Page
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PAGINATION_TIMEOUT_MILLIS = 10_000L

@RunWith(AndroidJUnit4::class)
class ReaderBookmarkTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    @Test
    fun bookmarkButton_showsTheState_togglesIt_andLeavesTheBarOpen() {
        var bookmarked by mutableStateOf(false)
        var visible by mutableStateOf(true)
        composeRule.setContent {
            DTOXTheme {
                ReaderControlsBar(
                    visible,
                    ReaderControlActions(
                        onBack = {},
                        bookmark = BookmarkToggle(bookmarked) { bookmarked = !bookmarked },
                    ),
                    onHide = { visible = false },
                )
            }
        }

        composeRule
            .onNodeWithText(text(R.string.reader_controls_bookmark))
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(true, bookmarked)
        composeRule.onNodeWithText(text(R.string.reader_controls_bookmarked)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.reader_controls_back)).assertIsDisplayed()
    }

    @Test
    fun barWithoutBookmarkAction_hasNoBookmarkButton() {
        composeRule.setContent {
            DTOXTheme { ReaderControlsBar(true, ReaderControlActions(onBack = {}), onHide = {}) }
        }

        composeRule.onNodeWithText(text(R.string.reader_controls_bookmark)).assertDoesNotExist()
    }

    @Test
    fun readerScreen_reportsTheRestoredPageAsShown() {
        val shownPages = mutableListOf<Page>()
        val chapter =
            Chapter.create(0, null, listOf(Block.Paragraph("Una página corta."))).getOrThrow()
        composeRule.setContent {
            DTOXTheme {
                ReaderScreen(
                    chapter = chapter,
                    position =
                        ReadingPosition(
                            null,
                            onPageSettled = {},
                            onPageShown = { page -> shownPages += page },
                        ),
                    paginationDispatcher = Dispatchers.Default,
                )
            }
        }

        composeRule.waitUntil(PAGINATION_TIMEOUT_MILLIS) { shownPages.isNotEmpty() }

        val chapterStart = Anchor.create(0, 0, 0).getOrThrow()
        assertEquals(0, shownPages.first().startAnchor.compareTo(chapterStart))
    }
}
