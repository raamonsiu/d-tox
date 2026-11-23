package dev.d1ito.dtox.data.export

import dev.d1ito.dtox.data.storage.BookData
import dev.d1ito.dtox.data.storage.BookDataFormat
import dev.d1ito.dtox.data.storage.BookDataFormatError
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

private const val BYTES_PER_MEBIBYTE = 1024L * 1024L
private const val DRAIN_BUFFER_BYTES = 64 * 1024

/** Entry names the exporter writes; any other name (`../x`, `/x`, `a\b`…) is rejected. */
private val bookDataEntryPattern = Regex("^$BOOK_DATA_DIRECTORY/([^/\\\\]+)\\$BOOK_DATA_EXTENSION$")
private val libraryEntryPattern = Regex("^$LIBRARY_DIRECTORY/([^/\\\\.]+)\\.[a-z0-9]+$")

/**
 * Upper bounds checked while reading an import zip, by bytes actually read (never trusting the
 * sizes the zip declares), so a zip bomb is rejected before it fills memory or disk.
 *
 * @property maximumEntryCount entries in the zip.
 * @property maximumJsonBytes each of `manifest.json`, `settings.json` and `books/<bookId>.json`.
 * @property maximumBookFileBytes each book file under `library/`.
 * @property maximumTotalBytes all entries together, uncompressed.
 */
data class UserDataImportLimits(
    val maximumEntryCount: Int = DEFAULT_MAXIMUM_ENTRY_COUNT,
    val maximumJsonBytes: Long = DEFAULT_MAXIMUM_JSON_MEBIBYTES * BYTES_PER_MEBIBYTE,
    val maximumBookFileBytes: Long = DEFAULT_MAXIMUM_BOOK_FILE_MEBIBYTES * BYTES_PER_MEBIBYTE,
    val maximumTotalBytes: Long = DEFAULT_MAXIMUM_TOTAL_MEBIBYTES * BYTES_PER_MEBIBYTE,
) {
    companion object {
        const val DEFAULT_MAXIMUM_ENTRY_COUNT = 20_000
        const val DEFAULT_MAXIMUM_JSON_MEBIBYTES = 16L
        const val DEFAULT_MAXIMUM_BOOK_FILE_MEBIBYTES = 512L
        const val DEFAULT_MAXIMUM_TOTAL_MEBIBYTES = 4096L
    }
}

/** Why an import zip was rejected; returned inside [Result.failure] before anything is written. */
sealed class UserDataImportError(
    message: String,
) : IllegalArgumentException(message) {
    /** Not a zip written by [writeUserDataZip]: wrong structure, name, size or JSON. */
    class InvalidExport(
        val reason: String,
    ) : UserDataImportError("Invalid export: $reason")

    /** The manifest or a book data file comes from a newer app; importing it could lose data. */
    class UnsupportedVersion(
        val foundVersion: Int,
    ) : UserDataImportError("Export version $foundVersion is not supported")
}

/**
 * One book file of the zip, `library/<hex>.<extension>`.
 *
 * @property entryName the full entry name, to find it again in the zip.
 * @property fileName the name without `library/`, as stored in the library.
 */
data class ExportedLibraryFile(
    val entryName: String,
    val fileName: String,
    val bookIdentifier: BookIdentifier,
)

/**
 * An export zip that passed every check of [validateUserDataZip].
 *
 * @property bookData every `books/<bookId>.json`, decoded.
 * @property libraryFiles the book files, read again from the zip when imported.
 */
data class ValidatedUserDataExport(
    val bookData: List<BookData>,
    val libraryFiles: List<ExportedLibraryFile>,
)

/**
 * Reads the whole zip [input] (closed when this returns) and checks it is an export of
 * docs/02-data-formats.md §9 this app can import (§10): a `manifest.json` with the right `format`
 * and a supported `version`, only known entry names (no paths outside the export, no duplicates),
 * every size within [limits], valid zip checksums, `settings.json` as a JSON object and every
 * book data file decodable and named after its `bookId`. Writes nothing. Blocking I/O.
 *
 * @return the validated content; a [UserDataImportError] when the zip is not importable, or the
 *   [IOException] that stopped the reading.
 */
fun validateUserDataZip(
    input: InputStream,
    limits: UserDataImportLimits = UserDataImportLimits(),
): Result<ValidatedUserDataExport> =
    try {
        val entries = ZipInputStream(input).use { zip -> zip.readEntries(limits) }
        Result.success(entries.validated())
    } catch (rejected: UserDataImportError) {
        Result.failure(rejected)
    } catch (notAZip: ZipException) {
        Result.failure(UserDataImportError.InvalidExport(notAZip.message ?: "broken zip"))
    } catch (tooLarge: EntryTooLargeException) {
        Result.failure(UserDataImportError.InvalidExport(tooLarge.message.orEmpty()))
    } catch (unreadable: IOException) {
        Result.failure(unreadable)
    } catch (malformedName: IllegalArgumentException) {
        // ZipInputStream throws this for an entry name that is not valid UTF-8.
        Result.failure(UserDataImportError.InvalidExport(malformedName.message.orEmpty()))
    }

/** The entries of a zip, before parsing: the JSON ones by name and the book files. */
private class ReadEntries(
    val jsonByName: Map<String, ByteArray>,
    val libraryFiles: List<ExportedLibraryFile>,
)

/**
 * Reads every entry within [limits]: JSON entries into memory, book files only counted.
 *
 * @throws UserDataImportError.InvalidExport for an unknown or repeated name or too many entries.
 * @throws EntryTooLargeException past a size limit.
 */
private fun ZipInputStream.readEntries(limits: UserDataImportLimits): ReadEntries {
    val jsonByName = LinkedHashMap<String, ByteArray>()
    val libraryFiles = mutableListOf<ExportedLibraryFile>()
    val seenNames = HashSet<String>()
    var totalBytes = 0L
    generateSequence { nextEntry }.forEach { entry ->
        val name = entry.name
        if (seenNames.size >= limits.maximumEntryCount) invalid("too many entries")
        if (!seenNames.add(name)) invalid("repeated entry")
        val libraryFile = libraryFileOf(name)
        totalBytes +=
            when {
                libraryFile != null -> {
                    libraryFiles += libraryFile
                    BoundedInputStream(this, limits.maximumBookFileBytes).drain()
                }
                isJsonEntry(name) -> {
                    val content = BoundedInputStream(this, limits.maximumJsonBytes).readBytes()
                    jsonByName[name] = content
                    content.size.toLong()
                }
                else -> invalid("unexpected entry")
            }
        if (totalBytes > limits.maximumTotalBytes) invalid("export too large")
    }
    return ReadEntries(jsonByName, libraryFiles)
}

private fun isJsonEntry(name: String): Boolean =
    name == MANIFEST_ENTRY || name == SETTINGS_FILE || bookDataIdentifierOf(name) != null

private fun bookDataIdentifierOf(entryName: String): BookIdentifier? {
    val text = bookDataEntryPattern.matchEntire(entryName)?.groupValues?.get(1) ?: return null
    return BookIdentifier.parse(text).getOrNull()
}

private fun libraryFileOf(entryName: String): ExportedLibraryFile? {
    val hex = libraryEntryPattern.matchEntire(entryName)?.groupValues?.get(1) ?: return null
    val identifier = BookIdentifier.fromHex(hex).getOrNull() ?: return null
    return ExportedLibraryFile(entryName, entryName.substringAfter('/'), identifier)
}

/** @throws UserDataImportError when the manifest, the settings or a book data file is invalid. */
private fun ReadEntries.validated(): ValidatedUserDataExport {
    val manifest = jsonByName[MANIFEST_ENTRY] ?: invalid("missing manifest")
    checkManifest(jsonObjectOf(manifest) ?: invalid("manifest is not a JSON object"))
    // Settings are validated but not imported (docs/02-data-formats.md §10).
    val settings = jsonByName[SETTINGS_FILE]
    if (settings != null && jsonObjectOf(settings) == null) invalid("settings not a JSON object")
    val bookData =
        jsonByName
            .filterKeys { name -> name != MANIFEST_ENTRY && name != SETTINGS_FILE }
            .map { (name, content) -> bookDataOf(name, content) }
    return ValidatedUserDataExport(bookData, libraryFiles)
}

private fun checkManifest(manifest: JsonObject) {
    val format = (manifest["format"] as? JsonPrimitive)?.contentOrNull
    if (format != EXPORT_FORMAT_NAME) invalid("not a D-TOX export")
    val version = (manifest["version"] as? JsonPrimitive)?.intOrNull ?: invalid("no version")
    if (version > EXPORT_FORMAT_VERSION) throw UserDataImportError.UnsupportedVersion(version)
    if (version < 1) invalid("version $version")
}

/** @return the decoded file, whose `bookId` must match its entry [name]. */
private fun bookDataOf(
    name: String,
    content: ByteArray,
): BookData {
    val decoded = BookDataFormat.decode(content.toString(Charsets.UTF_8))
    val bookData =
        decoded.getOrElse { error ->
            if (error is BookDataFormatError.UnsupportedVersion) {
                throw UserDataImportError.UnsupportedVersion(error.foundVersion)
            }
            invalid("corrupt book data")
        }
    if (bookData.bookIdentifier != bookDataIdentifierOf(name)) invalid("bookId does not match")
    return bookData
}

/** @return the parsed object, or `null` when [content] is not a JSON object. */
private fun jsonObjectOf(content: ByteArray): JsonObject? =
    try {
        Json.parseToJsonElement(content.toString(Charsets.UTF_8)) as? JsonObject
    } catch (expectedInvalidJson: SerializationException) {
        null
    }

private fun invalid(reason: String): Nothing = throw UserDataImportError.InvalidExport(reason)

/** Reads this stream to its end. @return the number of bytes read. */
private fun InputStream.drain(): Long {
    val buffer = ByteArray(DRAIN_BUFFER_BYTES)
    var total = 0L
    var count = read(buffer)
    while (count >= 0) {
        total += count
        count = read(buffer)
    }
    return total
}

/** Thrown by [BoundedInputStream] once more than its maximum has been read. */
internal class EntryTooLargeException(
    maximumBytes: Long,
) : IOException("Entry larger than $maximumBytes bytes")

/**
 * Reads [source] (e.g. the current zip entry) but fails past [maximumBytes]. Closing it leaves
 * [source] open, so the zip can go on to its next entry.
 */
internal class BoundedInputStream(
    private val source: InputStream,
    private val maximumBytes: Long,
) : InputStream() {
    private var bytesRead = 0L

    override fun read(): Int = source.read().also { value -> if (value >= 0) counted(1) }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = source.read(buffer, offset, length).also { count -> if (count > 0) counted(count) }

    /** @throws EntryTooLargeException past [maximumBytes]. */
    private fun counted(count: Int) {
        bytesRead += count
        if (bytesRead > maximumBytes) throw EntryTooLargeException(maximumBytes)
    }
}
