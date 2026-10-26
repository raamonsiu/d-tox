package dev.d1ito.dtox

import android.app.Application

/** Owns the [AppContainer] for the whole process (D-32). */
class DtoxApplication : Application() {
    val container: AppContainer by lazy {
        DefaultAppContainer(
            filesDirectory = filesDir,
            cacheDirectory = cacheDir,
        )
    }
}
