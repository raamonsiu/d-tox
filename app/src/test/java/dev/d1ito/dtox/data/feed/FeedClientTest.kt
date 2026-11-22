package dev.d1ito.dtox.data.feed

import dev.d1ito.dtox.data.storage.ContentPreferencesStore
import dev.d1ito.dtox.data.storage.SyncedAtomicFileWriter
import dev.d1ito.dtox.domain.feed.ContentPreferences
import dev.d1ito.dtox.domain.feed.ShortReadCategory
import dev.d1ito.dtox.domain.feed.ShortReadDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URL
import java.security.MessageDigest

private const val FEED_ROOT = "https://feed.test/d-tox/"
private const val INDEX_PATH = "/d-tox/index.json"
private const val SENECA = "seneca-cartas-lucilio-001"
private const val DARWIN = "darwin-origen-especies-001"
private const val EPICTETO = "epicteto-enquiridion-001"

/** One item of the fake feed and the body the server returns for it. */
private class PublishedItem(
    val identifier: String,
    val category: String = "filosofia",
    val minutes: Int = 5,
    val body: String = "# $identifier\n\nTexto de dominio público.\n",
    val announcedSha256: String = sha256HexOf(body),
) {
    val serverPath = "/d-tox/items/$identifier.md"

    fun toJson(): String =
        """
        {"id": "$identifier", "title": "Título", "category": "$category",
         "minutes": $minutes, "words": 1000, "license": "PD",
         "source": {"name": "Wikisource", "url": "https://es.wikisource.org/"},
         "path": "items/$identifier.md", "sha256": "$announcedSha256",
         "publishedAt": "2026-10-10"}
        """.trimIndent()
}

private fun sha256HexOf(text: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun indexOf(vararg items: PublishedItem): String =
    """
    {"version": 1, "generatedAt": "2026-10-10T04:00:00Z", "language": "es",
     "items": [${items.joinToString(",") { it.toJson() }}]}
    """.trimIndent()

private class InMemoryContentPreferencesStore(
    initial: ContentPreferences,
) : ContentPreferencesStore {
    override val preferences: StateFlow<ContentPreferences> = MutableStateFlow(initial)

    override suspend fun update(preferences: ContentPreferences): Result<Unit> =
        Result.success(Unit)
}

class FeedClientTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val cache by lazy {
        FileShortReadsCache(
            temporaryFolder.newFolder("shortreads"),
            SyncedAtomicFileWriter(Dispatchers.IO),
            Dispatchers.IO,
        )
    }
    private var server = FakeFeedServer(emptyMap())

    @After
    fun stopServer() {
        server.close()
    }

    /** Publishes [items] (with [index] as `index.json`, by default theirs) on a new server. */
    private fun publish(
        vararg items: PublishedItem,
        index: String = indexOf(*items),
    ) {
        server.close()
        val itemRoutes =
            items.associate { item ->
                item.serverPath to FakeResponse(200, item.body.toByteArray(Charsets.UTF_8))
            }
        server =
            FakeFeedServer(
                itemRoutes + (INDEX_PATH to FakeResponse(200, index.toByteArray(Charsets.UTF_8))),
            )
    }

    private fun synchronize(
        preferences: ContentPreferences = ContentPreferences.Default,
    ): Result<FeedSyncReport> {
        val client =
            FeedClient(
                transport =
                    HttpUrlConnectionFeedTransport(
                        Dispatchers.IO,
                        openConnection = server::openConnection,
                    ),
                cache = cache,
                preferencesStore = InMemoryContentPreferencesStore(preferences),
                ioDispatcher = Dispatchers.IO,
                feedBaseUrl = URL(FEED_ROOT),
            )
        return runBlocking { client.synchronize() }
    }

    private fun cachedIdentifiers(): List<String> =
        runBlocking { cache.unreadItems().getOrThrow().map { it.id } }

    @Test
    fun newItems_areDownloadedVerifiedAndCached() {
        val seneca = PublishedItem(SENECA)
        val darwin = PublishedItem(DARWIN, category = "ciencia", minutes = 10)
        publish(seneca, darwin)

        val report = synchronize().getOrThrow()

        assertEquals(listOf(SENECA, DARWIN), report.downloadedIdentifiers)
        assertEquals(emptyList<String>(), report.failedIdentifiers)
        assertEquals(listOf(SENECA, DARWIN), cachedIdentifiers())
        assertEquals(seneca.body, runBlocking { cache.readBody(SENECA).getOrThrow() })
    }

    @Test
    fun onlyItemsThatFitThePreferences_areDownloaded() {
        publish(
            PublishedItem(SENECA),
            PublishedItem(DARWIN, category = "ciencia"),
            PublishedItem(EPICTETO, minutes = 20),
        )
        val philosophyInFiveMinutes =
            ContentPreferences
                .create(setOf(ShortReadCategory.PHILOSOPHY), setOf(ShortReadDuration.SHORT))
                .getOrThrow()

        val report = synchronize(philosophyInFiveMinutes).getOrThrow()

        assertEquals(listOf(SENECA), report.downloadedIdentifiers)
        assertEquals(listOf(INDEX_PATH, "/d-tox/items/$SENECA.md"), server.requestedPaths)
    }

    @Test
    fun aBodyThatDoesNotMatchItsDigest_isNotCached() {
        publish(
            PublishedItem(SENECA, announcedSha256 = sha256HexOf("otro texto")),
            PublishedItem(DARWIN),
        )

        val report = synchronize().getOrThrow()

        assertEquals(listOf(SENECA), report.failedIdentifiers)
        assertEquals(listOf(DARWIN), cachedIdentifiers())
    }

    @Test
    fun cachedItems_areNotDownloadedAgain() {
        val seneca = PublishedItem(SENECA)
        publish(seneca)
        synchronize().getOrThrow()
        publish(seneca, PublishedItem(DARWIN))

        val report = synchronize().getOrThrow()

        assertEquals(listOf(DARWIN), report.downloadedIdentifiers)
        assertEquals(listOf(INDEX_PATH, "/d-tox/items/$DARWIN.md"), server.requestedPaths)
    }

    @Test
    fun aMissingIndex_failsAndLeavesTheCacheUntouched() {
        publish(PublishedItem(SENECA))
        synchronize().getOrThrow()
        server.close()
        server = FakeFeedServer(emptyMap())

        val result = synchronize()

        assertTrue(result.exceptionOrNull() is FeedDownloadError.UnexpectedStatus)
        assertEquals(listOf(SENECA), cachedIdentifiers())
    }

    @Test
    fun anUnreadableIndex_fails() {
        publish(index = "{\"version\": 1")

        val result = synchronize()

        assertTrue(result.exceptionOrNull() is ShortReadFeedFormatError.Corrupt)
    }
}
