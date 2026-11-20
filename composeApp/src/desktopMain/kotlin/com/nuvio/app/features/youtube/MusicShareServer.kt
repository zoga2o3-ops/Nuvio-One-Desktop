package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class MusicShareState(
    val running: Boolean = false,
    val url: String? = null,
    val error: String? = null,
)

/**
 * A small LAN page for the local music library: browse/search the scanned tracks, play them in the
 * browser, download one file, or download the whole library as a ZIP. Useful for copying music off
 * the desktop or listening from a phone on the same network.
 */
internal object MusicShareServer {
    var state by mutableStateOf(MusicShareState())
        private set

    private var server: HttpServer? = null
    private var token: String? = null

    fun start() {
        stop()
        runCatching {
            MusicLocalRepository.ensureLoaded()
            val newToken = UUID.randomUUID().toString().replace("-", "").take(10)
            val created = HttpServer.create(InetSocketAddress(0), 0)
            created.executor = Executors.newFixedThreadPool(3) { runnable ->
                Thread(runnable, "nuvio-music-share").apply { isDaemon = true }
            }
            created.createContext("/m/$newToken") { exchange -> handle(exchange, newToken) }
            created.start()
            server = created
            token = newToken
            val url = "http://${localIpv4()}:${created.address.port}/m/$newToken/"
            state = MusicShareState(running = true, url = url)
        }.onFailure {
            state = MusicShareState(error = it.message ?: "Could not start the share server.")
        }
    }

    fun stop() {
        runCatching { server?.stop(0) }
        server = null
        token = null
        state = MusicShareState()
    }

    private fun handle(exchange: HttpExchange, expectedToken: String) {
        try {
            if (token != expectedToken) {
                respond(exchange, 404, "text/plain", "Not found")
                return
            }
            val path = exchange.requestURI.path.removePrefix("/m/$expectedToken").trim('/')
            val tracks = MusicLocalRepository.tracks
            when {
                exchange.requestMethod != "GET" -> respond(exchange, 405, "text/plain", "Method not allowed")
                path.isEmpty() -> respond(exchange, 200, "text/html; charset=utf-8", page(tracks))
                path == "zip" -> zipResponse(exchange, tracks)
                path.startsWith("file/") -> fileResponse(exchange, tracks, path.substringAfter("file/"))
                else -> respond(exchange, 404, "text/plain", "Not found")
            }
        } catch (error: Exception) {
            runCatching {
                respond(exchange, 500, "text/plain", error.message ?: "Internal error")
            }
        } finally {
            exchange.close()
        }
    }

    private fun fileResponse(exchange: HttpExchange, tracks: List<LocalMusicTrack>, indexText: String) {
        val track = tracks.getOrNull(indexText.toIntOrNull() ?: -1)
        if (track == null) {
            respond(exchange, 404, "text/plain", "Not found")
            return
        }
        val file = Path.of(track.path)
        if (!Files.isRegularFile(file)) {
            respond(exchange, 404, "text/plain", "This file is no longer on disk.")
            return
        }
        val length = Files.size(file)
        val range = parseRange(exchange.requestHeaders.getFirst("Range"), length)
        exchange.responseHeaders.add("Content-Type", contentType(file))
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        exchange.responseHeaders.add("Content-Disposition", "inline; filename=\"${downloadName(track, file)}\"")
        if (range == null) {
            exchange.sendResponseHeaders(200, length)
            Files.newInputStream(file).use { input -> exchange.responseBody.use { output -> input.copyTo(output) } }
            return
        }
        val (start, end) = range
        val size = end - start + 1
        exchange.responseHeaders.add("Content-Range", "bytes $start-$end/$length")
        exchange.sendResponseHeaders(206, size)
        Files.newInputStream(file).use { input ->
            input.skipNBytes(start)
            exchange.responseBody.use { output ->
                var remaining = size
                val buffer = ByteArray(256 * 1024)
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }

    private fun zipResponse(exchange: HttpExchange, tracks: List<LocalMusicTrack>) {
        exchange.responseHeaders.add("Content-Type", "application/zip")
        exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"Nuvio Music.zip\"")
        exchange.sendResponseHeaders(200, 0)
        ZipOutputStream(exchange.responseBody.buffered()).use { zip ->
            tracks.forEachIndexed { index, track ->
                val file = runCatching { Path.of(track.path) }.getOrNull() ?: return@forEachIndexed
                if (!Files.isRegularFile(file)) return@forEachIndexed
                val name = downloadName(track, file)
                zip.putNextEntry(ZipEntry(String.format("%03d - %s", index + 1, name)))
                BufferedInputStream(Files.newInputStream(file)).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun page(tracks: List<LocalMusicTrack>): String {
        val json = buildJsonArray {
            tracks.forEachIndexed { index, track ->
                add(
                    buildJsonObject {
                        put("i", index)
                        put("t", track.title)
                        put("a", track.artist)
                        put("al", track.album)
                        put("d", track.durationMs)
                    },
                )
            }
        }
        return """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>Nuvio Music share</title>
<style>
  :root { color-scheme: dark; --accent:#9aa8bd; --panel:#1e1e22; --raised:#29292f; }
  * { box-sizing: border-box; }
  body { margin:0; background:#202024; color:#fff; font-family:system-ui,-apple-system,"Segoe UI",sans-serif; padding:16px 14px 92px; }
  h1 { font-size:19px; margin:0 0 2px; }
  .sub { color:#b7c0cc; font-size:12px; margin-bottom:14px; }
  .bar { display:flex; gap:8px; align-items:center; margin-bottom:12px; }
  input { flex:1; min-width:0; background:var(--panel); color:#fff; border:1px solid #33333b; border-radius:10px; padding:11px 12px; font-size:14px; outline:none; }
  a.zip { background:var(--accent); color:#14161a; text-decoration:none; font-weight:700; font-size:12.5px; border-radius:10px; padding:11px 12px; white-space:nowrap; }
  ul { list-style:none; margin:0; padding:0; }
  li { display:flex; align-items:center; gap:10px; padding:9px 6px; border-bottom:1px solid #2b2b32; }
  li:last-child { border-bottom:0; }
  .n { width:26px; color:#8b95a4; font-size:11px; flex:none; text-align:right; }
  .grow { flex:1; min-width:0; }
  .title { font-size:13px; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .meta { font-size:11px; color:#aeb8c5; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; margin-top:1px; }
  button.play { background:var(--raised); border:1px solid #3d3d46; color:#fff; border-radius:9px; width:36px; height:36px; font-size:15px; flex:none; }
  a.dl { color:var(--accent); text-decoration:none; font-size:18px; width:30px; text-align:center; flex:none; }
  .empty { color:#8b95a4; font-size:13px; padding:18px 4px; }
  #player { position:fixed; left:0; right:0; bottom:0; background:#151518; border-top:1px solid #33333b; padding:10px 14px; display:none; }
  #player.on { display:block; }
  #now { font-size:12px; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; margin-bottom:6px; }
  audio { width:100%; height:34px; }
</style>
</head>
<body>
<h1>Nuvio Music</h1>
<div class="sub" id="count">${tracks.size} tracks on this computer</div>
<div class="bar">
  <input id="q" placeholder="Search your library" autocomplete="off">
  <a class="zip" href="zip">Download all</a>
</div>
<ul id="list"></ul>
<div id="player"><div id="now"></div><audio id="audio" controls preload="none"></audio></div>
<script>
var TRACKS = $json;
var list = document.getElementById('list');
var audio = document.getElementById('audio');
var player = document.getElementById('player');
var now = document.getElementById('now');
function ms(value) {
  if (!value) return '';
  var total = Math.floor(value / 1000);
  return Math.floor(total / 60) + ':' + String(total % 60).padStart(2, '0');
}
function esc(value) {
  return String(value == null ? '' : value).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
function play(track) {
  audio.src = 'file/' + track.i;
  now.textContent = track.t + (track.a ? ' · ' + track.a : '');
  player.classList.add('on');
  audio.play();
}
function render() {
  var q = document.getElementById('q').value.trim().toLowerCase();
  var visible = TRACKS.filter(function (t) {
    return !q || (t.t + ' ' + t.a + ' ' + t.al).toLowerCase().indexOf(q) >= 0;
  });
  list.innerHTML = visible.map(function (t) {
    return '<li><div class="n">' + (t.i + 1) + '</div><button class="play" data-i="' + t.i + '">&#9654;</button>' +
      '<div class="grow"><div class="title">' + esc(t.t) + '</div><div class="meta">' + esc(t.a || '') +
      (t.al ? ' · ' + esc(t.al) : '') + '</div></div><div class="meta">' + ms(t.d) + '</div>' +
      '<a class="dl" href="file/' + t.i + '" download title="Download">&#8681;</a></li>';
  }).join('');
  if (!visible.length) list.innerHTML = '<li class="empty">No tracks match that search.</li>';
}
list.addEventListener('click', function (event) {
  var button = event.target.closest('.play');
  if (button) {
    var track = TRACKS.find(function (t) { return t.i === Number(button.dataset.i); });
    if (track) play(track);
  }
});
document.getElementById('q').addEventListener('input', render);
render();
</script>
</body>
</html>
""".trimIndent()
    }

    private fun respond(exchange: HttpExchange, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun parseRange(header: String?, length: Long): Pair<Long, Long>? {
        if (header == null || !header.startsWith("bytes=")) return null
        val spec = header.removePrefix("bytes=").substringBefore(',')
        val startText = spec.substringBefore('-').trim()
        val endText = spec.substringAfter('-', "").trim()
        val start = startText.toLongOrNull() ?: 0L
        if (start >= length) return null
        val end = endText.toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1)
        if (end < start) return null
        return start to end
    }

    private fun contentType(file: Path): String = when (file.fileName.toString().substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "m4b", "aac" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "webm" -> "audio/webm"
        else -> "application/octet-stream"
    }

    private fun downloadName(track: LocalMusicTrack, file: Path): String {
        val extension = file.fileName.toString().substringAfterLast('.', "")
        val base = listOf(track.artist, track.title)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { file.fileName.toString().substringBeforeLast('.') }
        return if (extension.isBlank()) base else "$base.$extension"
    }

    private fun localIpv4(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull() ?: "127.0.0.1"
}
