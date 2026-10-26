package dev.d1ito.dtox.data.importing

import java.net.URLDecoder

private const val PATH_SEPARATOR = '/'
private val uriSchemePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

/**
 * Resolves a manifest `href` against the directory of the package document, the way OCF defines
 * it: drops the fragment and query, percent-decodes (`%20` → space) and normalizes `.` and `..`.
 *
 * @param baseDirectory directory of the OPF inside the archive, `""` for the root, no trailing `/`.
 * @param href the raw attribute value.
 * @return the zip entry name, or `null` when the href is remote (has a scheme), malformed, empty
 *   or would escape the archive root.
 */
internal fun resolveArchivePath(
    baseDirectory: String,
    href: String,
): String? {
    val withoutFragment = href.substringBefore('#').substringBefore('?')
    if (withoutFragment.isEmpty() || uriSchemePattern.containsMatchIn(withoutFragment)) return null
    val decoded = percentDecoded(withoutFragment) ?: return null
    val joined = if (decoded.startsWith(PATH_SEPARATOR)) decoded else "$baseDirectory/$decoded"
    return normalizedArchivePath(joined)
}

/**
 * Normalizes a path inside the archive (no decoding): removes empty and `.` segments and applies
 * `..` to the previous segment.
 *
 * @return the normalized path, or `null` if it is empty or `..` climbs above the archive root.
 */
internal fun normalizedArchivePath(path: String): String? {
    val segments = ArrayDeque<String>()
    for (segment in path.split(PATH_SEPARATOR)) {
        when (segment) {
            "", "." -> Unit
            ".." -> segments.removeLastOrNull() ?: return null
            else -> segments.addLast(segment)
        }
    }
    return segments.joinToString(PATH_SEPARATOR.toString()).ifEmpty { null }
}

/** Directory part of an archive path, `""` for a file at the root. */
internal fun archiveDirectoryOf(path: String): String = path.substringBeforeLast(PATH_SEPARATOR, "")

/**
 * Decodes `%XX` sequences as UTF-8. `+` stays a literal plus: URLDecoder would turn it into a
 * space, which is only correct for form data, not for URI paths.
 *
 * @return the decoded text, or `null` for malformed escapes such as `%G1` or a trailing `%`.
 */
private fun percentDecoded(text: String): String? =
    try {
        // The charset-name overload exists on every API level; the Charset one needs API 33.
        URLDecoder.decode(text.replace("+", "%2B"), Charsets.UTF_8.name())
    } catch (expectedMalformedEscape: IllegalArgumentException) {
        null
    }
