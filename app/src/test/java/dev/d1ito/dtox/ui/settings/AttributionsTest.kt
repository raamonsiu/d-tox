package dev.d1ito.dtox.ui.settings

import dev.d1ito.dtox.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Unit tests run with the module directory as working directory. */
private const val RAW_RESOURCES_DIRECTORY = "src/main/res/raw"
private const val REPOSITORY_LICENSE = "../LICENSE"

class AttributionsTest {
    private fun rawResourceName(resource: Int): String =
        R.raw::class.java.fields
            .single { field -> field.getInt(null) == resource }
            .name

    @Test
    fun everyBundledLicenseText_isShownByAnAttribution() {
        val bundledTexts =
            File(RAW_RESOURCES_DIRECTORY)
                .listFiles()
                .orEmpty()
                .map { file -> file.nameWithoutExtension }
                .filter { name -> name.endsWith("_license") }
                .toSet()
        val shownTexts =
            Attributions.all.map { attribution ->
                rawResourceName(attribution.licenseText)
            }

        assertEquals(bundledTexts, shownTexts.toSet())
    }

    @Test
    fun codeLicenseText_isTheRepositoryLicense() {
        val shown =
            File(RAW_RESOURCES_DIRECTORY, "${rawResourceName(Attributions.code.licenseText)}.txt")

        assertEquals("GPL-3.0-or-later", Attributions.code.licenseName)
        assertArrayEquals(File(REPOSITORY_LICENSE).readBytes(), shown.readBytes())
    }

    @Test
    fun everyBundledFont_hasItsOwnLicense() {
        val fontLicenses = Attributions.fonts.map { font -> rawResourceName(font.licenseText) }

        assertEquals(
            listOf("libron_license", "opendyslexic_license", "ibm_plex_mono_license"),
            fontLicenses,
        )
    }

    @Test
    fun named_findsEachAttribution_andNothingElse() {
        Attributions.all.forEach { attribution ->
            assertEquals(attribution, Attributions.named(attribution.name))
        }
        assertNull(Attributions.named("Unknown"))
    }
}
