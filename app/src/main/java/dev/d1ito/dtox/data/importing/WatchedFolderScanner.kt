package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.data.storage.FolderDocument
import dev.d1ito.dtox.data.storage.FolderSource
import dev.d1ito.dtox.data.storage.LibraryEntry
import dev.d1ito.dtox.data.storage.LibraryIndex
import dev.d1ito.dtox.data.storage.WatchedFolderDocuments
import dev.d1ito.dtox.data.storage.WatchedFolderStore
import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * A book imported from a watched folder during a scan.
 *
 * @property imported the library copy and its parsed book.
 * @property document where it was found.
 */
internal data class FoundBook(
    val imported: ImportedBook,
    val document: FolderDocument,
)

/**
 * Scans the watched folders once, when asked (RF-05; no background service, docs/03-architecture.md
 * §Flujo de importación): imports the books it has not seen yet and marks the books whose document
 * is gone as missing, without deleting anything (docs/02-data-formats.md §7).
 *
 * A document already listed as some entry's [FolderSource] is not read again. A file that cannot
 * be opened or imported is skipped and tried again on the next scan. Callers run one scan at a
 * time, and not at the same time as other imports or removals.
 */
class WatchedFolderScanner(
    private val watchedFolderStore: WatchedFolderStore,
    private val folderDocuments: WatchedFolderDocuments,
    private val bookImporter: BookImporter,
    private val libraryIndex: LibraryIndex,
) {
    /** Whether there is any folder to scan, then every change. */
    val hasFolders: Flow<Boolean> =
        watchedFolderStore.folders.map { folders ->
            folders.isNotEmpty()
        }

    /**
     * Scans every watched folder; [libraryIndex] must be loaded. Each folder's changes are stored
     * in one write, so an interrupted scan keeps the folders already done.
     *
     * @return the folders that could not be listed (their books keep their marks), or the index
     *   write failure that stopped the scan.
     */
    suspend fun scan(): Result<List<WatchedFolder>> {
        val unreadableFolders = mutableListOf<WatchedFolder>()
        for (folder in watchedFolderStore.folders.first()) {
            val documents = folderDocuments.list(folder).getOrNull()
            if (documents == null) {
                unreadableFolders += folder
                continue
            }
            scanFolder(folder, documents).onFailure { writeFailure ->
                return Result.failure(writeFailure)
            }
        }
        return Result.success(unreadableFolders)
    }

    private suspend fun scanFolder(
        folder: WatchedFolder,
        documents: List<FolderDocument>,
    ): Result<Unit> {
        val books =
            documents.filter { document ->
                bookImporter.supports(document.displayName, document.mimeType)
            }
        val knownUris =
            libraryIndex.entries.value.mapNotNullTo(
                HashSet(),
            ) { entry -> entry.source?.documentUri }
        val found =
            books
                .filterNot { document -> document.uri in knownUris }
                .mapNotNull { document -> importedOrNull(document) }
        val presentUris = books.mapTo(HashSet()) { document -> document.uri }
        return libraryIndex.update { entries ->
            reconciledEntries(entries, folder, presentUris, found)
        }
    }

    /** @return [document] copied into the library, or `null` when it cannot be read or parsed. */
    private suspend fun importedOrNull(document: FolderDocument): FoundBook? {
        val source = folderDocuments.open(document).getOrNull() ?: return null
        val imported =
            try {
                source.use { stream ->
                    bookImporter.import(stream, document.displayName, document.mimeType)
                }
            } catch (closeFailure: IOException) {
                Result.failure(closeFailure)
            }
        return imported.getOrNull()?.let { book -> FoundBook(book, document) }
    }
}

/**
 * The library after scanning [folder]: its entries are marked missing unless their document is in
 * [presentUris], then each of [found] is listed. A book already in the library keeps its entry
 * (and its reading data); it only takes the new source when it had none or its own is missing.
 */
internal fun reconciledEntries(
    entries: List<LibraryEntry>,
    folder: WatchedFolder,
    presentUris: Set<String>,
    found: List<FoundBook>,
): List<LibraryEntry> {
    val marked =
        entries.map { entry ->
            val source = entry.source
            if (source?.folder == folder) {
                entry.copy(source = source.copy(missing = source.documentUri !in presentUris))
            } else {
                entry
            }
        }
    return found.fold(marked) { current, book ->
        withFoundBook(current, FolderSource(folder, book.document.uri), book)
    }
}

private fun withFoundBook(
    entries: List<LibraryEntry>,
    source: FolderSource,
    found: FoundBook,
): List<LibraryEntry> {
    val identifier = found.imported.book.identifier
    val listed =
        entries.find { entry -> entry.bookIdentifier == identifier }
            ?: return entries + found.imported.newLibraryEntry(source)
    if (listed.source?.missing == false) return entries
    return entries.map { entry -> if (entry === listed) entry.copy(source = source) else entry }
}
