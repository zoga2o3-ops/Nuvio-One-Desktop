package com.nuvio.app.features.youtube

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Feeds YouTube's adaptive streams to the native player in 10 MB pieces.
 *
 * YouTube caps one long download of a DASH stream at about 0.5 MB/s (measured on 1080p
 * VISIONOS streams: 0.5–0.6 MB/s flat for 40 s), while separate ranged requests on the same URL
 * run at 45–108 MB/s. mpv fetches each stream as one long request, so it was fed at about the
 * video's own bitrate and ran dry on busier scenes: playback slowed, then stopped, while mpv still
 * reported playing. yt-dlp is fast for the same reason this is: it asks in pieces.
 *
 * mpv plays `http://127.0.0.1:<port>/s/<token>` instead. Every request mpv makes (including
 * seeks, as `Range: bytes=N-`) is answered here by fetching the upstream URL in sequential
 * [Chunk]-sized ranged requests and streaming them straight back, so nothing is stored.
 */
internal object YouTubeStreamProxy {
    private const val Chunk = 10L * 1_048_576
    private const val FirstChunk = 1L * 1_048_576
    private const val MaxStreams = 64

    private class Upstream(val url: String, val headers: Map<String, String>) {
        @Volatile var length: Long = -1
        @Volatile var mime: String? = null
    }

    private val streams = object : LinkedHashMap<String, Upstream>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Upstream>) = size > MaxStreams
    }
    private val http = OkHttpClient.Builder()
        // A stalled connection is retried after 8 seconds, not 30, so the first seconds of a video don't wait on one.
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile private var server: HttpServer? = null
    private val stats = ConcurrentHashMap<String, Long>()

    /** YouTube's media hosts; anything else (manifests, other sites) is played directly. */
    fun shouldProxy(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return false
        return host.endsWith(".googlevideo.com") && !url.contains(".m3u8") && !url.contains("/manifest/")
    }

    /** The local address mpv should play for [url]; the same upstream URL always maps to the same address. */
    fun wrap(url: String?, headers: Map<String, String>): String? {
        if (url == null || !shouldProxy(url)) return url
        val port = ensureServer() ?: return url
        val token = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        synchronized(streams) { if (token !in streams) streams[token] = Upstream(url, headers) }
        return "http://127.0.0.1:$port/s/$token"
    }

    /** [playback] with its stream URLs (video, audio, alternate audio tracks) going through the proxy. */
    fun wrapPlayback(playback: YouTubePlayback): YouTubePlayback = playback.copy(
        videoUrl = wrap(playback.videoUrl, playback.headers) ?: playback.videoUrl,
        audioUrl = wrap(playback.audioUrl, playback.headers),
        audioTracks = playback.audioTracks.map { it.copy(url = wrap(it.url, playback.headers) ?: it.url) },
    )

    /** Bytes served to the player so far (for the debug log). */
    fun servedMb(): String = "%.1f".format((stats["bytes"] ?: 0L) / 1_048_576.0)

    @Synchronized
    private fun ensureServer(): Int? {
        server?.let { return it.address.port }
        return runCatching {
            val created = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16)
            created.executor = Executors.newCachedThreadPool { r -> Thread(r, "yt-stream-proxy").apply { isDaemon = true } }
            created.createContext("/s/") { exchange -> runCatching { handle(exchange) }; runCatching { exchange.close() } }
            created.start()
            server = created
            created.address.port
        }.getOrNull()
    }

    private fun handle(exchange: HttpExchange) {
        val token = exchange.requestURI.path.removePrefix("/s/").substringBefore('/')
        val upstream = synchronized(streams) { streams[token] }
        if (upstream == null) {
            exchange.sendResponseHeaders(404, -1)
            return
        }
        val total = lengthOf(upstream)
        if (total <= 0L) {
            exchange.sendResponseHeaders(502, -1)
            return
        }
        val (start, end) = parseRange(exchange.requestHeaders.getFirst("Range"), total)
        if (start >= total || start > end) {
            exchange.responseHeaders.add("Content-Range", "bytes */$total")
            exchange.sendResponseHeaders(416, -1)
            return
        }
        val partial = exchange.requestHeaders.getFirst("Range") != null
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        exchange.responseHeaders.add("Content-Type", upstream.mime ?: "application/octet-stream")
        if (partial) exchange.responseHeaders.add("Content-Range", "bytes $start-$end/$total")
        val length = end - start + 1
        if (exchange.requestMethod.equals("HEAD", ignoreCase = true)) {
            exchange.responseHeaders.add("Content-Length", length.toString())
            exchange.sendResponseHeaders(if (partial) 206 else 200, -1)
            return
        }
        exchange.sendResponseHeaders(if (partial) 206 else 200, length)
        exchange.responseBody.use { out -> pump(upstream, start, end, out) }
    }

    /**
     * Copies [start]..[end] of the upstream stream to [out], one ranged request per [Chunk].
     *
     * Each piece is downloaded whole before it is handed over, and the next one is fetched while
     * the player reads the current one. The YouTube connection is therefore never left idle when
     * mpv stops reading (full buffer, pause), which would make it time out mid-stream.
     */
    private fun pump(upstream: Upstream, start: Long, end: Long, out: OutputStream) {
        // Every YouTube request this pump starts, so they can all be cancelled when the player
        // drops this one (a seek): left running, they would compete with the new position's download.
        val calls = java.util.Collections.synchronizedSet(HashSet<okhttp3.Call>())
        // Small first piece so sound and picture come back quickly after a seek, then growing.
        var pieceSize = FirstChunk
        fun fetchAsync(from: Long, size: Long) = fetcher.submit<ByteArray?> { fetch(upstream, from, minOf(from + size - 1, end), calls) }
        var pos = start
        val askedAt = System.currentTimeMillis()
        var next = fetchAsync(pos, pieceSize)
        try {
            while (pos <= end) {
                val bytes = runCatching { next.get() }.getOrNull() ?: return
                if (bytes.isEmpty()) return
                if (pos == start) {
                    OneTubeDebugLog.line("proxy ${upstream.mime?.substringBefore(';') ?: "?"} from=${start / 1024}KB first=${System.currentTimeMillis() - askedAt}ms")
                }
                val after = pos + bytes.size
                pieceSize = minOf(pieceSize * 2, Chunk)
                if (after <= end) next = fetchAsync(after, pieceSize)
                try {
                    out.write(bytes)
                    out.flush()
                } catch (closed: IOException) {
                    return // the player closed this request (seek, stop): nothing more to send
                }
                stats.merge("bytes", bytes.size.toLong(), Long::plus)
                pos = after
            }
        } finally {
            next.cancel(true)
            synchronized(calls) { calls.forEach { it.cancel() } }
        }
    }

    private val fetcher = Executors.newCachedThreadPool { r -> Thread(r, "yt-stream-fetch").apply { isDaemon = true } }

    /** One ranged request, read whole; retried twice on network errors. Null when it can't be had. */
    private fun fetch(upstream: Upstream, from: Long, to: Long, calls: MutableSet<okhttp3.Call>): ByteArray? {
        var attempt = 0
        while (true) {
            val request = Request.Builder().url(upstream.url).apply {
                upstream.headers.forEach { (k, v) -> header(k, v) }
                header("Range", "bytes=$from-$to")
            }.build()
            val call = http.newCall(request)
            calls += call
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw UpstreamError(response.code)
                    return response.body?.bytes() ?: ByteArray(0)
                }
            } catch (e: UpstreamError) {
                // 403/404/410: the URL expired or was refused; retrying won't help.
                if (e.code in 400..499 || ++attempt > 2) return null
            } catch (e: IOException) {
                if (call.isCanceled() || Thread.currentThread().isInterrupted || ++attempt > 2) return null
            } finally {
                calls -= call
            }
        }
    }

    private class UpstreamError(val code: Int) : IOException("upstream $code")

    /** Total size: the URL's `clen`, else asked once from YouTube. Also learns the content type. */
    private fun lengthOf(upstream: Upstream): Long {
        if (upstream.length > 0) return upstream.length
        val query = runCatching { java.net.URI(upstream.url).rawQuery }.getOrNull().orEmpty()
        val params = query.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to runCatching { URLDecoder.decode(it.substring(i + 1), "UTF-8") }.getOrDefault("")
        }.toMap()
        upstream.mime = params["mime"]?.takeIf { it.contains('/') }
        params["clen"]?.toLongOrNull()?.takeIf { it > 0 }?.let { upstream.length = it; return it }
        // No clen: a one-byte request tells us the size in Content-Range.
        runCatching {
            val request = Request.Builder().url(upstream.url).apply {
                upstream.headers.forEach { (k, v) -> header(k, v) }
                header("Range", "bytes=0-0")
            }.build()
            http.newCall(request).execute().use { response ->
                response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { upstream.length = it }
                if (upstream.mime == null) upstream.mime = response.header("Content-Type")
            }
        }
        return upstream.length
    }

    /** "bytes=a-b" / "bytes=a-" / "bytes=-n" (or none) against [total]. */
    internal fun parseRange(header: String?, total: Long): Pair<Long, Long> {
        val spec = header?.trim()?.removePrefix("bytes=")?.substringBefore(',')?.trim()
        if (spec.isNullOrEmpty() || !spec.contains('-')) return 0L to total - 1
        val a = spec.substringBefore('-').trim()
        val b = spec.substringAfter('-').trim()
        return when {
            a.isEmpty() -> {
                val n = b.toLongOrNull() ?: return 0L to total - 1
                maxOf(0L, total - n) to total - 1
            }
            else -> {
                val start = a.toLongOrNull() ?: 0L
                val end = b.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
                start to end
            }
        }
    }
}
