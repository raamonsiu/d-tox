package dev.d1ito.dtox.ui.settings

import androidx.annotation.RawRes
import dev.d1ito.dtox.R

private const val APACHE_2_0 = "Apache-2.0"
private const val OFL_1_1 = "OFL-1.1"

/**
 * Something bundled in the APK whose license the app shows (RNF-10).
 *
 * @property name the work's proper name; never translated.
 * @property licenseName the license's SPDX identifier.
 * @property licenseText raw resource with the full license text, as the work distributes it.
 */
data class Attribution(
    val name: String,
    val licenseName: String,
    @param:RawRes val licenseText: Int,
)

/**
 * Everything the "Acerca de" screen credits. Libraries are the release runtime classpath grouped
 * by project; every artifact of a group shares the group's license.
 */
object Attributions {
    /** The app's own code (D-16); the text is the repository's `LICENSE`. */
    val code = Attribution("D-TOX", "GPL-3.0-or-later", R.raw.gpl_3_0_license)

    /** Fonts bundled unmodified (D-25, D-37, D-38). */
    val fonts =
        listOf(
            Attribution("Libron", OFL_1_1, R.raw.libron_license),
            Attribution("OpenDyslexic", OFL_1_1, R.raw.opendyslexic_license),
            Attribution("IBM Plex Mono", OFL_1_1, R.raw.ibm_plex_mono_license),
        )

    /** Libraries compiled into the APK, direct and transitive. */
    val libraries =
        listOf(
            Attribution("AndroidX / Jetpack Compose", APACHE_2_0, R.raw.apache_2_0_license),
            Attribution("Kotlin Standard Library", APACHE_2_0, R.raw.apache_2_0_license),
            Attribution("kotlinx.coroutines", APACHE_2_0, R.raw.apache_2_0_license),
            Attribution("kotlinx.serialization", APACHE_2_0, R.raw.apache_2_0_license),
            Attribution("JetBrains Java Annotations", APACHE_2_0, R.raw.apache_2_0_license),
            Attribution("JSpecify", APACHE_2_0, R.raw.apache_2_0_license),
            Attribution("Guava ListenableFuture", APACHE_2_0, R.raw.apache_2_0_license),
            Attribution("commonmark-java", "BSD-2-Clause", R.raw.commonmark_license),
            Attribution("jsoup", "MIT", R.raw.jsoup_license),
        )

    /** Every attribution, code first. */
    val all: List<Attribution> = listOf(code) + fonts + libraries

    /**
     * @return the attribution called [name], or null if none is.
     */
    fun named(name: String): Attribution? =
        all.firstOrNull { attribution ->
            attribution.name ==
                name
        }
}
