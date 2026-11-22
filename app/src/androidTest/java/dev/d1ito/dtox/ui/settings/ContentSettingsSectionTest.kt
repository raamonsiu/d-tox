package dev.d1ito.dtox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.feed.ContentPreferences
import dev.d1ito.dtox.domain.feed.ShortReadCategory
import dev.d1ito.dtox.domain.feed.ShortReadDuration
import dev.d1ito.dtox.ui.theme.DTOXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentSettingsSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var preferences by mutableStateOf(ContentPreferences.Default)

    private fun text(
        resource: Int,
        vararg arguments: Any,
    ): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)

    private fun showSection() {
        composeRule.setContent {
            DTOXTheme {
                Column {
                    ContentSettingsSection(
                        ContentSettings(preferences, saveFailed = false) { edit ->
                            edit(preferences).onSuccess { edited -> preferences = edited }
                        },
                    )
                }
            }
        }
    }

    @Test
    fun everyCategoryAndDuration_startsChosen() {
        showSection()

        composeRule.onNodeWithText(text(R.string.content_settings_section)).assertIsDisplayed()
        composeRule
            .onNodeWithText(text(R.string.content_settings_category_philosophy))
            .assertIsSelected()
        composeRule
            .onNodeWithText(text(R.string.content_settings_category_science))
            .assertIsSelected()
        ShortReadDuration.entries.forEach { duration ->
            composeRule
                .onNodeWithText(text(R.string.content_settings_duration_minutes, duration.minutes))
                .assertIsSelected()
        }
    }

    @Test
    fun tappingAChoice_togglesIt() {
        showSection()
        val twentyMinutes = text(R.string.content_settings_duration_minutes, 20)

        composeRule.onNodeWithText(twentyMinutes).performClick()
        composeRule.onNodeWithText(twentyMinutes).assertIsNotSelected()
        assertEquals(
            setOf(ShortReadDuration.SHORT, ShortReadDuration.MEDIUM),
            preferences.durations,
        )

        composeRule.onNodeWithText(twentyMinutes).performClick()
        composeRule.onNodeWithText(twentyMinutes).assertIsSelected()
    }

    @Test
    fun theLastCategory_staysChosen() {
        showSection()
        val philosophy = text(R.string.content_settings_category_philosophy)

        composeRule.onNodeWithText(text(R.string.content_settings_category_science)).performClick()
        composeRule.onNodeWithText(philosophy).performClick()

        composeRule.onNodeWithText(philosophy).assertIsSelected()
        assertEquals(setOf(ShortReadCategory.PHILOSOPHY), preferences.categories)
    }
}
