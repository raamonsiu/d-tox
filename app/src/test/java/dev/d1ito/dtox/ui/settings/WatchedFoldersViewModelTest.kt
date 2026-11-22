package dev.d1ito.dtox.ui.settings

import dev.d1ito.dtox.data.storage.FolderAccess
import dev.d1ito.dtox.data.storage.WatchedFolderStore
import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val BOOKS_URI = "content://com.android.externalstorage.documents/tree/primary%3ABooks"

class WatchedFoldersViewModelTest {
    private val books = WatchedFolder(BOOKS_URI)
    private val store = InMemoryWatchedFolderStore()
    private val folderAccess = RecordingFolderAccess()
    private val viewModel =
        WatchedFoldersViewModel(store, folderAccess, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun add_keepsTheAccessAndStoresTheFolder() {
        viewModel.add(BOOKS_URI)

        assertEquals(setOf(books), folderAccess.kept)
        assertEquals(listOf(books), viewModel.folders.value)
        assertFalse(viewModel.changeFailed.value)
    }

    @Test
    fun add_whenTheAccessIsRefused_storesNothingAndReportsIt() {
        folderAccess.refuse = true

        viewModel.add(BOOKS_URI)

        assertEquals(emptyList<WatchedFolder>(), viewModel.folders.value)
        assertTrue(viewModel.changeFailed.value)
    }

    @Test
    fun add_whenTheListCannotBeSaved_givesTheAccessBack() {
        store.failWrites = true

        viewModel.add(BOOKS_URI)

        assertEquals(emptySet<WatchedFolder>(), folderAccess.kept)
        assertEquals(emptyList<WatchedFolder>(), viewModel.folders.value)
        assertTrue(viewModel.changeFailed.value)
    }

    @Test
    fun remove_dropsTheFolderAndGivesTheAccessBack() {
        viewModel.add(BOOKS_URI)

        viewModel.remove(books)

        assertEquals(emptyList<WatchedFolder>(), viewModel.folders.value)
        assertEquals(emptySet<WatchedFolder>(), folderAccess.kept)
        assertFalse(viewModel.changeFailed.value)
    }

    @Test
    fun remove_whenTheListCannotBeSaved_keepsTheFolderAndItsAccess() {
        viewModel.add(BOOKS_URI)
        store.failWrites = true

        viewModel.remove(books)

        assertEquals(listOf(books), viewModel.folders.value)
        assertEquals(setOf(books), folderAccess.kept)
        assertTrue(viewModel.changeFailed.value)
    }

    @Test
    fun remove_ofAFolderWhoseAccessIsAlreadyGone_stillSucceeds() {
        viewModel.add(BOOKS_URI)
        folderAccess.kept.clear()

        viewModel.remove(books)

        assertEquals(emptyList<WatchedFolder>(), viewModel.folders.value)
        assertFalse(viewModel.changeFailed.value)
    }

    @Test
    fun aSuccessfulChange_clearsAPreviousFailure() {
        folderAccess.refuse = true
        viewModel.add(BOOKS_URI)
        folderAccess.refuse = false

        viewModel.add(BOOKS_URI)

        assertFalse(viewModel.changeFailed.value)
    }

    private class InMemoryWatchedFolderStore : WatchedFolderStore {
        private val current = MutableStateFlow(emptyList<WatchedFolder>())
        override val folders: StateFlow<List<WatchedFolder>> get() = current
        var failWrites = false

        override suspend fun add(folder: WatchedFolder): Result<Unit> =
            write(if (folder in current.value) current.value else current.value + folder)

        override suspend fun remove(folder: WatchedFolder): Result<Unit> =
            write(
                current.value - folder,
            )

        private fun write(folders: List<WatchedFolder>): Result<Unit> {
            if (failWrites) return Result.failure(IOException("disk full"))
            current.value = folders
            return Result.success(Unit)
        }
    }

    /** Grants every folder unless [refuse]; releasing an access not held fails, as on Android. */
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
