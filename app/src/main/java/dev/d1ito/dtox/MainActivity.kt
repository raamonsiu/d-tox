package dev.d1ito.dtox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.d1ito.dtox.ui.library.LibraryScreen
import dev.d1ito.dtox.ui.library.LibraryViewModel
import dev.d1ito.dtox.ui.reader.ReaderMessage
import dev.d1ito.dtox.ui.reader.ReaderScreen
import dev.d1ito.dtox.ui.reader.ReaderUiState
import dev.d1ito.dtox.ui.reader.ReaderViewModel
import dev.d1ito.dtox.ui.reader.ReadingPosition
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The only activity. It shows the library, or the reader while a book is open; back closes the
 * book. Which book is open lives in [ReaderViewModel], so it survives rotation and process death.
 */
class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as DtoxApplication).container

    private val libraryViewModel: LibraryViewModel by viewModels {
        LibraryViewModel.factory(container)
    }

    private val readerViewModel: ReaderViewModel by viewModels {
        ReaderViewModel.factory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            DTOXTheme {
                App(libraryViewModel, readerViewModel, container.dispatchers.computation)
            }
        }
    }

    override fun onStop() {
        // Leaving the app may end the process: save the position without waiting for the debounce.
        readerViewModel.flushProgress()
        super.onStop()
    }

    /** Reading is full screen; a swipe from the edge shows the bars for a moment. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

/** Library while no book is open, the reader otherwise. */
@Composable
private fun App(
    libraryViewModel: LibraryViewModel,
    readerViewModel: ReaderViewModel,
    paginationDispatcher: CoroutineDispatcher,
) {
    val readerState by readerViewModel.state.collectAsState()
    if (readerState == ReaderUiState.Closed) {
        LibraryScreen(libraryViewModel, onOpenBook = readerViewModel::open)
    } else {
        BackHandler(onBack = readerViewModel::close)
        Reader(readerState, readerViewModel, paginationDispatcher)
    }
}

@Composable
private fun Reader(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    paginationDispatcher: CoroutineDispatcher,
) {
    when (state) {
        ReaderUiState.Closed, ReaderUiState.Loading -> EmptyScreen()
        ReaderUiState.Unavailable -> ReaderMessage(stringResource(R.string.reader_book_unavailable))
        is ReaderUiState.Reading ->
            ReaderScreen(
                chapter = state.chapter,
                position =
                    ReadingPosition(
                        restoredAnchor = state.position,
                        onPageSettled = viewModel::onPageSettled,
                        onNextChapter = viewModel::onNextChapter,
                        onPreviousChapter = viewModel::onPreviousChapter,
                    ),
                paginationDispatcher = paginationDispatcher,
            )
    }
}

/** Shown while the book loads: only the theme background. */
@Composable
private fun EmptyScreen() {
    Box(modifier = Modifier.fillMaxSize().background(DTOXTheme.palette.background))
}
