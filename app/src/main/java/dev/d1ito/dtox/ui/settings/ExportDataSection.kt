package dev.d1ito.dtox.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.d1ito.dtox.R
import dev.d1ito.dtox.ui.theme.DTOXTheme
import java.time.LocalDate

private const val ZIP_MIME_TYPE = "application/zip"

private val includeBooksLabels =
    listOf(true to R.string.settings_on, false to R.string.settings_off)

/**
 * The "Datos" heading with the export rows. Temporary: once the "Datos" section of watched
 * folders exists, [ExportDataRows] goes into it and this heading is no longer needed.
 */
@Composable
fun ExportDataSection(viewModel: DataExportViewModel) {
    Text(stringResource(R.string.export_data_heading), style = MaterialTheme.typography.titleMedium)
    ExportDataRows(viewModel)
}

/**
 * "Incluir libros" and "Exportar datos" (RF-41): the action opens the system "save as" dialog
 * (`ACTION_CREATE_DOCUMENT`) and [viewModel] writes the zip to the chosen document.
 */
@Composable
fun ExportDataRows(viewModel: DataExportViewModel) {
    val status by viewModel.status.collectAsState()
    var includeBooks by rememberSaveable { mutableStateOf(false) }
    val saveAs = ActivityResultContracts.CreateDocument(ZIP_MIME_TYPE)
    val creator =
        rememberLauncherForActivityResult(saveAs) { uri ->
            uri?.let { chosen -> viewModel.export(chosen.toString(), includeBooks) }
        }
    ExportDataContent(
        status,
        includeBooks,
        onIncludeBooksChange = { included -> includeBooks = included },
        onExport = { creator.launch(exportFileName(LocalDate.now())) },
    )
}

/**
 * The export rows and a plain line with the result of the last export.
 *
 * @param status the last export; while one runs the action is disabled.
 * @param includeBooks whether the book files go into the zip.
 */
@Composable
internal fun ExportDataContent(
    status: ExportStatus,
    includeBooks: Boolean,
    onIncludeBooksChange: (Boolean) -> Unit,
    onExport: () -> Unit,
) {
    ChoiceSetting(
        R.string.export_data_include_books,
        includeBooksLabels,
        includeBooks,
        onIncludeBooksChange,
    )
    val running = status == ExportStatus.RUNNING
    TextButton(
        onClick = onExport,
        enabled = !running,
        modifier = Modifier.sizeIn(minHeight = minimumTouchTarget),
    ) {
        val label = if (running) R.string.export_data_running else R.string.export_data_action
        val palette = DTOXTheme.palette
        Text(stringResource(label), color = if (running) palette.textMuted else palette.text)
    }
    when (status) {
        ExportStatus.EXPORTED -> MutedText(stringResource(R.string.export_data_done))
        ExportStatus.FAILED -> MutedText(stringResource(R.string.export_data_failed))
        ExportStatus.IDLE, ExportStatus.RUNNING -> Unit
    }
}

/** Name suggested in the "save as" dialog, e.g. `dtox-datos-2026-11-21.zip`. */
internal fun exportFileName(date: LocalDate): String = "dtox-datos-$date.zip"
