package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Chapter

/** Whether the blocks have something to read, not only separators. */
internal fun List<Block>.hasReadableText(): Boolean = any { block -> block !is Block.Separator }

/**
 * Builds the chapters of an EPUB from its readable documents, in spine order (RF-16).
 *
 * A document is split at every element that a table of contents entry links with a fragment
 * (`chapter.xhtml#part-2`): each part starts at that element and is titled with the first entry
 * linking it, and the text before the first linked element stays a chapter. A document without
 * such links (no fragments, or fragments naming no element) stays one chapter. Unsplit parts take
 * the first entry linking the document without a usable fragment, or else their first heading.
 * Parts with only separators are skipped.
 *
 * @param tableOfContents entries in TOC order; empty when the book has no readable TOC.
 * @return the chapters, numbered by position.
 */
internal fun chaptersFromDocuments(
    documents: List<EpubContentDocument>,
    tableOfContents: List<EpubTableOfContentsEntry>,
): List<Chapter> {
    val entriesByPath = tableOfContents.groupBy(EpubTableOfContentsEntry::path)
    return documents
        .flatMap { document -> partsOf(document, entriesByPath[document.path].orEmpty()) }
        .mapIndexed { index, part ->
            // index comes from the list position, so it is never negative.
            Chapter.create(index, part.title, part.blocks).getOrThrow()
        }
}

/** A run of blocks of one document that becomes a chapter. */
private data class ChapterPart(
    val title: String?,
    val blocks: List<Block>,
)

/**
 * Splits [document] at the elements its [entries] link.
 *
 * @param entries the TOC entries pointing into this document, in TOC order.
 */
private fun partsOf(
    document: EpubContentDocument,
    entries: List<EpubTableOfContentsEntry>,
): List<ChapterPart> {
    val blocks = document.content.blocks
    val titleByStartBlock = mutableMapOf<Int, String>()
    val documentTitles = mutableListOf<String>()
    entries.forEach { entry ->
        val startBlock =
            entry.fragment
                ?.let(document.content.blockIndexByElementIdentifier::get)
                ?.takeIf { blockIndex -> blockIndex in blocks.indices }
        if (startBlock == null) {
            documentTitles += entry.title
        } else {
            titleByStartBlock.putIfAbsent(startBlock, entry.title)
        }
    }
    val startBlocks = (titleByStartBlock.keys + 0).sorted()
    return startBlocks.mapIndexedNotNull { position, startBlock ->
        val partBlocks =
            blocks.subList(
                startBlock,
                startBlocks.getOrNull(position + 1) ?: blocks.size,
            )
        val title =
            titleByStartBlock[startBlock]
                ?: documentTitles.firstOrNull()
                ?: partBlocks.filterIsInstance<Block.Heading>().firstOrNull()?.text
        ChapterPart(title, partBlocks).takeIf { partBlocks.hasReadableText() }
    }
}
