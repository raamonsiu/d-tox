package dev.d1ito.dtox.data.export

import dev.d1ito.dtox.data.export.ImportFixtures.BOOK_A
import dev.d1ito.dtox.data.export.ImportFixtures.BOOK_B
import dev.d1ito.dtox.data.export.ImportFixtures.EARLIER
import dev.d1ito.dtox.data.export.ImportFixtures.HEX_A
import dev.d1ito.dtox.data.export.ImportFixtures.HEX_B
import dev.d1ito.dtox.data.export.ImportFixtures.LATER
import dev.d1ito.dtox.data.export.ImportFixtures.MANIFEST
import dev.d1ito.dtox.data.export.ImportFixtures.bookData
import dev.d1ito.dtox.data.export.ImportFixtures.bookEntry
import dev.d1ito.dtox.data.export.ImportFixtures.identifier
import dev.d1ito.dtox.data.export.ImportFixtures.note
import dev.d1ito.dtox.data.export.ImportFixtures.zipOf
import dev.d1ito.dtox.data.storage.BookDataFormat
import dev.d1ito.dtox.data.storage.FolderSource
import dev.d1ito.dtox.data.storage.LibraryEntry
import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

private const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3ABooks"
private const val LOCAL_NOTE = "11111111-1111-4111-8111-111111111111"
private const val IMPORTED_NOTE = "22222222-2222-4222-8222-222222222222"

class UserDataImporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val harness = ImportHarness()
    private val store = harness.store
    private val libraryIndex = harness.libraryIndex
    private val importedBookFiles = harness.importedBookFiles

    private fun import(zip: ByteArray): Result<Unit> = harness.import(zip)

    @Test
    fun validZip_mergesEachBook_andUpdatesTheIndex() {
        val localNote = note(LOCAL_NOTE, "Escrita aquí")
        val importedNote = note(IMPORTED_NOTE, "Escrita en otro móvil")
        runBlocking { store.write(bookData(BOOK_A, EARLIER, listOf(localNote))) }
        store.writes.clear()
        val zip =
            zipOf(
                "manifest.json" to MANIFEST,
                bookEntry(bookData(BOOK_A, LATER, listOf(importedNote))),
                bookEntry(bookData(BOOK_B)),
            )

        assertTrue(import(zip).isSuccess)

        val merged = requireNotNull(runBlocking { store.read(identifier(BOOK_A)) }.getOrThrow())
        assertEquals(LATER, merged.progress?.updatedAt)
        assertEquals(listOf(localNote, importedNote), merged.annotations)
        val added = runBlocking { store.read(identifier(BOOK_B)) }.getOrThrow()
        assertEquals(bookData(BOOK_B), added?.copy(preservedFields = JsonObject(emptyMap())))
        assertEquals(1, libraryIndex.updates)
    }

    @Test
    fun zipWrittenByTheExporter_isImportedWithItsBookFiles() {
        val filesDirectory = temporaryFolder.newFolder("files")
        val exported = bookData(BOOK_A, LATER, listOf(note(IMPORTED_NOTE, "Nota")))
        File(filesDirectory, "books").mkdirs()
        File(filesDirectory, "books/$BOOK_A.json").writeText(BookDataFormat.encode(exported))
        File(filesDirectory, "library").mkdirs()
        File(filesDirectory, "library/$HEX_A.txt").writeText("Texto del libro")
        File(filesDirectory, "settings.json").writeText("""{"version":1}""")
        val zip = ByteArrayOutputStream()
        val manifest = UserDataExportManifest(true, "0.42.0", Instant.parse("2026-11-21T10:00:00Z"))
        writeUserDataZip(filesDirectory, zip, manifest).getOrThrow()

        assertTrue(import(zip.toByteArray()).isSuccess)

        val stored = runBlocking { store.read(identifier(BOOK_A)) }.getOrThrow()
        assertEquals(exported.annotations, stored?.annotations)
        assertEquals(listOf("$HEX_A.txt" to "Texto del libro"), importedBookFiles)
    }

    @Test
    fun bookFiles_alreadyInTheLibrary_areNotImportedAgain() {
        libraryIndex.listed(identifier(BOOK_A))
        val zip =
            zipOf(
                "manifest.json" to MANIFEST,
                "library/$HEX_A.epub" to "a",
                "library/$HEX_B.txt" to "b",
            )

        assertTrue(import(zip).isSuccess)

        assertEquals(listOf("$HEX_B.txt" to "b"), importedBookFiles)
        val listedFiles = libraryIndex.entries.value.map { entry -> entry.fileName }
        assertEquals(listOf("$HEX_A.epub", "$HEX_B.txt"), listedFiles)
    }

    @Test
    fun indexAfterImport_keepsFolderSources_andRefreshesProgress() {
        val source = FolderSource(WatchedFolder(FOLDER), "$FOLDER/a.epub")
        val listed =
            LibraryEntry(identifier(BOOK_A), "A", null, "$HEX_A.epub", EARLIER, false, source)
        val merged = bookData(BOOK_A, LATER)

        val indexed = indexedAfterImport(listOf(listed), added = listOf(listed), listOf(merged))

        assertEquals(listOf(listed.copy(lastOpenedAt = LATER)), indexed)
    }
}
