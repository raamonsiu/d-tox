package dev.d1ito.dtox.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.WatchedFolder
import dev.d1ito.dtox.ui.theme.DTOXTheme

/**
 * The "Datos" section (docs/04-design.md §Ajustes) with [viewModel]'s watched folders; "Añadir
 * carpeta" opens the system folder picker (`ACTION_OPEN_DOCUMENT_TREE`).
 *
 * @param extraRows further "Datos" rows shown after the folders, e.g. exporting the user's data.
 */
@Composable
fun DataSettingsSection(
    viewModel: WatchedFoldersViewModel,
    extraRows: @Composable () -> Unit = {},
) {
    val folders by viewModel.folders.collectAsState()
    val changeFailed by viewModel.changeFailed.collectAsState()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { picked -> viewModel.add(picked.toString()) }
        }
    DataSettingsContent(
        WatchedFoldersState(folders, changeFailed),
        WatchedFolderActions(onAdd = { picker.launch(null) }, onRemove = viewModel::remove),
        extraRows,
    )
}

/**
 * @property folders the watched folders, in the order they were added.
 * @property changeFailed shows that the last add or remove could not be done.
 */
class WatchedFoldersState(
    val folders: List<WatchedFolder>,
    val changeFailed: Boolean,
)

/**
 * @property onAdd opens the folder picker.
 * @property onRemove stops watching the touched folder.
 */
class WatchedFolderActions(
    val onAdd: () -> Unit,
    val onRemove: (WatchedFolder) -> Unit,
)

/**
 * The "Datos" heading, the watched folders with a "Quitar" action each, "Añadir carpeta", and then
 * [extraRows]. Emits its rows straight into the settings column, like the other sections.
 */
@Composable
internal fun DataSettingsContent(
    state: WatchedFoldersState,
    actions: WatchedFolderActions,
    extraRows: @Composable () -> Unit = {},
) {
    Text(stringResource(R.string.data_section), style = MaterialTheme.typography.titleMedium)
    if (state.changeFailed) MutedText(stringResource(R.string.data_folders_change_failed))
    Column {
        MutedText(stringResource(R.string.data_watched_folders))
        if (state.folders.isEmpty()) MutedText(stringResource(R.string.data_no_folders))
        state.folders.forEach { folder -> WatchedFolderRow(folder) { actions.onRemove(folder) } }
        TextAction(stringResource(R.string.data_add_folder), actions.onAdd)
    }
    extraRows()
}

/** The folder's name and a "Quitar" button that names the folder for screen readers. */
@Composable
private fun WatchedFolderRow(
    folder: WatchedFolder,
    onRemove: () -> Unit,
) {
    val name = folder.displayName
    val removeDescription = stringResource(R.string.data_remove_folder_description, name)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = DTOXTheme.palette.text, modifier = Modifier.weight(1f))
        TextButton(
            onClick = onRemove,
            modifier =
                Modifier
                    .sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget)
                    .semantics { contentDescription = removeDescription },
        ) {
            Text(stringResource(R.string.data_remove_folder), color = DTOXTheme.palette.text)
        }
    }
}
