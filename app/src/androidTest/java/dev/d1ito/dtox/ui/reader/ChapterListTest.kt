package dev.d1ito.dtox.ui.reader

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.ui.theme.DTOXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterListTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val chapters =
        listOf("Prólogo", null, "Capítulo II").mapIndexed { index, title ->
            Chapter.create(index, title, listOf(Block.Paragraph("Texto $index"))).getOrThrow()
        }

    @Test
    fun titles_areListed_andUntitledChaptersAreNumbered() {
        show(currentIndex = 0)

        composeRule.onNodeWithText("Prólogo", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Capítulo 2").assertIsDisplayed()
        composeRule.onNodeWithText("Capítulo II").assertIsDisplayed()
    }

    @Test
    fun theCurrentChapter_isMarkedAndSelected_withoutColor() {
        show(currentIndex = 2)

        composeRule.onNodeWithText("· Capítulo II", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("· Capítulo II").assertIsSelected()
        composeRule.onNodeWithText("Prólogo").assertIsNotSelected()
    }

    @Test
    fun choosingAChapter_reportsItsIndex_andRowsAreLargeEnoughToTouch() {
        val chosen = mutableListOf<Int>()
        show(currentIndex = 0) { index -> chosen += index }

        composeRule
            .onNodeWithText("Capítulo 2")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(listOf(1), chosen)
    }

    private fun show(
        currentIndex: Int,
        onSelect: (Int) -> Unit = {},
    ) {
        composeRule.setContent { DTOXTheme { ChapterList(chapters, currentIndex, onSelect) } }
    }
}
