package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.WatchedFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val QUIJOTE_HEX = "0123456789abcdef0123456789abcdef"
private const val NIEBLA_HEX = "fedcba9876543210fedcba9876543210"

/** The watched-folder sources of the index entries and the whole-list update (RF-05). */
class LibraryIndexSourcesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val indexFile: File by lazy { File(temporaryFolder.root, "library-index.json") }
    private val writer = SyncedAtomicFileWriter(Dispatchers.Unconfined)
    private val novels = WatchedFolder("content://provider/tree/primary%3ANovelas")

    @Test
    fun folderSources_areStoredAndLoaded() {
        val source = FolderSource(novels, "${novels.treeUri}/document/niebla.md", missing = true)
        val index = loadedIndex()

        runBlocking { index.add(entry(NIEBLA_HEX, "Niebla").copy(source = source)) }.getOrThrow()

        assertEquals(
            source,
            loadedIndex()
                .entries.value
                .single()
                .source,
        )
    }

    @Test
    fun anIndexWrittenBeforeSources_loadsWithoutThem() {
        val book = """{"bookId": "sha256:$QUIJOTE_HEX", "title": "Quijote", "file": "f"}"""
        indexFile.writeText("""{"version": 1, "books": [$book]}""")

        val loaded = loadedIndex().entries.value.single()

        assertEquals("Quijote", loaded.title)
        assertNull(loaded.source)
    }

    @Test
    fun update_replacesEveryEntryInOneDurableChange() {
        val index = loadedIndex()
        runBlocking { index.add(entry(QUIJOTE_HEX, "Quijote")) }.getOrThrow()

        runBlocking {
            index.update { entries ->
                entries.map { entry -> entry.copy(title = "Don Quijote") } +
                    entry(NIEBLA_HEX, "Niebla")
            }
        }.getOrThrow()

        assertEquals(listOf("Don Quijote", "Niebla"), loadedIndex().entries.value.map { it.title })
    }

    private fun loadedIndex(): LibraryIndex {
        val index =
            FileLibraryIndex(
                indexFile = indexFile,
                libraryDirectory = temporaryFolder.root.resolve("library"),
                bookDataStore =
                    FileBookDataStore(
                        temporaryFolder.root.resolve("books"),
                        writer,
                        Dispatchers.Unconfined,
                    ),
                fileWriter = writer,
                ioDispatcher = Dispatchers.Unconfined,
            )
        runBlocking { index.load() }.getOrThrow()
        return index
    }

    private fun entry(
        hex: String,
        title: String,
    ) = LibraryEntry(
        BookIdentifier.fromHex(hex).getOrThrow(),
        title,
        author = null,
        fileName = "$hex.epub",
        lastOpenedAt = null,
        finished = false,
    )
}
