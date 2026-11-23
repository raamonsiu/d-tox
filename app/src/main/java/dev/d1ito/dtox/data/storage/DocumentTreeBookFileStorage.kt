package dev.d1ito.dtox.data.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

/** Folder of the chosen tree that holds the data files, like `files/books` inside the app. */
internal const val BOOKS_FOLDER_NAME = "books"
private const val TEMPORARY_SUFFIX = ".tmp"

/**
 * A document of a [DocumentTree], as its provider describes it.
 *
 * @property documentIdentifier the provider's id of the document.
 * @property name its display name, which the provider may have changed on creation or rename.
 * @property lastModifiedMillis `null` when the provider does not tell.
 * @property supportsRename whether the provider can rename it.
 */
data class TreeDocument(
    val documentIdentifier: String,
    val name: String,
    val isDirectory: Boolean,
    val lastModifiedMillis: Long?,
    val supportsRename: Boolean,
)

/**
 * The few Storage Access Framework calls the data folder needs, on one folder tree the user chose.
 * Every call blocks and throws an [IOException] when the provider fails or the access is gone.
 */
interface DocumentTree {
    /** The id of the folder the user chose. */
    val rootIdentifier: String

    fun children(directoryIdentifier: String): List<TreeDocument>

    fun createDirectory(
        parentIdentifier: String,
        name: String,
    ): TreeDocument

    /** Creates an empty file; the provider may give it another name if [name] is taken. */
    fun createFile(
        parentIdentifier: String,
        name: String,
    ): TreeDocument

    fun readBytes(documentIdentifier: String): ByteArray

    /** Replaces the whole content of the file; a failure may leave it partly written. */
    fun writeBytes(
        documentIdentifier: String,
        content: ByteArray,
    )

    /** @return the renamed document, whose name the provider may have changed if it was taken. */
    fun rename(
        documentIdentifier: String,
        newName: String,
    ): TreeDocument

    fun delete(documentIdentifier: String)
}

/**
 * [BookFileStorage] in the `books` folder of a [DocumentTree] (RF-27), e.g. one synced with
 * Syncthing. A provider cannot replace a file atomically, so a write is: write `<name>.tmp`, read
 * it back to verify, delete the old file and rename the copy to `<name>`. A crash between the
 * last two steps leaves only the verified `<name>.tmp`, which [read] puts back in place (a
 * `<name>.tmp` cut short on the very first write is then set aside as corrupt). When the
 * provider cannot rename, the old file is overwritten after the copy is verified; a crash during
 * that overwrite can leave it incomplete, and it is then set aside as corrupt (docs/02 §4).
 *
 * @param ioDispatcher where the provider calls run; never the main thread.
 */
class DocumentTreeBookFileStorage(
    private val tree: DocumentTree,
    private val ioDispatcher: CoroutineDispatcher,
) : BookFileStorage {
    override suspend fun read(name: String): Result<ByteArray?> =
        treeCall {
            val folder = booksFolder() ?: return@treeCall null
            val children = tree.children(folder.documentIdentifier)
            val file =
                children.named(name)
                    ?: children
                        .named(name + TEMPORARY_SUFFIX)
                        ?.takeIf { interrupted -> interrupted.supportsRename }
                        ?.let { interrupted -> renamedExactly(interrupted, name) }
            file?.let { document -> tree.readBytes(document.documentIdentifier) }
        }

    override suspend fun write(
        name: String,
        content: ByteArray,
    ): Result<Unit> =
        treeCall {
            val folder = booksFolder() ?: createdBooksFolder()
            val children = tree.children(folder.documentIdentifier)
            children.named(name + TEMPORARY_SUFFIX)?.let { stale ->
                tree.delete(stale.documentIdentifier)
            }
            val copy = createdExactly(folder, name + TEMPORARY_SUFFIX)
            writeVerified(copy, content)
            val previous = children.named(name)
            if (copy.supportsRename) {
                previous?.let { old -> tree.delete(old.documentIdentifier) }
                renamedExactly(copy, name)
            } else {
                writeVerified(previous ?: createdExactly(folder, name), content)
                tree.delete(copy.documentIdentifier)
            }
            Unit
        }

    override suspend fun list(): Result<List<StoredBookFile>> =
        treeCall {
            val folder = booksFolder() ?: return@treeCall emptyList()
            tree
                .children(folder.documentIdentifier)
                .filterNot { document -> document.isDirectory }
                .map { document -> StoredBookFile(document.name, document.lastModifiedMillis) }
        }

    override suspend fun rename(
        name: String,
        newName: String,
    ): Result<Unit> =
        treeCall {
            val folder = booksFolder() ?: throw IOException("No $BOOKS_FOLDER_NAME folder")
            val file =
                tree.children(folder.documentIdentifier).named(name)
                    ?: throw IOException("No file to rename")
            renamedExactly(file, newName)
            Unit
        }

    private fun booksFolder(): TreeDocument? =
        tree
            .children(tree.rootIdentifier)
            .firstOrNull { document -> document.isDirectory && document.name == BOOKS_FOLDER_NAME }

    /** @throws IOException if the provider gave the folder another name. */
    private fun createdBooksFolder(): TreeDocument {
        val created = tree.createDirectory(tree.rootIdentifier, BOOKS_FOLDER_NAME)
        if (created.name != BOOKS_FOLDER_NAME) throw IOException("The folder name was not kept")
        return created
    }

    /** @throws IOException if the provider gave the new file another name; it is then deleted. */
    private fun createdExactly(
        folder: TreeDocument,
        name: String,
    ): TreeDocument {
        val created = tree.createFile(folder.documentIdentifier, name)
        if (created.name != name) {
            tree.delete(created.documentIdentifier)
            throw IOException("The provider did not keep the file name")
        }
        return created
    }

    /** @throws IOException if the provider gave the file another name, e.g. as [name] exists. */
    private fun renamedExactly(
        document: TreeDocument,
        name: String,
    ): TreeDocument {
        val renamed = tree.rename(document.documentIdentifier, name)
        if (renamed.name != name) throw IOException("The provider did not keep the file name")
        return renamed
    }

    /** @throws IOException if the content read back differs from [content]. */
    private fun writeVerified(
        document: TreeDocument,
        content: ByteArray,
    ) {
        tree.writeBytes(document.documentIdentifier, content)
        if (!tree.readBytes(document.documentIdentifier).contentEquals(content)) {
            throw IOException("The written file does not read back the same")
        }
    }

    /** Runs [call] on [ioDispatcher], returning its [IOException] as a failure. */
    private suspend fun <T> treeCall(call: () -> T): Result<T> =
        withContext(ioDispatcher) {
            try {
                Result.success(call())
            } catch (providerFailure: IOException) {
                Result.failure(providerFailure)
            }
        }
}

private fun List<TreeDocument>.named(name: String): TreeDocument? =
    firstOrNull { document -> !document.isDirectory && document.name == name }
