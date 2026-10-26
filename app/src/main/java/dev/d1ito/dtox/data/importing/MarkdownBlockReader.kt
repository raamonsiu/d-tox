package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block
import org.commonmark.node.BlockQuote
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Markdown read with commonmark-java (CommonMark spec) and mapped onto the document model.
 *
 * Inline markup is reduced to its text: emphasis markers, link targets and images are dropped.
 * Code blocks become paragraphs; raw HTML is ignored. Headings deeper than level 3 read as 3.
 */
object MarkdownBlockReader : TextBlockReader {
    private val parser: Parser = Parser.builder().build()

    override fun read(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        childrenOf(parser.parse(text)).forEach { node -> appendBlocks(node, blocks, listDepth = 0) }
        return blocks.toList()
    }

    /** Appends the model blocks for one CommonMark block node and its nested lists. */
    private fun appendBlocks(
        node: Node,
        blocks: MutableList<Block>,
        listDepth: Int,
    ) {
        if (node is ListBlock) {
            childrenOf(node).forEach { item -> appendListItem(item, node, blocks, listDepth) }
        } else {
            blockOf(node)?.let(blocks::add)
        }
    }

    /** @return the model block for a non-list node, or `null` if it has no readable text. */
    private fun blockOf(node: Node): Block? =
        when (node) {
            is Heading -> headingOf(node)
            is Paragraph, is FencedCodeBlock, is IndentedCodeBlock ->
                textOf(
                    node,
                )?.let(Block::Paragraph)
            is BlockQuote -> textOf(node)?.let(Block::Quote)
            is ThematicBreak -> Block.Separator
            else -> null // HTML blocks are not readable text.
        }

    /** One list item: its own text first, then any nested list one level deeper. */
    private fun appendListItem(
        item: Node,
        list: ListBlock,
        blocks: MutableList<Block>,
        depth: Int,
    ) {
        if (item !is ListItem) return
        val (nestedLists, ownContent) = childrenOf(item).partition { child -> child is ListBlock }
        joinedText(ownContent.map(::rawText))?.let { text ->
            // depth starts at 0 and only grows, so it is never negative.
            blocks.add(
                Block.ListItem
                    .create(
                        text,
                        ordered = list is OrderedList,
                        depth = depth,
                    ).getOrThrow(),
            )
        }
        nestedLists.forEach { nested -> appendBlocks(nested, blocks, depth + 1) }
    }

    private fun headingOf(heading: Heading): Block.Heading? {
        val text = textOf(heading) ?: return null
        // CommonMark levels are 1..6; capping at 3 keeps them valid.
        return Block.Heading
            .create(
                heading.level.coerceAtMost(Block.Heading.LOWEST_LEVEL),
                text,
            ).getOrThrow()
    }

    /** Normalized readable text of [node], or `null` when it has none. */
    private fun textOf(node: Node): String? = joinedText(listOf(rawText(node)))

    /** Text of [node] and all its descendants; markup is dropped, line breaks become spaces. */
    private fun rawText(node: Node): String =
        when (node) {
            is Text -> node.literal
            is Code -> node.literal
            is FencedCodeBlock -> node.literal
            is IndentedCodeBlock -> node.literal
            is SoftLineBreak, is HardLineBreak -> " "
            is Image -> ""
            // Block children (paragraphs in a quote) need a space between them; inline ones do not.
            else -> {
                val separator = if (node.firstChild is org.commonmark.node.Block) " " else ""
                childrenOf(node).joinToString(separator = separator, transform = ::rawText)
            }
        }

    private fun childrenOf(node: Node): List<Node> =
        generateSequence(node.firstChild) { child -> child.next }.toList()
}
