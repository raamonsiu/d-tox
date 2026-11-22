package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.data.storage.FolderDocument
import dev.d1ito.dtox.data.storage.WatchedFolderDocuments
import dev.d1ito.dtox.data.storage.WatchedFolderStore
import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * An in-memory document provider: each folder holds files by name, with their text. A folder in
 * [unreadableFolders] fails to list, as one whose access was revoked.
 */
class FakeWatchedFolderDocuments : WatchedFolderDocuments {
    private val filesByFolder = mutableMapOf<WatchedFolder, MutableMap<String, String>>()
    val unreadableFolders = mutableSetOf<WatchedFolder>()

    /** URIs of every document opened, in order. */
    val opened = mutableListOf<String>()

    fun put(
        folder: WatchedFolder,
        fileName: String,
        content: String,
    ) {
        filesByFolder.getOrPut(folder) { mutableMapOf() }[fileName] = content
    }

    fun delete(
        folder: WatchedFolder,
        fileName: String,
    ) {
        filesByFolder[folder]?.remove(fileName)
    }

    override suspend fun list(folder: WatchedFolder): Result<List<FolderDocument>> {
        if (folder in unreadableFolders) return Result.failure(SecurityException("revoked"))
        val names = filesByFolder[folder]?.keys.orEmpty()
        return Result.success(names.map { name -> FolderDocument(uriOf(folder, name), name, null) })
    }

    override suspend fun open(document: FolderDocument): Result<InputStream> {
        opened += document.uri
        val content =
            filesByFolder.firstNotNullOfOrNull { (folder, files) ->
                files.entries
                    .firstOrNull { (name, _) ->
                        uriOf(folder, name) == document.uri
                    }?.value
            } ?: return Result.failure(FileNotFoundException(document.uri))
        return Result.success(ByteArrayInputStream(content.toByteArray()))
    }

    /** The URI the fake gives [fileName] inside [folder]. */
    fun uriOf(
        folder: WatchedFolder,
        fileName: String,
    ): String = "${folder.treeUri}/document/$fileName"
}

/** A fixed list of watched folders that tests change directly. */
class FakeWatchedFolderStore(
    initial: List<WatchedFolder> = emptyList(),
) : WatchedFolderStore {
    val current = MutableStateFlow(initial)
    override val folders: StateFlow<List<WatchedFolder>> get() = current

    override suspend fun add(folder: WatchedFolder): Result<Unit> {
        current.value += folder
        return Result.success(Unit)
    }

    override suspend fun remove(folder: WatchedFolder): Result<Unit> {
        current.value -= folder
        return Result.success(Unit)
    }
}
