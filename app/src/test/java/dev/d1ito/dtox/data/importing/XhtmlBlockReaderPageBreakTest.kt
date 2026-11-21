package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block
import org.junit.Assert.assertEquals
import org.junit.Test

/** Printed page-break markers (`epub:type="pagebreak"`, `role="doc-pagebreak"`) read as nothing. */
class XhtmlBlockReaderPageBreakTest {
    @Test
    fun standalonePageBreakMarkers_produceNoParagraph() {
        // W3C reference EPUB style: the page-number span sits alone in its own div.
        val xhtml =
            """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <body><section>
            <div><span epub:type="pagebreak" id="p169" title="169">169</span></div>
            <h2>IV</h2>
            <div role="doc-pagebreak" aria-label="170">170</div>
            <p>Texto del capítulo.</p>
            <div>Antes <span epub:type="bodymatter pagebreak">171</span>después</div>
            </section></body></html>
            """.trimIndent()
        assertEquals(
            listOf(heading(2, "IV"), paragraph("Texto del capítulo."), paragraph("Antes después")),
            XhtmlBlockReader.read(xhtml),
        )
    }

    @Test
    fun pageBreakMarkersInsideParagraphs_leaveNoText_andTheRestReadsTheSame() {
        val xhtml =
            "<p>Final de una página <span epub:type=\"pagebreak\" title=\"12\">12</span>" +
                "y comienzo de otra.</p>" +
                "<p>Otra <span role=\"doc-pagebreak\">13</span><em>frase</em>.</p>"
        assertEquals(
            listOf(paragraph("Final de una página y comienzo de otra."), paragraph("Otra frase.")),
            XhtmlBlockReader.read(xhtml),
        )
    }

    @Test
    fun similarTokens_areNotPageBreakMarkers() {
        val xhtml =
            "<p>Un <span epub:type=\"pagebreaks\" role=\"doc-pagelist\">14</span> no marcador.</p>"
        assertEquals(listOf(paragraph("Un 14 no marcador.")), XhtmlBlockReader.read(xhtml))
    }

    private fun paragraph(text: String) = Block.Paragraph(text)

    private fun heading(
        level: Int,
        text: String,
    ) = Block.Heading.create(level, text).getOrThrow()
}
