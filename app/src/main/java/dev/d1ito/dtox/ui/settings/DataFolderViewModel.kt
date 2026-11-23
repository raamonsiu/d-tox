package dev.d1ito.dtox.ui.settings

import android.content.ContentResolver
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.d1ito.dtox.AppContainer
import dev.d1ito.dtox.data.storage.BookDataFolder
import dev.d1ito.dtox.data.storage.BookDataLocation
import dev.d1ito.dtox.data.storage.FolderAccess
import dev.d1ito.dtox.data.storage.PersistableFolderAccess
import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The data files are read and written in the chosen folder. */
private const val READ_AND_WRITE_ACCESS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

/** Where the last change of the data folder stands, for the line the settings screen shows. */
enum class DataFolderStatus { IDLE, MOVING, FAILED }

/**
 * The "Carpeta de datos" setting (RF-27). The app keeps read and write access to the chosen folder
 * exactly while its data lives there: the access is kept before moving and given back once the
 * data left the folder (or the move failed).
 *
 * @param dataFolder moves the data files and remembers where they are.
 * @param folderAccess keeps and gives back the read and write access to the folder.
 * @param scope runs the moves; cancelled when the ViewModel is cleared.
 */
class DataFolderViewModel(
    private val dataFolder: BookDataFolder,
    private val folderAccess: FolderAccess,
    private val scope: CoroutineScope,
) : ViewModel(scope) {
    private val mutableLocation = MutableStateFlow<BookDataLocation?>(null)
    private val mutableStatus = MutableStateFlow(DataFolderStatus.IDLE)

    /** Where the data files live; `null` until read from disk. */
    val location: StateFlow<BookDataLocation?> = mutableLocation.asStateFlow()

    /** How the last move went; [DataFolderStatus.IDLE] before the first one. */
    val status: StateFlow<DataFolderStatus> = mutableStatus.asStateFlow()

    init {
        scope.launch { mutableLocation.value = dataFolder.currentLocation() }
    }

    /** Moves the data to the folder the picker returned as [treeUri]. Ignored during a move. */
    fun chooseFolder(treeUri: String) {
        val folder = WatchedFolder(treeUri)
        move(BookDataLocation.Folder(treeUri)) { folderAccess.keep(folder) }
    }

    /** Moves the data back to the app's own storage. Ignored during a move. */
    fun useInternalStorage() {
        move(BookDataLocation.Internal) { Result.success(Unit) }
    }

    /**
     * Gets the access with [takeAccess], moves to [target] and gives back the access no longer
     * needed: the previous folder's on success, the target's on failure.
     */
    private fun move(
        target: BookDataLocation,
        takeAccess: suspend () -> Result<Unit>,
    ) {
        if (mutableStatus.value == DataFolderStatus.MOVING) return
        mutableStatus.value = DataFolderStatus.MOVING
        scope.launch {
            val previous = dataFolder.currentLocation()
            val access = takeAccess()
            val moved = if (access.isFailure) access else dataFolder.moveTo(target)
            val released = if (moved.isSuccess) previous else target
            if (released != dataFolder.currentLocation() && released is BookDataLocation.Folder) {
                // A failed release means the access was already gone: nothing left to give back.
                folderAccess.release(WatchedFolder(released.treeUri))
            }
            mutableLocation.value = dataFolder.currentLocation()
            mutableStatus.value =
                if (moved.isSuccess) DataFolderStatus.IDLE else DataFolderStatus.FAILED
        }
    }

    companion object {
        /**
         * @param contentResolver the application's resolver, which the container does not hold;
         *   it keeps the read and write access to the chosen folder.
         * @return a factory that builds the ViewModel with the container's data folder.
         */
        fun factory(
            container: AppContainer,
            contentResolver: ContentResolver,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DataFolderViewModel(
                        dataFolder = container.bookDataFolder,
                        folderAccess =
                            PersistableFolderAccess(
                                contentResolver,
                                container.dispatchers.io,
                                READ_AND_WRITE_ACCESS,
                            ),
                        scope = CoroutineScope(SupervisorJob() + container.dispatchers.main),
                    )
                }
            }
    }
}
