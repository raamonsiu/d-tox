package dev.d1ito.dtox.data.feed

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

private const val HTTPS_PROTOCOL = "https"
private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000
private const val DEFAULT_READ_TIMEOUT_MILLIS = 30_000
private const val MAX_REDIRECTS = 5
private const val READ_BUFFER_BYTES = 8 * 1024
private const val END_OF_STREAM = -1
private const val LOCATION_HEADER = "Location"
private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

/** Why a feed download failed. Returned inside [Result.failure], never thrown to callers. */
sealed class FeedDownloadError(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    /** The address, or a redirect, is not HTTPS: the feed is never fetched in cleartext. */
    class InsecureAddress(
        val address: String,
    ) : FeedDownloadError("Not an HTTPS address: $address")

    /** The server answered something other than 200 or a usable redirect. */
    class UnexpectedStatus(
        val statusCode: Int,
    ) : FeedDownloadError("Unexpected HTTP status $statusCode")

    /** More than the allowed redirects in a row, or a redirect without a valid `Location`. */
    class InvalidRedirect(
        val reason: String,
    ) : FeedDownloadError("Invalid redirect: $reason")

    /** The body is longer than the limit given for it; nothing is returned. */
    class TooLarge(
        val limitBytes: Int,
    ) : FeedDownloadError("Body larger than $limitBytes bytes")

    /** The body does not match the `sha256` the feed announces for it. */
    class DigestMismatch(
        val itemIdentifier: String,
    ) : FeedDownloadError("Digest mismatch for item '$itemIdentifier'")

    /** No connection, a timeout or a broken transfer. */
    class Network(
        cause: IOException,
    ) : FeedDownloadError("Network failure: ${cause.javaClass.simpleName}", cause)
}

/** Downloads feed files. The only network access of the app (RNF-02). */
fun interface FeedTransport {
    /**
     * @param address an HTTPS address; any other scheme fails without connecting.
     * @param maxBytes the largest body accepted.
     * @return the whole body, or a [FeedDownloadError].
     */
    suspend fun download(
        address: URL,
        maxBytes: Int,
    ): Result<ByteArray>
}

/**
 * [FeedTransport] over [HttpURLConnection] (no library, docs/03-architecture.md). Follows up to
 * [MAX_REDIRECTS] redirects by hand so none can downgrade to cleartext HTTP, and stops reading as
 * soon as the body passes its limit. Never logs addresses or contents.
 *
 * @param ioDispatcher where the blocking connection runs; never the main thread.
 * @param connectTimeoutMillis how long to wait for each connection.
 * @param readTimeoutMillis how long to wait for each read.
 * @param openConnection opens a connection to an already checked HTTPS address; tests replace it
 *   to reach a local fake server.
 */
class HttpUrlConnectionFeedTransport(
    private val ioDispatcher: CoroutineDispatcher,
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    private val openConnection: (URL) -> HttpURLConnection = ::openHttpConnection,
) : FeedTransport {
    override suspend fun download(
        address: URL,
        maxBytes: Int,
    ): Result<ByteArray> =
        withContext(ioDispatcher) {
            try {
                Result.success(followRedirects(address, maxBytes))
            } catch (failure: FeedDownloadError) {
                Result.failure(failure)
            } catch (networkFailure: IOException) {
                Result.failure(FeedDownloadError.Network(networkFailure))
            }
        }

    /**
     * @return the body of the first answer that is not a redirect.
     * @throws FeedDownloadError for an insecure address, a bad status, redirect or size.
     * @throws IOException if the connection fails.
     */
    private fun followRedirects(
        address: URL,
        maxBytes: Int,
    ): ByteArray {
        var currentAddress = address
        repeat(MAX_REDIRECTS + 1) {
            when (val answer = requestOnce(currentAddress, maxBytes)) {
                is Answer.Body -> return answer.bytes
                is Answer.Redirect -> currentAddress = answer.target
            }
        }
        throw FeedDownloadError.InvalidRedirect("more than $MAX_REDIRECTS redirects")
    }

    /**
     * Sends one GET to [address], checked to be HTTPS first.
     *
     * @throws FeedDownloadError for an insecure address, a bad status, redirect or size.
     * @throws IOException if the connection fails.
     */
    private fun requestOnce(
        address: URL,
        maxBytes: Int,
    ): Answer {
        requireHttps(address)
        val connection = openConnection(address)
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            return when (val statusCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    Answer.Body(connection.inputStream.use { readLimited(it, maxBytes) })
                in REDIRECT_STATUS_CODES -> Answer.Redirect(redirectTarget(address, connection))
                else -> throw FeedDownloadError.UnexpectedStatus(statusCode)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** @throws FeedDownloadError.InvalidRedirect when `Location` is missing or not an address. */
    private fun redirectTarget(
        currentAddress: URL,
        connection: HttpURLConnection,
    ): URL {
        val location =
            connection.getHeaderField(LOCATION_HEADER)
                ?: throw FeedDownloadError.InvalidRedirect("missing Location")
        return try {
            URL(currentAddress, location)
        } catch (invalidLocation: MalformedURLException) {
            throw FeedDownloadError.InvalidRedirect(invalidLocation.message.orEmpty())
        }
    }

    /**
     * @return every byte of [source].
     * @throws FeedDownloadError.TooLarge as soon as more than [maxBytes] arrive.
     */
    private fun readLimited(
        source: InputStream,
        maxBytes: Int,
    ): ByteArray {
        val body = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var bytesRead = source.read(buffer)
        while (bytesRead != END_OF_STREAM) {
            if (body.size() + bytesRead > maxBytes) throw FeedDownloadError.TooLarge(maxBytes)
            body.write(buffer, 0, bytesRead)
            bytesRead = source.read(buffer)
        }
        return body.toByteArray()
    }
}

/** One answer of the server: the body, or where it redirects to. */
private sealed interface Answer {
    class Body(
        val bytes: ByteArray,
    ) : Answer

    class Redirect(
        val target: URL,
    ) : Answer
}

/** @throws FeedDownloadError.InsecureAddress unless [address] is HTTPS. */
private fun requireHttps(address: URL) {
    if (!address.protocol.equals(HTTPS_PROTOCOL, ignoreCase = true)) {
        throw FeedDownloadError.InsecureAddress(address.toString())
    }
}

/** @throws IOException if [address] does not open an HTTP(S) connection. */
private fun openHttpConnection(address: URL): HttpURLConnection =
    address.openConnection() as? HttpURLConnection
        ?: throw IOException("Not an HTTP connection")
