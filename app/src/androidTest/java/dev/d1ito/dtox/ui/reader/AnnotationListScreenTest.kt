package dev.d1ito.dtox.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnchorRange
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.AnnotationMetadata
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import dev.d1ito.dtox.ui.theme.DTOXTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val ROWS_TIMEOUT_MILLIS = 5_000L

@RunWith(AndroidJUnit4::class)
class AnnotationListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    private val book: Book =
        Book
            .create(
                BookIdentifier.parse("sha256:0123456789abcdef0123456789abcdef").getOrThrow(),
                BookMetadata(title = "Libro", author = null, language = "es"),
                listOf(
                    chapter(0, "Prólogo", "Era un día claro."),
                    chapter(1, null, "Al fondo, el Corazón de la sierra."),
                ),
            ).getOrThrow()

    @Test
    fun rows_showTypeChapterExcerptAndNote_andTappingOneReportsItsAnchor() {
        val chosen = mutableListOf<Anchor>()
        val highlightStart = Anchor.create(1, 0, 13).getOrThrow()
        val highlight =
            BookAnnotation.Highlight
                .create(
                    metadata("00000000-0000-4000-8000-000000000001"),
                    AnchorRange
                        .create(
                            highlightStart,
                            Anchor.create(1, 0, 20).getOrThrow(),
                        ).getOrThrow(),
                    "Corazón",
                    "Volver aquí",
                ).getOrThrow()
        val bookmark =
            BookAnnotation.Bookmark(
                metadata("00000000-0000-4000-8000-000000000002"),
                Anchor.create(0, 0, 0).getOrThrow(),
            )
        show(listOf(bookmark, highlight)) { anchor -> chosen += anchor }
        composeRule.waitUntil(ROWS_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText("«Corazón»")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Marcador · Prólogo").assertIsDisplayed()
        composeRule.onNodeWithText("«Era un día claro.»").assertIsDisplayed()
        composeRule.onNodeWithText("Resaltado · Capítulo 2").assertIsDisplayed()
        composeRule.onNodeWithText("Volver aquí").assertIsDisplayed()
        composeRule
            .onNodeWithText("«Corazón»")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(listOf(highlightStart), chosen.map { anchor -> anchor.withoutQuote() })
    }

    @Test
    fun noAnnotations_sayItInOneSentence_butUnreadableOnesShowNoSentence() {
        var annotations by mutableStateOf<List<BookAnnotation>?>(null)
        composeRule.setContent {
            DTOXTheme {
                AnnotationListScreen(
                    book,
                    annotations,
                    Dispatchers.Default,
                    onSelect = {},
                )
            }
        }
        composeRule.onNodeWithText(text(R.string.annotations_list_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.annotations_list_empty)).assertDoesNotExist()

        annotations = emptyList()

        composeRule.waitUntil(ROWS_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(hasText(text(R.string.annotations_list_empty)))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun theControlsBar_opensTheList() {
        var opened = false
        composeRule.setContent {
            DTOXTheme {
                ReaderControlsBar(
                    visible = true,
                    ReaderControlActions(onBack = {}, onAnnotations = { opened = true }),
                    onHide = {},
                )
            }
        }

        composeRule
            .onNodeWithText(text(R.string.reader_controls_annotations))
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(true, opened)
    }

    private fun show(
        annotations: List<BookAnnotation>,
        onSelect: (Anchor) -> Unit,
    ) {
        composeRule.setContent {
            DTOXTheme { AnnotationListScreen(book, annotations, Dispatchers.Default, onSelect) }
        }
    }

    private fun chapter(
        index: Int,
        title: String?,
        text: String,
    ): Chapter = Chapter.create(index, title, listOf(Block.Paragraph(text))).getOrThrow()

    private fun metadata(identifier: String): AnnotationMetadata =
        AnnotationMetadata(
            AnnotationIdentifier.parse(identifier).getOrThrow(),
            Instant.EPOCH,
            Instant.EPOCH,
        )
}
