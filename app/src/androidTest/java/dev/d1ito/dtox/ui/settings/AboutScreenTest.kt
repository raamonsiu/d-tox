package dev.d1ito.dtox.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val LOAD_TIMEOUT_MILLISECONDS = 5_000L

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backPresses = 0

    private fun text(
        resource: Int,
        vararg arguments: Any,
    ): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource, *arguments)

    private fun entry(attribution: Attribution): String =
        text(R.string.about_entry, attribution.name, attribution.licenseName)

    /** The version and license texts arrive from the IO dispatcher, which Compose does not await. */
    private fun waitForText(
        expected: String,
        substring: Boolean = false,
    ) {
        composeRule.waitUntil(LOAD_TIMEOUT_MILLISECONDS) {
            composeRule
                .onAllNodesWithText(expected, substring = substring)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun showAbout() {
        composeRule.setContent {
            DTOXTheme { AboutScreen(Dispatchers.IO, onBack = { backPresses++ }) }
        }
    }

    @Test
    fun showsTheInstalledVersion_andEveryBundledWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        showAbout()

        waitForText(text(R.string.about_version, versionName.orEmpty()))
        Attributions.all.forEach { attribution ->
            composeRule.onNodeWithText(entry(attribution)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun tappingAFont_showsItsLicenseText_andBackReturnsToTheList() {
        showAbout()

        composeRule
            .onNodeWithText(
                entry(Attributions.fonts.first()),
            ).performScrollTo()
            .performClick()
        waitForText("SIL OPEN FONT LICENSE", substring = true)

        composeRule.onNodeWithText(text(R.string.settings_back)).performClick()
        composeRule.onNodeWithText(text(R.string.about_title)).assertIsDisplayed()
        assertEquals(0, backPresses)

        composeRule.onNodeWithText(text(R.string.settings_back)).performClick()
        assertEquals(1, backPresses)
    }

    @Test
    fun codeLicense_isGplVersionThreeOrLater() {
        showAbout()

        composeRule.onNodeWithText(entry(Attributions.code)).performScrollTo().performClick()

        waitForText("GNU GENERAL PUBLIC LICENSE", substring = true)
    }
}
