package dev.d1ito.dtox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.WatchedFolder
import dev.d1ito.dtox.ui.theme.DTOXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TREE = "content://com.android.externalstorage.documents/tree/"

@RunWith(AndroidJUnit4::class)
class DataSettingsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val books = WatchedFolder(TREE + "primary%3ADocuments%2FBooks")
    private val papers = WatchedFolder(TREE + "primary%3APapers")
    private var additions = 0
    private val removed = mutableListOf<WatchedFolder>()

    private fun text(
        resource: Int,
        vararg arguments: Any,
    ): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)

    private fun showSection(
        folders: List<WatchedFolder>,
        changeFailed: Boolean = false,
        extraRows: @Composable () -> Unit = {},
    ) {
        composeRule.setContent {
            DTOXTheme {
                Column {
                    DataSettingsContent(
                        WatchedFoldersState(folders, changeFailed),
                        WatchedFolderActions(onAdd = { additions++ }, onRemove = { folder ->
                            removed +=
                                folder
                        }),
                        extraRows,
                    )
                }
            }
        }
    }

    @Test
    fun withoutFolders_saysSoAndOffersToAddOne() {
        showSection(emptyList())

        composeRule.onNodeWithText(text(R.string.data_section)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.data_no_folders)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.data_add_folder)).performClick()

        assertEquals(1, additions)
    }

    @Test
    fun folders_showTheirNames_andEachCanBeRemoved() {
        showSection(listOf(books, papers))

        composeRule.onNodeWithText("Books").assertIsDisplayed()
        composeRule.onNodeWithText("Papers").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(text(R.string.data_remove_folder_description, "Papers"))
            .performClick()

        assertEquals(listOf(papers), removed)
    }

    @Test
    fun failedChange_isShown() {
        showSection(listOf(books), changeFailed = true)

        composeRule.onNodeWithText(text(R.string.data_folders_change_failed)).assertIsDisplayed()
    }

    @Test
    fun extraRows_areShownInTheSection() {
        showSection(emptyList()) { Text("Exportar") }

        composeRule.onNodeWithText("Exportar").assertIsDisplayed()
    }
}
