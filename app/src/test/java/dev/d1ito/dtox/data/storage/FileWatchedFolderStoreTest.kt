package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream

private const val TREE = "content://com.android.externalstorage.documents/tree/"

class FileWatchedFolderStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val foldersFile by lazy { File(temporaryFolder.root, "watched-folders.json") }
    private val books = WatchedFolder(TREE + "primary%3ABooks")
    private val papers = WatchedFolder(TREE + "primary%3APapers")

    @Test
    fun missingFile_readsNoFolders() {
        assertEquals(emptyList<WatchedFolder>(), runBlocking { store().folders.first() })
    }

    @Test
    fun addedFolders_persistInOrderForANewStore() {
        runBlocking {
            store().add(books).getOrThrow()
            store().add(papers).getOrThrow()
        }

        assertEquals(listOf(books, papers), runBlocking { store().folders.first() })
    }

    @Test
    fun addingAFolderTwice_keepsOneEntryInItsPlace() {
        val store = store()
        runBlocking {
            store.add(books).getOrThrow()
            store.add(papers).getOrThrow()
            store.add(books).getOrThrow()
        }

        assertEquals(listOf(books, papers), runBlocking { store().folders.first() })
    }

    @Test
    fun removedFolder_isGoneForANewStore_andUnknownOnesAreIgnored() {
        val store = store()
        runBlocking {
            store.add(books).getOrThrow()
            store.add(papers).getOrThrow()
            store.remove(books).getOrThrow()
            store.remove(WatchedFolder(TREE + "primary%3AOther")).getOrThrow()
        }

        assertEquals(listOf(papers), runBlocking { store().folders.first() })
    }

    @Test
    fun change_isEmittedToCollectors() {
        val store = store()

        val emitted =
            runBlocking {
                val collected = launch(Dispatchers.Unconfined) { store.folders.take(2).toList() }
                yield()
                store.add(books).getOrThrow()
                collected.join()
                store.folders.first()
            }

        assertEquals(listOf(books), emitted)
    }

    @Test
    fun writtenFile_hasTheVersionAndTheTreeUris() {
        runBlocking { store().add(books) }.getOrThrow()

        val written = Json.parseToJsonElement(foldersFile.readText()).jsonObject
        assertEquals("1", written.getValue("version").jsonPrimitive.content)
        assertEquals(
            listOf(books.treeUri),
            written.getValue("folders").jsonArray.map { entry -> entry.jsonPrimitive.content },
        )
    }

    @Test
    fun corruptFile_readsNoFolders() {
        foldersFile.writeText("{ not json")

        assertEquals(emptyList<WatchedFolder>(), runBlocking { store().folders.first() })
    }

    @Test
    fun entriesThatAreNotUris_areSkipped() {
        val uri = books.treeUri
        foldersFile.writeText("""{ "version": 1, "folders": [ 3, "$uri", "", {}, "$uri" ] }""")

        assertEquals(listOf(books), runBlocking { store().folders.first() })
    }

    @Test
    fun failedWrite_keepsTheListAndReportsTheError() {
        val store = store(FailingFileWriter)

        val added = runBlocking { store.add(books) }

        assertTrue(added.exceptionOrNull() is AtomicWriteError)
        assertEquals(emptyList<WatchedFolder>(), runBlocking { store.folders.first() })
        assertFalse(foldersFile.exists())
    }

    private fun store(
        fileWriter: AtomicFileWriter = SyncedAtomicFileWriter(Dispatchers.Unconfined),
    ) = FileWatchedFolderStore(
        foldersFile = foldersFile,
        fileWriter = fileWriter,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private object FailingFileWriter : AtomicFileWriter {
        override suspend fun write(
            target: File,
            writeContent: (OutputStream) -> Unit,
        ): Result<Unit> =
            Result.failure(AtomicWriteError.WriteFailed(target, IOException("disk full")))
    }
}
