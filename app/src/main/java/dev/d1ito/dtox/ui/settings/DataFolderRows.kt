package dev.d1ito.dtox.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import dev.d1ito.dtox.R
import dev.d1ito.dtox.data.storage.BookDataLocation
import dev.d1ito.dtox.domain.WatchedFolder
import dev.d1ito.dtox.ui.theme.DTOXTheme

/**
 * "Carpeta de datos" (RF-27), inside the "Datos" section: where the progress and notes live,
 * "Elegir carpeta de datos" (the system folder picker, `ACTION_OPEN_DOCUMENT_TREE`) and, while a
 * folder is in use, "Volver a la memoria interna". Nothing is shown until the location is read.
 */
@Composable
fun DataFolderRows(viewModel: DataFolderViewModel) {
    val location by viewModel.location.collectAsState()
    val status by viewModel.status.collectAsState()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { picked -> viewModel.chooseFolder(picked.toString()) }
        }
    location?.let { current ->
        DataFolderContent(
            current,
            status,
            onChoose = { picker.launch(null) },
            onUseInternal = viewModel::useInternalStorage,
        )
    }
}

/**
 * The current data folder, its actions and a plain line with the result of the last move; while
 * a move runs the actions are hidden.
 */
@Composable
internal fun DataFolderContent(
    location: BookDataLocation,
    status: DataFolderStatus,
    onChoose: () -> Unit,
    onUseInternal: () -> Unit,
) {
    Column {
        MutedText(stringResource(R.string.data_folder_title))
        val name =
            when (location) {
                BookDataLocation.Internal -> stringResource(R.string.data_folder_internal)
                is BookDataLocation.Folder -> WatchedFolder(location.treeUri).displayName
            }
        Text(name, color = DTOXTheme.palette.text)
        MutedText(stringResource(R.string.data_folder_hint))
        when (status) {
            DataFolderStatus.MOVING -> MutedText(stringResource(R.string.data_folder_moving))
            DataFolderStatus.IDLE, DataFolderStatus.FAILED -> {
                if (status == DataFolderStatus.FAILED) {
                    MutedText(stringResource(R.string.data_folder_failed))
                }
                TextAction(stringResource(R.string.data_folder_choose), onChoose)
                if (location is BookDataLocation.Folder) {
                    TextAction(stringResource(R.string.data_folder_use_internal), onUseInternal)
                }
            }
        }
    }
}
