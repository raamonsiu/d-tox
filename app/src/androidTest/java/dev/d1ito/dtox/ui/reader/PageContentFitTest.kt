package dev.d1ito.dtox.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.d1ito.dtox.data.importing.MarkdownBlockReader
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.domain.textOrNull
import dev.d1ito.dtox.pagination.Page
import dev.d1ito.dtox.pagination.PageFragment
import dev.d1ito.dtox.pagination.Paginator
import dev.d1ito.dtox.pagination.Viewport
import dev.d1ito.dtox.ui.theme.DTOXTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

private val SAMPLE_CHAPTER_MARKDOWN =
    """
    # De la brevedad de la vida

    La mayor parte de los mortales se queja de la malignidad de la naturaleza, porque nacemos
    para un tiempo breve y porque los espacios de tiempo que se nos conceden pasan tan veloces
    y rápidos que, salvo muy pocos, a los demás la vida los abandona en los preparativos mismos.

    > No tenemos poco tiempo, sino que perdemos mucho. La vida es suficientemente larga y se
    > nos ha dado con generosidad para realizar las mayores empresas.

    ## Lo que nos quita el tiempo

    1. La avaricia insaciable.
    2. El trabajo laborioso en cosas inútiles.
       - La embriaguez.
       - La pereza.
    3. La ambición que depende del juicio ajeno.

    ***

    ### Nota

    Nadie te devolverá los años, nadie te restituirá a ti mismo. Seguirá su curso la vida y no
    volverá atrás ni detendrá su carrera; no hará ruido, no advertirá de su velocidad.
    """.trimIndent()

@RunWith(AndroidJUnit4::class)
class PageContentFitTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun drawnPages_fitTheMeasuredViewport_onAPhoneSizedPage() =
        assertPagesFit(widthPixels = 900, heightPixels = 1400)

    @Test
    fun drawnPages_fitTheMeasuredViewport_onASmallPage() =
        assertPagesFit(widthPixels = 480, heightPixels = 600)

    @Test
    fun drawnPages_fitTheMeasuredViewport_withDoubleSystemFontScale() =
        assertPagesFit(widthPixels = 900, heightPixels = 1400, fontScale = 2f)

    @Test
    fun drawnPages_fitTheMeasuredViewport_withTheSmallestTextSettings() =
        assertPagesFit(
            widthPixels = 900,
            heightPixels = 1400,
            settings =
                ReadingSettings.Default
                    .edited(
                        textSizeSp = 14,
                        lineSpacing = 1.5,
                    ).getOrThrow(),
        )

    @Test
    fun drawnPages_fitTheMeasuredViewport_withTheLargestTextSettings() =
        assertPagesFit(
            widthPixels = 900,
            heightPixels = 1400,
            settings =
                ReadingSettings.Default
                    .edited(
                        textSizeSp = 28,
                        lineSpacing = 1.7,
                    ).getOrThrow(),
        )

    private fun assertPagesFit(
        widthPixels: Int,
        heightPixels: Int,
        fontScale: Float? = null,
        settings: ReadingSettings = ReadingSettings.Default,
    ) {
        val chapter =
            Chapter
                .create(
                    0,
                    null,
                    MarkdownBlockReader.read(SAMPLE_CHAPTER_MARKDOWN),
                ).getOrThrow()
        val viewport = Viewport.create(widthPixels, heightPixels).getOrThrow()
        var pages: List<Page> = emptyList()
        var rewrapErrors: List<String> = emptyList()
        composeRule.setContent {
            val systemDensity = LocalDensity.current
            val density =
                fontScale?.let { scale -> Density(systemDensity.density, scale) } ?: systemDensity
            CompositionLocalProvider(LocalDensity provides density) {
                DTOXTheme {
                    val styles = readingStyles(settings)
                    val textMeasurer = rememberTextMeasurer()
                    val measurer = ComposeBlockMeasurer(textMeasurer, styles, density)
                    pages = Paginator.paginate(chapter, viewport, measurer).getOrThrow()
                    rewrapErrors =
                        pages.flatMap(Page::fragments).mapNotNull { fragment ->
                            rewrapErrorOf(
                                fragment,
                                chapter,
                                viewport,
                                measurer,
                                FragmentLayout(textMeasurer, styles, density),
                            )
                        }
                    DrawnPages(pages, chapter, styles, with(density) { widthPixels.toDp() })
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue("Expected several pages, got ${pages.size}", pages.size > 1)
        assertEquals(
            "Fragments that wrap differently when drawn",
            emptyList<String>(),
            rewrapErrors,
        )
        pages.indices.forEach { index ->
            val drawnHeight =
                composeRule
                    .onNodeWithTag(
                        "page-$index",
                    ).fetchSemanticsNode()
                    .size.height
            assertTrue(
                "Page $index draws $drawnHeight px > $heightPixels px",
                drawnHeight <= heightPixels,
            )
        }
        assertEquals(chapterText(chapter), pagesText(pages, chapter))
    }

    @androidx.compose.runtime.Composable
    private fun DrawnPages(
        pages: List<Page>,
        chapter: Chapter,
        styles: ReadingStyles,
        columnWidth: androidx.compose.ui.unit.Dp,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            pages.forEachIndexed { index, page ->
                Box(Modifier.requiredWidth(columnWidth)) {
                    PageContent(
                        page,
                        chapter,
                        styles,
                        Modifier.wrapContentHeight(unbounded = true).testTag("page-$index"),
                    )
                }
            }
        }
    }

    /**
     * Lays the fragment text out exactly as [PageContent] draws it and compares it with the lines
     * the paginator placed for that fragment.
     *
     * @return a description of the mismatch, or `null` when the fragment wraps as measured.
     */
    private fun rewrapErrorOf(
        fragment: PageFragment,
        chapter: Chapter,
        viewport: Viewport,
        measurer: ComposeBlockMeasurer,
        fragmentLayout: FragmentLayout,
    ): String? {
        val block = chapter.blocks[fragment.blockIndex]
        val measuredLines =
            measurer.measure(block, viewport.width).lines.filter { line ->
                line.startOffset >= fragment.startOffset && line.endOffset <= fragment.endOffset
            }
        val (drawnLineCount, drawnHeight) =
            fragmentLayout.lineCountAndHeight(
                fragment,
                block,
                viewport.width,
            )
        val measuredHeight = measuredLines.sumOf { line -> line.height }
        return if (drawnLineCount != measuredLines.size || drawnHeight > measuredHeight) {
            "block ${fragment.blockIndex} [${fragment.startOffset}, ${fragment.endOffset}): " +
                "drawn $drawnLineCount lines / $drawnHeight px, measured ${measuredLines.size} / $measuredHeight px"
        } else {
            null
        }
    }

    /** Lays out a fragment's text with the drawing style and width. */
    private class FragmentLayout(
        private val textMeasurer: TextMeasurer,
        private val styles: ReadingStyles,
        private val density: Density,
    ) {
        fun lineCountAndHeight(
            fragment: PageFragment,
            block: dev.d1ito.dtox.domain.Block,
            viewportWidth: Int,
        ): Pair<Int, Int> {
            val style = styles.styleOf(block)
            val indent = with(density) { style.startIndent.roundToPx() }
            val text =
                block.textOrNull?.substring(fragment.startOffset, fragment.endOffset)
                    ?: SEPARATOR_GLYPHS
            val layout =
                textMeasurer.measure(
                    text,
                    style.textStyle,
                    constraints =
                        Constraints(
                            maxWidth =
                                viewportWidth - indent,
                        ),
                )
            return layout.lineCount to ceil(layout.size.height.toFloat()).toInt()
        }
    }

    private fun chapterText(chapter: Chapter): String =
        chapter.blocks.joinToString("|") { block -> block.textOrNull.orEmpty() }

    /** Concatenates every fragment; equal to [chapterText] only if no text is lost or repeated. */
    private fun pagesText(
        pages: List<Page>,
        chapter: Chapter,
    ): String {
        val byBlock = sortedMapOf<Int, StringBuilder>()
        pages.flatMap(Page::fragments).forEach { fragment ->
            val text = chapter.blocks[fragment.blockIndex].textOrNull.orEmpty()
            byBlock
                .getOrPut(fragment.blockIndex) {
                    StringBuilder()
                }.append(text, fragment.startOffset, fragment.endOffset)
        }
        return chapter.blocks.indices.joinToString(
            "|",
        ) { index -> byBlock[index]?.toString().orEmpty() }
    }
}
