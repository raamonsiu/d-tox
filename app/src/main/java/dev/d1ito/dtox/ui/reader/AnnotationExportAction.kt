package dev.d1ito.dtox.ui.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.ui.settings.ExportStatus
import dev.d1ito.dtox.ui.settings.MutedText
import dev.d1ito.dtox.ui.settings.minimumTouchTarget
import dev.d1ito.dtox.ui.theme.DTOXTheme

private const val MARKDOWN_MIME_TYPE = "text/markdown"

/**
 * "Exportar a Markdown" (RF-26) for the list of marks and notes: it opens the system "save as"
 * dialog (`ACTION_CREATE_DOCUMENT`, no storage permission) with `<title>-notas.md` and
 * [viewModel] writes the book's highlights and notes to the chosen document.
 *
 * @param annotations the book's annotations; with no highlight or note the action is disabled.
 */
@Composable
fun AnnotationExportAction(
    book: Book,
    annotations: List<BookAnnotation>,
    viewModel: AnnotationExportViewModel,
) {
    val state by viewModel.state.collectAsState()
    val saveAs = ActivityResultContracts.CreateDocument(MARKDOWN_MIME_TYPE)
    val creator =
        rememberLauncherForActivityResult(saveAs) { uri ->
            uri?.let { chosen -> viewModel.export(chosen.toString(), book, annotations) }
        }
    AnnotationExportContent(
        status = if (state.book == book.identifier) state.status else ExportStatus.IDLE,
        exportable = annotations.any { annotation -> annotation !is BookAnnotation.Bookmark },
        onExport = { creator.launch(annotationsFileName(book.metadata.title)) },
    )
}

/**
 * The action and a plain line with the result of the last export of this book.
 *
 * @param status while an export runs the action is disabled.
 * @param exportable whether there is any highlight or note to export.
 */
@Composable
internal fun AnnotationExportContent(
    status: ExportStatus,
    exportable: Boolean,
    onExport: () -> Unit,
) {
    Column {
        val running = status == ExportStatus.RUNNING
        val enabled = exportable && !running
        TextButton(
            onClick = onExport,
            enabled = enabled,
            modifier = Modifier.sizeIn(minHeight = minimumTouchTarget),
        ) {
            val label =
                if (running) {
                    R.string.annotations_export_running
                } else {
                    R.string.annotations_export_action
                }
            val palette = DTOXTheme.palette
            Text(stringResource(label), color = if (enabled) palette.text else palette.textMuted)
        }
        when (status) {
            ExportStatus.EXPORTED -> MutedText(stringResource(R.string.annotations_export_done))
            ExportStatus.FAILED -> MutedText(stringResource(R.string.annotations_export_failed))
            ExportStatus.IDLE, ExportStatus.RUNNING -> Unit
        }
    }
}
