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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.d1ito.dtox.data.storage.ReadingSettingsStore
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.ui.library.LibraryScreen
import dev.d1ito.dtox.ui.library.LibraryViewModel
import dev.d1ito.dtox.ui.reader.ChapterList
import dev.d1ito.dtox.ui.reader.ReaderControlActions
import dev.d1ito.dtox.ui.reader.ReaderControlsBar
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
                App(
                    libraryViewModel,
                    readerViewModel,
                    container.readingSettingsStore,
                    container.dispatchers.computation,
                )
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
    settingsStore: ReadingSettingsStore,
    paginationDispatcher: CoroutineDispatcher,
) {
    val readerState by readerViewModel.state.collectAsState()
    // null until read from disk: paginating with the defaults first would paginate twice.
    val settings by settingsStore.settings.collectAsState(initial = null)
    if (readerState == ReaderUiState.Closed) {
        LibraryScreen(libraryViewModel, onOpenBook = readerViewModel::open)
    } else {
        BackHandler(onBack = readerViewModel::close)
        val loadedSettings = settings
        if (loadedSettings == null) {
            EmptyScreen()
        } else {
            Reader(readerState, readerViewModel, loadedSettings, paginationDispatcher)
        }
    }
}

@Composable
private fun Reader(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    settings: ReadingSettings,
    paginationDispatcher: CoroutineDispatcher,
) {
    when (state) {
        ReaderUiState.Closed, ReaderUiState.Loading -> EmptyScreen()
        ReaderUiState.Unavailable -> ReaderMessage(stringResource(R.string.reader_book_unavailable))
        is ReaderUiState.Reading ->
            ReadingWithControls(state, viewModel, PageRendering(settings, paginationDispatcher))
    }
}

/**
 * How pages are laid out: the reading settings and where pagination runs.
 *
 * @property paginationDispatcher where pages are measured; never the main thread.
 */
private class PageRendering(
    val settings: ReadingSettings,
    val paginationDispatcher: CoroutineDispatcher,
)

/** The pages with their controls bar, or the chapter list the bar opens; back closes the list. */
@Composable
private fun ReadingWithControls(
    state: ReaderUiState.Reading,
    viewModel: ReaderViewModel,
    rendering: PageRendering,
) {
    var chaptersVisible by rememberSaveable { mutableStateOf(false) }
    if (chaptersVisible) {
        BackHandler { chaptersVisible = false }
        ChapterList(
            chapters = state.book.chapters,
            currentIndex = state.position.chapter,
            onSelect = { index ->
                chaptersVisible = false
                viewModel.onChapterSelected(index)
            },
        )
    } else {
        PagesWithControls(state, viewModel, rendering) { chaptersVisible = true }
    }
}

/** The pages, and the controls bar a tap in the center shows; turning a page hides it. */
@Composable
private fun PagesWithControls(
    state: ReaderUiState.Reading,
    viewModel: ReaderViewModel,
    rendering: PageRendering,
    onOpenChapters: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.position) { controlsVisible = false }
    Box {
        ReaderScreen(
            chapter = state.chapter,
            position =
                ReadingPosition(
                    restoredAnchor = state.position,
                    onPageSettled = viewModel::onPageSettled,
                    onNextChapter = viewModel::onNextChapter,
                    onPreviousChapter = viewModel::onPreviousChapter,
                    onCenterTap = { controlsVisible = !controlsVisible },
                ),
            paginationDispatcher = rendering.paginationDispatcher,
            settings = rendering.settings,
        )
        ReaderControlsBar(
            visible = controlsVisible,
            actions = ReaderControlActions(onBack = viewModel::close, onChapters = onOpenChapters),
            onHide = { controlsVisible = false },
        )
    }
}

/** Shown while the book loads: only the theme background. */
@Composable
private fun EmptyScreen() {
    Box(modifier = Modifier.fillMaxSize().background(DTOXTheme.palette.background))
}
