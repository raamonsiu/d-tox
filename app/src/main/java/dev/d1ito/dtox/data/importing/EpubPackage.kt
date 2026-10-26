package dev.d1ito.dtox.data.importing

/**
 * The structure of an EPUB read from its package document (OPF), before any content is parsed.
 *
 * @property metadata descriptive data declared in the OPF.
 * @property spine content documents in reading order; never empty.
 */
data class EpubPackage(
    val metadata: EpubMetadata,
    val spine: List<EpubSpineItem>,
)

/**
 * @property title first non-blank `dc:title`, `null` when the OPF declares none.
 * @property authors `dc:creator` values whose role is author or undeclared, in OPF order.
 * @property language first `dc:language` (BCP 47), `null` when missing.
 */
data class EpubMetadata(
    val title: String?,
    val authors: List<String>,
    val language: String?,
)

/**
 * One spine entry that exists inside the archive.
 *
 * @property path decoded, normalized zip entry name (e.g. `OEBPS/Text/chapter 1.xhtml`).
 * @property mediaType as declared in the manifest (usually `application/xhtml+xml`).
 * @property isLinear `false` for `linear="no"` items such as covers, read only on demand.
 */
data class EpubSpineItem(
    val path: String,
    val mediaType: String,
    val isLinear: Boolean,
)

/** Why a file could not be read as an EPUB package. */
sealed class EpubPackageError(
    message: String,
) : BookParseError(message) {
    /** The file is not a readable zip archive. */
    class NotAnArchive : EpubPackageError("The file is not a zip archive")

    /** `META-INF/container.xml` is missing, unparsable or names no package document. */
    class MissingContainer(
        reason: String,
    ) : EpubPackageError("Invalid EPUB container: $reason")

    /** The package document (OPF) is missing, unparsable or not an OPF `package`. */
    class InvalidPackageDocument(
        val packagePath: String,
        reason: String,
    ) : EpubPackageError("Invalid package document '$packagePath': $reason")

    /** No spine item points to a content document present in the archive. */
    class EmptySpine(
        val packagePath: String,
    ) : EpubPackageError("The spine of '$packagePath' has no readable items")
}
