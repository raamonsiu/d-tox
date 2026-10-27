package dev.d1ito.dtox.ui.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.d1ito.dtox.R
import dev.d1ito.dtox.data.storage.LibraryEntry
import dev.d1ito.dtox.domain.BookIdentifier
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

/** Minimum touch target of each book row (docs/04-design.md: zonas táctiles ≥ 48 dp). */
private val minimumTouchTarget = 48.dp

/**
 * The library (docs/04-design.md §Biblioteca): a plain text list and "Añadir libro".
 *
 * @param onOpenBook opens the touched book in the reader.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenBook: (BookIdentifier) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val contentResolver = LocalContext.current.contentResolver
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { picked -> viewModel.importBook(contentResolver.pickedDocument(picked)) }
        }
    LibraryContent(
        state,
        LibraryActions(
            onAddBook = { picker.launch(pickableMimeTypes) },
            onOpenBook = { entry -> onOpenBook(entry.bookIdentifier) },
            onRemoveBook = { entry -> viewModel.removeBook(entry.bookIdentifier) },
        ),
    )
}

/**
 * What the library screen can do.
 *
 * @property onAddBook opens the document picker.
 * @property onOpenBook opens the touched book.
 * @property onRemoveBook removes a book the user confirmed to remove.
 */
class LibraryActions(
    val onAddBook: () -> Unit,
    val onOpenBook: (LibraryEntry) -> Unit,
    val onRemoveBook: (LibraryEntry) -> Unit,
)

/**
 * Stateless library layout. A long press on a book asks, with a text confirmation, whether to
 * remove it from the library.
 *
 * @param state what to show.
 * @param actions what touching the screen does.
 */
@Composable
fun LibraryContent(
    state: LibraryUiState,
    actions: LibraryActions,
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
        LibraryHeader(state, actions.onAddBook)
        Spacer(Modifier.height(entrySpacing))
        if (state.books.isEmpty()) {
            MutedText(stringResource(R.string.library_empty))
        } else {
            BookListWithRemoval(state.books, actions)
        }
    }
}

/** The book list, and the confirmation a long press on a book asks before removing it. */
@Composable
private fun BookListWithRemoval(
    books: List<LibraryEntry>,
    actions: LibraryActions,
) {
    // The identifier text survives rotation and process death; the entry is looked up again.
    var pendingRemovalHex by rememberSaveable { mutableStateOf<String?>(null) }
    BookList(books, actions.onOpenBook) { entry -> pendingRemovalHex = entry.bookIdentifier.hex }
    books.firstOrNull { entry -> entry.bookIdentifier.hex == pendingRemovalHex }?.let { entry ->
        RemoveBookDialog(
            entry = entry,
            onConfirm = {
                pendingRemovalHex = null
                actions.onRemoveBook(entry)
            },
            onDismiss = { pendingRemovalHex = null },
        )
    }
}

/** The title, "Añadir libro" (disabled while importing) and the last message, if any. */
@Composable
private fun LibraryHeader(
    state: LibraryUiState,
    onAddBook: () -> Unit,
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
}

/** Text confirmation before removing [entry]; it says the original file stays untouched. */
@Composable
private fun RemoveBookDialog(
    entry: LibraryEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DTOXTheme.palette.background,
        text = {
            Text(
                text = stringResource(R.string.library_remove_question, entry.title),
                color = DTOXTheme.palette.text,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.library_remove_confirm),
                    color = DTOXTheme.palette.text,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_remove_cancel), color = DTOXTheme.palette.text)
            }
        },
    )
}

@Composable
private fun BookList(
    books: List<LibraryEntry>,
    onOpenBook: (LibraryEntry) -> Unit,
    onAskToRemove: (LibraryEntry) -> Unit,
) {
    val removeLabel = stringResource(R.string.library_remove_action)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(entrySpacing)) {
        items(books, key = { entry -> entry.bookIdentifier.value }) { entry ->
            Column(
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = minimumTouchTarget).combinedClickable(
                        onLongClickLabel = removeLabel,
                        onLongClick = { onAskToRemove(entry) },
                        onClick = { onOpenBook(entry) },
                    ),
            ) {
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
        LibraryMessage.RemoveFailed -> R.string.library_remove_failed
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
