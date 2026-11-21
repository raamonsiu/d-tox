package dev.d1ito.dtox.ui.reader

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SEARCH_TIMEOUT_MILLIS = 5_000L

@RunWith(AndroidJUnit4::class)
class BookSearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    private val book: Book =
        Book
            .create(
                BookIdentifier.parse("sha256:0123456789abcdef0123456789abcdef").getOrThrow(),
                BookMetadata(title = "Libro", author = null, language = "es"),
                listOf(
                    chapter(0, "Prólogo", "Era un día claro."),
                    chapter(1, null, "Al fondo, el Corazón de la sierra."),
                ),
            ).getOrThrow()

    @Test
    fun typing_listsOccurrencesWithTheirChapter_andTappingOneReportsItsAnchor() {
        val chosen = mutableListOf<Anchor>()
        show { anchor -> chosen += anchor }

        composeRule
            .onNodeWithContentDescription(text(R.string.search_field_hint))
            .performTextInput("corazon")
        composeRule.waitUntil(SEARCH_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(hasText("Corazón", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("Capítulo 2").assertIsDisplayed()
        composeRule
            .onNodeWithText("Corazón", substring = true)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        val occurrence = Anchor.create(1, 0, 13).getOrThrow()
        assertEquals(listOf(occurrence), chosen.map { anchor -> anchor.withoutQuote() })
    }

    @Test
    fun aQueryWithoutOccurrences_saysSo_butAnEmptyFieldDoesNot() {
        show()
        composeRule.onNodeWithText(text(R.string.search_no_results)).assertDoesNotExist()

        composeRule
            .onNodeWithContentDescription(text(R.string.search_field_hint))
            .performTextInput("ballena")

        composeRule.waitUntil(SEARCH_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(hasText(text(R.string.search_no_results)))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun theControlsBar_opensTheSearch() {
        var opened = false
        composeRule.setContent {
            DTOXTheme {
                ReaderControlsBar(
                    visible = true,
                    ReaderControlActions(onBack = {}, onSearch = { opened = true }),
                    onHide = {},
                )
            }
        }

        composeRule
            .onNodeWithText(text(R.string.reader_controls_search))
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(true, opened)
    }

    private fun show(onSelect: (Anchor) -> Unit = {}) {
        composeRule.setContent {
            DTOXTheme { BookSearchScreen(book, Dispatchers.Default, onSelect) }
        }
    }

    private fun chapter(
        index: Int,
        title: String?,
        text: String,
    ): Chapter = Chapter.create(index, title, listOf(Block.Paragraph(text))).getOrThrow()
}
