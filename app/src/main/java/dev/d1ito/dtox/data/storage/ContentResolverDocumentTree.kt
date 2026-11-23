package dev.d1ito.dtox.data.storage

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileNotFoundException
import java.io.IOException

/** Lets a temporary or data file keep its exact name: no extension is added for this type. */
private const val FILE_MIME_TYPE = "application/octet-stream"

/** Truncates before writing, so no bytes of the previous content remain. */
private const val TRUNCATING_WRITE_MODE = "wt"

private val documentColumns =
    arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
    )

/**
 * [DocumentTree] through [DocumentsContract] on a tree the app holds a persistable read and write
 * permission for (docs/03-architecture.md §Almacenamiento y permisos).
 *
 * @param treeUri as `ACTION_OPEN_DOCUMENT_TREE` returned it.
 */
class ContentResolverDocumentTree(
    private val contentResolver: ContentResolver,
    private val treeUri: Uri,
) : DocumentTree {
    override val rootIdentifier: String
        get() = providerCall { DocumentsContract.getTreeDocumentId(treeUri) }

    override fun children(directoryIdentifier: String): List<TreeDocument> =
        providerCall {
            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryIdentifier)
            queried(childrenUri).use { cursor ->
                generateSequence { if (cursor.moveToNext()) treeDocumentOf(cursor) else null }
                    .toList()
            }
        }

    override fun createDirectory(
        parentIdentifier: String,
        name: String,
    ): TreeDocument = created(parentIdentifier, Document.MIME_TYPE_DIR, name)

    override fun createFile(
        parentIdentifier: String,
        name: String,
    ): TreeDocument = created(parentIdentifier, FILE_MIME_TYPE, name)

    override fun readBytes(documentIdentifier: String): ByteArray =
        providerCall {
            val stream =
                contentResolver.openInputStream(documentUriOf(documentIdentifier))
                    ?: throw FileNotFoundException("The provider gave no stream")
            stream.use { input -> input.readBytes() }
        }

    override fun writeBytes(
        documentIdentifier: String,
        content: ByteArray,
    ) = providerCall {
        val uri = documentUriOf(documentIdentifier)
        val stream =
            contentResolver.openOutputStream(uri, TRUNCATING_WRITE_MODE)
                ?: throw FileNotFoundException("The provider gave no stream")
        stream.use { output -> output.write(content) }
    }

    override fun rename(
        documentIdentifier: String,
        newName: String,
    ): TreeDocument =
        providerCall {
            val renamed =
                DocumentsContract.renameDocument(
                    contentResolver,
                    documentUriOf(documentIdentifier),
                    newName,
                ) ?: throw IOException("The provider did not rename the file")
            described(renamed)
        }

    override fun delete(documentIdentifier: String) =
        providerCall {
            val documentUri = documentUriOf(documentIdentifier)
            if (!DocumentsContract.deleteDocument(contentResolver, documentUri)) {
                throw IOException("The provider did not delete the file")
            }
        }

    private fun created(
        parentIdentifier: String,
        mimeType: String,
        name: String,
    ): TreeDocument =
        providerCall {
            val createdUri =
                DocumentsContract.createDocument(
                    contentResolver,
                    documentUriOf(parentIdentifier),
                    mimeType,
                    name,
                ) ?: throw IOException("The provider did not create the document")
            described(createdUri)
        }

    private fun described(documentUri: Uri): TreeDocument =
        queried(documentUri).use { cursor ->
            if (!cursor.moveToFirst()) throw FileNotFoundException("The document is gone")
            treeDocumentOf(cursor)
        }

    /** @throws IOException if the provider returns no cursor. */
    private fun queried(uri: Uri): Cursor =
        contentResolver.query(uri, documentColumns, null, null, null)
            ?: throw IOException("The provider returned no listing")

    private fun documentUriOf(documentIdentifier: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentIdentifier)
}

/**
 * Runs [call], turning the provider's refusals (e.g. a revoked permission or an unsupported
 * operation) into the [IOException] every [DocumentTree] call promises.
 */
private fun <T> providerCall(call: () -> T): T =
    try {
        call()
    } catch (refused: SecurityException) {
        throw IOException("The folder is no longer accessible", refused)
    } catch (rejected: IllegalArgumentException) {
        throw IOException("The provider rejected the call", rejected)
    } catch (failed: IllegalStateException) {
        throw IOException("The provider failed", failed)
    } catch (unsupported: UnsupportedOperationException) {
        throw IOException("The provider does not support the call", unsupported)
    }

/**
 * The document at [cursor]'s current row, read with the columns of [documentColumns].
 *
 * @throws IOException if the row has no id, name or type.
 */
private fun treeDocumentOf(cursor: Cursor): TreeDocument {
    val lastModifiedColumn = cursor.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
    val flagsColumn = cursor.getColumnIndex(Document.COLUMN_FLAGS)
    val flags = if (flagsColumn >= 0) cursor.getInt(flagsColumn) else 0
    return TreeDocument(
        documentIdentifier = requiredText(cursor, Document.COLUMN_DOCUMENT_ID),
        name = requiredText(cursor, Document.COLUMN_DISPLAY_NAME),
        isDirectory = requiredText(cursor, Document.COLUMN_MIME_TYPE) == Document.MIME_TYPE_DIR,
        lastModifiedMillis =
            lastModifiedColumn
                .takeIf { column -> column >= 0 && !cursor.isNull(column) }
                ?.let(cursor::getLong),
        supportsRename = (flags and Document.FLAG_SUPPORTS_RENAME) != 0,
    )
}

/** @throws IOException if [column] is missing or empty in [cursor]'s current row. */
private fun requiredText(
    cursor: Cursor,
    column: String,
): String {
    val index = cursor.getColumnIndex(column)
    val text = if (index >= 0) cursor.getString(index) else null
    return text ?: throw IOException("The provider left out $column")
}
