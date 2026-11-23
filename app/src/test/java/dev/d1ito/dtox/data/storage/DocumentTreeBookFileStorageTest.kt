package dev.d1ito.dtox.data.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NAME = "sha256:0123456789abcdef0123456789abcdef.json"

class DocumentTreeBookFileStorageTest {
    private val tree = FakeDocumentTree()
    private val storage = DocumentTreeBookFileStorage(tree, Dispatchers.Unconfined)

    @Test
    fun emptyTree_readsNothingAndListsNothing() {
        assertNull(runBlocking { storage.read(NAME) }.getOrThrow())
        assertEquals(emptyList<StoredBookFile>(), runBlocking { storage.list() }.getOrThrow())
    }

    @Test
    fun write_createsTheBooksFolderAndReadsBack() {
        runBlocking { storage.write(NAME, "first".toByteArray()) }.getOrThrow()

        assertEquals("first", runBlocking { storage.read(NAME) }.getOrThrow()?.decodeToString())
        assertEquals(listOf(NAME), tree.namesIn("books"))
    }

    @Test
    fun write_replacesThePreviousVersionWithoutLeavingTemporaryFiles() {
        runBlocking { storage.write(NAME, "first".toByteArray()) }.getOrThrow()

        runBlocking { storage.write(NAME, "second".toByteArray()) }.getOrThrow()

        assertEquals("second", tree.textAt("books/$NAME"))
        assertEquals(listOf(NAME), tree.namesIn("books"))
    }

    @Test
    fun failedWriteOfTheCopy_leavesThePreviousVersion() {
        runBlocking { storage.write(NAME, "first".toByteArray()) }.getOrThrow()
        tree.failWritesOfNamesEndingWith = ".tmp"

        val result = runBlocking { storage.write(NAME, "second".toByteArray()) }

        assertTrue(result.isFailure)
        assertEquals("first", tree.textAt("books/$NAME"))
    }

    @Test
    fun copyLeftByAnInterruptedReplace_isPutBackInPlaceOnRead() {
        tree.put("books/$NAME.tmp", "verified")

        val read = runBlocking { storage.read(NAME) }.getOrThrow()

        assertEquals("verified", read?.decodeToString())
        assertEquals(listOf(NAME), tree.namesIn("books"))
    }

    @Test
    fun staleCopy_isReplacedByTheNextWrite() {
        tree.put("books/$NAME", "first")
        tree.put("books/$NAME.tmp", "stale")

        runBlocking { storage.write(NAME, "second".toByteArray()) }.getOrThrow()

        assertEquals("second", tree.textAt("books/$NAME"))
        assertEquals(listOf(NAME), tree.namesIn("books"))
    }

    @Test
    fun providerWithoutRename_overwritesInPlaceAfterVerifyingTheCopy() {
        tree.supportsRename = false
        tree.put("books/$NAME", "first")

        runBlocking { storage.write(NAME, "second".toByteArray()) }.getOrThrow()
        runBlocking { storage.write("other.json", "new".toByteArray()) }.getOrThrow()

        assertEquals("second", tree.textAt("books/$NAME"))
        assertEquals(listOf("other.json", NAME), tree.namesIn("books"))
    }

    @Test
    fun rename_movesTheFileAndRefusesATakenName() {
        tree.put("books/$NAME", "corrupt")
        tree.put("books/taken", "other")

        runBlocking { storage.rename(NAME, "$NAME.1.corrupt") }.getOrThrow()
        tree.put("books/$NAME", "again")
        val clash = runBlocking { storage.rename(NAME, "taken") }

        assertEquals("corrupt", tree.textAt("books/$NAME.1.corrupt"))
        assertTrue(clash.isFailure)
    }

    @Test
    fun list_givesNamesAndModificationTimes() {
        tree.put("books/$NAME", "data", lastModifiedMillis = 42)

        val listed = runBlocking { storage.list() }.getOrThrow()

        assertEquals(listOf(StoredBookFile(NAME, 42)), listed)
    }

    @Test
    fun revokedAccess_failsInsteadOfThrowing() {
        tree.revoked = true

        assertTrue(runBlocking { storage.read(NAME) }.isFailure)
        assertTrue(runBlocking { storage.write(NAME, "data".toByteArray()) }.isFailure)
        assertTrue(runBlocking { storage.list() }.isFailure)
    }
}
