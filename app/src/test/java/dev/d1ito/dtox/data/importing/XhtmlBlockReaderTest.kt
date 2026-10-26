package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block
import org.junit.Assert.assertEquals
import org.junit.Test

/** Project Gutenberg style: XML declaration, DOCTYPE, head with title and CSS, plain body. */
private val GUTENBERG_CHAPTER =
    """
    <?xml version="1.0" encoding="utf-8"?>
    <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
    <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="es">
    <head><title>Don Quijote</title><style>p { text-indent: 1em; }</style></head>
    <body>
    <h2><a id="c1"></a>CAPÍTULO PRIMERO</h2>
    <p>En un lugar de la Mancha, de cuyo
       nombre no quiero acordarme,&nbsp;no ha mucho tiempo&mdash;que vivía.</p>
    <p>&nbsp;</p>
    <p class="center">Fin del capítulo.</p>
    </body>
    </html>
    """.trimIndent()

/** Standard Ebooks style: EPUB namespaces, sections, header and epub:type attributes. */
private val STANDARD_EBOOKS_CHAPTER =
    """
    <?xml version="1.0" encoding="utf-8"?>
    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
    <body epub:type="bodymatter z3998:fiction">
    <section id="chapter-1" epub:type="chapter">
    <header><h2 epub:type="ordinal">I</h2><p epub:type="title">El hidalgo</p></header>
    <p>Primer párrafo con <i>cursiva</i>, <b>negrita</b> y una nota<a href="#n1" epub:type="noteref">1</a>.</p>
    <hr/>
    <blockquote><p>Primera línea citada.</p><p>Segunda línea citada.</p></blockquote>
    </section>
    </body>
    </html>
    """.trimIndent()

/** Calibre style: no paragraphs at all, only nested div/span with generated classes. */
private val CALIBRE_CHAPTER =
    """
    <html><body class="calibre">
    <div class="calibre1"><div class="calibre2"><span class="calibre3">Texto en un div</span></div>
    <div class="calibre2">Otro <span class="italic">bloque</span></div></div>
    </body></html>
    """.trimIndent()

class XhtmlBlockReaderTest {
    @Test
    fun gutenbergChapter_readsHeadingAndParagraphs_withEntitiesDecodedAndSpacesNormalized() {
        assertEquals(
            listOf(
                heading(2, "CAPÍTULO PRIMERO"),
                paragraph(
                    "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho " +
                        "tiempo—que vivía.",
                ),
                paragraph("Fin del capítulo."),
            ),
            XhtmlBlockReader.read(GUTENBERG_CHAPTER),
        )
    }

    @Test
    fun standardEbooksChapter_readsSectionsHeaderSeparatorAndQuote() {
        assertEquals(
            listOf(
                heading(2, "I"),
                paragraph("El hidalgo"),
                paragraph("Primer párrafo con cursiva, negrita y una nota1."),
                Block.Separator,
                Block.Quote("Primera línea citada. Segunda línea citada."),
            ),
            XhtmlBlockReader.read(STANDARD_EBOOKS_CHAPTER),
        )
    }

    @Test
    fun calibreChapter_readsTextInsideNestedDivsAsParagraphs() {
        assertEquals(
            listOf(paragraph("Texto en un div"), paragraph("Otro bloque")),
            XhtmlBlockReader.read(CALIBRE_CHAPTER),
        )
    }

    @Test
    fun headingLevels_mapToOneToThree() {
        val xhtml = (1..6).joinToString("") { level -> "<h$level>Nivel $level</h$level>" }
        assertEquals(
            listOf(
                heading(1, "Nivel 1"),
                heading(2, "Nivel 2"),
                heading(3, "Nivel 3"),
                heading(3, "Nivel 4"),
                heading(3, "Nivel 5"),
                heading(3, "Nivel 6"),
            ),
            XhtmlBlockReader.read(xhtml),
        )
    }

    @Test
    fun lists_keepOrderAndDepth_withParagraphsInsideItems() {
        val xhtml =
            """
            <ol>
              <li><p>Primero</p></li>
              <li>Segundo
                <ul><li>Anidado <em>uno</em></li><li>Anidado dos</li></ul>
              </li>
            </ol>
            <ul><li>Suelto</li></ul>
            """.trimIndent()
        assertEquals(
            listOf(
                listItem("Primero", ordered = true, depth = 0),
                listItem("Segundo", ordered = true, depth = 0),
                listItem("Anidado uno", ordered = false, depth = 1),
                listItem("Anidado dos", ordered = false, depth = 1),
                listItem("Suelto", ordered = false, depth = 0),
            ),
            XhtmlBlockReader.read(xhtml),
        )
    }

    @Test
    fun activeContentMediaAndTables_areDroppedWithoutLeakingText() {
        val xhtml =
            """
            <script>alert('x')</script><style>p { color: red }</style>
            <p>Antes<script>steal()</script> después<img src="a.png" alt="imagen"/></p>
            <table><tr><td>celda</td></tr></table>
            <svg><text>vector</text></svg>
            <noscript>sin script</noscript>
            <form><button>Enviar</button></form>
            <p onclick="steal()">Texto con evento</p>
            """.trimIndent()
        assertEquals(
            listOf(paragraph("Antes después"), paragraph("Texto con evento")),
            XhtmlBlockReader.read(xhtml),
        )
    }

    @Test
    fun inlineMarkup_keepsWordSpacing_andLineBreaksBecomeSpaces() {
        val xhtml = "<p><em>Hola </em>mundo<br/>segunda<span>línea</span> <b>fin</b></p>"
        assertEquals(listOf(paragraph("Hola mundo segundalínea fin")), XhtmlBlockReader.read(xhtml))
    }

    @Test
    fun looseTextBetweenBlocks_becomesParagraphs() {
        val xhtml = "<div>Introducción suelta<p>Párrafo</p>  cola <em>final</em> </div>"
        assertEquals(
            listOf(paragraph("Introducción suelta"), paragraph("Párrafo"), paragraph("cola final")),
            XhtmlBlockReader.read(xhtml),
        )
    }

    @Test
    fun malformedMarkup_isReadLeniently() {
        val xhtml = "<body><p>Uno<p>Dos &amp; tres &#8217;cuatro&rsquo;<div>Cinco</body>"
        assertEquals(
            listOf(paragraph("Uno"), paragraph("Dos & tres ’cuatro’"), paragraph("Cinco")),
            XhtmlBlockReader.read(xhtml),
        )
    }

    @Test
    fun selfClosedAnchors_doNotMergeTheParagraphsAfterThem() {
        // Project Gutenberg: the HTML parser keeps <a/> open and it wraps what follows.
        val xhtml =
            """
            <?xml version='1.0' encoding='utf-8'?>
            <html xmlns="http://www.w3.org/1999/xhtml"><body><div class="chapter">
            <h3 id="t"><a id="id_tasa"/>TASA</h3>
            <p>Uno.</p>
            <p>Dos <a id="nota"/>con ancla.</p>
            <p>Tres.</p>
            </div></body></html>
            """.trimIndent()
        assertEquals(
            listOf(
                heading(3, "TASA"),
                paragraph("Uno."),
                paragraph("Dos con ancla."),
                paragraph("Tres."),
            ),
            XhtmlBlockReader.read(xhtml),
        )
    }

    @Test
    fun inlineElementsWrappingBlocks_areReadAsWrappers() {
        assertEquals(
            listOf(paragraph("Uno"), paragraph("Dos")),
            XhtmlBlockReader.read("<span class=\"calibre\"><p>Uno</p><p>Dos</p></span>"),
        )
    }

    @Test
    fun blankDocuments_haveNoBlocks() {
        assertEquals(emptyList<Block>(), XhtmlBlockReader.read(""))
        assertEquals(
            emptyList<Block>(),
            XhtmlBlockReader.read(
                "<html><body><p> &nbsp; </p><div>\n</div><h1></h1></body></html>",
            ),
        )
    }

    @Test
    fun preformattedText_becomesAParagraph() {
        assertEquals(
            listOf(paragraph("línea uno línea dos")),
            XhtmlBlockReader.read("<pre>línea uno\n   línea dos</pre>"),
        )
    }

    private fun paragraph(text: String) = Block.Paragraph(text)

    private fun heading(
        level: Int,
        text: String,
    ) = Block.Heading.create(level, text).getOrThrow()

    private fun listItem(
        text: String,
        ordered: Boolean,
        depth: Int,
    ) = Block.ListItem.create(text, ordered, depth).getOrThrow()
}
