package dev.d1ito.dtox.ui.library

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
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

    private fun text(
        resource: Int,
        vararg arguments: Any,
    ): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)

    @Test
    fun emptyLibrary_showsTheSentenceAndTheAddAction() {
        var addRequests = 0
        show(LibraryUiState(), onAddBook = { addRequests++ })

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
    fun tappingABook_opensIt() {
        val opened = mutableListOf<String>()
        val niebla = entry("fedcba9876543210fedcba9876543210", "Niebla", author = null)
        show(
            LibraryUiState(books = listOf(niebla)),
            onOpenBook = { entry -> opened += entry.title },
        )

        composeRule.onNodeWithText("Niebla").performClick()

        assertEquals(listOf("Niebla"), opened)
    }

    @Test
    fun longPressingABook_asksFirst_andOnlyRemovesItOnConfirmation() {
        val removed = mutableListOf<String>()
        val niebla = entry("fedcba9876543210fedcba9876543210", "Niebla", author = null)
        show(LibraryUiState(books = listOf(niebla)), onRemoveBook = { entry ->
            removed +=
                entry.title
        })

        composeRule.onNodeWithText("Niebla").performTouchInput { longClick() }
        composeRule.onNodeWithText(text(R.string.library_remove_cancel)).performClick()
        assertEquals(emptyList<String>(), removed)
        composeRule.onNodeWithText(text(R.string.library_remove_confirm)).assertDoesNotExist()

        composeRule.onNodeWithText("Niebla").performTouchInput { longClick() }
        composeRule
            .onNodeWithText(
                text(R.string.library_remove_question, "Niebla"),
            ).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.library_remove_confirm)).performClick()

        assertEquals(listOf("Niebla"), removed)
        composeRule.onNodeWithText(text(R.string.library_remove_confirm)).assertDoesNotExist()
    }

    @Test
    fun back_closesTheConfirmation_withoutRemoving() {
        val removed = mutableListOf<String>()
        val niebla = entry("fedcba9876543210fedcba9876543210", "Niebla", author = null)
        show(LibraryUiState(books = listOf(niebla)), onRemoveBook = { entry ->
            removed +=
                entry.title
        })
        composeRule.onNodeWithText("Niebla").performTouchInput { longClick() }
        composeRule.onNodeWithText(text(R.string.library_remove_confirm)).assertIsDisplayed()

        Espresso.pressBack()

        composeRule.onNodeWithText(text(R.string.library_remove_confirm)).assertDoesNotExist()
        assertEquals(emptyList<String>(), removed)
    }

    @Test
    fun theRemoveAction_isNamedForTalkBack_andRowsAreLargeEnoughToTouch() {
        show(
            LibraryUiState(
                books = listOf(entry("fedcba9876543210fedcba9876543210", "Niebla", null)),
            ),
        )

        composeRule
            .onNodeWithText("Niebla")
            .assertHeightIsAtLeast(48.dp)
            .assert(
                SemanticsMatcher("long press named for TalkBack") { node ->
                    node.config.getOrNull(SemanticsActions.OnLongClick)?.label ==
                        text(R.string.library_remove_action)
                },
            )
    }

    @Test
    fun theSettingsButton_appearsOnlyWithAnAction_andOpensTheSettings() {
        var opened = 0
        composeRule.setContent {
            DTOXTheme {
                LibraryContent(
                    LibraryUiState(),
                    LibraryActions(
                        onAddBook = {},
                        onOpenBook = {},
                        onRemoveBook = {},
                        onOpenSettings = { opened++ },
                    ),
                )
            }
        }

        composeRule.onNodeWithText(text(R.string.library_settings)).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun withoutASettingsAction_thereIsNoSettingsButton() {
        show(LibraryUiState())

        composeRule.onNodeWithText(text(R.string.library_settings)).assertDoesNotExist()
    }

    @Test
    fun whileImporting_theActionIsDisabled_andAMessageIsShownAfterAFailure() {
        show(LibraryUiState(isImporting = true, message = LibraryMessage.UnsupportedFormat))

        composeRule.onNodeWithText(text(R.string.library_importing)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.library_unsupported_format)).assertIsDisplayed()
    }

    private fun show(
        state: LibraryUiState,
        onOpenBook: (LibraryEntry) -> Unit = {},
        onAddBook: () -> Unit = {},
        onRemoveBook: (LibraryEntry) -> Unit = {},
    ) {
        composeRule.setContent {
            DTOXTheme { LibraryContent(state, LibraryActions(onAddBook, onOpenBook, onRemoveBook)) }
        }
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
