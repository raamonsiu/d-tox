package dev.d1ito.dtox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import dev.d1ito.dtox.data.storage.PersistableFolderAccess
import dev.d1ito.dtox.data.storage.SafWatchedFolderDocuments
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.domain.ReadingTheme
import dev.d1ito.dtox.ui.library.LibraryScreen
import dev.d1ito.dtox.ui.library.LibraryViewModel
import dev.d1ito.dtox.ui.reader.BookSearchScreen
import dev.d1ito.dtox.ui.reader.BookmarkToggle
import dev.d1ito.dtox.ui.reader.ChapterList
import dev.d1ito.dtox.ui.reader.ReaderControlActions
import dev.d1ito.dtox.ui.reader.ReaderControlsBar
import dev.d1ito.dtox.ui.reader.ReaderMessage
import dev.d1ito.dtox.ui.reader.ReaderScreen
import dev.d1ito.dtox.ui.reader.ReaderUiState
import dev.d1ito.dtox.ui.reader.ReaderViewModel
import dev.d1ito.dtox.ui.reader.ReadingPosition
import dev.d1ito.dtox.ui.settings.AboutScreen
import dev.d1ito.dtox.ui.settings.ContentPreferencesViewModel
import dev.d1ito.dtox.ui.settings.DataExportViewModel
import dev.d1ito.dtox.ui.settings.DataFolderRows
import dev.d1ito.dtox.ui.settings.DataFolderViewModel
import dev.d1ito.dtox.ui.settings.DataSettingsSection
import dev.d1ito.dtox.ui.settings.ExportDataRows
import dev.d1ito.dtox.ui.settings.ReadingSettingsViewModel
import dev.d1ito.dtox.ui.settings.ReminderSettingsViewModel
import dev.d1ito.dtox.ui.settings.SettingsContent
import dev.d1ito.dtox.ui.settings.SettingsViewModels
import dev.d1ito.dtox.ui.settings.WatchedFoldersViewModel
import dev.d1ito.dtox.ui.settings.contentSettings
import dev.d1ito.dtox.ui.settings.reminderSettings
import dev.d1ito.dtox.ui.theme.DTOXTheme
import dev.d1ito.dtox.ui.theme.WindowMatchesPalette
import dev.d1ito.dtox.ui.theme.readingPaletteFor
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The only activity. It shows the library, or the reader while a book is open; back closes the
 * book. Which book is open lives in [ReaderViewModel], so it survives rotation and process death.
 */
class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as DtoxApplication).container

    private val libraryViewModel: LibraryViewModel by viewModels {
        val folderDocuments =
            SafWatchedFolderDocuments(applicationContext.contentResolver, container.dispatchers.io)
        LibraryViewModel.factory(container, folderDocuments)
    }

    private val readerViewModel: ReaderViewModel by viewModels {
        ReaderViewModel.factory(container)
    }

    private val settingsViewModel: ReadingSettingsViewModel by viewModels {
        ReadingSettingsViewModel.factory(container)
    }

    private val watchedFoldersViewModel: WatchedFoldersViewModel by viewModels {
        val folderAccess =
            PersistableFolderAccess(applicationContext.contentResolver, container.dispatchers.io)
        WatchedFoldersViewModel.factory(container, folderAccess)
    }

    private val dataExportViewModel: DataExportViewModel by viewModels {
        DataExportViewModel.factory(container, applicationContext)
    }

    private val dataFolderViewModel: DataFolderViewModel by viewModels {
        DataFolderViewModel.factory(container, applicationContext.contentResolver)
    }

    /** The "Datos" section: the watched folders, exporting the user data, the data folder. */
    private val dataSettings: @Composable () -> Unit = {
        DataSettingsSection(watchedFoldersViewModel) {
            ExportDataRows(dataExportViewModel)
            DataFolderRows(dataFolderViewModel)
        }
    }

    private val contentPreferencesViewModel: ContentPreferencesViewModel by viewModels {
        ContentPreferencesViewModel.factory(container)
    }

    private val reminderSettingsViewModel: ReminderSettingsViewModel by viewModels {
        ReminderSettingsViewModel.factory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            val settings by settingsViewModel.settings.collectAsState()
            // Until the settings are read, the system palette matches the launch window.
            val palette =
                readingPaletteFor(
                    settings?.theme ?: ReadingTheme.SYSTEM,
                    systemIsDark = isSystemInDarkTheme(),
                )
            WindowMatchesPalette(palette)
            DTOXTheme(palette, font = (settings ?: ReadingSettings.Default).font) {
                App(
                    libraryViewModel,
                    readerViewModel,
                    SettingsViewModels(
                        settingsViewModel,
                        contentPreferencesViewModel,
                        reminderSettingsViewModel,
                    ),
                    container.dispatchers,
                    dataSettings = dataSettings,
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

/**
 * Library while no book is open, the reader otherwise; the settings screen over either of them.
 * Back closes the settings first, then the book.
 */
@Suppress("LongParameterList") // Screens' view models plus the settings sections they own.
@Composable
private fun App(
    libraryViewModel: LibraryViewModel,
    readerViewModel: ReaderViewModel,
    settingsViewModels: SettingsViewModels,
    dispatchers: AppDispatchers,
    dataSettings: @Composable () -> Unit,
) {
    val readerState by readerViewModel.state.collectAsState()
    // null until read from disk: paginating with the defaults first would paginate twice.
    val settings by settingsViewModels.reading.settings.collectAsState()
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    val loadedSettings = settings
    when {
        settingsVisible && loadedSettings != null ->
            Settings(settingsViewModels, loadedSettings, dispatchers.io, dataSettings) {
                settingsVisible = false
            }
        readerState == ReaderUiState.Closed ->
            LibraryScreen(
                libraryViewModel,
                onOpenBook = readerViewModel::open,
                onOpenSettings = { settingsVisible = true },
            )
        loadedSettings == null -> EmptyScreen()
        else -> {
            BackHandler(onBack = readerViewModel::close)
            val reading =
                ReadingSetup(loadedSettings, dispatchers.computation) {
                    settingsVisible =
                        true
                }
            Reader(readerState, readerViewModel, reading)
        }
    }
}

/**
 * The settings screen, or the "Acerca de" screen it opens; back and their own buttons close them.
 *
 * @param ioDispatcher where "Acerca de" reads the version and license texts.
 * @param dataSettings the "Datos" section.
 */
@Suppress("LongParameterList") // The reading settings plus the sections from other features.
@Composable
private fun Settings(
    viewModels: SettingsViewModels,
    settings: ReadingSettings,
    ioDispatcher: CoroutineDispatcher,
    dataSettings: @Composable () -> Unit,
    onClose: () -> Unit,
) {
    var aboutVisible by rememberSaveable { mutableStateOf(false) }
    if (aboutVisible) {
        AboutScreen(ioDispatcher, onBack = { aboutVisible = false })
    } else {
        BackHandler(onBack = onClose)
        val saveFailed by viewModels.reading.saveFailed.collectAsState()
        SettingsContent(
            settings,
            saveFailed,
            onChange = viewModels.reading::change,
            content = viewModels.content.contentSettings(),
            reminders = viewModels.reminders.reminderSettings(),
            onBack = onClose,
            onOpenAbout = { aboutVisible = true },
            data = dataSettings,
        )
    }
}

/**
 * What the reader needs besides its state.
 *
 * @property settings current reading settings.
 * @property paginationDispatcher where pages are measured and the book is searched; never the
 *   main thread.
 * @property onOpenSettings opens the settings screen from the controls bar.
 */
private class ReadingSetup(
    val settings: ReadingSettings,
    val paginationDispatcher: CoroutineDispatcher,
    val onOpenSettings: () -> Unit,
)

@Composable
private fun Reader(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    setup: ReadingSetup,
) {
    when (state) {
        ReaderUiState.Closed, ReaderUiState.Loading -> EmptyScreen()
        ReaderUiState.Unavailable -> ReaderMessage(stringResource(R.string.reader_book_unavailable))
        is ReaderUiState.Reading -> ReadingWithControls(state, viewModel, setup)
    }
}

/**
 * The pages with their controls bar, or the chapter list or the search the bar opens; back
 * closes the list or the search.
 */
@Composable
private fun ReadingWithControls(
    state: ReaderUiState.Reading,
    viewModel: ReaderViewModel,
    setup: ReadingSetup,
) {
    var chaptersVisible by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    if (searchVisible) {
        BackHandler { searchVisible = false }
        BookSearchScreen(
            book = state.book,
            searchDispatcher = setup.paginationDispatcher,
            onSelect = { anchor ->
                searchVisible = false
                viewModel.onSearchResultSelected(anchor)
            },
        )
    } else if (chaptersVisible) {
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
        PagesWithControls(state, viewModel, setup, { searchVisible = true }) {
            chaptersVisible = true
        }
    }
}

/** Opens the reader at [state]'s position and sends its page turns to [viewModel]. */
private fun readingPositionOf(
    state: ReaderUiState.Reading,
    viewModel: ReaderViewModel,
    onCenterTap: () -> Unit,
): ReadingPosition =
    ReadingPosition(
        restoredAnchor = state.position,
        onPageSettled = viewModel::onPageSettled,
        onNextChapter = viewModel::onNextChapter,
        onPreviousChapter = viewModel::onPreviousChapter,
        onCenterTap = onCenterTap,
        onPageShown = { page ->
            viewModel.annotations.onPageShown(page.startAnchor, page.endAnchor)
        },
    )

/** The pages, and the controls bar a tap in the center shows; turning a page hides it. */
@Suppress("LongParameterList") // The state and its owners, plus the two screens the bar opens.
@Composable
private fun PagesWithControls(
    state: ReaderUiState.Reading,
    viewModel: ReaderViewModel,
    setup: ReadingSetup,
    onOpenSearch: () -> Unit,
    onOpenChapters: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(false) }
    val pageBookmarked by viewModel.annotations.pageBookmarked.collectAsState()
    LaunchedEffect(state.position) { controlsVisible = false }
    Box {
        ReaderScreen(
            chapter = state.chapter,
            position = readingPositionOf(state, viewModel) { controlsVisible = !controlsVisible },
            paginationDispatcher = setup.paginationDispatcher,
            settings = setup.settings,
            book = state.book,
        )
        ReaderControlsBar(
            visible = controlsVisible,
            actions =
                ReaderControlActions(
                    onBack = viewModel::close,
                    onChapters = onOpenChapters,
                    onTextSettings = setup.onOpenSettings,
                    onSearch = onOpenSearch,
                    bookmark =
                        pageBookmarked?.let { bookmarked ->
                            BookmarkToggle(bookmarked, viewModel.annotations::toggleBookmark)
                        },
                ),
            onHide = { controlsVisible = false },
        )
    }
}

/** Shown while the book loads: only the theme background. */
@Composable
private fun EmptyScreen() {
    Box(modifier = Modifier.fillMaxSize().background(DTOXTheme.palette.background))
}
