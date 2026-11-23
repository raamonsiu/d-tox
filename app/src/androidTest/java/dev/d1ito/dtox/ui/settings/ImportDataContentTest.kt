package dev.d1ito.dtox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
class ImportDataContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var status by mutableStateOf(ImportStatus.IDLE)
    private var imports = 0

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    private fun showContent() {
        composeRule.setContent {
            DTOXTheme {
                Column { ImportDataContent(status, onImport = { imports++ }) }
            }
        }
    }

    @Test
    fun importAction_startsTheImport() {
        showContent()

        composeRule.onNodeWithText(text(R.string.import_data_action)).performClick()

        assertEquals(1, imports)
    }

    @Test
    fun runningImport_disablesTheAction() {
        status = ImportStatus.RUNNING
        showContent()

        composeRule.onNodeWithText(text(R.string.import_data_running)).assertIsNotEnabled()
    }

    @Test
    fun result_isShownAsAPlainLine() {
        status = ImportStatus.INVALID
        showContent()
        composeRule.onNodeWithText(text(R.string.import_data_invalid)).assertIsDisplayed()

        status = ImportStatus.FAILED
        composeRule.onNodeWithText(text(R.string.import_data_failed)).assertIsDisplayed()

        status = ImportStatus.IMPORTED
        composeRule.onNodeWithText(text(R.string.import_data_done)).assertIsDisplayed()
    }
}
