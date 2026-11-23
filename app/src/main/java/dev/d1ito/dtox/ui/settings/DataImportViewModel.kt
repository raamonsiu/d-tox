package dev.d1ito.dtox.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.d1ito.dtox.AppContainer
import dev.d1ito.dtox.data.export.UserDataImportError
import dev.d1ito.dtox.data.export.UserDataImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Where the import of user data stands, for the line the settings screen shows: [INVALID] when
 * the file is not an export this version can import (nothing was written), [FAILED] when reading
 * or writing failed.
 */
enum class ImportStatus { IDLE, RUNNING, IMPORTED, INVALID, FAILED }

/**
 * Imports the user data from the zip the user chose (docs/02-data-formats.md §10). The import
 * runs in [scope], so it finishes even if the screen is rotated or closed meanwhile.
 *
 * @param importer validates and merges the zip.
 * @param openSource opens the chosen document (its URI as text) for reading; it throws an
 *   [java.io.IOException] when it cannot.
 * @param scope runs the import; cancelled when the ViewModel is cleared.
 */
class DataImportViewModel(
    private val importer: UserDataImporter,
    private val openSource: (String) -> InputStream,
    private val scope: CoroutineScope,
) : ViewModel(scope) {
    private val mutableStatus = MutableStateFlow(ImportStatus.IDLE)

    /** How the last import went; [ImportStatus.IDLE] before the first one. */
    val status: StateFlow<ImportStatus> = mutableStatus.asStateFlow()

    /**
     * Imports [source]. Ignored while another import runs.
     *
     * @param source URI of the document picked in the system "open" dialog.
     */
    fun import(source: String) {
        if (mutableStatus.value == ImportStatus.RUNNING) return
        mutableStatus.value = ImportStatus.RUNNING
        scope.launch {
            val result = importer.import { openSource(source) }
            mutableStatus.value =
                when (result.exceptionOrNull()) {
                    null -> ImportStatus.IMPORTED
                    is UserDataImportError -> ImportStatus.INVALID
                    else -> ImportStatus.FAILED
                }
        }
    }

    companion object {
        /**
         * @param context the application context, for its content resolver.
         * @return a factory that builds the ViewModel for the app's data.
         */
        fun factory(
            container: AppContainer,
            context: Context,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DataImportViewModel(
                        importer =
                            UserDataImporter(
                                bookDataStore = container.bookDataStore,
                                libraryIndex = container.libraryIndex,
                                importLibraryBook = { content, fileName ->
                                    container.bookImporter.import(content, fileName, null).map {}
                                },
                                ioDispatcher = container.dispatchers.io,
                            ),
                        openSource = { source -> openForReading(context, source) },
                        scope = CoroutineScope(SupervisorJob() + container.dispatchers.main),
                    )
                }
            }

        /** @throws FileNotFoundException when the document cannot be opened. */
        private fun openForReading(
            context: Context,
            source: String,
        ): InputStream =
            context.contentResolver.openInputStream(Uri.parse(source))
                ?: throw FileNotFoundException("The chosen document cannot be opened")
    }
}
