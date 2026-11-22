package dev.d1ito.dtox.data.importing

import org.junit.Assert.assertEquals
import org.junit.Test

/** Identified wrappers, blocks, inline elements and empty elements. */
private val IDENTIFIED_ELEMENTS =
    """
    <body><p>Antes.</p>
    <div class="chapter" id="part">
      <h3 id="heading"><a id="inside-heading"></a>Capítulo</h3>
      <p>Uno <span id="inside-paragraph">dos</span>.</p>
      <ul id="list"><li>Elemento <b id="inside-list">uno</b></li></ul>
    </div>
    <a id="loose"></a><p>Después.</p>
    <p id="empty"> </p><hr id="rule"/>
    <span id="last"></span>
    </body>
    """.trimIndent()

/** Where element `id`s start in the blocks read by [XhtmlBlockReader.readDocument]. */
class XhtmlBlockReaderIdentifiersTest {
    @Test
    fun identifiers_mapToTheBlockWhereTheirElementStarts() {
        val document =
            XhtmlBlockReader.readDocument(IDENTIFIED_ELEMENTS)

        assertEquals(
            mapOf(
                "part" to 1,
                "heading" to 1,
                "inside-heading" to 1,
                "inside-paragraph" to 2,
                "list" to 3,
                "inside-list" to 3,
                "loose" to 4,
                "empty" to 5,
                "rule" to 5,
                "last" to 6,
            ),
            document.blockIndexByElementIdentifier,
        )
        assertEquals(6, document.blocks.size)
    }

    @Test
    fun selfClosedGutenbergAnchor_wrappingParagraphs_mapsToTheFirstOne() {
        val document =
            XhtmlBlockReader.readDocument(
                "<body><p>Prólogo.</p><a id=\"c1\"/><h2>Capítulo I</h2><p>Texto.</p></body>",
            )

        assertEquals(1, document.blockIndexByElementIdentifier["c1"])
    }

    @Test
    fun repeatedIdentifier_keepsTheFirstElement() {
        val document =
            XhtmlBlockReader.readDocument("<p id=\"twice\">Uno.</p><p id=\"twice\">Dos.</p>")

        assertEquals(mapOf("twice" to 0), document.blockIndexByElementIdentifier)
    }

    @Test
    fun readDocument_readsTheSameBlocksAsRead() {
        val xhtml = "<h1 id=\"t\">Título</h1><div><p>Uno.</p>suelto<hr/></div>"

        assertEquals(XhtmlBlockReader.read(xhtml), XhtmlBlockReader.readDocument(xhtml).blocks)
    }
}
