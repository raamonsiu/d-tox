package dev.d1ito.dtox.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Gradle runs unit tests from the module directory, so resources are reachable by path. */
private const val RESOURCES_DIRECTORY = "src/main/res"
private const val WINDOW_BACKGROUND_NAME = "window_background"

/**
 * The launch window is drawn from XML resources before Compose exists; these tests keep its
 * background equal to the Compose palette so there is no flash or color jump on start.
 */
class LaunchWindowColorTest {
    @Test
    fun lightLaunchWindow_matchesPaperBackground() {
        assertEquals(
            argbHex(ReadingPalettes.Paper.background.toArgb()),
            windowBackgroundIn("values"),
        )
    }

    @Test
    fun nightLaunchWindow_matchesNightBackground() {
        assertEquals(
            argbHex(ReadingPalettes.Night.background.toArgb()),
            windowBackgroundIn("values-night"),
        )
    }

    /**
     * @param valuesDirectory resource qualifier directory, e.g. `values-night`.
     * @return the `window_background` color declared in its colors.xml, upper case `#AARRGGBB`.
     * @throws IllegalStateException if the color is not declared there.
     */
    private fun windowBackgroundIn(valuesDirectory: String): String {
        val colorsFile = File("$RESOURCES_DIRECTORY/$valuesDirectory/colors.xml")
        val colorNodes =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(colorsFile)
                .getElementsByTagName("color")
        val windowBackground =
            (0 until colorNodes.length)
                .map { index -> colorNodes.item(index) }
                .firstOrNull { node ->
                    node.attributes.getNamedItem("name")?.nodeValue == WINDOW_BACKGROUND_NAME
                }
        checkNotNull(windowBackground) { "$WINDOW_BACKGROUND_NAME missing in $colorsFile" }
        return windowBackground.textContent.trim().uppercase()
    }

    private fun argbHex(argb: Int): String = "#%08X".format(argb)
}
