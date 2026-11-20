package dev.d1ito.dtox.data.importing

import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.textOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.net.URL

private const val CORPUS_RESOURCE_ROOT = "/corpus/"
private const val MANIFEST_RESOURCE = "${CORPUS_RESOURCE_ROOT}manifest.json"
private const val EPUB_FORMAT = "epub"
private const val EPUB_EXTENSION = ".epub"

/** The first parse includes class loading and JIT warm-up; the fastest is nearer steady state. */
private const val TIMED_PARSES = 3
private const val NANOSECONDS_PER_MILLISECOND = 1_000_000L

/**
 * Parses every downloaded EPUB of the test corpus (`make corpus`, see `scripts/corpus.sh`) and
 * prints how long each parse takes. The large books are not versioned, so each one whose file is
 * missing is skipped with an assumption instead of failing: CI stays green without the corpus.
 *
 * The versioned, deliberately malformed EPUBs are covered by [EpubBookParserRobustnessTest].
 * Only the corpus id, file size, chapter count and times are printed, never book content.
 *
 * @property corpusEntry the manifest entry of one downloaded EPUB.
 */
@RunWith(Parameterized::class)
class EpubCorpusTest(
    private val corpusEntry: CorpusEpub,
) {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val workDirectory: File by lazy { temporaryFolder.newFolder("work") }
    private val parser by lazy { EpubBookParser(Dispatchers.Unconfined, workDirectory) }

    @Test
    fun downloadedEpub_parsesIntoATitledBookWithText() {
        val resource = javaClass.getResource(CORPUS_RESOURCE_ROOT + corpusEntry.path)
        assumeTrue("Not downloaded (make corpus): ${corpusEntry.path}", resource != null)
        val epubFile = File(checkNotNull(resource).toURI())

        val timedParses = List(TIMED_PARSES) { timedParse(epubFile) }
        val book = timedParses.first().first.getOrThrow()
        val parseTimesInMilliseconds = timedParses.map { (_, milliseconds) -> milliseconds }

        assertTrue("${corpusEntry.identifier}: blank title", book.metadata.title.isNotBlank())
        assertNotEquals(
            "${corpusEntry.identifier}: title fell back to the file name",
            epubFile.name.removeSuffix(EPUB_EXTENSION),
            book.metadata.title,
        )
        assertTrue("${corpusEntry.identifier}: no chapter with text", book.hasChapterWithText())
        println(
            "epub-corpus id=${corpusEntry.identifier} bytes=${epubFile.length()} " +
                "chapters=${book.chapters.size} firstMs=${parseTimesInMilliseconds.first()} " +
                "bestMs=${parseTimesInMilliseconds.min()}",
        )
    }

    /** @return the parse result and its wall-clock duration in milliseconds. */
    private fun timedParse(epubFile: File): Pair<Result<Book>, Long> {
        val startNanoseconds = System.nanoTime()
        val result =
            epubFile.inputStream().use { stream ->
                runBlocking { parser.parse(stream, testBookIdentifier(), epubFile.name) }
            }
        return result to (System.nanoTime() - startNanoseconds) / NANOSECONDS_PER_MILLISECOND
    }

    private fun Book.hasChapterWithText(): Boolean =
        chapters.any { chapter ->
            chapter.blocks.any { block -> !block.textOrNull.isNullOrBlank() }
        }

    /**
     * One downloadable EPUB listed in the corpus manifest.
     *
     * @property identifier the manifest `id`.
     * @property path relative to the corpus root (`downloads/<id>.epub`).
     */
    data class CorpusEpub(
        val identifier: String,
        val path: String,
    ) {
        override fun toString(): String = identifier
    }

    companion object {
        /** @return the non-versioned EPUBs of the manifest, which `make corpus` downloads. */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun downloadableEpubs(): List<CorpusEpub> {
            val manifest: URL =
                checkNotNull(EpubCorpusTest::class.java.getResource(MANIFEST_RESOURCE)) {
                    "Missing $MANIFEST_RESOURCE"
                }
            return Json
                .parseToJsonElement(manifest.readText())
                .jsonObject
                .getValue("files")
                .jsonArray
                .map { file -> file.jsonObject }
                .filter { file -> file.text("format") == EPUB_FORMAT && !file.isVersioned() }
                .map { file -> CorpusEpub(file.text("id"), file.text("path")) }
        }

        private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

        private fun JsonObject.isVersioned(): Boolean = getValue("versioned").jsonPrimitive.boolean
    }
}
