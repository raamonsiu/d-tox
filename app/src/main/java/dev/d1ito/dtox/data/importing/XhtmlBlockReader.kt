package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

/** Active content, media and layout that never becomes reading text (RNF-13, MVP scope). */
private const val DROPPED_ELEMENTS =
    "script, style, noscript, template, img, picture, svg, math, video, audio, canvas, " +
        "object, embed, iframe, table, form, button, input, select, textarea"

/** XHTML heading level → model level; levels below 3 read as 3. */
private val headingLevels =
    mapOf("h1" to 1, "h2" to 2, "h3" to 3, "h4" to 3, "h5" to 3, "h6" to 3)

/** Elements read as one block of their whole text. */
private val textBlockFactories: Map<String, (String) -> Block> =
    mapOf("p" to Block::Paragraph, "pre" to Block::Paragraph, "blockquote" to Block::Quote)

private val listTags = setOf("ul", "ol")

/** Wrappers whose children are read one by one; loose text inside them becomes paragraphs. */
private val containerTags =
    setOf(
        "body",
        "div",
        "section",
        "article",
        "main",
        "header",
        "footer",
        "aside",
        "nav",
        "figure",
        "figcaption",
        "dl",
        "dt",
        "dd",
        "address",
        "center",
        "hgroup",
        "details",
        "summary",
    )

/**
 * Reads the XHTML of one EPUB content document into document model blocks.
 *
 * Parsing is lenient (jsoup's HTML parser), so malformed markup and HTML entities undefined in
 * XML still read. Nothing is executed: scripts, styles, images, tables and forms are removed
 * before reading, and inline markup is reduced to its text. `h1`–`h6` become headings 1..3,
 * `p`/`pre` paragraphs, `blockquote` a quote, `ul`/`ol` list items with their nesting depth and
 * `hr` a separator. Loose text inside wrappers such as `div` becomes paragraphs, and inline
 * elements that end up wrapping blocks (misnested or self-closed `a`) are read as wrappers.
 */
object XhtmlBlockReader : TextBlockReader {
    override fun read(text: String): List<Block> {
        val body = Jsoup.parse(text).body()
        body.select(DROPPED_ELEMENTS).remove()
        return BlockCollector().apply { addContainer(body) }.blocks.toList()
    }
}

/** Accumulates blocks while walking the element tree in reading order. */
private class BlockCollector {
    val blocks = mutableListOf<Block>()

    /** Reads block children in order; runs of inline content between them become paragraphs. */
    fun addContainer(container: Element) {
        val inlineRun = mutableListOf<Node>()
        container.childNodes().forEach { node ->
            if (node is Element && (node.isBlockLevel() || node.containsBlocks())) {
                addParagraphOf(inlineRun)
                inlineRun.clear()
                addBlock(node)
            } else {
                inlineRun += node
            }
        }
        addParagraphOf(inlineRun)
    }

    private fun addBlock(element: Element) {
        val tag = element.normalName()
        val headingLevel = headingLevels[tag]
        when {
            headingLevel != null -> addHeading(headingLevel, element)
            tag in textBlockFactories ->
                readableText(element.text())?.let { text ->
                    blocks +=
                        textBlockFactories.getValue(tag)(text)
                }
            tag in listTags -> addList(element, depth = 0)
            tag == "li" -> addListItem(element, ordered = false, depth = 0)
            tag == "hr" -> blocks += Block.Separator
            else -> addContainer(element)
        }
    }

    private fun addHeading(
        level: Int,
        element: Element,
    ) {
        val text = readableText(element.text()) ?: return
        // Levels come from headingLevels, always 1..3.
        blocks += Block.Heading.create(level, text).getOrThrow()
    }

    private fun addList(
        list: Element,
        depth: Int,
    ) {
        list.children().filter { child -> child.normalName() == "li" }.forEach { item ->
            addListItem(item, ordered = list.normalName() == "ol", depth = depth)
        }
    }

    /** The item's own text first, then its nested lists one level deeper. */
    private fun addListItem(
        item: Element,
        ordered: Boolean,
        depth: Int,
    ) {
        val (nestedLists, ownContent) =
            item.childNodes().partition { child ->
                child is Element && child.normalName() in listTags
            }
        textOf(ownContent)?.let { text ->
            // depth starts at 0 and only grows, so it is never negative.
            blocks += Block.ListItem.create(text, ordered, depth).getOrThrow()
        }
        nestedLists.filterIsInstance<Element>().forEach { nested -> addList(nested, depth + 1) }
    }

    private fun addParagraphOf(inlineNodes: List<Node>) {
        textOf(inlineNodes)?.let { text -> blocks += Block.Paragraph(text) }
    }
}

private fun Element.isBlockLevel(): Boolean {
    val tag = normalName()
    return tag in headingLevels ||
        tag in textBlockFactories ||
        tag in listTags ||
        tag in containerTags ||
        tag == "li" ||
        tag == "hr"
}

/**
 * Whether an element has block-level descendants. In XHTML `<a id="x"/>` is an empty anchor, but
 * the HTML parser ignores `/>` on non-void elements, so the anchor stays open and wraps the
 * paragraphs after it (Project Gutenberg EPUBs do this). Such inline elements are read as
 * containers so their paragraphs are not merged into one.
 */
private fun Element.containsBlocks(): Boolean =
    children().any { child -> child.isBlockLevel() || child.containsBlocks() }

/**
 * Text of sibling nodes as jsoup renders it (spaces between words kept across inline elements,
 * `br` as a space), or `null` when it is blank.
 */
private fun textOf(nodes: List<Node>): String? {
    if (nodes.isEmpty()) return null
    val wrapper = Element("span")
    nodes.forEach { node -> wrapper.appendChild(node.clone()) }
    return readableText(wrapper.text())
}

/** Normalized text (jsoup already turns no-break spaces into spaces), or `null` if blank. */
private fun readableText(text: String): String? = joinedText(listOf(text))
