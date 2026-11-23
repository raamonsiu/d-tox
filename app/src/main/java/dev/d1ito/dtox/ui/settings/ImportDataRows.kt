package dev.d1ito.dtox.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.d1ito.dtox.R
import dev.d1ito.dtox.ui.theme.DTOXTheme

private const val ZIP_MIME_TYPE = "application/zip"

/**
 * "Importar datos": the action opens the system "open" dialog (`ACTION_OPEN_DOCUMENT`) for a zip
 * and [viewModel] merges it into the local data. Shown inside the "Datos" section, after
 * exporting.
 */
@Composable
fun ImportDataRows(viewModel: DataImportViewModel) {
    val status by viewModel.status.collectAsState()
    val opener =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { chosen -> viewModel.import(chosen.toString()) }
        }
    ImportDataContent(status, onImport = { opener.launch(arrayOf(ZIP_MIME_TYPE)) })
}

/**
 * The import action and a plain line with the result of the last import.
 *
 * @param status the last import; while one runs the action is disabled.
 */
@Composable
internal fun ImportDataContent(
    status: ImportStatus,
    onImport: () -> Unit,
) {
    val running = status == ImportStatus.RUNNING
    TextButton(
        onClick = onImport,
        enabled = !running,
        modifier = Modifier.sizeIn(minHeight = minimumTouchTarget),
    ) {
        val label = if (running) R.string.import_data_running else R.string.import_data_action
        val palette = DTOXTheme.palette
        Text(stringResource(label), color = if (running) palette.textMuted else palette.text)
    }
    when (status) {
        ImportStatus.IMPORTED -> MutedText(stringResource(R.string.import_data_done))
        ImportStatus.INVALID -> MutedText(stringResource(R.string.import_data_invalid))
        ImportStatus.FAILED -> MutedText(stringResource(R.string.import_data_failed))
        ImportStatus.IDLE, ImportStatus.RUNNING -> Unit
    }
}
