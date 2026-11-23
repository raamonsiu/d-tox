package dev.d1ito.dtox.data.feed

import java.io.Closeable
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

private const val CONNECTION_BACKLOG = 8
private const val NOT_FOUND = 404

/**
 * One canned answer of [FakeFeedServer].
 *
 * @property statusCode e.g. 200 or 301.
 * @property headers extra response headers, e.g. `Location`.
 */
class FakeResponse(
    val statusCode: Int,
    val body: ByteArray = ByteArray(0),
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Minimal HTTP/1.1 server on the loopback interface for tests: answers each request path from
 * [routes] (404 otherwise), one request per connection, and records the paths requested.
 */
class FakeFeedServer(
    private val routes: Map<String, FakeResponse>,
) : Closeable {
    private val serverSocket = ServerSocket(0, CONNECTION_BACKLOG, InetAddress.getLoopbackAddress())

    /** Request paths in arrival order, e.g. `/d-tox/index.json`. */
    val requestedPaths: MutableList<String> = CopyOnWriteArrayList()

    init {
        thread(isDaemon = true) { serve() }
    }

    /**
     * Opens connections as the transport would, but to this server over plain HTTP: the HTTPS
     * check has already run on the logical address, whose path is kept.
     */
    fun openConnection(address: URL): HttpURLConnection =
        URL("http", serverSocket.inetAddress.hostAddress, serverSocket.localPort, address.file)
            .openConnection() as HttpURLConnection

    override fun close() {
        serverSocket.close()
    }

    private fun serve() {
        while (true) {
            val socket =
                try {
                    serverSocket.accept()
                } catch (expectedOnClose: SocketException) {
                    return
                }
            socket.use(::answer)
        }
    }

    private fun answer(socket: Socket) {
        val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val requestLine = reader.readLine() ?: return
        do {
            val headerLine = reader.readLine() // Skipped: no request header is needed.
        } while (!headerLine.isNullOrEmpty())
        val path = requestLine.split(" ")[1]
        requestedPaths += path
        val response = routes[path] ?: FakeResponse(NOT_FOUND)
        val head =
            buildString {
                append("HTTP/1.1 ${response.statusCode} Fake\r\n")
                response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("Content-Length: ${response.body.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.ISO_8859_1))
            write(response.body)
            flush()
        }
    }
}
