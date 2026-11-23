package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

private const val IDENTIFIER = "sha256:0123456789abcdef0123456789abcdef"
private const val FILE_NAME = "$IDENTIFIER.json"
private const val TREE_URI = "content://com.android.externalstorage.documents/tree/primary%3ASync"

class BookDataFolderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val tree = FakeDocumentTree()
    private val fileWriter = SyncedAtomicFileWriter(Dispatchers.Unconfined)
    private val booksDirectory by lazy { File(temporaryFolder.root, "books") }
    private val locationFile by lazy { File(temporaryFolder.root, "data-folder.json") }
    private val identifier = BookIdentifier.parse(IDENTIFIER).getOrThrow()
    private val folder = BookDataLocation.Folder(TREE_URI)

    private fun newDataFolder() =
        BookDataFolder(
            locationFile = locationFile,
            internalStorage =
                DirectoryBookFileStorage(booksDirectory, fileWriter, Dispatchers.Unconfined),
            folderStorageOf = { DocumentTreeBookFileStorage(tree, Dispatchers.Unconfined) },
            fileWriter = fileWriter,
            ioDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun withoutAChoice_dataLivesInTheInternalBooksDirectory() {
        val dataFolder = newDataFolder()

        runBlocking { dataFolder.store.write(sampleBookData("Interna")) }.getOrThrow()

        assertEquals(BookDataLocation.Internal, runBlocking { dataFolder.currentLocation() })
        assertTrue(File(booksDirectory, FILE_NAME).isFile)
        assertFalse(locationFile.exists())
    }

    @Test
    fun moveToAFolder_copiesTheDataAndKeepsUsingItAfterARestart() {
        val dataFolder = newDataFolder()
        runBlocking { dataFolder.store.write(sampleBookData("Interna")) }.getOrThrow()

        runBlocking { dataFolder.moveTo(folder) }.getOrThrow()
        runBlocking { dataFolder.store.write(sampleBookData("Externa")) }.getOrThrow()
        val restarted = newDataFolder()

        assertEquals(folder, runBlocking { restarted.currentLocation() })
        assertEquals(
            "Externa",
            runBlocking { restarted.store.read(identifier) }.getOrThrow()?.title,
        )
        assertTrue(File(booksDirectory, FILE_NAME).readText().contains("Interna"))
    }

    @Test
    fun moveBackToInternal_bringsTheNewerFolderDataWith() {
        val dataFolder = newDataFolder()
        runBlocking { dataFolder.store.write(sampleBookData("Antes")) }.getOrThrow()
        File(booksDirectory, FILE_NAME).setLastModified(1_000)
        runBlocking { dataFolder.moveTo(folder) }.getOrThrow()
        runBlocking { dataFolder.store.write(sampleBookData("En la carpeta")) }.getOrThrow()

        runBlocking { dataFolder.moveTo(BookDataLocation.Internal) }.getOrThrow()

        val readBack = runBlocking { newDataFolder().store.read(identifier) }.getOrThrow()
        assertEquals("En la carpeta", readBack?.title)
    }

    @Test
    fun moveToAFolderWithNewerData_keepsTheFolderData() {
        val dataFolder = newDataFolder()
        runBlocking { dataFolder.store.write(sampleBookData("Interna")) }.getOrThrow()
        val newer = File(booksDirectory, FILE_NAME).readText().replace("Interna", "Otro equipo")
        tree.put("books/$FILE_NAME", newer, lastModifiedMillis = Long.MAX_VALUE)

        runBlocking { dataFolder.moveTo(folder) }.getOrThrow()

        assertEquals(
            "Otro equipo",
            runBlocking { dataFolder.store.read(identifier) }.getOrThrow()?.title,
        )
    }

    @Test
    fun copy_leavesOutFilesThatAreNotBookData() {
        booksDirectory.mkdirs()
        File(booksDirectory, "$FILE_NAME.1.corrupt").writeText("{")
        File(booksDirectory, "notes.json").writeText("{}")

        runBlocking { newDataFolder().moveTo(folder) }.getOrThrow()

        assertEquals(emptyList<String>(), tree.namesIn("books"))
    }

    @Test
    fun failedCopy_keepsTheCurrentLocation() {
        val dataFolder = newDataFolder()
        runBlocking { dataFolder.store.write(sampleBookData("Interna")) }.getOrThrow()
        tree.failWritesOfNamesEndingWith = ".tmp"

        val moved = runBlocking { dataFolder.moveTo(folder) }

        assertTrue(moved.isFailure)
        assertEquals(BookDataLocation.Internal, runBlocking { dataFolder.currentLocation() })
        assertFalse(locationFile.exists())
        assertEquals(
            "Interna",
            runBlocking { dataFolder.store.read(identifier) }.getOrThrow()?.title,
        )
    }

    @Test
    fun unreachableFolder_canStillBeLeftForTheInternalStorage() {
        val dataFolder = newDataFolder()
        runBlocking { dataFolder.moveTo(folder) }.getOrThrow()
        tree.revoked = true

        runBlocking { dataFolder.moveTo(BookDataLocation.Internal) }.getOrThrow()

        assertEquals(BookDataLocation.Internal, runBlocking { newDataFolder().currentLocation() })
        assertNull(runBlocking { dataFolder.store.read(identifier) }.getOrThrow())
    }

    @Test
    fun corruptLocationFile_meansInternalStorage() {
        locationFile.writeText("{ not json")

        assertEquals(BookDataLocation.Internal, runBlocking { newDataFolder().currentLocation() })
    }

    @Test
    fun locationJson_readsBackTheSameLocation() {
        listOf(BookDataLocation.Internal, folder).forEach { location ->
            assertEquals(location, bookDataLocationOf(locationJson(location).toString()))
        }
    }

    private fun sampleBookData(title: String): BookData =
        BookData(
            bookIdentifier = identifier,
            title = title,
            author = "Séneca",
            source = BookSource("cartas.epub", Instant.parse("2026-10-10T18:20:00Z")),
            progress = null,
        )
}
