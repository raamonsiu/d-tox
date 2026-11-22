package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val SPLIT_NCX_NAMESPACE = "http://www.daisy.org/z3986/2005/ncx/"
private const val SPLIT_EPUB_NAMESPACE = "http://www.idpf.org/2007/ops"

/** Several chapters in one document, Project Gutenberg style, with text before the first one. */
private val GROUPED_CHAPTERS =
    chapterDocument(
        "<p>Dedicatoria.</p>" +
            "<div class=\"chapter\" id=\"c1\"><h2>Capítulo I</h2><p>Uno.</p></div>" +
            "<div class=\"chapter\"><h2 id=\"c2\"><a id=\"old-c2\"/>Capítulo II</h2>" +
            "<p>Dos.</p></div>" +
            "<div class=\"chapter\" id=\"c3\"><h2>Capítulo III</h2><p>Tres.</p></div>",
    )

/** A second document linked without fragments. */
private val SINGLE_CHAPTER = chapterDocument("<h2>Apéndice</h2><p>Final.</p>")

/** Splitting spine documents at the elements the table of contents links (#179, RF-16). */
class EpubChapterSplittingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val parser by lazy {
        EpubBookParser(Dispatchers.Unconfined, temporaryFolder.newFolder("work"))
    }

    @Test
    fun epub3Navigation_linkingSeveralAnchors_splitsTheDocumentAtEachOne() {
        val epub =
            epubWithNavigation(
                links =
                    listOf(
                        "Text/grouped.xhtml" to "Libro primero",
                        "Text/grouped.xhtml#c1" to "Capítulo I",
                        "Text/grouped.xhtml#c2" to "Capítulo II",
                        "Text/grouped.xhtml#c2" to "Repetido",
                        "Text/grouped.xhtml#c3" to "Capítulo III",
                        "Text/appendix.xhtml" to "Apéndice final",
                    ),
            )

        val chapters = parser.parseFile(epub).getOrThrow().chapters

        assertEquals(
            listOf("Libro primero", "Capítulo I", "Capítulo II", "Capítulo III", "Apéndice final"),
            chapters.map { chapter -> chapter.title },
        )
        assertEquals(listOf(0, 1, 2, 3, 4), chapters.map { chapter -> chapter.index })
        assertEquals(listOf(Block.Paragraph("Dedicatoria.")), chapters[0].blocks)
        assertEquals(
            listOf(heading("Capítulo II"), Block.Paragraph("Dos.")),
            chapters[2].blocks,
        )
    }

    @Test
    fun epub2Ncx_linkingSeveralAnchors_splitsTheDocument_titlingTheLeadingTextByHeading() {
        val epub =
            epubWithNcx(
                links =
                    listOf(
                        "Text/grouped.xhtml#c1" to "Capítulo I",
                        "Text/grouped.xhtml#old-c2" to "Capítulo II",
                        "Text/grouped.xhtml#c3" to "Capítulo III",
                    ),
            )

        val chapters = parser.parseFile(epub).getOrThrow().chapters

        assertEquals(
            listOf(null, "Capítulo I", "Capítulo II", "Capítulo III", "Apéndice"),
            chapters.map { chapter -> chapter.title },
        )
        assertEquals(listOf(Block.Paragraph("Tres.")), chapters[3].blocks.drop(1))
    }

    @Test
    fun missingAnchorsOrNoFragments_keepOneChapterPerDocument() {
        val epub =
            epubWithNavigation(
                links =
                    listOf(
                        "Text/grouped.xhtml#missing" to "Primera parte",
                        "Text/grouped.xhtml#other-missing" to "Otra",
                        "Text/appendix.xhtml" to "Apéndice final",
                    ),
            )

        val chapters = parser.parseFile(epub).getOrThrow().chapters

        assertEquals(
            listOf("Primera parte", "Apéndice final"),
            chapters.map { chapter -> chapter.title },
        )
        assertEquals(7, chapters[0].blocks.size)
    }

    @Test
    fun anchorAtTheStartOfTheDocument_doesNotLeaveAnEmptyLeadingChapter() {
        val document =
            chapterDocument(
                "<hr/><section id=\"s1\"><p>Uno.</p></section><section id=\"s2\"><p>Dos.</p>" +
                    "</section>",
            )
        val epub =
            epubWithNavigation(
                links = listOf("Text/grouped.xhtml#s1" to "Uno", "Text/grouped.xhtml#s2" to "Dos"),
                groupedDocument = document,
            )

        val chapters = parser.parseFile(epub).getOrThrow().chapters

        assertEquals(
            listOf("Uno", "Dos", "Apéndice"),
            chapters.map { chapter -> chapter.title },
        )
    }

    /** `grouped.xhtml` then `appendix.xhtml` under `OEBPS/Text`, with an EPUB 3 `toc` nav. */
    private fun epubWithNavigation(
        links: List<Pair<String, String>>,
        groupedDocument: String = GROUPED_CHAPTERS,
    ): File {
        val items =
            links.joinToString(
                "",
            ) { (href, title) -> "<li><a href=\"$href\">$title</a></li>" }
        val navigation =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"$SPLIT_EPUB_NAMESPACE\">" +
                "<body><nav epub:type=\"toc\"><ol>$items</ol></nav></body></html>"
        return epub(
            tableOfContentsItem =
                """<item id="nav" href="nav.xhtml" properties="nav" """ +
                    """media-type="application/xhtml+xml"/>""",
            spineAttributes = "",
            tableOfContents = "OEBPS/nav.xhtml" to navigation,
            groupedDocument = groupedDocument,
        )
    }

    /** The same documents with an EPUB 2 NCX. */
    private fun epubWithNcx(links: List<Pair<String, String>>): File {
        val navigationPoints =
            links.withIndex().joinToString("") { (position, link) ->
                "<navPoint id=\"p$position\"><navLabel><text>${link.second}</text></navLabel>" +
                    "<content src=\"${link.first}\"/></navPoint>"
            }
        return epub(
            tableOfContentsItem =
                """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""",
            spineAttributes = """ toc="ncx"""",
            tableOfContents =
                "OEBPS/toc.ncx" to
                    "<ncx xmlns=\"$SPLIT_NCX_NAMESPACE\" version=\"2005-1\"><navMap>" +
                    "$navigationPoints</navMap></ncx>",
            groupedDocument = GROUPED_CHAPTERS,
        )
    }

    private fun epub(
        tableOfContentsItem: String,
        spineAttributes: String,
        tableOfContents: Pair<String, String>,
        groupedDocument: String,
    ): File {
        val manifest =
            xhtmlItem("grouped", "Text/grouped.xhtml") +
                xhtmlItem("appendix", "Text/appendix.xhtml") +
                tableOfContentsItem
        val opf =
            packageDocument(
                manifest = manifest,
                spine = """<itemref idref="grouped"/><itemref idref="appendix"/>""",
            ).replace("<spine>", "<spine$spineAttributes>")
        return temporaryFolder.epubFile(
            containerFor("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf,
            tableOfContents,
            "OEBPS/Text/grouped.xhtml" to groupedDocument,
            "OEBPS/Text/appendix.xhtml" to SINGLE_CHAPTER,
        )
    }

    private fun heading(text: String) = Block.Heading.create(2, text).getOrThrow()
}
