package com.nuvio.app.features.cast

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Makes a stream reachable by a TV on the home network when it can't fetch it itself: the stream
 * needs request headers, only exists on this PC (127.0.0.1), or is HTTPS and the TV is a DLNA set
 * (many can't do HTTPS). The TV asks `http://<this PC>:<port>/c/<token>/<name>` and this forwards
 * the request (with its Range) and streams the answer back.
 *
 * HLS playlists are passed through with their absolute addresses rewritten to come here too, and
 * relative ones resolve here by themselves (same path under the token).
 *
 * Only addresses registered with [wrap] are served, under random tokens; Windows may ask once
 * whether Nuvio One may accept connections on private networks.
 */
internal object CastRelay {
    private class Target(val url: String, val headers: Map<String, String>)

    private val targets = ConcurrentHashMap<String, Target>()
    private val random = SecureRandom()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    @Volatile private var server: HttpServer? = null

    /** Whether [media] has to go through the relay to play on [device]. */
    fun needed(device: CastDevice, media: CastMedia): Boolean {
        val host = runCatching { URI(media.url).host }.getOrNull().orEmpty()
        val loopback = host == "localhost" || host.startsWith("127.") || host == "::1" || host == "[::1]"
        return loopback || media.headers.isNotEmpty() || (device.kind == CastKind.DLNA && media.url.startsWith("https:", ignoreCase = true))
    }

    /** The address [device] should play for [url]. */
    fun wrap(url: String, headers: Map<String, String>, device: CastDevice): String {
        val port = ensureServer()
        val token = ByteArray(12).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        targets[token] = Target(url, headers)
        val name = runCatching { URI(url).path.substringAfterLast('/') }.getOrNull()?.takeIf { it.isNotBlank() } ?: "media"
        return "http://${localAddressFacing(device.host)}:$port/c/$token/$name"
    }

    fun clear() = targets.clear()

    /** This PC's address on the network that reaches [host] (the adapter the OS would use). */
    private fun localAddressFacing(host: String): String = runCatching {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName(host), 9)
            socket.localAddress.hostAddress
        }
    }.getOrNull()?.takeIf { it != "0.0.0.0" } ?: InetAddress.getLocalHost().hostAddress

    @Synchronized
    private fun ensureServer(): Int {
        server?.let { return it.address.port }
        val created = HttpServer.create(InetSocketAddress(0), 32)
        created.executor = Executors.newCachedThreadPool { r -> Thread(r, "cast-relay").apply { isDaemon = true } }
        created.createContext("/c/") { exchange -> runCatching { handle(exchange) }; runCatching { exchange.close() } }
        created.start()
        server = created
        return created.address.port
    }

    private fun handle(exchange: HttpExchange) {
        val rest = exchange.requestURI.rawPath.removePrefix("/c/")
        val token = rest.substringBefore('/')
        val target = targets[token] ?: run { exchange.sendResponseHeaders(404, -1); return }
        val subPath = rest.substringAfter('/', "")
        val baseName = runCatching { URI(target.url).path.substringAfterLast('/') }.getOrNull().orEmpty()
        val upstreamUrl = if (subPath.isEmpty() || subPath == baseName || subPath == "media") {
            target.url
        } else {
            URI(target.url).resolve(subPath + (exchange.requestURI.rawQuery?.let { "?$it" } ?: "")).toString()
        }
        val head = exchange.requestMethod.equals("HEAD", ignoreCase = true)
        val request = Request.Builder().url(upstreamUrl).apply {
            target.headers.forEach { (k, v) -> header(k, v) }
            exchange.requestHeaders.getFirst("Range")?.let { header("Range", it) }
            if (head) head()
        }.build()
        http.newCall(request).execute().use { response ->
            val type = response.header("Content-Type").orEmpty()
            val isPlaylist = type.contains("mpegurl", ignoreCase = true) || upstreamUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
            val out = exchange.responseHeaders
            out.add("Content-Type", type.ifBlank { CastFormats.contentTypeOf(upstreamUrl) })
            out.add("Accept-Ranges", response.header("Accept-Ranges") ?: "bytes")
            response.header("Content-Range")?.let { out.add("Content-Range", it) }
            // DLNA sets (Samsung, LG) look for these before they start playing.
            out.add("transferMode.dlna.org", "Streaming")
            out.add("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
            if (isPlaylist && !head) {
                val playlist = rewritePlaylist(response.body?.string().orEmpty(), target, exchange.requestHeaders.getFirst("Host"))
                val bytes = playlist.toByteArray()
                exchange.sendResponseHeaders(response.code, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                return
            }
            val length = response.header("Content-Length")?.toLongOrNull()
            if (head) {
                length?.let { out.add("Content-Length", it.toString()) }
                exchange.sendResponseHeaders(response.code, -1)
                return
            }
            exchange.sendResponseHeaders(response.code, length ?: 0)
            val body = response.body ?: return
            exchange.responseBody.use { sink -> runCatching { body.byteStream().copyTo(sink, 64 * 1024) } }
        }
    }

    /** Absolute addresses in an HLS playlist are sent through the relay too (same headers). */
    private fun rewritePlaylist(text: String, target: Target, hostHeader: String?): String {
        val host = hostHeader ?: return text
        fun relay(url: String): String {
            val token = ByteArray(12).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            targets[token] = Target(url, target.headers)
            val name = runCatching { URI(url).path.substringAfterLast('/') }.getOrNull()?.takeIf { it.isNotBlank() } ?: "media"
            return "http://$host/c/$token/$name"
        }
        return text.lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith("http://") || line.startsWith("https://") -> relay(line.trim())
                line.startsWith("#") && line.contains("URI=\"http") ->
                    Regex("URI=\"(https?://[^\"]+)\"").replace(line) { "URI=\"${relay(it.groupValues[1])}\"" }
                else -> line
            }
        }
    }
}
