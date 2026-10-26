package dev.d1ito.dtox.data.importing

import org.junit.Assert.assertTrue
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val OPF_NAMESPACE = "http://www.idpf.org/2007/opf"
private const val DUBLIN_CORE_NAMESPACE = "http://purl.org/dc/elements/1.1/"

/** Minimal well-formed XHTML content document. */
internal const val TEST_CHAPTER =
    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>x</p></body></html>"

/**
 * Writes synthetic EPUB archives into [folder]: a `mimetype` entry, the optional container and
 * the given entries, each text trimmed with [trimIndent].
 *
 * @param container `META-INF/container.xml` content, or `null` to leave it out.
 * @param entries `zip path to text content` pairs.
 */
internal fun TemporaryFolder.epubFile(
    container: String?,
    vararg entries: Pair<String, String>,
): File {
    val file = newFile()
    val containerEntry = listOfNotNull(container?.let { xml -> "META-INF/container.xml" to xml })
    val allEntries = listOf("mimetype" to "application/epub+zip") + containerEntry + entries
    ZipOutputStream(file.outputStream()).use { zip ->
        allEntries.forEach { (path, content) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(content.trimIndent().toByteArray())
            zip.closeEntry()
        }
    }
    return file
}

/** A container whose only rootfile is the OPF at [packagePath]. */
internal fun containerFor(packagePath: String): String =
    """
    <?xml version="1.0"?>
    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
      <rootfiles>
        <rootfile full-path="$packagePath" media-type="application/oebps-package+xml"/>
      </rootfiles>
    </container>
    """

/** An OPF with the given raw XML fragments; `dc` and `opf` prefixes are declared. */
internal fun packageDocument(
    version: String = "3.0",
    metadata: String = "<dc:title>Título</dc:title>",
    manifest: String = xhtmlItem("one", "one.xhtml"),
    spine: String = """<itemref idref="one"/>""",
): String =
    """
    <?xml version="1.0" encoding="utf-8"?>
    <package xmlns="$OPF_NAMESPACE" xmlns:opf="$OPF_NAMESPACE" version="$version">
      <metadata xmlns:dc="$DUBLIN_CORE_NAMESPACE">$metadata</metadata>
      <manifest>$manifest</manifest>
      <spine>$spine</spine>
    </package>
    """.trimIndent()

/** A manifest item for an XHTML content document. */
internal fun xhtmlItem(
    identifier: String,
    href: String,
): String = """<item id="$identifier" href="$href" media-type="application/xhtml+xml"/>"""

/** Asserts that [result] failed with an [ExpectedError]. */
internal inline fun <reified ExpectedError : EpubPackageError> assertEpubFailure(
    result: Result<EpubPackage>,
) {
    assertTrue(
        "Expected ${ExpectedError::class.simpleName}, got $result",
        result.exceptionOrNull() is ExpectedError,
    )
}
