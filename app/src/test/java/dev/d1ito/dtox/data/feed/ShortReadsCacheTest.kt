package dev.d1ito.dtox.data.feed

import dev.d1ito.dtox.data.storage.AtomicFileWriter
import dev.d1ito.dtox.data.storage.AtomicWriteError
import dev.d1ito.dtox.data.storage.SyncedAtomicFileWriter
import dev.d1ito.dtox.domain.feed.ShortRead
import dev.d1ito.dtox.domain.feed.ShortReadCategory
import dev.d1ito.dtox.domain.feed.ShortReadFeed
import dev.d1ito.dtox.domain.feed.ShortReadSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

private const val SENECA = "seneca-cartas-lucilio-001"
private const val DARWIN = "darwin-origen-especies-001"
private const val EPICTETO = "epicteto-enquiridion-001"

private fun reading(
    identifier: String,
    sha256: String = "sha-$identifier",
): ShortRead =
    ShortRead(
        id = identifier,
        title = "Título de $identifier",
        author = null,
        category = ShortReadCategory.PHILOSOPHY,
        tags = listOf("estoicismo"),
        minutes = 5,
        words = 1000,
        license = "PD",
        source = ShortReadSource("Wikisource", "https://es.wikisource.org/"),
        translator = null,
        path = "items/$identifier.md",
        sha256 = sha256,
        publishedAt = LocalDate.of(2026, 10, 10),
    )

private fun feedOf(vararg items: ShortRead): ShortReadFeed =
    ShortReadFeed(Instant.parse("2026-10-10T04:00:00Z"), "es", items.toList())

private fun bodyOf(identifier: String): String = "# $identifier\n\nTexto de dominio público.\n"

/** Delegates to [delegate] until [failWrites] is set; then every write fails untouched. */
private class SwitchableFileWriter(
    private val delegate: AtomicFileWriter,
) : AtomicFileWriter {
    var failWrites = false

    override suspend fun write(
        target: File,
        writeContent: (OutputStream) -> Unit,
    ): Result<Unit> =
        if (failWrites) {
            Result.failure(AtomicWriteError.WriteFailed(target, IOException("simulated")))
        } else {
            delegate.write(target, writeContent)
        }
}

class ShortReadsCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory: File by lazy { File(temporaryFolder.root, "shortreads") }
    private val fileWriter by lazy {
        SwitchableFileWriter(SyncedAtomicFileWriter(Dispatchers.Unconfined))
    }
    private val cache by lazy { newCache() }

    private fun newCache(): ShortReadsCache =
        FileShortReadsCache(directory, fileWriter, Dispatchers.Unconfined)

    private fun bodyFile(identifier: String) = File(directory, "items/$identifier.md")

    private fun synchronize(
        feed: ShortReadFeed,
        vararg fetched: String,
    ) = runBlocking { cache.synchronize(feed, fetched.associateWith(::bodyOf)) }

    private fun unread(target: ShortReadsCache = cache) =
        runBlocking {
            target.unreadItems()
        }.getOrThrow()

    @Test
    fun emptyCache_hasNothingUnread_andFetchesTheWholeFeed() {
        val feed = feedOf(reading(SENECA), reading(DARWIN))

        assertEquals(emptyList<ShortRead>(), unread())
        assertNull(runBlocking { cache.readBody(SENECA) }.getOrThrow())
        assertEquals(feed.items, runBlocking { cache.itemsToFetch(feed) }.getOrThrow())
    }

    @Test
    fun synchronize_cachesFetchedBodies_inFeedOrder_andPersists() {
        val feed = feedOf(reading(SENECA), reading(DARWIN), reading(EPICTETO))

        synchronize(feed, EPICTETO, SENECA).getOrThrow()

        assertEquals(listOf(reading(SENECA), reading(EPICTETO)), unread(newCache()))
        assertEquals(bodyOf(SENECA), runBlocking { newCache().readBody(SENECA) }.getOrThrow())
        assertEquals(listOf(reading(DARWIN)), runBlocking { cache.itemsToFetch(feed) }.getOrThrow())
    }

    @Test
    fun markAsRead_dropsTheReading_andItNeverComesBack() {
        val feed = feedOf(reading(SENECA), reading(DARWIN))
        synchronize(feed, SENECA, DARWIN).getOrThrow()

        runBlocking { cache.markAsRead(SENECA) }.getOrThrow()
        synchronize(feed, SENECA).getOrThrow()

        assertEquals(listOf(reading(DARWIN)), unread(newCache()))
        assertFalse(bodyFile(SENECA).exists())
        assertNull(runBlocking { cache.readBody(SENECA) }.getOrThrow())
        assertEquals(emptyList<ShortRead>(), runBlocking { cache.itemsToFetch(feed) }.getOrThrow())
    }

    @Test
    fun markAsRead_acceptsUncachedIdentifiers_andIsIdempotent() {
        runBlocking { cache.markAsRead(SENECA) }.getOrThrow()
        runBlocking { cache.markAsRead(SENECA) }.getOrThrow()

        synchronize(feedOf(reading(SENECA)), SENECA).getOrThrow()

        assertEquals(emptyList<ShortRead>(), unread())
    }

    @Test
    fun synchronize_deletesReadingsWithdrawnFromTheFeed() {
        synchronize(feedOf(reading(SENECA), reading(DARWIN)), SENECA, DARWIN).getOrThrow()

        synchronize(feedOf(reading(DARWIN))).getOrThrow()

        assertEquals(listOf(reading(DARWIN)), unread())
        assertFalse(bodyFile(SENECA).exists())
        assertTrue(bodyFile(DARWIN).exists())
    }

    @Test
    fun readStateOfWithdrawnReadings_isKept_soTheyNeverRepeat() {
        synchronize(feedOf(reading(SENECA)), SENECA).getOrThrow()
        runBlocking { cache.markAsRead(SENECA) }.getOrThrow()
        synchronize(feedOf()).getOrThrow()

        val feedWithItBack = feedOf(reading(SENECA))
        synchronize(feedWithItBack, SENECA).getOrThrow()

        assertEquals(emptyList<ShortRead>(), unread())
        assertEquals(
            emptyList<ShortRead>(),
            runBlocking { cache.itemsToFetch(feedWithItBack) }.getOrThrow(),
        )
    }

    @Test
    fun aNewVersionOfACachedReading_isFetched_andReplacesTheOldOne() {
        synchronize(feedOf(reading(SENECA)), SENECA).getOrThrow()
        val corrected = feedOf(reading(SENECA, sha256 = "corrected"))
        assertEquals(corrected.items, runBlocking { cache.itemsToFetch(corrected) }.getOrThrow())

        runBlocking { cache.synchronize(corrected, mapOf(SENECA to "# Corregido\n")) }.getOrThrow()

        assertEquals(corrected.items, unread())
        assertEquals("# Corregido\n", runBlocking { cache.readBody(SENECA) }.getOrThrow())
    }

    @Test
    fun cachedReadingsWithoutANewBody_keepTheirCachedVersion() {
        synchronize(feedOf(reading(SENECA)), SENECA).getOrThrow()

        synchronize(feedOf(reading(SENECA, sha256 = "corrected"))).getOrThrow()

        assertEquals(listOf(reading(SENECA)), unread())
    }

    @Test
    fun bodiesOfUnknownOrUnsafeIdentifiers_areIgnored() {
        val unsafe = reading("../escape")
        val feed = feedOf(unsafe, reading(SENECA))

        runBlocking {
            cache.synchronize(feed, mapOf("../escape" to "x", DARWIN to "y", SENECA to "z"))
        }.getOrThrow()

        assertEquals(listOf(reading(SENECA)), unread())
        assertEquals(listOf("$SENECA.md"), File(directory, "items").list()?.toList())
        assertFalse(File(directory, "escape.md").exists())
        assertEquals(emptyList<ShortRead>(), runBlocking { cache.itemsToFetch(feed) }.getOrThrow())
    }

    @Test
    fun synchronize_deletesUnlistedBodyFiles() {
        val orphan = bodyFile(DARWIN).apply { parentFile?.mkdirs() }
        orphan.writeText("left over by a crash")

        synchronize(feedOf(reading(SENECA)), SENECA).getOrThrow()

        assertFalse(orphan.exists())
    }

    @Test
    fun aFailedWrite_leavesThePreviousState() {
        synchronize(feedOf(reading(SENECA)), SENECA).getOrThrow()
        fileWriter.failWrites = true

        val syncResult = synchronize(feedOf(reading(DARWIN)), DARWIN)
        val markResult = runBlocking { cache.markAsRead(SENECA) }

        assertTrue("Got $syncResult", syncResult.exceptionOrNull() is AtomicWriteError)
        assertTrue("Got $markResult", markResult.exceptionOrNull() is AtomicWriteError)
        assertEquals(listOf(reading(SENECA)), unread())
        assertEquals(bodyOf(SENECA), runBlocking { cache.readBody(SENECA) }.getOrThrow())
    }

    @Test
    fun corruptState_isSetAside_andTheCacheStartsEmpty() {
        val stateFile = File(directory.apply { mkdirs() }, "state.json")
        stateFile.writeText("{ not json")

        assertEquals(emptyList<ShortRead>(), unread())
        assertFalse(stateFile.exists())
        assertEquals("{ not json", File(directory, "state.json.corrupt").readText())
    }

    @Test
    fun stateFromANewerApp_failsEveryOperation_andIsLeftUntouched() {
        val stateFile = File(directory.apply { mkdirs() }, "state.json")
        val newerState = """{ "version": 2, "readings": {} }"""
        stateFile.writeText(newerState)

        val results =
            runBlocking {
                listOf(
                    cache.unreadItems(),
                    cache.readBody(SENECA),
                    cache.markAsRead(SENECA),
                    cache.itemsToFetch(feedOf(reading(SENECA))),
                    cache.synchronize(feedOf(reading(SENECA)), mapOf(SENECA to "z")),
                )
            }

        results.forEach { result ->
            assertTrue(
                "Got $result",
                result.exceptionOrNull() is ShortReadsCacheFormatError.UnsupportedVersion,
            )
        }
        assertEquals(newerState, stateFile.readText())
    }
}
