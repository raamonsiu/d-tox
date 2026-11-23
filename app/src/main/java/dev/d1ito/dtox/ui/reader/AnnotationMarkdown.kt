package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.data.storage.AnnotationReadingOrder
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation

private const val TITLE_PREFIX = "# "
private const val CHAPTER_PREFIX = "## "
private const val QUOTE_PREFIX = "> "
private const val EMPTY_QUOTE_LINE = ">"
private const val PARAGRAPH_BREAK = "\n\n"

/** Backslash at the end of a line: CommonMark's hard line break, so the user's breaks stay. */
private const val HARD_LINE_BREAK = "\\\n"

/** Characters escaped wherever they appear: emphasis, code, links, HTML, tables, strikethrough. */
private const val INLINE_MARKDOWN_CHARACTERS = "\\`*_[]<|~"

/** Characters that open a heading, quote, list or heading underline at the start of a line. */
private const val LINE_START_MARKDOWN_CHARACTERS = "#>-+="

/** Also escaped inside headings, where a trailing `#` would be read as a closing sequence. */
private const val HEADING_MARKDOWN_CHARACTERS = "#"

private val lineBreaks = Regex("\r\n|[\n\r\u0085\u2028\u2029]")
private val blankLineRuns = Regex("\n{2,}")
private val orderedListMarker = Regex("^(\\d+)([.)])")
private val entityReference = Regex("&(#?[A-Za-z0-9]+;)")

private const val ANNOTATIONS_FILE_SUFFIX = "-notas.md"
private const val ANNOTATIONS_FILE_FALLBACK = "notas.md"
private const val FILE_NAME_TITLE_MAXIMUM_LENGTH = 80
private val fileNameUnsafeCharacters = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
private val whitespaceRuns = Regex("\\s+")

/**
 * The book's highlights and notes as Markdown (RF-26): `# Title`, the author, and per chapter a
 * `## Chapter` heading followed by each annotation in [AnnotationReadingOrder]. A highlight is
 * its text as a blockquote, then its note; a note without highlight quotes a short excerpt of the
 * text it points at (see [annotationRows]), then the note. Bookmarks are left out: they hold no
 * text. Book and user text is escaped so it reads as plain text, and its line breaks are kept.
 * Relocating anchors may search a chapter's text: call it off the main thread.
 *
 * @param annotations the book's annotations, in any order.
 * @param untitledChapterTitle heading of a chapter without title, from its 1-based number.
 * @return the document, ending with a line break; only the title and author when there is
 *   nothing to export.
 */
fun Book.annotationsMarkdown(
    annotations: List<BookAnnotation>,
    untitledChapterTitle: (chapterNumber: Int) -> String,
): String {
    val exported =
        annotations
            .filterNot { annotation -> annotation is BookAnnotation.Bookmark }
            .sortedWith(AnnotationReadingOrder)
    val sections = mutableListOf(markdownHeading(TITLE_PREFIX, metadata.title))
    metadata.author?.let { author -> sections += markdownParagraphs(author) }
    var currentChapter: Int? = null
    annotationRows(exported).forEach { row ->
        if (row.target.chapter != currentChapter) {
            currentChapter = row.target.chapter
            val title =
                row.chapterTitle?.takeIf(String::isNotBlank)
                    ?: untitledChapterTitle(row.target.chapter + 1)
            sections += markdownHeading(CHAPTER_PREFIX, title)
        }
        row.excerpt?.let { excerpt -> sections += markdownQuote(excerpt) }
        row.note?.let { note -> sections += markdownParagraphs(note) }
    }
    return sections.filter(String::isNotEmpty).joinToString(PARAGRAPH_BREAK, postfix = "\n")
}

/**
 * Name suggested in the "save as" dialog: the title without characters that file systems reject,
 * shortened, plus `-notas.md`; `notas.md` when nothing of the title is left.
 */
internal fun annotationsFileName(title: String): String {
    val safeTitle =
        title
            .replace(fileNameUnsafeCharacters, " ")
            .replace(whitespaceRuns, " ")
            .trim { character -> character == '.' || character.isWhitespace() }
            .take(FILE_NAME_TITLE_MAXIMUM_LENGTH)
            .trimEnd { character -> character == '.' || character.isWhitespace() }
    return if (safeTitle.isEmpty()) {
        ANNOTATIONS_FILE_FALLBACK
    } else {
        safeTitle + ANNOTATIONS_FILE_SUFFIX
    }
}

/** [text] on one line after [prefix], its line breaks as spaces. */
private fun markdownHeading(
    prefix: String,
    text: String,
): String {
    val line = paragraphsOf(text).flatten().joinToString(" ")
    return prefix + escapeLine(line, HEADING_MARKDOWN_CHARACTERS)
}

/** [text] as escaped paragraphs; empty when it has no visible text. */
private fun markdownParagraphs(text: String): String =
    paragraphsOf(text).joinToString(PARAGRAPH_BREAK) { lines ->
        lines.joinToString(HARD_LINE_BREAK) { line -> escapeLine(line) }
    }

/** [text] as a blockquote; empty when it has no visible text. */
private fun markdownQuote(text: String): String {
    val paragraphs = markdownParagraphs(text)
    if (paragraphs.isEmpty()) return ""
    return paragraphs.lines().joinToString("\n") { line ->
        if (line.isEmpty()) EMPTY_QUOTE_LINE else QUOTE_PREFIX + line
    }
}

/**
 * The paragraphs of [text], each as its lines: every kind of line break read as one, lines
 * trimmed (leading spaces would start a code block), runs of blank lines as one paragraph break.
 */
private fun paragraphsOf(text: String): List<List<String>> =
    text
        .split(lineBreaks)
        .joinToString("\n") { line -> line.trim() }
        .trim()
        .split(blankLineRuns)
        .filter(String::isNotEmpty)
        .map { paragraph -> paragraph.split("\n") }

/**
 * [line] with every Markdown character that would change how it renders escaped with a
 * backslash, so it reads as the plain text it is.
 *
 * @param alsoEscaped characters escaped anywhere besides [INLINE_MARKDOWN_CHARACTERS].
 */
private fun escapeLine(
    line: String,
    alsoEscaped: String = "",
): String {
    val inlineEscaped =
        buildString {
            line.forEach { character ->
                if (character in INLINE_MARKDOWN_CHARACTERS || character in alsoEscaped) {
                    append('\\')
                }
                append(character)
            }
        }.replace(entityReference, "\\\\&$1")
    val opensBlock = inlineEscaped.firstOrNull() in LINE_START_MARKDOWN_CHARACTERS.toList()
    return if (opensBlock) {
        "\\" + inlineEscaped
    } else {
        inlineEscaped.replace(orderedListMarker, "$1\\\\$2")
    }
}
