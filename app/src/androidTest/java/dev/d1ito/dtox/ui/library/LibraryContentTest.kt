package dev.d1ito.dtox.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.data.storage.LibraryEntry
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.ui.theme.DTOXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    @Test
    fun emptyLibrary_showsTheSentenceAndTheAddAction() {
        var addRequests = 0
        show(LibraryUiState()) { addRequests++ }

        composeRule.onNodeWithText(text(R.string.library_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.library_add)).assertIsEnabled().performClick()

        assertEquals(1, addRequests)
    }

    @Test
    fun books_areListedWithTitleAndAuthor() {
        show(
            LibraryUiState(
                books =
                    listOf(
                        entry("0123456789abcdef0123456789abcdef", "Cartas a Lucilio", "Séneca"),
                        entry("fedcba9876543210fedcba9876543210", "Niebla", author = null),
                    ),
            ),
        )

        composeRule.onNodeWithText("Cartas a Lucilio").assertIsDisplayed()
        composeRule.onNodeWithText("Séneca").assertIsDisplayed()
        composeRule.onNodeWithText("Niebla").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.library_empty)).assertDoesNotExist()
    }

    @Test
    fun whileImporting_theActionIsDisabled_andAMessageIsShownAfterAFailure() {
        show(LibraryUiState(isImporting = true, message = LibraryMessage.UnsupportedFormat))

        composeRule.onNodeWithText(text(R.string.library_importing)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.library_unsupported_format)).assertIsDisplayed()
    }

    private fun show(
        state: LibraryUiState,
        onAddBook: () -> Unit = {},
    ) {
        composeRule.setContent { DTOXTheme { LibraryContent(state, onAddBook) } }
    }

    private fun entry(
        hex: String,
        title: String,
        author: String?,
    ) = LibraryEntry(
        BookIdentifier.fromHex(hex).getOrThrow(),
        title,
        author,
        "$hex.epub",
        null,
        false,
    )
}
