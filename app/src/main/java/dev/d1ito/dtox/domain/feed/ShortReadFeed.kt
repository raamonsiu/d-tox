package dev.d1ito.dtox.domain.feed

import java.time.Instant
import java.time.LocalDate

/**
 * The short-reads feed `index.json` (docs/02-data-formats.md §6): every reading published so far,
 * cumulative (D-36).
 *
 * @property generatedAt when the pipeline generated the feed.
 * @property language language code of the readings, e.g. `es`.
 * @property items in the stable editorial order of the feed.
 */
data class ShortReadFeed(
    val generatedAt: Instant,
    val language: String,
    val items: List<ShortRead>,
)

/**
 * One reading of the feed. Its text is the Markdown file at [path].
 *
 * @property id unique within the feed, e.g. `seneca-cartas-lucilio-001`.
 * @property author `null` when the feed gives none.
 * @property tags free labels; empty when the feed gives none.
 * @property minutes estimated reading time; the feed rounds it to a duration bucket.
 * @property words word count of the text.
 * @property license e.g. `PD` (public domain), `CC-BY`, `CC-BY-SA`.
 * @property translator `null` when the text is not a translation or the feed gives none.
 * @property path relative to the feed root, e.g. `items/seneca-cartas-lucilio-001.md`.
 * @property sha256 digest of the item file, to verify integrity and deduplicate.
 * @property publishedAt day the reading entered the feed.
 */
data class ShortRead(
    val id: String,
    val title: String,
    val author: String?,
    val category: ShortReadCategory,
    val tags: List<String>,
    val minutes: Int,
    val words: Int,
    val license: String,
    val source: ShortReadSource,
    val translator: String?,
    val path: String,
    val sha256: String,
    val publishedAt: LocalDate,
)

/**
 * Where the text was taken from, shown as attribution (docs/05-content-sources.md).
 *
 * @property name e.g. `Wikisource`.
 * @property url the original page.
 */
data class ShortReadSource(
    val name: String,
    val url: String,
)

/**
 * Category of a reading. The set is extensible, so values other than the MVP ones (D-04) are kept
 * as they come.
 *
 * @property value the feed form, e.g. `filosofia`.
 */
@JvmInline
value class ShortReadCategory(
    val value: String,
) {
    companion object {
        /** Classical philosophy (D-04). */
        val PHILOSOPHY = ShortReadCategory("filosofia")

        /** Popular science (D-04). */
        val SCIENCE = ShortReadCategory("ciencia")
    }
}
