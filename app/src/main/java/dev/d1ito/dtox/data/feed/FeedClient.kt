package dev.d1ito.dtox.data.feed

import dev.d1ito.dtox.data.storage.ContentPreferencesStore
import dev.d1ito.dtox.domain.feed.ShortRead
import dev.d1ito.dtox.domain.feed.ShortReadFeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.MalformedURLException
import java.net.URL
import java.security.MessageDigest

/**
 * Root of the published feed (D-10: GitHub Pages of this repository, docs/05-content-sources.md).
 * The only address the app ever contacts (RNF-02). Open point: confirm the path once the publish
 * workflow exists; it must stay HTTPS and end with `/`.
 */
const val FEED_BASE_URL = "https://raamonsiu.github.io/d-tox/"

private const val INDEX_FILE_NAME = "index.json"
private const val BYTES_PER_KIBIBYTE = 1024

/** Upper bound of `index.json`: thousands of items (D-36 accumulates them). */
private const val MAX_INDEX_BYTES = 2 * BYTES_PER_KIBIBYTE * BYTES_PER_KIBIBYTE

/** Upper bound of one item: a 20-minute reading is a few tens of KiB of Markdown. */
private const val MAX_ITEM_BYTES = 512 * BYTES_PER_KIBIBYTE
private const val SHA_256_ALGORITHM = "SHA-256"
private const val HEX_FORMAT = "%02x"

/**
 * Outcome of one feed synchronization.
 *
 * @property downloadedIdentifiers items downloaded, verified and cached, in feed order.
 * @property failedIdentifiers items that could not be downloaded or verified; they are not cached
 *   and are retried on the next synchronization.
 */
data class FeedSyncReport(
    val downloadedIdentifiers: List<String>,
    val failedIdentifiers: List<String>,
)

/**
 * Brings the short-reads cache up to date with the published feed (RF-31): downloads
 * `index.json`, then only the new readings that fit the user's content preferences (RF-32), and
 * caches each body after checking its `sha256` (docs/02-data-formats.md §6).
 *
 * @param transport the only network access; HTTPS only.
 * @param ioDispatcher where parsing and hashing run; never the main thread.
 * @param feedBaseUrl root of the feed, ending with `/`; [FEED_BASE_URL] in production.
 */
class FeedClient(
    private val transport: FeedTransport,
    private val cache: ShortReadsCache,
    private val preferencesStore: ContentPreferencesStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val feedBaseUrl: URL = URL(FEED_BASE_URL),
) {
    private val indexAddress = URL(feedBaseUrl, INDEX_FILE_NAME)

    /**
     * Downloads the feed and reconciles the cache with it ([ShortReadsCache.synchronize]). A
     * reading that fails to download or verify is skipped; the others are still cached.
     *
     * @return the report, or the first failure that stops the whole synchronization: a
     *   [FeedDownloadError] for the index, a [ShortReadFeedFormatError] for an unreadable index,
     *   or the cache error. The cache is left untouched when the index cannot be used.
     */
    suspend fun synchronize(): Result<FeedSyncReport> =
        withContext(ioDispatcher) {
            val feed = downloadFeed().getOrElse { return@withContext Result.failure(it) }
            val preferences = preferencesStore.preferences.first()
            val newItems =
                cache.itemsToFetch(feed).getOrElse { return@withContext Result.failure(it) }
            val bodies = linkedMapOf<String, String>()
            val failedIdentifiers = mutableListOf<String>()
            newItems.filter(preferences::accepts).forEach { item ->
                downloadItem(item)
                    .onSuccess { body -> bodies[item.id] = body }
                    .onFailure { failedIdentifiers += item.id }
            }
            cache
                .synchronize(feed, bodies)
                .map { FeedSyncReport(bodies.keys.toList(), failedIdentifiers) }
        }

    private suspend fun downloadFeed(): Result<ShortReadFeed> {
        val index =
            transport
                .download(indexAddress, MAX_INDEX_BYTES)
                .getOrElse { return Result.failure(it) }
        return ShortReadFeedFormat.decode(index.toString(Charsets.UTF_8))
    }

    /**
     * @return the Markdown body of [item], or a failure: [FeedDownloadError.DigestMismatch] when
     *   it does not match the announced `sha256`, the transport error, or a
     *   [MalformedURLException] for a path that is not an address.
     */
    private suspend fun downloadItem(item: ShortRead): Result<String> {
        val address = addressOf(item.path).getOrElse { return Result.failure(it) }
        val body =
            transport.download(address, MAX_ITEM_BYTES).getOrElse { return Result.failure(it) }
        return if (sha256HexOf(body).equals(item.sha256, ignoreCase = true)) {
            Result.success(body.toString(Charsets.UTF_8))
        } else {
            Result.failure(FeedDownloadError.DigestMismatch(item.id))
        }
    }

    /** @return [path] resolved against the feed root. */
    private fun addressOf(path: String): Result<URL> =
        try {
            Result.success(URL(feedBaseUrl, path))
        } catch (invalidPath: MalformedURLException) {
            Result.failure(invalidPath)
        }
}

/** @return the SHA-256 digest of [bytes] in lowercase hexadecimal. */
private fun sha256HexOf(bytes: ByteArray): String =
    MessageDigest.getInstance(SHA_256_ALGORITHM).digest(bytes).joinToString("") { byte ->
        HEX_FORMAT.format(byte)
    }
