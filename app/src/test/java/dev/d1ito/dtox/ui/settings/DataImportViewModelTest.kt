package dev.d1ito.dtox.ui.settings

import dev.d1ito.dtox.data.export.UserDataImporter
import dev.d1ito.dtox.data.storage.LibraryEntry
import dev.d1ito.dtox.data.storage.LibraryIndex
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.ui.reader.InMemoryBookDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val SOURCE = "content://documents/dtox-datos.zip"
private const val MANIFEST = """{"format":"dtox-export","version":1}"""

class DataImportViewModelTest {
    private val openedSources = mutableListOf<String>()

    private fun viewModel(openSource: (String) -> InputStream): DataImportViewModel {
        val importer =
            UserDataImporter(
                bookDataStore = InMemoryBookDataStore(),
                libraryIndex = EmptyLibraryIndex(),
                importLibraryBook = { _, _ -> Result.failure(IllegalStateException("no books")) },
                ioDispatcher = Dispatchers.Unconfined,
            )
        return DataImportViewModel(importer, openSource, CoroutineScope(Dispatchers.Unconfined))
    }

    private fun zipWith(manifest: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    @Test
    fun status_isIdleBeforeTheFirstImport() {
        assertEquals(
            ImportStatus.IDLE,
            viewModel { ByteArrayInputStream(zipWith(MANIFEST)) }.status.value,
        )
    }

    @Test
    fun validExport_isImportedFromTheChosenDocument() {
        val viewModel =
            viewModel { source ->
                openedSources += source
                ByteArrayInputStream(zipWith(MANIFEST))
            }

        viewModel.import(SOURCE)

        assertEquals(ImportStatus.IMPORTED, viewModel.status.value)
        assertEquals(listOf(SOURCE), openedSources)
    }

    @Test
    fun fileThatIsNotAnExport_isReportedAsInvalid() {
        val viewModel = viewModel { ByteArrayInputStream(zipWith("""{"format":"otro"}""")) }

        viewModel.import(SOURCE)

        assertEquals(ImportStatus.INVALID, viewModel.status.value)
    }

    @Test
    fun documentThatCannotBeOpened_isReportedAsFailed() {
        val viewModel = viewModel { throw FileNotFoundException("revoked") }

        viewModel.import(SOURCE)

        assertEquals(ImportStatus.FAILED, viewModel.status.value)
    }
}

/** [LibraryIndex] with no books whose changes all succeed. */
private class EmptyLibraryIndex : LibraryIndex {
    override val entries = MutableStateFlow<List<LibraryEntry>>(emptyList())

    override suspend fun load(): Result<Unit> = Result.success(Unit)

    override suspend fun add(entry: LibraryEntry): Result<Unit> = Result.success(Unit)

    override suspend fun remove(bookIdentifier: BookIdentifier): Result<Unit> = Result.success(Unit)

    override suspend fun markOpened(bookIdentifier: BookIdentifier): Result<Unit> =
        Result.success(Unit)

    override suspend fun update(change: (List<LibraryEntry>) -> List<LibraryEntry>): Result<Unit> =
        Result.success(Unit)

    override suspend fun rebuild(): Result<Unit> = Result.success(Unit)
}
