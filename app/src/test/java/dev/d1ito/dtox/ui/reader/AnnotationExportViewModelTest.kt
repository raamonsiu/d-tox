package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.ui.settings.ExportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.OutputStream
import java.time.Instant

private const val DESTINATION = "content://documents/cuentos-notas.md"
private val CREATED: Instant = Instant.parse("2026-11-23T10:00:00Z")

class AnnotationExportViewModelTest {
    private val written = ByteArrayOutputStream()
    private val openedDestinations = mutableListOf<String>()

    private val book: Book =
        Book
            .create(
                BookIdentifier.parse("sha256:0123456789abcdef0123456789abcdef").getOrThrow(),
                BookMetadata(title = "Cuentos", author = null, language = "es"),
                listOf(
                    Chapter.create(0, null, listOf(Block.Paragraph("Era un día."))).getOrThrow(),
                ),
            ).getOrThrow()

    private val note: BookAnnotation =
        BookAnnotation.Note
            .create(
                AnnotationMetadata(
                    AnnotationIdentifier.parse("00000000-0000-4000-8000-000000000001").getOrThrow(),
                    CREATED,
                    CREATED,
                ),
                Anchor.create(0, 0, 0, null).getOrThrow(),
                "Luz",
            ).getOrThrow()

    private fun viewModel(openDestination: (String) -> OutputStream): AnnotationExportViewModel =
        AnnotationExportViewModel(
            openDestination = openDestination,
            untitledChapterTitle = { chapterNumber -> "Capítulo $chapterNumber" },
            ioDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

    @Test
    fun state_isIdleForNoBookBeforeTheFirstExport() {
        assertEquals(AnnotationExportState(), viewModel { written }.state.value)
    }

    @Test
    fun export_writesTheMarkdownAsUtf8ToTheChosenDocument() {
        val viewModel =
            viewModel { destination ->
                openedDestinations += destination
                written
            }

        viewModel.export(DESTINATION, book, listOf(note))

        assertEquals(
            AnnotationExportState(book.identifier, ExportStatus.EXPORTED),
            viewModel.state.value,
        )
        assertEquals(listOf(DESTINATION), openedDestinations)
        assertEquals(
            "# Cuentos\n\n## Capítulo 1\n\n> Era un día.\n\nLuz\n",
            written.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun export_failsWhenTheDocumentCannotBeOpened() {
        val viewModel = viewModel { throw FileNotFoundException("gone") }

        viewModel.export(DESTINATION, book, listOf(note))

        assertEquals(
            AnnotationExportState(book.identifier, ExportStatus.FAILED),
            viewModel.state.value,
        )
    }

    @Test
    fun export_failsWhenThePermissionIsMissing() {
        val viewModel = viewModel { throw SecurityException("revoked") }

        viewModel.export(DESTINATION, book, listOf(note))

        assertEquals(ExportStatus.FAILED, viewModel.state.value.status)
    }
}
