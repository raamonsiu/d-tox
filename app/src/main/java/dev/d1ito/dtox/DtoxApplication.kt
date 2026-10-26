package dev.d1ito.dtox

import android.app.Application

/** Owns the [AppContainer] for the whole process (D-32). */
class DtoxApplication : Application() {
    val container: AppContainer by lazy {
        DefaultAppContainer(
            openSampleBook = { resources.openRawResource(R.raw.sample_quijote_chapter_one) },
            filesDirectory = filesDir,
            cacheDirectory = cacheDir,
        )
    }
}
