package dev.d1ito.dtox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.ReminderSettings
import dev.d1ito.dtox.domain.ReminderSettingsError
import dev.d1ito.dtox.ui.theme.DTOXTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RunWith(AndroidJUnit4::class)
class ReminderSettingsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var settings by mutableStateOf(ReminderSettings.Default)
    private var rejectedChange by mutableStateOf<ReminderSettingsError?>(null)
    private var receivedEdits = 0

    private fun text(
        resource: Int,
        vararg arguments: Any,
    ): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)

    private fun shown(time: LocalTime): String =
        time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    private fun showReminders() {
        composeRule.setContent {
            DTOXTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReminderSettingsContent(settings, saveFailed = false, rejectedChange) { edit ->
                        receivedEdits++
                        settings.edit().onSuccess { edited -> settings = edited }
                    }
                }
            }
        }
    }

    @Test
    fun turningRemindersOn_showsCountTimesAndQuietHours() {
        showReminders()

        composeRule.onNodeWithText(text(R.string.settings_on)).performClick()

        assertTrue(settings.enabled)
        composeRule.onNodeWithText(text(R.string.reminders_per_day)).assertIsDisplayed()
        composeRule.onNodeWithText(shown(LocalTime.of(19, 0))).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.reminders_quiet_hours)).assertIsDisplayed()
    }

    @Test
    fun increaseButton_addsAReminderUpToFour() {
        settings = settings.edited(enabled = true).getOrThrow()
        showReminders()
        val increase = text(R.string.settings_increase, text(R.string.reminders_per_day))

        repeat(ReminderSettings.MAX_REMINDERS_PER_DAY) {
            composeRule.onNodeWithContentDescription(increase).performScrollTo().performClick()
        }

        assertEquals(ReminderSettings.MAX_REMINDERS_PER_DAY, settings.remindersPerDay)
        composeRule.onNodeWithContentDescription(increase).assertIsNotEnabled()
    }

    @Test
    fun tappingATime_editsItInPlace() {
        settings = settings.edited(enabled = true).getOrThrow()
        showReminders()
        val description = text(R.string.reminders_time_description, shown(LocalTime.of(19, 0)))

        composeRule.onNodeWithContentDescription(description).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.reminders_time_accept)).performClick()

        assertEquals(1, receivedEdits)
        composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
    }

    @Test
    fun refusedChange_isExplained() {
        settings = settings.edited(enabled = true).getOrThrow()
        rejectedChange = ReminderSettingsError.TimeInQuietHours(LocalTime.of(23, 0))
        showReminders()

        composeRule.onNodeWithText(text(R.string.reminders_error_quiet_time)).assertIsDisplayed()
    }
}
