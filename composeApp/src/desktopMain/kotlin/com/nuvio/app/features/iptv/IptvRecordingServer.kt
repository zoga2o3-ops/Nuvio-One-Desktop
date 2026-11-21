package com.nuvio.app.features.iptv

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Hands SMB recordings to the player as ordinary web links, so mpv can stream, seek and read
 * the HLS playlists without knowing anything about SMB. Only this computer can reach it.
 */
internal object IptvRecordingServer {
    private data class Source(
        val config: IptvSmbConfig,
        val shareDir: String,
    )

    @Volatile private var server: ServerSocket? = null
    private val sources = ConcurrentHashMap<String, Source>()

    /** The link for a recording's playlist (starts the server if needed). */
    fun urlFor(id: String, config: IptvSmbConfig, shareDir: String): String {
        sources[id] = Source(config, shareDir.trim('/'))
        val port = ensure()
        return "http://127.0.0.1:$port/$id/index.m3u8"
    }

    @Synchronized
    private fun ensure(): Int {
        server?.takeIf { !it.isClosed }?.let { return it.localPort }
        val s = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        server = s
        thread(name = "recording-server", isDaemon = true) {
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                thread(name = "recording-client", isDaemon = true) {
                    runCatching { serve(client) }
                }
            }
        }
        return s.localPort
    }

    private fun serve(client: Socket) = client.use { sock ->
        sock.soTimeout = 30_000
        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
        val requestLine = reader.readLine() ?: return@use
        var rangeStart = -1L
        var rangeEnd = -1L
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) {
                Regex("""bytes=(\d*)-(\d*)""").find(line)?.let { m ->
                    rangeStart = m.groupValues[1].toLongOrNull() ?: -1L
                    rangeEnd = m.groupValues[2].toLongOrNull() ?: -1L
                }
            }
        }
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0) ?: "GET"
        val path = URLDecoder.decode(parts.getOrNull(1)?.substringBefore('?') ?: "/", "UTF-8").trimStart('/')
        val source = sources[path.substringBefore('/')] ?: return@use
        val relative = path.substringAfter('/', "")
        if (relative.contains("..")) {
            notFound(sock)
            return@use
        }
        val remote = "${source.shareDir}/$relative"
        val out = sock.getOutputStream()
        val extension = relative.substringAfterLast('.', "").lowercase()
        val type = when (extension) {
            "m3u8" -> "application/vnd.apple.mpegurl"
            "ts" -> "video/mp2t"
            "m4s", "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }

        if (type.contains("mpegurl")) {
            val text = IptvSmbClient.readText(source.config, remote).orEmpty()
            if (text.isNotEmpty()) {
                val body = if (!text.contains("#EXT-X-ENDLIST") && !text.contains("#EXT-X-START")) {
                    text.replaceFirst("#EXTM3U", "#EXTM3U\n#EXT-X-START:TIME-OFFSET=0,PRECISE=YES")
                } else {
                    text
                }.toByteArray()
                out.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                        "Cache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray()
                )
                if (!method.equals("HEAD", ignoreCase = true)) out.write(body)
                out.flush()
                return@use
            }
        }

        val length = IptvSmbClient.length(source.config, remote)
        if (length < 0) {
            notFound(sock)
            return@use
        }
        val start = if (rangeStart >= 0) rangeStart.coerceAtMost(length) else 0L
        val end = if (rangeEnd >= 0) rangeEnd.coerceAtMost(length - 1) else length - 1
        val count = (end - start + 1).coerceAtLeast(0)
        val head = StringBuilder()
        head.append(if (rangeStart >= 0) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        head.append("Content-Type: ").append(type).append("\r\n")
        head.append("Content-Length: ").append(count).append("\r\n")
        head.append("Accept-Ranges: bytes\r\n")
        if (rangeStart >= 0) {
            head.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(length).append("\r\n")
        }
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray())
        if (method.equals("HEAD", ignoreCase = true)) return@use
        val file = IptvSmbClient.randomAccess(source.config, remote) ?: return@use
        file.use {
            it.seek(start)
            val buf = ByteArray(64 * 1024)
            var left = count
            while (left > 0) {
                val n = it.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                out.write(buf, 0, n)
                left -= n
            }
        }
        out.flush()
    }

    private fun notFound(sock: Socket) {
        runCatching {
            sock.getOutputStream().write(
                "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
            )
        }
    }
}
