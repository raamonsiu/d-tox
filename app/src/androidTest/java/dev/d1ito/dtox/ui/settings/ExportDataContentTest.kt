package dev.d1ito.dtox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.ui.theme.DTOXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportDataContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var status by mutableStateOf(ExportStatus.IDLE)
    private var includeBooks by mutableStateOf(false)
    private var exports = 0

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    private fun showContent() {
        composeRule.setContent {
            DTOXTheme {
                Column {
                    ExportDataContent(
                        status,
                        includeBooks,
                        onIncludeBooksChange = { included -> includeBooks = included },
                        onExport = { exports++ },
                    )
                }
            }
        }
    }

    @Test
    fun booksAreLeftOutByDefault_andCanBeIncluded() {
        showContent()
        composeRule.onNodeWithText(text(R.string.settings_off)).assertIsSelected()

        composeRule.onNodeWithText(text(R.string.settings_on)).performClick()

        composeRule.onNodeWithText(text(R.string.settings_on)).assertIsSelected()
    }

    @Test
    fun exportAction_startsTheExport() {
        showContent()

        composeRule.onNodeWithText(text(R.string.export_data_action)).performClick()

        assertEquals(1, exports)
    }

    @Test
    fun runningExport_disablesTheAction() {
        status = ExportStatus.RUNNING
        showContent()

        composeRule.onNodeWithText(text(R.string.export_data_running)).assertIsNotEnabled()
    }

    @Test
    fun result_isShownAsAPlainLine() {
        status = ExportStatus.FAILED
        showContent()
        composeRule.onNodeWithText(text(R.string.export_data_failed)).assertIsDisplayed()

        status = ExportStatus.EXPORTED

        composeRule.onNodeWithText(text(R.string.export_data_done)).assertIsDisplayed()
    }
}
