package dev.d1ito.dtox.data.importing

import org.w3c.dom.Element
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Reads chapter titles from an EPUB table of contents: the `<nav epub:type="toc">` links of an
 * EPUB 3 navigation document, or the `navPoint` labels of an EPUB 2 NCX. Links are resolved like
 * manifest hrefs (relative to the TOC document, fragment dropped), so several entries pointing
 * into the same document collapse to the first one, which is its title.
 *
 * @return zip entry name of each linked document → its title; empty if the TOC is unreadable,
 *   so callers fall back to other titles.
 * @throws IOException if the TOC entry exists but cannot be read.
 */
internal fun titlesFromTableOfContents(
    archive: ZipFile,
    tableOfContents: EpubTableOfContentsDocument,
): Map<String, String> {
    val entry = archive.getEntry(tableOfContents.path) ?: return emptyMap()
    val document =
        archive.getInputStream(entry).use { input -> parsedXmlOrNull(input) } ?: return emptyMap()
    val links =
        when (tableOfContents.format) {
            EpubTableOfContentsFormat.NAVIGATION_DOCUMENT ->
                navigationLinks(
                    document.documentElement,
                )
            EpubTableOfContentsFormat.NCX -> ncxLinks(document.documentElement)
        }
    val tableOfContentsDirectory = archiveDirectoryOf(tableOfContents.path)
    val titles = LinkedHashMap<String, String>()
    links.forEach { (href, title) ->
        val path = resolveArchivePath(tableOfContentsDirectory, href) ?: return@forEach
        titles.putIfAbsent(path, title)
    }
    return titles
}

/** `href to title` of the links in the `toc` nav, or in the first nav if none is marked. */
private fun navigationLinks(root: Element): List<Pair<String, String>> {
    val navigations = root.descendants("nav")
    val tableOfContents =
        navigations.firstOrNull(Element::isTableOfContentsNavigation)
            ?: navigations.firstOrNull()
            ?: return emptyList()
    return tableOfContents.descendants("a").mapNotNull { link ->
        val title = link.normalizedText()
        val href = link.getAttribute("href")
        if (title == null || href.isEmpty()) null else href to title
    }
}

/** `epub:type` lists `toc` among its space-separated values. */
private fun Element.isTableOfContentsNavigation(): Boolean =
    attributeByLocalName("type")?.split(' ')?.contains("toc") == true

/** `src to label` of every `navPoint`, in document order (parents before their children). */
private fun ncxLinks(root: Element): List<Pair<String, String>> =
    root.descendants("navPoint").mapNotNull { navPoint ->
        val label = navPoint.directChild("navLabel")?.normalizedText() ?: return@mapNotNull null
        val source = navPoint.directChild("content")?.getAttribute("src").orEmpty()
        if (source.isEmpty()) null else source to label
    }

/** First child element with this local name; descendants of nested navPoints are not searched. */
private fun Element.directChild(localName: String): Element? =
    (0 until childNodes.length)
        .map { position -> childNodes.item(position) }
        .firstOrNull { node ->
            node is Element && (node.localName ?: node.nodeName) == localName
        } as? Element
