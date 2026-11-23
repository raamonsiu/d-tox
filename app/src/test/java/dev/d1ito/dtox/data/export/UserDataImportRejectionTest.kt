package dev.d1ito.dtox.data.export

import dev.d1ito.dtox.data.export.ImportFixtures.BOOK_A
import dev.d1ito.dtox.data.export.ImportFixtures.BOOK_B
import dev.d1ito.dtox.data.export.ImportFixtures.HEX_A
import dev.d1ito.dtox.data.export.ImportFixtures.HEX_B
import dev.d1ito.dtox.data.export.ImportFixtures.MANIFEST
import dev.d1ito.dtox.data.export.ImportFixtures.bookData
import dev.d1ito.dtox.data.export.ImportFixtures.bookEntry
import dev.d1ito.dtox.data.export.ImportFixtures.zipOf
import dev.d1ito.dtox.data.storage.BookData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

/** Zips that must be rejected before anything is written (docs/02-data-formats.md §10). */
class UserDataImportRejectionTest {
    private fun assertRejected(
        zip: ByteArray,
        expected: KClass<out Throwable> = UserDataImportError.InvalidExport::class,
        limits: UserDataImportLimits = UserDataImportLimits(),
    ) {
        val harness = ImportHarness(limits)

        val failure = harness.import(zip).exceptionOrNull()

        assertTrue("expected ${expected.simpleName}, got $failure", expected.isInstance(failure))
        assertNothingWritten(harness)
    }

    private fun assertNothingWritten(harness: ImportHarness) {
        assertEquals(emptyList<BookData>(), harness.store.writes)
        assertEquals(emptyList<Pair<String, String>>(), harness.importedBookFiles)
        assertEquals(0, harness.libraryIndex.rebuilds)
    }

    @Test
    fun notAZip_isRejected() {
        assertRejected("esto no es un zip".toByteArray())
    }

    @Test
    fun missingManifest_isRejected() {
        assertRejected(zipOf(bookEntry(bookData(BOOK_A))))
    }

    @Test
    fun manifestOfAnotherFormat_isRejected() {
        assertRejected(zipOf("manifest.json" to """{"format":"otro","version":1}"""))
    }

    @Test
    fun exportOfANewerVersion_isRejected() {
        val manifest = MANIFEST.replace("\"version\":1", "\"version\":2")

        assertRejected(
            zipOf("manifest.json" to manifest),
            UserDataImportError.UnsupportedVersion::class,
        )
    }

    @Test
    fun bookDataOfANewerVersion_isRejected() {
        val (name, content) = bookEntry(bookData(BOOK_A))
        val newer = content.replace("\"version\": 1", "\"version\": 9")

        assertRejected(
            zipOf("manifest.json" to MANIFEST, name to newer),
            UserDataImportError.UnsupportedVersion::class,
        )
    }

    @Test
    fun pathsOutsideTheExport_areRejected() {
        val (_, content) = bookEntry(bookData(BOOK_A))
        listOf(
            "../books/$BOOK_A.json",
            "/books/$BOOK_A.json",
            "books/../../$BOOK_A.json",
            "library/../$HEX_A.txt",
        ).forEach { name -> assertRejected(zipOf("manifest.json" to MANIFEST, name to content)) }
    }

    @Test
    fun unexpectedEntry_isRejected() {
        assertRejected(zipOf("manifest.json" to MANIFEST, "watched-folders.json" to "{}"))
    }

    @Test
    fun oneCorruptBookFile_rejectsTheWholeZip_writingNothing() {
        val zip =
            zipOf(
                "manifest.json" to MANIFEST,
                bookEntry(bookData(BOOK_A)),
                "books/$BOOK_B.json" to """{"version":1,"bookId":""",
                "library/$HEX_A.txt" to "Texto",
            )

        assertRejected(zip)
    }

    @Test
    fun bookFileNamedAfterAnotherBook_isRejected() {
        val (_, content) = bookEntry(bookData(BOOK_A))

        assertRejected(zipOf("manifest.json" to MANIFEST, "books/$BOOK_B.json" to content))
    }

    @Test
    fun settingsThatAreNotAJsonObject_areRejected() {
        assertRejected(zipOf("manifest.json" to MANIFEST, "settings.json" to "[]"))
    }

    @Test
    fun entriesBeyondTheLimits_areRejected() {
        val small =
            UserDataImportLimits(
                maximumEntryCount = 3,
                maximumJsonBytes = 512,
                maximumBookFileBytes = 4,
            )
        val manyEntries =
            zipOf(
                "manifest.json" to MANIFEST,
                "library/$HEX_A.txt" to "a",
                "library/$HEX_B.txt" to "b",
                "settings.json" to "{}",
            )

        assertRejected(manyEntries, limits = small)
        assertRejected(
            zipOf("manifest.json" to MANIFEST, "library/$HEX_A.txt" to "Demasiado"),
            limits = small,
        )
        assertRejected(zipOf("manifest.json" to MANIFEST + " ".repeat(600)), limits = small)
        val tinyTotal = UserDataImportLimits(maximumTotalBytes = 10)
        assertRejected(zipOf("manifest.json" to MANIFEST), limits = tinyTotal)
    }

    @Test
    fun damagedZipBytes_writeNothing() {
        val zip = zipOf("manifest.json" to MANIFEST, bookEntry(bookData(BOOK_A)))
        val damaged = zip.copyOf()
        damaged[DAMAGED_BYTE] = (damaged[DAMAGED_BYTE].toInt() xor ALL_BITS).toByte()
        val harness = ImportHarness()

        assertTrue(harness.import(damaged).isFailure)
        assertNothingWritten(harness)
    }

    private companion object {
        /** Inside the compressed manifest, right after its 30-byte header and its name. */
        const val DAMAGED_BYTE = 46
        const val ALL_BITS = 0xFF
    }
}
