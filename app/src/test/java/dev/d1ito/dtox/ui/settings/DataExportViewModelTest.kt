package dev.d1ito.dtox.ui.settings

import dev.d1ito.dtox.data.export.UserDataExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipInputStream

private const val DESTINATION = "content://documents/export.zip"

class DataExportViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val written = ByteArrayOutputStream()
    private val openedDestinations = mutableListOf<String>()

    private fun viewModel(openDestination: (String) -> OutputStream): DataExportViewModel {
        val filesDirectory = temporaryFolder.newFolder("files")
        filesDirectory.resolve("settings.json").writeText("{}")
        val exporter =
            UserDataExporter(
                filesDirectory = filesDirectory,
                ioDispatcher = Dispatchers.Unconfined,
                clock = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC),
                appVersionName = { "0.42.0" },
            )
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return DataExportViewModel(exporter, openDestination, scope)
    }

    @Test
    fun status_isIdleBeforeTheFirstExport() {
        assertEquals(ExportStatus.IDLE, viewModel { written }.status.value)
    }

    @Test
    fun export_writesTheZipToTheChosenDocument() {
        val viewModel =
            viewModel { destination ->
                openedDestinations += destination
                written
            }

        viewModel.export(DESTINATION, includeBooks = false)

        assertEquals(ExportStatus.EXPORTED, viewModel.status.value)
        assertEquals(listOf(DESTINATION), openedDestinations)
        val names =
            ZipInputStream(ByteArrayInputStream(written.toByteArray())).use { zip ->
                generateSequence { zip.nextEntry?.name }.toList()
            }
        assertTrue("settings.json" in names)
    }

    @Test
    fun destinationThatCannotBeOpened_isReportedAsFailed() {
        val viewModel = viewModel { throw FileNotFoundException("revoked") }

        viewModel.export(DESTINATION, includeBooks = true)

        assertEquals(ExportStatus.FAILED, viewModel.status.value)
    }
}
