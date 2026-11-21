package dev.d1ito.dtox.domain

import java.net.URLDecoder

private const val TREE_SEGMENT = "/tree/"
private const val VOLUME_SEPARATOR = ':'
private const val PATH_SEPARATOR = '/'

/**
 * A folder the user chose to watch for books (RF-05), as the Storage Access Framework tree URI
 * the picker returned, e.g. `content://com.android.externalstorage.documents/tree/primary%3ABooks`.
 *
 * @property treeUri the URI exactly as granted; it identifies the folder and its permission.
 */
@JvmInline
value class WatchedFolder(
    val treeUri: String,
) {
    /**
     * The folder's own name, read from the URI without asking its provider: the last path part of
     * the tree's document id (`primary:Books/Novelas` → `Novelas`), the volume for a volume root
     * (`primary:` → `primary`), or the whole URI when it has no tree id.
     */
    val displayName: String
        get() {
            val documentIdentifier = treeDocumentIdentifier() ?: return treeUri
            val path = documentIdentifier.substringAfter(VOLUME_SEPARATOR)
            val lastPart = path.trimEnd(PATH_SEPARATOR).substringAfterLast(PATH_SEPARATOR)
            return lastPart.ifEmpty { documentIdentifier.substringBefore(VOLUME_SEPARATOR) }
        }

    /**
     * @return the decoded tree document id (the path segment after `/tree/`), or `null` when the
     *   URI has none or its percent-encoding is malformed.
     */
    private fun treeDocumentIdentifier(): String? {
        val encoded =
            treeUri
                .substringAfter(TREE_SEGMENT, missingDelimiterValue = "")
                .substringBefore(PATH_SEPARATOR)
        if (encoded.isEmpty()) return null
        return try {
            // URI segments encode a literal '+' as %2B; keep a raw '+' from becoming a space.
            URLDecoder.decode(encoded.replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (expectedMalformed: IllegalArgumentException) {
            null
        }
    }
}
