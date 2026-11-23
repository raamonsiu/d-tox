package dev.d1ito.dtox.data.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Keeps or gives back the app's read access to a folder across restarts. */
interface FolderAccess {
    /**
     * Keeps the read access the folder picker just granted, so it survives app restarts.
     *
     * @return success, or a [SecurityException] when the access was not granted (or is no longer).
     */
    suspend fun keep(folder: WatchedFolder): Result<Unit>

    /**
     * Gives back the access kept with [keep]; the folder can no longer be read.
     *
     * @return success, or a [SecurityException] when no access was held for [folder].
     */
    suspend fun release(folder: WatchedFolder): Result<Unit>
}

/**
 * [FolderAccess] through the Storage Access Framework's persistable URI permissions
 * (docs/03-architecture.md §Flujo de importación); no storage permission is needed.
 *
 * @param contentResolver the application's resolver, which outlives every screen.
 * @param ioDispatcher where the calls to the system run; never the main thread.
 * @param accessFlags the access kept and given back: read by default, or read and write for the
 *   data folder (RF-27).
 */
class PersistableFolderAccess(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher,
    private val accessFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION,
) : FolderAccess {
    override suspend fun keep(folder: WatchedFolder): Result<Unit> =
        permissionCall(folder) { uri ->
            contentResolver.takePersistableUriPermission(uri, accessFlags)
        }

    override suspend fun release(folder: WatchedFolder): Result<Unit> =
        permissionCall(folder) { uri ->
            contentResolver.releasePersistableUriPermission(uri, accessFlags)
        }

    /** Runs [call] on [folder]'s URI, turning the system's refusal into a failure. */
    private suspend fun permissionCall(
        folder: WatchedFolder,
        call: (Uri) -> Unit,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                call(Uri.parse(folder.treeUri))
                Result.success(Unit)
            } catch (refused: SecurityException) {
                Result.failure(refused)
            }
        }
}
