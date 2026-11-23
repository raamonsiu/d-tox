package dev.d1ito.dtox.ui.settings

import dev.d1ito.dtox.data.storage.BookDataFolder
import dev.d1ito.dtox.data.storage.BookDataLocation
import dev.d1ito.dtox.data.storage.DirectoryBookFileStorage
import dev.d1ito.dtox.data.storage.DocumentTreeBookFileStorage
import dev.d1ito.dtox.data.storage.FakeDocumentTree
import dev.d1ito.dtox.data.storage.FolderAccess
import dev.d1ito.dtox.data.storage.SyncedAtomicFileWriter
import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val SYNC_URI = "content://com.android.externalstorage.documents/tree/primary%3ASync"
private const val OTHER_URI = "content://com.android.externalstorage.documents/tree/primary%3AOtra"

class DataFolderViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val trees = mutableMapOf<String, FakeDocumentTree>()
    private val folderAccess = RecordingFolderAccess()
    private val viewModel by lazy {
        val fileWriter = SyncedAtomicFileWriter(Dispatchers.Unconfined)
        val dataFolder =
            BookDataFolder(
                locationFile = File(temporaryFolder.root, "data-folder.json"),
                internalStorage =
                    DirectoryBookFileStorage(
                        File(temporaryFolder.root, "books"),
                        fileWriter,
                        Dispatchers.Unconfined,
                    ),
                folderStorageOf = { treeUri ->
                    val tree = trees.getOrPut(treeUri) { FakeDocumentTree() }
                    DocumentTreeBookFileStorage(tree, Dispatchers.Unconfined)
                },
                fileWriter = fileWriter,
                ioDispatcher = Dispatchers.Unconfined,
            )
        DataFolderViewModel(dataFolder, folderAccess, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun startsWithTheInternalStorage() {
        assertEquals(BookDataLocation.Internal, viewModel.location.value)
        assertEquals(DataFolderStatus.IDLE, viewModel.status.value)
    }

    @Test
    fun chooseFolder_keepsItsAccessAndMovesThere() {
        viewModel.chooseFolder(SYNC_URI)

        assertEquals(BookDataLocation.Folder(SYNC_URI), viewModel.location.value)
        assertEquals(setOf(WatchedFolder(SYNC_URI)), folderAccess.kept)
        assertEquals(DataFolderStatus.IDLE, viewModel.status.value)
    }

    @Test
    fun chooseAnotherFolder_givesBackTheAccessOfThePreviousOne() {
        viewModel.chooseFolder(SYNC_URI)

        viewModel.chooseFolder(OTHER_URI)

        assertEquals(BookDataLocation.Folder(OTHER_URI), viewModel.location.value)
        assertEquals(setOf(WatchedFolder(OTHER_URI)), folderAccess.kept)
    }

    @Test
    fun refusedAccess_staysAndReportsIt() {
        folderAccess.refuse = true

        viewModel.chooseFolder(SYNC_URI)

        assertEquals(BookDataLocation.Internal, viewModel.location.value)
        assertEquals(DataFolderStatus.FAILED, viewModel.status.value)
    }

    @Test
    fun failedMove_givesTheNewAccessBack() {
        trees[SYNC_URI] = FakeDocumentTree().apply { failWritesOfNamesEndingWith = ".tmp" }
        File(temporaryFolder.root, "books").mkdirs()
        File(temporaryFolder.root, "books/sha256:0123456789abcdef0123456789abcdef.json")
            .writeText("{}")

        viewModel.chooseFolder(SYNC_URI)

        assertEquals(BookDataLocation.Internal, viewModel.location.value)
        assertEquals(emptySet<WatchedFolder>(), folderAccess.kept)
        assertEquals(DataFolderStatus.FAILED, viewModel.status.value)
    }

    @Test
    fun useInternalStorage_movesBackAndGivesTheAccessBack() {
        viewModel.chooseFolder(SYNC_URI)

        viewModel.useInternalStorage()

        assertEquals(BookDataLocation.Internal, viewModel.location.value)
        assertEquals(emptySet<WatchedFolder>(), folderAccess.kept)
    }

    private class RecordingFolderAccess : FolderAccess {
        val kept = mutableSetOf<WatchedFolder>()
        var refuse = false

        override suspend fun keep(folder: WatchedFolder): Result<Unit> {
            if (refuse) return Result.failure(SecurityException("not granted"))
            kept += folder
            return Result.success(Unit)
        }

        override suspend fun release(folder: WatchedFolder): Result<Unit> =
            if (kept.remove(folder)) {
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("not held"))
            }
    }
}
