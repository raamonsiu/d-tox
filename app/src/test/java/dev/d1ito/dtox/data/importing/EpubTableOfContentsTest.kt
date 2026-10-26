package dev.d1ito.dtox.data.importing

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val NCX_NAMESPACE = "http://www.daisy.org/z3986/2005/ncx/"
private const val EPUB_NAMESPACE = "http://www.idpf.org/2007/ops"
private const val NAVIGATION_ITEM =
    """<item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/>"""
private const val NCX_ITEM =
    """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>"""

/** Chapter titles from the EPUB table of contents (RF-16), falling back to the first heading. */
class EpubTableOfContentsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val parser by lazy {
        EpubBookParser(Dispatchers.Unconfined, temporaryFolder.newFolder("work"))
    }

    private val chapters =
        listOf(
            "Text/one.xhtml" to chapterDocument("<h1>Encabezado uno</h1><p>Texto uno.</p>"),
            "Text/two two.xhtml" to chapterDocument("<h1>Encabezado dos</h1><p>Texto dos.</p>"),
            "Text/three.xhtml" to chapterDocument("<h2>Encabezado tres</h2><p>Texto tres.</p>"),
        )

    @Test
    fun epub3NavigationDocument_titlesTheLinkedChapters() {
        val navigation =
            """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="$EPUB_NAMESPACE"><body>
              <nav epub:type="landmarks"><ol><li><a href="Text/one.xhtml">Inicio</a></li></ol></nav>
              <nav epub:type="toc"><ol>
                <li><a href="Text/one.xhtml">Carta  I</a></li>
                <li><a href="Text/two%20two.xhtml#start">Carta II</a>
                  <ol><li><a href="Text/two%20two.xhtml#part">Una parte</a></li></ol></li>
              </ol></nav>
            </body></html>
            """
        val epub =
            epub(
                tableOfContentsItem = NAVIGATION_ITEM,
                spineAttributes = "",
                tableOfContents = "OEBPS/nav.xhtml" to navigation,
            )

        assertEquals(listOf("Carta I", "Carta II", "Encabezado tres"), chapterTitles(epub))
    }

    @Test
    fun epub2Ncx_titlesTheLinkedChapters() {
        val ncx =
            """
            <ncx xmlns="$NCX_NAMESPACE" version="2005-1"><navMap>
              <navPoint id="p1">
                <navLabel><text>Libro primero</text></navLabel><content src="Text/one.xhtml"/>
                <navPoint id="p2">
                  <navLabel><text>Sección</text></navLabel><content src="Text/one.xhtml#s"/>
                </navPoint>
              </navPoint>
              <navPoint id="p3">
                <navLabel><text>Libro tercero</text></navLabel><content src="Text/three.xhtml"/>
              </navPoint>
            </navMap></ncx>
            """
        val epub =
            epub(
                tableOfContentsItem = NCX_ITEM,
                spineAttributes = """toc="ncx"""",
                tableOfContents = "OEBPS/toc.ncx" to ncx,
            )

        assertEquals(
            listOf("Libro primero", "Encabezado dos", "Libro tercero"),
            chapterTitles(epub),
        )
    }

    @Test
    fun withoutTableOfContents_titlesComeFromTheFirstHeading() {
        val epub = epub(tableOfContentsItem = "", spineAttributes = "", tableOfContents = null)

        assertEquals(
            listOf("Encabezado uno", "Encabezado dos", "Encabezado tres"),
            chapterTitles(epub),
        )
    }

    @Test
    fun unreadableTableOfContents_fallsBackToTheFirstHeading() {
        val epub =
            epub(
                tableOfContentsItem = NAVIGATION_ITEM,
                spineAttributes = "",
                tableOfContents = "OEBPS/nav.xhtml" to "<html><nav><a href=",
            )

        assertEquals(
            listOf("Encabezado uno", "Encabezado dos", "Encabezado tres"),
            chapterTitles(epub),
        )
    }

    private fun chapterTitles(epub: File): List<String?> =
        parser
            .parseFile(epub)
            .getOrThrow()
            .chapters
            .map { chapter -> chapter.title }

    /** Three chapters under `OEBPS/Text`, with an optional table of contents next to the OPF. */
    private fun epub(
        tableOfContentsItem: String,
        spineAttributes: String,
        tableOfContents: Pair<String, String>?,
    ): File {
        val manifest =
            listOf(
                "one" to "Text/one.xhtml",
                "two" to "Text/two%20two.xhtml",
                "three" to "Text/three.xhtml",
            ).joinToString("") { (identifier, href) -> xhtmlItem(identifier, href) } +
                tableOfContentsItem
        val opf =
            packageDocument(manifest = manifest, spine = "")
                .replace("<spine>", "<spine $spineAttributes>")
                .replace("<spine >", "<spine>")
                .replace(
                    "</spine>",
                    listOf(
                        "one",
                        "two",
                        "three",
                    ).joinToString("", postfix = "</spine>") { identifier ->
                        """<itemref idref="$identifier"/>"""
                    },
                )
        val entries =
            listOfNotNull("OEBPS/content.opf" to opf, tableOfContents) +
                chapters.map { (path, document) -> "OEBPS/$path" to document }
        return temporaryFolder.epubFile(containerFor("OEBPS/content.opf"), *entries.toTypedArray())
    }
}
