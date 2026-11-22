package dev.d1ito.dtox.data.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Version of the export zip layout (docs/02-data-formats.md §9); an importer checks it. */
const val EXPORT_FORMAT_VERSION = 1

private const val EXPORT_FORMAT_NAME = "dtox-export"
private const val MANIFEST_ENTRY = "manifest.json"
private const val SETTINGS_FILE = "settings.json"
private const val BOOK_DATA_DIRECTORY = "books"
private const val LIBRARY_DIRECTORY = "library"
private const val BOOK_DATA_EXTENSION = ".json"

/** Leftovers that are never user data: atomic writer temporaries and set-aside corrupt files. */
private val leftoverExtensions = listOf(".tmp", ".corrupt")
private val prettyJson = Json { prettyPrint = true }

/**
 * What one export contains, recorded in its `manifest.json`.
 *
 * @property includeBooks also packs the book files of `library/`.
 * @property appVersionName version of the app that exports.
 * @property exportedAt when the export started; also the time of every entry, so the same data
 *   exported at the same instant gives the same bytes.
 */
data class UserDataExportManifest(
    val includeBooks: Boolean,
    val appVersionName: String,
    val exportedAt: Instant,
)

/**
 * Packs the user data of [filesDirectory] (the app's `files/`) as a zip into [output], with the
 * layout of docs/02-data-formats.md §9: `manifest.json`, `settings.json`, `books/<bookId>.json`
 * and, only with [UserDataExportManifest.includeBooks], `library/<file>`. Each group is sorted by
 * name, so the entry order is deterministic. Missing files and directories are skipped; `*.tmp`,
 * `*.corrupt`, caches and any other file are never packed. Files are copied byte for byte.
 *
 * Blocking I/O: call it off the main thread.
 *
 * @param output where the zip goes; closed when this returns.
 * @return success once the zip is complete, or the [IOException] that stopped it; [output] may
 *   then hold a partial zip.
 */
fun writeUserDataZip(
    filesDirectory: File,
    output: OutputStream,
    manifest: UserDataExportManifest,
): Result<Unit> =
    try {
        ZipOutputStream(output).use { zip -> zip.writeUserData(filesDirectory, manifest) }
        Result.success(Unit)
    } catch (writeFailure: IOException) {
        Result.failure(writeFailure)
    }

/** Writes every entry, in zip order; see [writeUserDataZip]. */
private fun ZipOutputStream.writeUserData(
    filesDirectory: File,
    manifest: UserDataExportManifest,
) {
    val entryTime = manifest.exportedAt.toEpochMilli()
    val manifestBytes = manifestJson(manifest).toByteArray(Charsets.UTF_8)
    writeEntry(MANIFEST_ENTRY, entryTime) { write(manifestBytes) }
    exportedFiles(filesDirectory, manifest.includeBooks).forEach { (entryName, file) ->
        writeEntry(entryName, entryTime) { file.inputStream().use { input -> input.copyTo(this) } }
    }
}

/** Adds the entry [name] dated [time], with the content [writeContent] writes to this stream. */
private fun ZipOutputStream.writeEntry(
    name: String,
    time: Long,
    writeContent: () -> Unit,
) {
    putNextEntry(ZipEntry(name).apply { this.time = time })
    writeContent()
    closeEntry()
}

/** The files to pack, as (entry name, file) pairs in zip order. */
private fun exportedFiles(
    filesDirectory: File,
    includeBooks: Boolean,
): List<Pair<String, File>> {
    val settings = File(filesDirectory, SETTINGS_FILE)
    val bookData =
        filesIn(File(filesDirectory, BOOK_DATA_DIRECTORY)) { name ->
            name.endsWith(BOOK_DATA_EXTENSION)
        }
    val books =
        if (includeBooks) {
            filesIn(File(filesDirectory, LIBRARY_DIRECTORY)) { name ->
                leftoverExtensions.none { extension -> name.endsWith(extension) }
            }
        } else {
            emptyList()
        }
    return listOfNotNull(settings.takeIf(File::isFile)?.let { SETTINGS_FILE to it }) +
        bookData.map { file -> "$BOOK_DATA_DIRECTORY/${file.name}" to file } +
        books.map { file -> "$LIBRARY_DIRECTORY/${file.name}" to file }
}

/** Regular files directly in [directory] whose name is [accepted], sorted by name. */
private fun filesIn(
    directory: File,
    accepted: (String) -> Boolean,
): List<File> =
    directory
        .listFiles()
        .orEmpty()
        .filter { file -> file.isFile && accepted(file.name) }
        .sortedBy(File::getName)

private fun manifestJson(manifest: UserDataExportManifest): String =
    prettyJson.encodeToString(
        JsonElement.serializer(),
        buildJsonObject {
            put("format", EXPORT_FORMAT_NAME)
            put("version", EXPORT_FORMAT_VERSION)
            put("appVersion", manifest.appVersionName)
            put("exportedAt", manifest.exportedAt.toString())
            put("includesBooks", manifest.includeBooks)
        },
    )
