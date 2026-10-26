package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block
import org.junit.Assert.assertEquals
import org.junit.Test

private val MIXED_DOCUMENT =
    """
    # Sobre la brevedad de la vida

    La mayor parte de los mortales se queja
    de la malignidad de la naturaleza.

    > No es poco el tiempo que tenemos,
    > sino mucho el que perdemos.

    ## Ideas

    1. El tiempo es lo único nuestro.
    2. Vivir es aprender a vivir.
       - Toda la vida.

    ***

    Fin.
    """.trimIndent()

class TextBlockReaderTest {
    @Test
    fun plainText_splitsParagraphsOnBlankLinesAndUnwrapsLines() {
        val text = "First line\nstill first.\r\n\r\n\n  Second   paragraph.  \rWrapped."
        assertEquals(
            listOf(paragraph("First line still first."), paragraph("Second paragraph. Wrapped.")),
            PlainTextBlockReader.read(text),
        )
    }

    @Test
    fun plainText_keepsMarkupCharactersAsText() {
        assertEquals(
            listOf(paragraph("# Not a title - not a list")),
            PlainTextBlockReader.read("# Not a title\n- not a list"),
        )
    }

    @Test
    fun blankInput_givesNoBlocks() {
        assertEquals(emptyList<Block>(), PlainTextBlockReader.read(" \n\t\n"))
        assertEquals(emptyList<Block>(), MarkdownBlockReader.read("\n\n   \n"))
    }

    @Test
    fun markdown_readsAtxHeadingsAndStripsClosingSequence() {
        assertEquals(
            listOf(heading(1, "Cartas a Lucilio"), heading(2, "Carta I"), heading(3, "Notas")),
            MarkdownBlockReader.read("# Cartas a Lucilio\n## Carta I ##\n   ### Notas #"),
        )
    }

    @Test
    fun markdown_treatsDeepHeadingsAsLowestLevel() {
        assertEquals(
            listOf(heading(3, "Four"), heading(3, "Six")),
            MarkdownBlockReader.read("#### Four\n###### Six"),
        )
    }

    @Test
    fun markdown_requiresSpaceAfterHashesAndKeepsInnerHashes() {
        assertEquals(
            listOf(paragraph("#hashtag ####### seven"), heading(2, "Notes on C#")),
            MarkdownBlockReader.read("#hashtag\n####### seven\n\n## Notes on C#"),
        )
    }

    @Test
    fun markdown_skipsEmptyHeadings() {
        assertEquals(listOf(paragraph("Text")), MarkdownBlockReader.read("##\n\nText"))
    }

    @Test
    fun markdown_joinsConsecutiveQuoteLinesIntoOneQuote() {
        assertEquals(
            listOf(quote("Vindica te tibi, et tempus collige."), paragraph("After.")),
            MarkdownBlockReader.read("> Vindica te tibi,\n>et tempus collige.\n\nAfter."),
        )
    }

    @Test
    fun markdown_readsUnorderedAndOrderedListsWithDepth() {
        val text = "- one\n* two\n  + nested\n\t- tab nested\n1. first\n2) second"
        assertEquals(
            listOf(
                listItem("one", ordered = false, depth = 0),
                listItem("two", ordered = false, depth = 0),
                listItem("nested", ordered = false, depth = 1),
                listItem("tab nested", ordered = false, depth = 2),
                listItem("first", ordered = true, depth = 0),
                listItem("second", ordered = true, depth = 0),
            ),
            MarkdownBlockReader.read(text),
        )
    }

    @Test
    fun markdown_joinsContinuationLinesIntoListItem() {
        assertEquals(
            listOf(listItem("a long item that wraps", ordered = false, depth = 0)),
            MarkdownBlockReader.read("- a long item\n  that wraps"),
        )
    }

    @Test
    fun markdown_readsThematicBreaks() {
        assertEquals(
            listOf(
                paragraph("Before."),
                Block.Separator,
                Block.Separator,
                Block.Separator,
                paragraph("After."),
            ),
            MarkdownBlockReader.read("Before.\n\n---\n* * *\n___\nAfter."),
        )
    }

    @Test
    fun markdown_dropsInlineMarkupKeepingItsText() {
        assertEquals(
            listOf(paragraph("Una vida breve y foobar, ver Séneca y el código x = 1.")),
            MarkdownBlockReader.read(
                "Una *vida* **breve** y [foo*bar*](https://x.org), " +
                    "ver [Séneca](s) y el código `x = 1`.![img](a.png)",
            ),
        )
    }

    @Test
    fun markdown_readsSetextHeadingsAndNestedQuotes() {
        assertEquals(
            listOf(heading(1, "Libro"), heading(2, "Capítulo"), quote("Fuera. Dentro.")),
            MarkdownBlockReader.read(
                "Libro\n=====\n\nCapítulo\n--------\n\n> Fuera.\n>\n> > Dentro.",
            ),
        )
    }

    @Test
    fun markdown_readsCodeBlocksAsParagraphsAndIgnoresHtml() {
        assertEquals(
            listOf(paragraph("val tiempo = breve"), paragraph("After.")),
            MarkdownBlockReader.read(
                "```kotlin\nval tiempo =\n  breve\n```\n\n<div>oculto</div>\n\nAfter.",
            ),
        )
    }

    @Test
    fun markdown_listItemOrHeadingEndsTheOpenParagraph() {
        assertEquals(
            listOf(
                paragraph("Intro."),
                listItem("item", ordered = false, depth = 0),
                heading(2, "Next"),
            ),
            MarkdownBlockReader.read("Intro.\n- item\n## Next"),
        )
    }

    @Test
    fun markdown_readsARealisticMixedDocument() {
        assertEquals(expectedMixedDocumentBlocks(), MarkdownBlockReader.read(MIXED_DOCUMENT))
    }

    private fun expectedMixedDocumentBlocks(): List<Block> =
        listOf(
            heading(1, "Sobre la brevedad de la vida"),
            paragraph(
                "La mayor parte de los mortales se queja de la malignidad de la naturaleza.",
            ),
            quote("No es poco el tiempo que tenemos, sino mucho el que perdemos."),
            heading(2, "Ideas"),
            listItem("El tiempo es lo único nuestro.", ordered = true, depth = 0),
            listItem("Vivir es aprender a vivir.", ordered = true, depth = 0),
            listItem("Toda la vida.", ordered = false, depth = 1),
            Block.Separator,
            paragraph("Fin."),
        )

    private fun paragraph(text: String) = Block.Paragraph(text)

    private fun quote(text: String) = Block.Quote(text)

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
