package dev.d1ito.dtox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.ui.reader.ReaderMessage
import dev.d1ito.dtox.ui.reader.ReaderScreen
import dev.d1ito.dtox.ui.theme.DTOXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            DTOXTheme {
                SampleReader((application as DtoxApplication).container)
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

/** Reader prototype on the bundled sample chapter, until importing and the library exist. */
@Composable
private fun SampleReader(container: AppContainer) {
    val sampleBook by produceState<Result<Book>?>(initialValue = null, container) {
        value = container.sampleBookLoader.load()
    }
    val loaded = sampleBook
    when {
        loaded == null -> EmptyScreen()
        loaded.isFailure -> ReaderMessage(stringResource(R.string.reader_chapter_unavailable))
        else ->
            ReaderScreen(
                chapter = loaded.getOrThrow().chapters.first(),
                paginationDispatcher = container.dispatchers.computation,
            )
    }
}

/** Shown while the sample loads: only the theme background. */
@Composable
private fun EmptyScreen() {
    Box(modifier = Modifier.fillMaxSize().background(DTOXTheme.palette.background))
}
