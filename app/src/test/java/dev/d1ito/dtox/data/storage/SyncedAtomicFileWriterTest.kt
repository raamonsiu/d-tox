package dev.d1ito.dtox.data.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SyncedAtomicFileWriterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val writer = SyncedAtomicFileWriter(Dispatchers.Unconfined)

    @Test
    fun createsTheFileAndItsMissingDirectory() {
        val target = File(temporaryFolder.root, "books/abc/data.json")

        val result = runBlocking { writer.write(target, "{}".toByteArray()) }

        assertTrue(result.isSuccess)
        assertEquals("{}", target.readText())
        assertNoTemporaryFiles(target.parentFile)
    }

    @Test
    fun replacesThePreviousContentCompletely() {
        val target = temporaryFolder.newFile("data.json").apply { writeText("old content, longer") }

        runBlocking { writer.write(target, "new".toByteArray()) }.getOrThrow()

        assertEquals("new", target.readText())
        assertNoTemporaryFiles(target.parentFile)
    }

    @Test
    fun failureMidWrite_keepsTheOldFileAndRemovesTheTemporary() {
        val target = temporaryFolder.newFile("data.json").apply { writeText("old") }

        val result =
            runBlocking {
                writer.write(target) { output ->
                    output.write("partial".toByteArray())
                    throw IOException("disk full")
                }
            }

        assertTrue(result.exceptionOrNull() is AtomicWriteError.WriteFailed)
        assertEquals("disk full", result.exceptionOrNull()?.cause?.message)
        assertEquals("old", target.readText())
        assertNoTemporaryFiles(target.parentFile)
    }

    @Test
    fun targetThatCannotBeReplaced_isReplaceFailedAndLeavesNoTemporary() {
        val target = temporaryFolder.newFolder("data.json")
        File(target, "child").writeText("keeps the directory non-empty")

        val result = runBlocking { writer.write(target, "new".toByteArray()) }

        assertTrue(result.exceptionOrNull() is AtomicWriteError.ReplaceFailed)
        assertTrue(target.isDirectory)
        assertNoTemporaryFiles(target.parentFile)
    }

    @Test
    fun parentThatIsAFile_isDirectoryUnavailable() {
        val blocker = temporaryFolder.newFile("not-a-directory")

        val result = runBlocking { writer.write(File(blocker, "data.json"), ByteArray(0)) }

        assertTrue(result.exceptionOrNull() is AtomicWriteError.DirectoryUnavailable)
        assertArrayEquals(ByteArray(0), blocker.readBytes())
    }

    private fun assertNoTemporaryFiles(directory: File) {
        val leftovers =
            directory.listFiles().orEmpty().filter { file ->
                file.name.endsWith(".tmp")
            }
        assertTrue("Temporary files left: $leftovers", leftovers.isEmpty())
    }
}
