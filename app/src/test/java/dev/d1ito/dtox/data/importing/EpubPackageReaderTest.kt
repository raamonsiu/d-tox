package dev.d1ito.dtox.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Lists a non-OPF rootfile first: the reader must pick the OPF by media type. */
private const val PDF_FIRST_CONTAINER =
    """
    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
      <rootfiles>
        <rootfile full-path="other.pdf" media-type="application/pdf"/>
        <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
      </rootfiles>
    </container>
    """

class EpubPackageReaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val reader = EpubPackageReader()

    @Test
    fun epub3_readsTitleAuthorsByRefinedRoleAndLanguage() {
        val metadata = reader.read(epub3Letters()).getOrThrow().metadata

        assertEquals(EpubMetadata("Cartas a Lucilio", listOf("Séneca"), "es"), metadata)
    }

    @Test
    fun epub3_listsDecodedSpinePathsInSpineOrderWithLinearity() {
        val spine = reader.read(epub3Letters()).getOrThrow().spine

        assertEquals(
            listOf("OEBPS/cover.xhtml", "OEBPS/Text/carta2.xhtml", "OEBPS/Text/carta 1.xhtml"),
            spine.map { item -> item.path },
        )
        assertEquals(listOf(false, true, true), spine.map { item -> item.isLinear })
        assertEquals("application/xhtml+xml", spine.first().mediaType)
    }

    @Test
    fun epub2_keepsAuthorsByOpfRoleAndPicksThePackageRootFile() {
        val opf =
            packageDocument(
                version = "2.0",
                metadata =
                    """
                    <dc:title>Niebla</dc:title>
                    <dc:creator opf:role="aut">Miguel de Unamuno</dc:creator>
                    <dc:creator opf:role="edt">Editor</dc:creator>
                    <dc:creator>Sin rol</dc:creator>
                    """,
            )
        val epub =
            temporaryFolder.epubFile(
                PDF_FIRST_CONTAINER,
                "content.opf" to opf,
                "one.xhtml" to TEST_CHAPTER,
            )

        val epubPackage = reader.read(epub).getOrThrow()

        assertEquals(listOf("Miguel de Unamuno", "Sin rol"), epubPackage.metadata.authors)
        assertNull(epubPackage.metadata.language)
        assertEquals(listOf("one.xhtml"), epubPackage.spine.map { item -> item.path })
    }

    @Test
    fun skipsSpineItemsThatAreMissingRemoteOrOutsideTheArchive() {
        val manifest =
            listOf(
                xhtmlItem("ok", "ok.xhtml"),
                xhtmlItem("gone", "gone.xhtml"),
                xhtmlItem("escape", "../../outside.xhtml"),
                xhtmlItem("remote", "https://example.invalid/r.xhtml"),
            ).joinToString("")
        val spine =
            listOf("ghost", "gone", "escape", "remote", "ok").joinToString("") { identifier ->
                """<itemref idref="$identifier"/>"""
            }
        val epub =
            temporaryFolder.epubFile(
                containerFor("OEBPS/content.opf"),
                "OEBPS/content.opf" to packageDocument(manifest = manifest, spine = spine),
                "OEBPS/ok.xhtml" to TEST_CHAPTER,
            )

        val spinePaths =
            reader
                .read(epub)
                .getOrThrow()
                .spine
                .map { item -> item.path }

        assertEquals(listOf("OEBPS/ok.xhtml"), spinePaths)
    }

    /** EPUB 3 with the OPF in a subfolder, a non-linear cover and a percent-encoded href. */
    private fun epub3Letters(): File {
        val metadata =
            """
            <dc:title>  Cartas
              a Lucilio </dc:title>
            <dc:creator id="author">Séneca</dc:creator>
            <meta refines="#author" property="role" scheme="marc:relators">aut</meta>
            <dc:creator id="illustrator">Ilustradora</dc:creator>
            <meta refines="#illustrator" property="role" scheme="marc:relators">ill</meta>
            <dc:language>es</dc:language>
            """
        val manifest =
            xhtmlItem("cover", "cover.xhtml") + xhtmlItem("one", "Text/carta%201.xhtml") +
                xhtmlItem("two", "Text/carta2.xhtml")
        val spine =
            """<itemref idref="cover" linear="no"/>""" +
                """<itemref idref="two"/><itemref idref="one"/>"""
        return temporaryFolder.epubFile(
            containerFor("OEBPS/content.opf"),
            "OEBPS/content.opf" to packageDocument("3.0", metadata, manifest, spine),
            "OEBPS/cover.xhtml" to TEST_CHAPTER,
            "OEBPS/Text/carta 1.xhtml" to TEST_CHAPTER,
            "OEBPS/Text/carta2.xhtml" to TEST_CHAPTER,
        )
    }
}
