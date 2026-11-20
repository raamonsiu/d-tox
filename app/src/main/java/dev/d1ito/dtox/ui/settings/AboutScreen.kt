package dev.d1ito.dtox.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import dev.d1ito.dtox.R
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * "Acerca de" (docs/04-design.md §Ajustes): version and code license, with the full text of every
 * bundled license one tap away. Nothing here uses the network or opens the browser. Back closes an
 * open license first, then the screen.
 *
 * @param ioDispatcher where the version and the license texts are read; never the main thread.
 * @param onBack closes the screen.
 */
@Composable
fun AboutScreen(
    ioDispatcher: CoroutineDispatcher,
    onBack: () -> Unit,
) {
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    val open = openName?.let(Attributions::named)
    val context = LocalContext.current
    if (open == null) {
        BackHandler(onBack = onBack)
        val versionName by produceState<Result<String>?>(null, context) {
            value = withContext(ioDispatcher) { installedVersionName(context) }
        }
        AboutContent(versionName, onOpenLicense = { openName = it.name }, onBack = onBack)
    } else {
        BackHandler { openName = null }
        val resources = LocalResources.current
        val text by produceState<Result<String>?>(null, open, resources) {
            value = withContext(ioDispatcher) { readText(resources, open.licenseText) }
        }
        LicenseContent(open, text, onBack = { openName = null })
    }
}

/**
 * The version, the code license and the list of bundled works.
 *
 * @param versionName the installed version; null while it is read.
 * @param onOpenLicense shows the license text of the tapped work.
 * @param onBack closes the screen.
 */
@Composable
fun AboutContent(
    versionName: Result<String>?,
    onOpenLicense: (Attribution) -> Unit,
    onBack: () -> Unit,
) {
    PlainScrollingScreen {
        TextAction(stringResource(R.string.settings_back), onBack)
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleLarge)
        versionName?.let { version -> VersionLine(version) }
        Section(R.string.about_section_code)
        MutedText(stringResource(R.string.about_code_notice))
        AttributionEntry(Attributions.code, onOpenLicense)
        Section(R.string.about_section_fonts)
        Attributions.fonts.forEach { font -> AttributionEntry(font, onOpenLicense) }
        Section(R.string.about_section_libraries)
        Attributions.libraries.forEach { library -> AttributionEntry(library, onOpenLicense) }
    }
}

/**
 * The full license text of [attribution], verbatim.
 *
 * @param text the text; null while it is read.
 * @param onBack goes back to the list.
 */
@Composable
fun LicenseContent(
    attribution: Attribution,
    text: Result<String>?,
    onBack: () -> Unit,
) {
    PlainScrollingScreen {
        TextAction(stringResource(R.string.settings_back), onBack)
        Text(attribution.name, style = MaterialTheme.typography.titleLarge)
        MutedText(attribution.licenseName)
        text?.fold(
            onSuccess = { licenseText -> Text(licenseText, color = DTOXTheme.palette.text) },
            onFailure = { MutedText(stringResource(R.string.about_license_unavailable)) },
        )
    }
}

@Composable
private fun PlainScrollingScreen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DTOXTheme.palette.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(screenMargin),
        verticalArrangement = Arrangement.spacedBy(settingSpacing),
        content = content,
    )
}

@Composable
private fun VersionLine(versionName: Result<String>) {
    val line =
        versionName.fold(
            onSuccess = { version -> stringResource(R.string.about_version, version) },
            onFailure = { stringResource(R.string.about_version_unavailable) },
        )
    Text(line, color = DTOXTheme.palette.text)
}

@Composable
private fun Section(
    @StringRes title: Int,
) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun AttributionEntry(
    attribution: Attribution,
    onOpenLicense: (Attribution) -> Unit,
) {
    TextAction(
        stringResource(R.string.about_entry, attribution.name, attribution.licenseName),
        onClick = { onOpenLicense(attribution) },
    )
}

/**
 * Reads the installed `versionName` (the `VERSION` file at build time). Blocking: call it off the
 * main thread.
 *
 * @return the version, or the failure if the package manager cannot describe this app.
 */
private fun installedVersionName(context: Context): Result<String> =
    runCatching {
        val packageManager = context.packageManager
        val packageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION") // The flags overload needs API 33.
                packageManager.getPackageInfo(context.packageName, 0)
            }
        checkNotNull(packageInfo.versionName) { "The package has no versionName" }
    }

/**
 * Reads a UTF-8 raw resource whole. Blocking: call it off the main thread.
 *
 * @return the text, or the failure if the resource is missing or unreadable.
 */
private fun readText(
    resources: Resources,
    @RawRes resource: Int,
): Result<String> =
    runCatching {
        resources.openRawResource(resource).bufferedReader().use { reader -> reader.readText() }
    }
