package dev.d1ito.dtox.data.storage

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * A file found inside a watched folder.
 *
 * @property uri the document's URI, stable while the file stays where it is.
 * @property displayName the name shown by the provider, e.g. `cartas.epub`.
 * @property mimeType the type reported by the provider, `null` if unknown.
 */
data class FolderDocument(
    val uri: String,
    val displayName: String,
    val mimeType: String?,
)

/** Lists and opens the files of the watched folders (RF-05) through their document provider. */
interface WatchedFolderDocuments {
    /**
     * @return every file of [folder] and its subfolders, in no particular order, or a failure
     *   (e.g. a [SecurityException] once the access was revoked, or an [IOException]).
     */
    suspend fun list(folder: WatchedFolder): Result<List<FolderDocument>>

    /** @return [document]'s content, which the caller closes, or why it could not be opened. */
    suspend fun open(document: FolderDocument): Result<InputStream>
}

private val childColumns =
    arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )
private const val DOCUMENT_ID_COLUMN = 0
private const val DISPLAY_NAME_COLUMN = 1
private const val MIME_TYPE_COLUMN = 2

/** One row of a children query: a file, or a subfolder when [mimeType] is the folder type. */
private class ChildDocument(
    val documentIdentifier: String,
    val displayName: String,
    val mimeType: String?,
)

/**
 * [WatchedFolderDocuments] through the Storage Access Framework: walks the folder tree with
 * `DocumentsContract` children queries, using the persistable access kept by [FolderAccess].
 *
 * @param contentResolver the application's resolver, which outlives every screen.
 * @param ioDispatcher where the provider is queried; never the main thread.
 */
class SafWatchedFolderDocuments(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher,
) : WatchedFolderDocuments {
    override suspend fun list(folder: WatchedFolder): Result<List<FolderDocument>> =
        providerCall {
            val treeUri = Uri.parse(folder.treeUri)
            val documents = mutableListOf<FolderDocument>()
            val pendingFolders = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(treeUri)))
            while (pendingFolders.isNotEmpty()) {
                listChildren(treeUri, pendingFolders.removeFirst(), documents, pendingFolders)
            }
            documents
        }

    override suspend fun open(document: FolderDocument): Result<InputStream> =
        providerCall {
            contentResolver.openInputStream(Uri.parse(document.uri))
                ?: throw FileNotFoundException(document.displayName)
        }

    /**
     * Adds the files directly inside [parentDocumentIdentifier] to [documents] and its subfolders
     * to [pendingFolders].
     *
     * @throws IOException when the provider gives no answer.
     */
    private fun listChildren(
        treeUri: Uri,
        parentDocumentIdentifier: String,
        documents: MutableList<FolderDocument>,
        pendingFolders: ArrayDeque<String>,
    ) {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentIdentifier)
        val cursor =
            contentResolver.query(childrenUri, childColumns, null, null, null)
                ?: throw IOException("No answer for a watched folder")
        val children =
            cursor.use { rows ->
                buildList { while (rows.moveToNext()) add(childOf(rows)) }
            }
        children.forEach { child ->
            if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                pendingFolders.addLast(child.documentIdentifier)
            } else {
                val uri =
                    DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        child.documentIdentifier,
                    )
                documents += FolderDocument(uri.toString(), child.displayName, child.mimeType)
            }
        }
    }

    /** The child document at the cursor's current row. */
    private fun childOf(rows: Cursor): ChildDocument {
        val documentIdentifier = rows.getString(DOCUMENT_ID_COLUMN)
        return ChildDocument(
            documentIdentifier = documentIdentifier,
            displayName = rows.getString(DISPLAY_NAME_COLUMN) ?: documentIdentifier,
            mimeType = rows.getString(MIME_TYPE_COLUMN),
        )
    }

    /** Runs [call] off the main thread, turning the provider's refusals into failures. */
    private suspend fun <T> providerCall(call: () -> T): Result<T> =
        withContext(ioDispatcher) {
            try {
                Result.success(call())
            } catch (unreadable: IOException) {
                Result.failure(unreadable)
            } catch (accessRevoked: SecurityException) {
                Result.failure(accessRevoked)
            } catch (invalidUri: IllegalArgumentException) {
                Result.failure(invalidUri)
            }
        }
}
