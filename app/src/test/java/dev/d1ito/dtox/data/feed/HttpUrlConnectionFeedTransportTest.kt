package dev.d1ito.dtox.data.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL

private const val FEED_ROOT = "https://feed.test/d-tox/"
private const val GENEROUS_LIMIT = 1024
private const val SHORT_TIMEOUT_MILLIS = 200
private val BODY = "# Lectura\n\nTexto.\n".toByteArray(Charsets.UTF_8)

class HttpUrlConnectionFeedTransportTest {
    private var server = FakeFeedServer(emptyMap())

    @After
    fun stopServer() {
        server.close()
    }

    private fun serve(vararg routes: Pair<String, FakeResponse>): HttpUrlConnectionFeedTransport {
        server.close()
        server = FakeFeedServer(routes.toMap())
        return HttpUrlConnectionFeedTransport(
            Dispatchers.IO,
            openConnection = server::openConnection,
        )
    }

    private fun redirectTo(location: String) =
        FakeResponse(
            301,
            headers =
                mapOf(
                    "Location" to location,
                ),
        )

    private fun download(
        transport: FeedTransport,
        address: String,
        maxBytes: Int = GENEROUS_LIMIT,
    ): Result<ByteArray> = runBlocking { transport.download(URL(address), maxBytes) }

    @Test
    fun anHttpsAddress_returnsTheBody() {
        val transport = serve("/d-tox/index.json" to FakeResponse(200, BODY))

        val result = download(transport, FEED_ROOT + "index.json")

        assertArrayEquals(BODY, result.getOrThrow())
    }

    @Test
    fun aRedirectToHttps_isFollowed() {
        val transport =
            serve(
                "/d-tox/index.json" to redirectTo("https://mirror.test/feed/index.json"),
                "/feed/index.json" to FakeResponse(200, BODY),
            )

        val result = download(transport, FEED_ROOT + "index.json")

        assertArrayEquals(BODY, result.getOrThrow())
    }

    @Test
    fun aRedirectToCleartextHttp_isRefusedWithoutFollowingIt() {
        val cleartextAddress = URL("http", "127.0.0.1", "/feed/index.json").toString()
        val transport =
            serve(
                "/d-tox/index.json" to redirectTo(cleartextAddress),
                "/feed/index.json" to FakeResponse(200, BODY),
            )

        val result = download(transport, FEED_ROOT + "index.json")

        assertTrue(result.exceptionOrNull() is FeedDownloadError.InsecureAddress)
        assertEquals(listOf("/d-tox/index.json"), server.requestedPaths)
    }

    @Test
    fun aCleartextAddress_failsWithoutConnecting() {
        val transport = serve("/index.json" to FakeResponse(200, BODY))
        val cleartextAddress = URL("http", "127.0.0.1", "/index.json").toString()

        val result = download(transport, cleartextAddress)

        assertTrue(result.exceptionOrNull() is FeedDownloadError.InsecureAddress)
        assertTrue(server.requestedPaths.isEmpty())
    }

    @Test
    fun aRedirectLoop_stopsWithAnError() {
        val transport = serve("/d-tox/index.json" to redirectTo(FEED_ROOT + "index.json"))

        val result = download(transport, FEED_ROOT + "index.json")

        assertTrue(result.exceptionOrNull() is FeedDownloadError.InvalidRedirect)
    }

    @Test
    fun aRedirectWithoutLocation_fails() {
        val transport = serve("/d-tox/index.json" to FakeResponse(302))

        val result = download(transport, FEED_ROOT + "index.json")

        assertTrue(result.exceptionOrNull() is FeedDownloadError.InvalidRedirect)
    }

    @Test
    fun anErrorStatus_isReportedWithItsCode() {
        val transport = serve()

        val result = download(transport, FEED_ROOT + "index.json")

        val error = result.exceptionOrNull()
        assertTrue(error is FeedDownloadError.UnexpectedStatus)
        assertEquals(404, (error as FeedDownloadError.UnexpectedStatus).statusCode)
    }

    @Test
    fun aBodyOverTheLimit_isRefused() {
        val transport = serve("/d-tox/index.json" to FakeResponse(200, BODY))

        val result = download(transport, FEED_ROOT + "index.json", maxBytes = BODY.size - 1)

        assertTrue(result.exceptionOrNull() is FeedDownloadError.TooLarge)
    }

    @Test
    fun aBodyExactlyAtTheLimit_isAccepted() {
        val transport = serve("/d-tox/index.json" to FakeResponse(200, BODY))

        val result = download(transport, FEED_ROOT + "index.json", maxBytes = BODY.size)

        assertArrayEquals(BODY, result.getOrThrow())
    }

    @Test
    fun aServerThatNeverAnswers_timesOutAsANetworkError() {
        // Connections wait in the backlog of a socket that never accepts nor answers.
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { silentSocket ->
            val transport =
                HttpUrlConnectionFeedTransport(
                    ioDispatcher = Dispatchers.IO,
                    connectTimeoutMillis = SHORT_TIMEOUT_MILLIS,
                    readTimeoutMillis = SHORT_TIMEOUT_MILLIS,
                    openConnection = { address ->
                        URL("http", "127.0.0.1", silentSocket.localPort, address.file)
                            .openConnection() as HttpURLConnection
                    },
                )

            val result = download(transport, FEED_ROOT + "index.json")

            assertTrue(result.exceptionOrNull() is FeedDownloadError.Network)
        }
    }
}
