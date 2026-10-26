package dev.d1ito.dtox

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import dev.d1ito.dtox.ui.reader.ReaderMessage
import dev.d1ito.dtox.ui.reader.ReaderScreen
import dev.d1ito.dtox.ui.reader.ReaderUiState
import dev.d1ito.dtox.ui.reader.ReaderViewModel
import dev.d1ito.dtox.ui.reader.ReadingPosition
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.CoroutineDispatcher

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as DtoxApplication).container

    private val readerViewModel: ReaderViewModel by viewModels {
        ReaderViewModel.factory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            DTOXTheme {
                Reader(readerViewModel, container.dispatchers.computation)
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

/** The reader on the bundled sample chapter, until importing and the library exist. */
@Composable
private fun Reader(
    viewModel: ReaderViewModel,
    paginationDispatcher: CoroutineDispatcher,
) {
    val state by viewModel.state.collectAsState()
    when (val current = state) {
        ReaderUiState.Loading -> EmptyScreen()
        ReaderUiState.Unavailable ->
            ReaderMessage(
                stringResource(R.string.reader_chapter_unavailable),
            )
        is ReaderUiState.Reading ->
            ReaderScreen(
                chapter = current.chapter,
                position = ReadingPosition(current.position, viewModel::onPageSettled),
                paginationDispatcher = paginationDispatcher,
            )
    }
}

/** Shown while the book loads: only the theme background. */
@Composable
private fun EmptyScreen() {
    Box(modifier = Modifier.fillMaxSize().background(DTOXTheme.palette.background))
}
