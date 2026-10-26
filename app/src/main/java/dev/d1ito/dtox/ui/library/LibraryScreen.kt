package dev.d1ito.dtox.ui.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.d1ito.dtox.R
import dev.d1ito.dtox.data.storage.LibraryEntry
import dev.d1ito.dtox.ui.theme.DTOXTheme

/** Types offered in the picker. Markdown often arrives as octet-stream; the importer checks. */
private val pickableMimeTypes =
    arrayOf(
        "application/epub+zip",
        "text/plain",
        "text/markdown",
        "text/x-markdown",
        "application/octet-stream",
    )

private val screenMargin = 24.dp
private val entrySpacing = 16.dp

/** The library (docs/04-design.md §Biblioteca): a plain text list and "Añadir libro". */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val state by viewModel.state.collectAsState()
    val contentResolver = LocalContext.current.contentResolver
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { picked -> viewModel.importBook(contentResolver.pickedDocument(picked)) }
        }
    LibraryContent(state, onAddBook = { picker.launch(pickableMimeTypes) })
}

/**
 * Stateless library layout.
 *
 * @param state what to show.
 * @param onAddBook opens the document picker.
 */
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onAddBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(DTOXTheme.palette.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(screenMargin),
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.titleLarge,
        )
        TextButton(onClick = onAddBook, enabled = !state.isImporting) {
            val label = if (state.isImporting) R.string.library_importing else R.string.library_add
            Text(stringResource(label))
        }
        state.message?.let { message -> MutedText(stringResource(message.textResource())) }
        Spacer(Modifier.height(entrySpacing))
        if (state.books.isEmpty()) {
            MutedText(stringResource(R.string.library_empty))
        } else {
            BookList(state.books)
        }
    }
}

@Composable
private fun BookList(books: List<LibraryEntry>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(entrySpacing)) {
        items(books, key = { entry -> entry.bookIdentifier.value }) { entry ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DTOXTheme.palette.text,
                )
                entry.author?.let { author -> MutedText(author) }
                Spacer(Modifier.height(entrySpacing))
                HorizontalDivider(color = DTOXTheme.palette.divider)
            }
        }
    }
}

@Composable
private fun MutedText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = DTOXTheme.palette.textMuted,
    )
}

private fun LibraryMessage.textResource(): Int =
    when (this) {
        LibraryMessage.UnsupportedFormat -> R.string.library_unsupported_format
        LibraryMessage.NoReadableText -> R.string.library_no_readable_text
        LibraryMessage.ImportFailed -> R.string.library_import_failed
        LibraryMessage.LibraryUnavailable -> R.string.library_unavailable
    }

/** Asks the provider for the name and type only when called, which happens off the main thread. */
private fun ContentResolver.pickedDocument(uri: Uri): PickedDocument =
    PickedDocument(
        displayName = { displayNameOf(uri) ?: uri.lastPathSegment.orEmpty() },
        mimeType = { getType(uri) },
        openStream = {
            openInputStream(
                uri,
            ) ?: throw java.io.FileNotFoundException(uri.toString())
        },
    )

private fun ContentResolver.displayNameOf(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
