package dev.d1ito.dtox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.d1ito.dtox.AppContainer
import dev.d1ito.dtox.data.storage.FolderAccess
import dev.d1ito.dtox.data.storage.WatchedFolderStore
import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The watched folders of the "Datos" settings (RF-05). A folder is in the list exactly while the
 * app keeps its access: adding keeps the access before storing the folder, and removing gives it
 * back after the folder left the list.
 *
 * @param store where the list is read and written.
 * @param folderAccess keeps and gives back the access to each folder.
 * @param scope runs the changes; cancelled when the ViewModel is cleared.
 */
class WatchedFoldersViewModel(
    private val store: WatchedFolderStore,
    private val folderAccess: FolderAccess,
    private val scope: CoroutineScope,
) : ViewModel(scope) {
    /** Current folders in the order they were added; empty until read from disk. */
    val folders: StateFlow<List<WatchedFolder>> =
        store.folders.stateIn(scope, SharingStarted.Eagerly, initialValue = emptyList())

    private val mutableChangeFailed = MutableStateFlow(false)

    /** `true` after the last add or remove failed and the list was left as it was. */
    val changeFailed: StateFlow<Boolean> = mutableChangeFailed.asStateFlow()

    /** Watches the folder the picker returned as [treeUri]. */
    fun add(treeUri: String) {
        scope.launch { mutableChangeFailed.value = keepAndStore(WatchedFolder(treeUri)).isFailure }
    }

    /** @return success once [folder] is stored with its access kept; on failure, neither is. */
    private suspend fun keepAndStore(folder: WatchedFolder): Result<Unit> {
        folderAccess.keep(folder).onFailure { refused -> return Result.failure(refused) }
        return store.add(folder).onFailure { folderAccess.release(folder) }
    }

    /** Stops watching [folder] and gives back its access; its files are not touched. */
    fun remove(folder: WatchedFolder) {
        scope.launch {
            val removed = store.remove(folder)
            // A failed release means the access was already gone, e.g. revoked by the system:
            // there is nothing left to give back, so the removal still succeeded.
            if (removed.isSuccess) folderAccess.release(folder)
            mutableChangeFailed.value = removed.isFailure
        }
    }

    companion object {
        /**
         * @param folderAccess built from the application's content resolver, which the container
         *   does not hold.
         * @return a factory that builds the ViewModel with the container's folder store.
         */
        fun factory(
            container: AppContainer,
            folderAccess: FolderAccess,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    WatchedFoldersViewModel(
                        store = container.watchedFolderStore,
                        folderAccess = folderAccess,
                        scope = CoroutineScope(SupervisorJob() + container.dispatchers.main),
                    )
                }
            }
    }
}
