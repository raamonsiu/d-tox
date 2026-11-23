package dev.d1ito.dtox.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.data.storage.BookDataLocation
import dev.d1ito.dtox.ui.theme.DTOXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SYNC_URI = "content://com.android.externalstorage.documents/tree/primary%3ASync"

@RunWith(AndroidJUnit4::class)
class DataFolderContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var location: BookDataLocation by mutableStateOf(BookDataLocation.Internal)
    private var status by mutableStateOf(DataFolderStatus.IDLE)
    private var choices = 0
    private var returns = 0

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    private fun showContent() {
        composeRule.setContent {
            DTOXTheme {
                DataFolderContent(
                    location,
                    status,
                    onChoose = { choices++ },
                    onUseInternal = { returns++ },
                )
            }
        }
    }

    @Test
    fun internalStorage_offersOnlyToChooseAFolder() {
        showContent()

        composeRule.onNodeWithText(text(R.string.data_folder_internal)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.data_folder_choose)).performClick()

        composeRule.onNodeWithText(text(R.string.data_folder_use_internal)).assertDoesNotExist()
        assertEquals(1, choices)
    }

    @Test
    fun chosenFolder_showsItsNameAndOffersToGoBack() {
        location = BookDataLocation.Folder(SYNC_URI)
        showContent()

        composeRule.onNodeWithText("Sync").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.data_folder_use_internal)).performClick()

        assertEquals(1, returns)
    }

    @Test
    fun whileMoving_hidesTheActions_andAFailureIsShown() {
        status = DataFolderStatus.MOVING
        showContent()

        composeRule.onNodeWithText(text(R.string.data_folder_moving)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.data_folder_choose)).assertDoesNotExist()

        status = DataFolderStatus.FAILED
        composeRule.onNodeWithText(text(R.string.data_folder_failed)).assertIsDisplayed()
    }
}
