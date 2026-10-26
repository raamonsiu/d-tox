package dev.d1ito.dtox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

private const val LETTER = "Ser feliz no es otra cosa que vivir conforme a la naturaleza propia."

class AnchorRelocationTest {
    private val original = book(listOf(Block.Paragraph("Carta primera."), Block.Paragraph(LETTER)))
    private val anchor = original.anchorAt(chapter = 0, block = 1, offset = 4).getOrThrow()

    @Test
    fun quote_isUpToFortyCharactersFromThePosition() {
        assertEquals(LETTER.substring(4, 4 + ANCHOR_QUOTE_LENGTH), anchor.quote)
        assertEquals("naturaleza propia.", original.quoteAt(0, 1, LETTER.indexOf("naturaleza")))
        assertNull(original.quoteAt(0, 1, LETTER.length))
        assertNull(book(listOf(Block.Separator)).quoteAt(0, 0, 0))
    }

    @Test
    fun matchingQuote_keepsTheAnchor() {
        assertSame(anchor, original.relocate(anchor))
    }

    @Test
    fun quoteShiftedInsideTheBlock_movesTheOffset() {
        val edited =
            book(listOf(Block.Paragraph("Carta primera."), Block.Paragraph("Dice Séneca: $LETTER")))

        val relocated = edited.relocate(anchor)

        assertEquals(Triple(0, 1, "Dice Séneca: ".length + 4), relocated.position())
        assertEquals(anchor.quote, relocated.quote)
    }

    @Test
    fun quoteMovedToAnotherBlock_findsTheNearestBlock() {
        val split =
            book(
                listOf(
                    Block.Paragraph("Carta primera."),
                    Block.Heading.create(2, "Nota añadida").getOrThrow(),
                    Block.Paragraph(LETTER),
                ),
            )

        assertEquals(Triple(0, 2, 4), split.relocate(anchor).position())
    }

    @Test
    fun quoteNotFound_fallsBackToTheStartOfTheBlock() {
        val rewritten =
            book(
                listOf(
                    Block.Paragraph("Carta primera."),
                    Block.Paragraph("Texto distinto por completo."),
                ),
            )

        val relocated = rewritten.relocate(anchor)

        assertEquals(Triple(0, 1, 0), relocated.position())
        assertEquals("Texto distinto por completo.", relocated.quote)
    }

    @Test
    fun anchorOutsideTheBook_fallsBackToTheNearestStart() {
        val shorter = book(listOf(Block.Paragraph("Único párrafo.")))
        val beyondBlocks = Anchor.create(0, 5, 3, "no está").getOrThrow()
        val beyondChapters = Anchor.create(7, 0, 0, "no está").getOrThrow()

        assertEquals(Triple(0, 0, 0), shorter.relocate(beyondBlocks).position())
        assertEquals(Triple(0, 0, 0), shorter.relocate(beyondChapters).position())
        assertEquals("Único párrafo.", shorter.relocate(beyondChapters).quote)
    }

    @Test
    fun anchorWithoutQuote_isOnlyClampedToItsBlock() {
        val withoutQuote = Anchor.create(0, 1, 500).getOrThrow()

        assertEquals(Triple(0, 1, LETTER.length), original.relocate(withoutQuote).position())
    }

    private fun Anchor.position(): Triple<Int, Int, Int> = Triple(chapter, block, offset)

    private fun book(blocks: List<Block>): Book =
        Book
            .create(
                BookIdentifier.parse("sha256:0123456789abcdef0123456789abcdef").getOrThrow(),
                BookMetadata("Cartas", null, "es"),
                listOf(Chapter.create(0, null, blocks).getOrThrow()),
            ).getOrThrow()
}
