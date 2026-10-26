package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block

/** Turns the decoded text of a TXT, Markdown or XHTML document into document model blocks. */
fun interface TextBlockReader {
    /**
     * @param text the whole file content, any line ending style.
     * @return the blocks in reading order; never contains blocks with blank text.
     */
    fun read(text: String): List<Block>
}

/** Plain text: only paragraphs, separated by blank lines. Markup characters are kept as text. */
object PlainTextBlockReader : TextBlockReader {
    override fun read(text: String): List<Block> =
        paragraphLineGroups(linesOf(text))
            .mapNotNull { lines -> joinedText(lines) }
            .map { paragraphText -> Block.Paragraph(paragraphText) }

    private fun paragraphLineGroups(lines: List<String>): List<List<String>> =
        lines.fold(mutableListOf(mutableListOf<String>())) { groups, line ->
            if (line.isBlank()) groups.add(mutableListOf()) else groups.last().add(line)
            groups
        }
}

private val whitespaceRun = Regex("\\s+")

/** Splits [text] into lines, accepting `\r\n`, `\r` and `\n` endings. */
internal fun linesOf(text: String): List<String> =
    text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

/**
 * Joins wrapped lines of one block with single spaces.
 *
 * @return the joined text, or `null` when every line is blank (so no empty block is emitted).
 */
internal fun joinedText(lines: List<String>): String? =
    lines
        .joinToString(separator = " ")
        .replace(whitespaceRun, " ")
        .trim()
        .ifEmpty { null }
