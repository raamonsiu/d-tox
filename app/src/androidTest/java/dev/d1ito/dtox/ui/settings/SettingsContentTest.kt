package dev.d1ito.dtox.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.PageMargins
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.domain.ReadingTheme
import dev.d1ito.dtox.ui.theme.DTOXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var settings by mutableStateOf(ReadingSettings.Default)
    private var backPresses = 0

    private fun text(
        resource: Int,
        vararg arguments: Any,
    ): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)

    private fun showSettings(saveFailed: Boolean = false) {
        composeRule.setContent {
            DTOXTheme {
                SettingsContent(
                    settings = settings,
                    saveFailed = saveFailed,
                    onChange = { edit -> settings = settings.edit().getOrThrow() },
                    onBack = { backPresses++ },
                )
            }
        }
    }

    @Test
    fun choosingATheme_appliesItAndMarksItSelected() {
        showSettings()
        composeRule.onNodeWithText(text(R.string.settings_theme_system)).assertIsSelected()

        composeRule.onNodeWithText(text(R.string.settings_theme_night)).performClick()

        assertEquals(ReadingTheme.NIGHT, settings.theme)
        composeRule.onNodeWithText(text(R.string.settings_theme_night)).assertIsSelected()
        composeRule.onNodeWithText(text(R.string.settings_theme_system)).assertIsNotSelected()
    }

    @Test
    fun textSizeButtons_stepByOne_andStopAtTheRangeEnds() {
        showSettings()
        val increase = text(R.string.settings_increase, text(R.string.settings_text_size))
        val decrease = text(R.string.settings_decrease, text(R.string.settings_text_size))

        composeRule.onNodeWithContentDescription(increase).performScrollTo().performClick()
        assertEquals(19, settings.textSizeSp)

        settings = settings.edited(textSizeSp = ReadingSettings.TEXT_SIZE_RANGE.last).getOrThrow()
        composeRule.onNodeWithContentDescription(increase).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(decrease).assertIsEnabled()
    }

    @Test
    fun lineSpacingButtons_stepByATenth_withinTheRange() {
        showSettings()
        val increase = text(R.string.settings_increase, text(R.string.settings_line_spacing))

        composeRule.onNodeWithContentDescription(increase).performScrollTo().performClick()

        assertEquals(1.7, settings.lineSpacing, 0.0)
        composeRule.onNodeWithContentDescription(increase).assertIsNotEnabled()
    }

    @Test
    fun switches_marginsAndBack_work() {
        showSettings()

        composeRule
            .onNodeWithText(
                text(R.string.settings_margins_wide),
            ).performScrollTo()
            .performClick()
        composeRule.onNodeWithText(text(R.string.settings_back)).performClick()

        assertEquals(PageMargins.WIDE, settings.margins)
        assertEquals(1, backPresses)
    }

    @Test
    fun failedSave_isShown() {
        showSettings(saveFailed = true)

        composeRule.onNodeWithText(text(R.string.settings_save_failed)).assertIsDisplayed()
    }
}
