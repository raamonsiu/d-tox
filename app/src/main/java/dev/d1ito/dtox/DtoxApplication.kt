package dev.d1ito.dtox

import android.app.Application
import android.net.Uri
import dev.d1ito.dtox.data.storage.ContentResolverDocumentTree

/** Owns the [AppContainer] for the whole process (D-32). */
class DtoxApplication : Application() {
    val container: AppContainer by lazy {
        DefaultAppContainer(
            filesDirectory = filesDir,
            cacheDirectory = cacheDir,
            documentTreeOf = { treeUri ->
                ContentResolverDocumentTree(contentResolver, Uri.parse(treeUri))
            },
        )
    }
}
