package dev.d1ito.dtox

import android.app.Application
import java.io.File

/** Per-book user data files under `files/` (docs/02-data-formats.md §4). */
private const val BOOKS_DIRECTORY = "books"

/** Owns the [AppContainer] for the whole process (D-32). */
class DtoxApplication : Application() {
    val container: AppContainer by lazy {
        DefaultAppContainer(
            openSampleBook = { resources.openRawResource(R.raw.sample_quijote_chapter_one) },
            booksDirectory = File(filesDir, BOOKS_DIRECTORY),
        )
    }
}
