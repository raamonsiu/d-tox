package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.domain.Anchor
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.relocate
import dev.d1ito.dtox.domain.textOrNull

/** Characters of the book's text shown for a bookmark or a note, which hold no text themselves. */
internal const val ANNOTATION_EXCERPT_LENGTH = 80

/** Marks where the book's text is cut in an excerpt. */
internal const val ANNOTATION_EXCERPT_CUT_MARK = "…"

/** What an annotation is, as the list of marks and notes names it. */
enum class AnnotationKind { BOOKMARK, HIGHLIGHT, NOTE }

/**
 * One row of the list of marks and notes (RF-23).
 *
 * @property identifier the annotation's, unique in the list.
 * @property target where tapping the row opens the reader: the annotation's start, relocated in
 *   the book as it is now (docs/02-data-formats.md §3).
 * @property chapterTitle title of the [target]'s chapter, `null` when the chapter has none.
 * @property excerpt the highlighted text, or for a bookmark or a note up to
 *   [ANNOTATION_EXCERPT_LENGTH] characters of the book's text at [target], cut with
 *   [ANNOTATION_EXCERPT_CUT_MARK]; `null` when there is no text there (e.g. a separator).
 * @property note the user's text, `null` when there is none.
 */
data class AnnotationRow(
    val identifier: AnnotationIdentifier,
    val kind: AnnotationKind,
    val target: Anchor,
    val chapterTitle: String?,
    val excerpt: String?,
    val note: String?,
)

/**
 * Builds the rows of the list of marks and notes. Relocating an anchor may search a chapter's
 * text: call it off the main thread.
 *
 * @param annotations the book's annotations, already in reading order; the rows keep that order.
 * @return one row per annotation.
 */
fun Book.annotationRows(annotations: List<BookAnnotation>): List<AnnotationRow> =
    annotations.map { annotation -> annotationRow(annotation) }

private fun Book.annotationRow(annotation: BookAnnotation): AnnotationRow {
    val target = relocate(annotation.start)
    return AnnotationRow(
        identifier = annotation.metadata.identifier,
        kind =
            when (annotation) {
                is BookAnnotation.Bookmark -> AnnotationKind.BOOKMARK
                is BookAnnotation.Highlight -> AnnotationKind.HIGHLIGHT
                is BookAnnotation.Note -> AnnotationKind.NOTE
            },
        target = target,
        chapterTitle = chapters.getOrNull(target.chapter)?.title,
        excerpt = (annotation as? BookAnnotation.Highlight)?.text ?: excerptAt(target),
        note = annotation.note,
    )
}

/** @return the start of the book's text at [anchor], or `null` when there is none there. */
private fun Book.excerptAt(anchor: Anchor): String? {
    val text =
        chapters
            .getOrNull(anchor.chapter)
            ?.blocks
            ?.getOrNull(anchor.block)
            ?.textOrNull
            ?.takeIf { blockText -> anchor.offset in blockText.indices }
            ?: return null
    val end = anchor.offset + ANNOTATION_EXCERPT_LENGTH
    return if (end < text.length) {
        text.substring(anchor.offset, end).trimEnd() + ANNOTATION_EXCERPT_CUT_MARK
    } else {
        text.substring(anchor.offset)
    }
}
