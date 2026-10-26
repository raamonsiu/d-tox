package dev.d1ito.dtox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.d1ito.dtox.ui.library.LibraryScreen
import dev.d1ito.dtox.ui.library.LibraryViewModel
import dev.d1ito.dtox.ui.theme.DTOXTheme

/**
 * Opens on the library; moving to the reader arrives with the app navigation (#48), which must
 * also call `ReaderViewModel.flushProgress()` from `onStop` again and pass
 * `ReaderViewModel::onNextChapter`/`onPreviousChapter` in the reader's `ReadingPosition`.
 */
class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as DtoxApplication).container

    private val libraryViewModel: LibraryViewModel by viewModels {
        LibraryViewModel.factory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            DTOXTheme {
                LibraryScreen(libraryViewModel)
            }
        }
    }

    /** Reading is full screen; a swipe from the edge shows the bars for a moment. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
