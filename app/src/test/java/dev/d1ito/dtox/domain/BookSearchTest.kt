package dev.d1ito.dtox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSearchTest {
    @Test
    fun search_ignoresCaseAndAccents_inBothDirections() {
        val book = bookOf(listOf("El Corazón late; CORAZON y corazon."))

        val fromAccented = book.search("corazón").map { result -> result.anchor.offset }
        val fromPlain = book.search("CORAZON").map { result -> result.anchor.offset }

        assertEquals(listOf(3, 17, 27), fromAccented)
        assertEquals(fromAccented, fromPlain)
    }

    @Test
    fun spanishLetters_matchTheirPlainForm() {
        val book = bookOf(listOf("Un pingüino pasó el AÑO en la cañada."))

        assertEquals(listOf(3), book.search("pinguino").map { result -> result.anchor.offset })
        assertEquals(listOf(20), book.search("año").map { result -> result.anchor.offset })
        assertEquals(listOf(30), book.search("canada").map { result -> result.anchor.offset })
    }

    @Test
    fun positions_areInTheOriginalText_whenFoldingChangesItsLength() {
        // "ﬁ" is one character that folds into two; "é" is two that fold into one.
        val text = "La ﬁesta del café fue el ﬁnal y el fin."
        val book = bookOf(listOf(text))

        val fiesta = book.search("fiesta").single()
        val cafe = book.search("café").single()
        val endings = book.search("fin")

        assertEquals(3, fiesta.anchor.offset)
        assertEquals("ﬁesta", fiesta.excerpt.substring(fiesta.occurrenceInExcerpt))
        assertEquals("café", cafe.excerpt.substring(cafe.occurrenceInExcerpt))
        assertEquals(
            listOf(text.indexOf("ﬁnal"), text.indexOf("fin.")),
            endings.map { it.anchor.offset },
        )
        assertEquals(
            listOf("ﬁn", "fin"),
            endings.map { it.excerpt.substring(it.occurrenceInExcerpt) },
        )
    }

    @Test
    fun results_followReadingOrder_withChapterTitleAndQuote() {
        val book =
            bookOf(
                listOf("Primera luz."),
                listOf("Sin luz", null, "Otra luz al final."),
                titles = listOf("Uno", null),
            )

        val results = book.search("luz")

        assertEquals(
            listOf(Triple(0, 0, 8), Triple(1, 0, 4), Triple(1, 2, 5)),
            results.map { result ->
                Triple(result.anchor.chapter, result.anchor.block, result.anchor.offset)
            },
        )
        assertEquals(listOf("Uno", null, null), results.map { result -> result.chapterTitle })
        assertEquals("luz al final.", results.last().anchor.quote)
    }

    @Test
    fun longBlocks_areCutAroundTheOccurrence() {
        val text = "a".repeat(100) + " aguja " + "b".repeat(100)
        val result = bookOf(listOf(text)).search("aguja").single()

        assertTrue(result.excerpt.startsWith("…"))
        assertTrue(result.excerpt.endsWith("…"))
        assertTrue(result.excerpt.length < text.length)
        assertEquals("aguja", result.excerpt.substring(result.occurrenceInExcerpt))
    }

    @Test
    fun results_stopAtTheMaximum() {
        val book = bookOf(listOf("la ".repeat(10), "la ".repeat(10)))

        assertEquals(5, book.search("la", maximumResults = 5).size)
        assertEquals(20, book.search("la").size)
    }

    @Test
    fun blankQuery_findsNothing() {
        val book = bookOf(listOf("Texto con espacios."))

        assertEquals(emptyList<SearchResult>(), book.search("   "))
        assertEquals(emptyList<SearchResult>(), book.search("́"))
    }

    /**
     * A book whose chapters hold the given block texts; `null` is a separator.
     *
     * @param titles chapter titles; untitled when missing.
     */
    private fun bookOf(
        vararg chapterTexts: List<String?>,
        titles: List<String?> = emptyList(),
    ): Book =
        Book
            .create(
                BookIdentifier.parse("sha256:0123456789abcdef0123456789abcdef").getOrThrow(),
                BookMetadata(title = "Libro", author = null, language = "es"),
                chapterTexts.mapIndexed { index, texts ->
                    Chapter
                        .create(
                            index,
                            titles.getOrNull(index),
                            texts.map { text -> text?.let(Block::Paragraph) ?: Block.Separator },
                        ).getOrThrow()
                },
            ).getOrThrow()
}
