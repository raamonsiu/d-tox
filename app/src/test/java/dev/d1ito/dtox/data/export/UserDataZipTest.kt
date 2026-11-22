package dev.d1ito.dtox.data.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipInputStream

private const val BOOK_A = "sha256:aaaa.json"
private const val BOOK_B = "sha256:bbbb.json"

class UserDataZipTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val filesDirectory: File by lazy { temporaryFolder.newFolder("files") }
    private val exportedAt = Instant.parse("2026-11-21T10:00:00Z")

    private fun manifest(includeBooks: Boolean = false) =
        UserDataExportManifest(includeBooks, appVersionName = "0.42.0", exportedAt = exportedAt)

    private fun file(
        path: String,
        content: String,
    ) = File(filesDirectory, path).apply {
        parentFile?.mkdirs()
        writeText(content)
    }

    private fun givenUserData() {
        file("settings.json", """{"version":1}""")
        file("books/$BOOK_B", "b")
        file("books/$BOOK_A", "a")
        file("library/bbbb.epub", "epub bytes")
        file("library/aaaa.txt", "text bytes")
    }

    /** Entries of the exported zip in order, with their content as text. */
    private fun export(includeBooks: Boolean = false): List<Pair<String, String>> {
        val output = ByteArrayOutputStream()
        assertTrue(writeUserDataZip(filesDirectory, output, manifest(includeBooks)).isSuccess)
        return entriesOf(output.toByteArray())
    }

    private fun entriesOf(zip: ByteArray): List<Pair<String, String>> =
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            generateSequence { input.nextEntry }
                .map { entry -> entry.name to input.readBytes().toString(Charsets.UTF_8) }
                .toList()
        }

    @Test
    fun withoutBooks_packsManifestSettingsAndBookData_inOrder() {
        givenUserData()

        val entries = export()

        assertEquals(
            listOf("manifest.json", "settings.json", "books/$BOOK_A", "books/$BOOK_B"),
            entries.map { (name, _) -> name },
        )
        assertEquals("""{"version":1}""", entries[1].second)
        assertEquals("a", entries[2].second)
    }

    @Test
    fun withBooks_alsoPacksTheLibraryFiles_sortedByName() {
        givenUserData()

        val names = export(includeBooks = true).map { (name, _) -> name }

        assertEquals(listOf("library/aaaa.txt", "library/bbbb.epub"), names.takeLast(2))
    }

    @Test
    fun manifest_recordsFormatVersionAppVersionDateAndBooksChoice() {
        val manifest = Json.parseToJsonElement(export(includeBooks = true).first().second)

        assertEquals(
            JsonObject(
                mapOf(
                    "format" to JsonPrimitive("dtox-export"),
                    "version" to JsonPrimitive(EXPORT_FORMAT_VERSION),
                    "appVersion" to JsonPrimitive("0.42.0"),
                    "exportedAt" to JsonPrimitive("2026-11-21T10:00:00Z"),
                    "includesBooks" to JsonPrimitive(true),
                ),
            ),
            manifest,
        )
    }

    @Test
    fun temporaryCorruptCacheAndUnknownFiles_areNeverPacked() {
        givenUserData()
        file("books/$BOOK_A.1700000000000.corrupt", "broken")
        file("books/$BOOK_A.123456.tmp", "half written")
        file("settings.json.987.tmp", "half written")
        file("library/cccc.epub.555.tmp", "half copied")
        file("library-index.json", "cache")
        file("shortreads/state.json", "cache")
        file("watched-folders.json", "[]")

        val names = export(includeBooks = true).map { (name, _) -> name }

        assertEquals(
            listOf(
                "manifest.json",
                "settings.json",
                "books/$BOOK_A",
                "books/$BOOK_B",
                "library/aaaa.txt",
                "library/bbbb.epub",
            ),
            names,
        )
    }

    @Test
    fun noUserDataYet_givesOnlyTheManifest() {
        assertEquals(listOf("manifest.json"), export().map { (name, _) -> name })
    }

    @Test
    fun sameDataAndInstant_giveTheSameBytes() {
        givenUserData()
        val first = ByteArrayOutputStream()
        val second = ByteArrayOutputStream()

        writeUserDataZip(filesDirectory, first, manifest())
        writeUserDataZip(filesDirectory, second, manifest())

        assertArrayEquals(first.toByteArray(), second.toByteArray())
    }

    @Test
    fun failingOutput_isAFailure() {
        givenUserData()
        val failing =
            object : OutputStream() {
                override fun write(byte: Int) = throw IOException("disk full")
            }

        val result = writeUserDataZip(filesDirectory, failing, manifest())

        assertTrue(result.exceptionOrNull() is IOException)
    }
}
