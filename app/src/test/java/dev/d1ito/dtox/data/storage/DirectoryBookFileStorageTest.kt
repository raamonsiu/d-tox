package dev.d1ito.dtox.data.storage

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

class DirectoryBookFileStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory by lazy { File(temporaryFolder.root, "books") }
    private val storage by lazy {
        DirectoryBookFileStorage(
            directory,
            SyncedAtomicFileWriter(Dispatchers.Unconfined),
            Dispatchers.Unconfined,
        )
    }

    @Test
    fun missingDirectory_readsNothingAndListsNothing() {
        assertNull(runBlocking { storage.read("a.json") }.getOrThrow())
        assertEquals(emptyList<StoredBookFile>(), runBlocking { storage.list() }.getOrThrow())
    }

    @Test
    fun write_createsTheDirectoryAndListsTheFileWithItsTime() {
        runBlocking { storage.write("a.json", "data".toByteArray()) }.getOrThrow()

        val listed = runBlocking { storage.list() }.getOrThrow()

        assertEquals("data", File(directory, "a.json").readText())
        assertEquals(
            listOf(StoredBookFile("a.json", File(directory, "a.json").lastModified())),
            listed,
        )
    }

    @Test
    fun rename_movesTheFile() {
        runBlocking { storage.write("a.json", "data".toByteArray()) }.getOrThrow()

        runBlocking { storage.rename("a.json", "a.json.1.corrupt") }.getOrThrow()

        assertFalse(File(directory, "a.json").exists())
        assertTrue(File(directory, "a.json.1.corrupt").isFile)
    }

    @Test
    fun rename_ofAMissingFile_fails() {
        directory.mkdirs()

        assertTrue(runBlocking { storage.rename("a.json", "b.json") }.isFailure)
    }
}
