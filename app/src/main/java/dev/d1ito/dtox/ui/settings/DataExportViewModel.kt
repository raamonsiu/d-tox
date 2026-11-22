package dev.d1ito.dtox.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.d1ito.dtox.AppContainer
import dev.d1ito.dtox.data.export.UserDataExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.OutputStream
import java.time.Clock

/** Recorded in the manifest when the installed version cannot be read. */
private const val UNKNOWN_VERSION = "unknown"

/** Truncates the chosen document before writing, so no bytes of an older file remain. */
private const val TRUNCATING_WRITE_MODE = "wt"

/** Where the export of the user data stands, for the line the settings screen shows. */
enum class ExportStatus { IDLE, RUNNING, EXPORTED, FAILED }

/**
 * Exports the user data to the document the user chose (RF-41). The export runs in [scope], so
 * it finishes even if the screen is rotated or closed meanwhile.
 *
 * @param exporter writes the zip.
 * @param openDestination opens the chosen document (its URI as text) for writing; it throws an
 *   [java.io.IOException] when it cannot.
 * @param scope runs the export; cancelled when the ViewModel is cleared.
 */
class DataExportViewModel(
    private val exporter: UserDataExporter,
    private val openDestination: (String) -> OutputStream,
    private val scope: CoroutineScope,
) : ViewModel(scope) {
    private val mutableStatus = MutableStateFlow(ExportStatus.IDLE)

    /** How the last export went; [ExportStatus.IDLE] before the first one. */
    val status: StateFlow<ExportStatus> = mutableStatus.asStateFlow()

    /**
     * Exports to [destination]. Ignored while another export runs.
     *
     * @param destination URI of the document created by the system "save as" dialog.
     * @param includeBooks also packs the book files.
     */
    fun export(
        destination: String,
        includeBooks: Boolean,
    ) {
        if (mutableStatus.value == ExportStatus.RUNNING) return
        mutableStatus.value = ExportStatus.RUNNING
        scope.launch {
            val result = exporter.export(includeBooks) { openDestination(destination) }
            mutableStatus.value =
                if (result.isSuccess) ExportStatus.EXPORTED else ExportStatus.FAILED
        }
    }

    companion object {
        /**
         * @param context the application context: its `files/`, version and content resolver.
         * @return a factory that builds the ViewModel for the app's data.
         */
        fun factory(
            container: AppContainer,
            context: Context,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DataExportViewModel(
                        exporter =
                            UserDataExporter(
                                filesDirectory = context.filesDir,
                                ioDispatcher = container.dispatchers.io,
                                clock = Clock.systemUTC(),
                                appVersionName = {
                                    installedVersionName(context).getOrDefault(UNKNOWN_VERSION)
                                },
                            ),
                        openDestination = { destination -> openForWriting(context, destination) },
                        scope = CoroutineScope(SupervisorJob() + container.dispatchers.main),
                    )
                }
            }

        /** @throws FileNotFoundException when the document cannot be opened. */
        private fun openForWriting(
            context: Context,
            destination: String,
        ): OutputStream =
            context.contentResolver.openOutputStream(Uri.parse(destination), TRUNCATING_WRITE_MODE)
                ?: throw FileNotFoundException("The chosen document cannot be opened")
    }
}
