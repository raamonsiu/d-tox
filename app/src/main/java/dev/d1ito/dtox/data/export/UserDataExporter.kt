package dev.d1ito.dtox.data.export

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Clock

/**
 * Exports every piece of user data as one zip (RF-41), so it survives a reinstall.
 *
 * @param filesDirectory the app's `files/`, laid out as in docs/02-data-formats.md.
 * @param ioDispatcher where the export runs; never the main thread.
 * @param clock dates the export.
 * @param appVersionName reads the installed version; called on [ioDispatcher].
 */
class UserDataExporter(
    private val filesDirectory: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
    private val appVersionName: () -> String,
) {
    /**
     * Writes the zip (see [writeUserDataZip]) to the stream [openDestination] gives.
     *
     * @param includeBooks also packs the book files, not only progress, notes and settings.
     * @param openDestination opens the destination chosen by the user; called on [ioDispatcher].
     *   It throws an [IOException] when the destination cannot be opened.
     * @return success once the zip is complete, or the [IOException] that stopped it.
     */
    suspend fun export(
        includeBooks: Boolean,
        openDestination: () -> OutputStream,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            val manifest = UserDataExportManifest(includeBooks, appVersionName(), clock.instant())
            val output =
                try {
                    openDestination()
                } catch (openFailure: IOException) {
                    return@withContext Result.failure(openFailure)
                }
            writeUserDataZip(filesDirectory, output, manifest)
        }
}
