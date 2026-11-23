package dev.d1ito.dtox.ui.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.d1ito.dtox.AppContainer
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.ui.settings.ExportStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/** Truncates the chosen document before writing, so no bytes of an older file remain. */
private const val TRUNCATING_WRITE_MODE = "wt"

/**
 * Where the last export of annotations stands.
 *
 * @property book the book it was for; `null` before the first export.
 */
data class AnnotationExportState(
    val book: BookIdentifier? = null,
    val status: ExportStatus = ExportStatus.IDLE,
)

/**
 * Exports a book's highlights and notes as Markdown (RF-26) to the document the user chose. The
 * export runs in [scope], so it finishes even if the screen is rotated or closed meanwhile.
 *
 * @param openDestination opens the chosen document (its URI as text) for writing; it throws an
 *   [IOException] or a [SecurityException] when it cannot.
 * @param untitledChapterTitle heading of a chapter without title, from its 1-based number.
 * @param ioDispatcher where the Markdown is built and written; never the main thread.
 * @param scope runs the export; cancelled when the ViewModel is cleared.
 */
class AnnotationExportViewModel(
    private val openDestination: (String) -> OutputStream,
    private val untitledChapterTitle: (Int) -> String,
    private val ioDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow(AnnotationExportState())

    /** How the last export went, and for which book. */
    val state: StateFlow<AnnotationExportState> = mutableState.asStateFlow()

    /**
     * Writes the Markdown of [book]'s [annotations] to [destination]. Ignored while another
     * export runs.
     *
     * @param destination URI of the document created by the system "save as" dialog.
     */
    fun export(
        destination: String,
        book: Book,
        annotations: List<BookAnnotation>,
    ) {
        if (mutableState.value.status == ExportStatus.RUNNING) return
        mutableState.value = AnnotationExportState(book.identifier, ExportStatus.RUNNING)
        scope.launch {
            val result = writeMarkdown(destination, book, annotations)
            val status = if (result.isSuccess) ExportStatus.EXPORTED else ExportStatus.FAILED
            mutableState.value = AnnotationExportState(book.identifier, status)
        }
    }

    /** @return success once the whole document is written, or the failure that stopped it. */
    private suspend fun writeMarkdown(
        destination: String,
        book: Book,
        annotations: List<BookAnnotation>,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            val markdown = book.annotationsMarkdown(annotations, untitledChapterTitle)
            try {
                openDestination(destination).use { output ->
                    output.write(markdown.toByteArray(Charsets.UTF_8))
                }
                Result.success(Unit)
            } catch (writeFailure: IOException) {
                Result.failure(writeFailure)
            } catch (permissionFailure: SecurityException) {
                Result.failure(permissionFailure)
            }
        }

    companion object {
        /**
         * @param context the application context: its content resolver and texts.
         * @return a factory that builds the ViewModel.
         */
        fun factory(
            container: AppContainer,
            context: Context,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AnnotationExportViewModel(
                        openDestination = { destination -> openForWriting(context, destination) },
                        untitledChapterTitle = { chapterNumber ->
                            context.getString(R.string.chapter_untitled, chapterNumber)
                        },
                        ioDispatcher = container.dispatchers.io,
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
