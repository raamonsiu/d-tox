package dev.d1ito.dtox.data.importing

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

private const val CONTAINER_PATH = "META-INF/container.xml"
private const val PACKAGE_MEDIA_TYPE = "application/oebps-package+xml"
private const val AUTHOR_ROLE = "aut"

/**
 * Reads the structure of an EPUB 2 or 3: `META-INF/container.xml` → package document (OPF) →
 * metadata and spine. The archive is opened by its central directory and only the container and
 * the OPF are read, so the book is never loaded whole into memory. Spine items that are missing
 * from the manifest or the archive, remote or outside the archive are skipped.
 *
 * Blocking: call it off the main thread.
 */
class EpubPackageReader {
    /**
     * @param epubFile a local copy of the book.
     * @return the package, or a failure: an [EpubPackageError] when the file is not a usable EPUB,
     *   or an [IOException] when reading the file fails.
     */
    fun read(epubFile: File): Result<EpubPackage> =
        try {
            ZipFile(epubFile).use { archive -> packageOf(archive) }
        } catch (expectedNotAZip: ZipException) {
            Result.failure(EpubPackageError.NotAnArchive())
        } catch (readFailure: IOException) {
            Result.failure(readFailure)
        }

    /** @throws IOException if the container or the OPF cannot be read from the archive. */
    private fun packageOf(archive: ZipFile): Result<EpubPackage> {
        val packagePath =
            packagePathOf(archive).getOrElse { failure ->
                return Result.failure(failure)
            }
        val packageElement =
            packageElementOf(archive, packagePath).getOrElse { failure ->
                return Result.failure(failure)
            }
        val spine = spineOf(packageElement, archiveDirectoryOf(packagePath), archive)
        return if (spine.isEmpty()) {
            Result.failure(EpubPackageError.EmptySpine(packagePath))
        } else {
            Result.success(EpubPackage(metadataOf(packageElement), spine))
        }
    }

    private fun packagePathOf(archive: ZipFile): Result<String> {
        val container =
            parsedEntry(archive, CONTAINER_PATH)
                ?: return Result.failure(
                    EpubPackageError.MissingContainer("$CONTAINER_PATH missing or not XML"),
                )
        val rootFiles = container.documentElement.descendants("rootfile")
        val packageRootFile =
            rootFiles.firstOrNull { rootFile ->
                rootFile.getAttribute("media-type") == PACKAGE_MEDIA_TYPE
            } ?: rootFiles.firstOrNull()
        val packagePath =
            packageRootFile?.getAttribute("full-path")?.let { fullPath ->
                normalizedArchivePath(fullPath)
            }
        return packagePath?.let { path -> Result.success(path) }
            ?: Result.failure(EpubPackageError.MissingContainer("no rootfile full-path"))
    }

    private fun packageElementOf(
        archive: ZipFile,
        packagePath: String,
    ): Result<Element> {
        val root =
            parsedEntry(archive, packagePath)?.documentElement
                ?: return Result.failure(
                    EpubPackageError.InvalidPackageDocument(packagePath, "missing or not XML"),
                )
        return if (root.localName == "package") {
            Result.success(root)
        } else {
            Result.failure(
                EpubPackageError.InvalidPackageDocument(packagePath, "root is <${root.localName}>"),
            )
        }
    }

    /** @throws IOException if the entry exists but cannot be read. */
    private fun parsedEntry(
        archive: ZipFile,
        path: String,
    ): Document? =
        archive.getEntry(path)?.let { entry ->
            archive.getInputStream(entry).use { input -> parsedXmlOrNull(input) }
        }

    private fun spineOf(
        packageElement: Element,
        packageDirectory: String,
        archive: ZipFile,
    ): List<EpubSpineItem> {
        val manifest =
            packageElement.descendants("item").associateBy { item -> item.getAttribute("id") }
        return packageElement.descendants("itemref").mapNotNull { itemReference ->
            val item = manifest[itemReference.getAttribute("idref")] ?: return@mapNotNull null
            resolveArchivePath(packageDirectory, item.getAttribute("href"))
                ?.takeIf { path -> archive.getEntry(path) != null }
                ?.let { path ->
                    EpubSpineItem(
                        path = path,
                        mediaType = item.getAttribute("media-type"),
                        isLinear = itemReference.getAttribute("linear") != "no",
                    )
                }
        }
    }

    private fun metadataOf(packageElement: Element): EpubMetadata {
        val metadata = packageElement.descendants("metadata").firstOrNull()
        val firstText = { localName: String ->
            metadata?.descendants(localName)?.firstNotNullOfOrNull { element ->
                element.normalizedText()
            }
        }
        return EpubMetadata(
            title = firstText("title"),
            authors = metadata?.let(::authorsOf).orEmpty(),
            language = firstText("language"),
        )
    }

    /**
     * `dc:creator` names whose role is author or undeclared. The role comes from `opf:role`
     * (EPUB 2) or from a `<meta refines="#id" property="role">` (EPUB 3).
     */
    private fun authorsOf(metadata: Element): List<String> {
        val refinedRoles =
            metadata
                .descendants("meta")
                .filter { meta -> meta.getAttribute("property") == "role" }
                .associate { meta ->
                    meta.getAttribute("refines").removePrefix("#") to meta.normalizedText()
                }
        return metadata.descendants("creator").mapNotNull { creator ->
            val role =
                creator.attributeByLocalName("role") ?: refinedRoles[creator.getAttribute("id")]
            creator.normalizedText()?.takeIf { role == null || role == AUTHOR_ROLE }
        }
    }
}
