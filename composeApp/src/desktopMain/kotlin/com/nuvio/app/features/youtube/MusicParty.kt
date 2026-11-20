package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class PartyPendingRequest(
    val id: String,
    val item: YouTubeVideoItem,
    val guestName: String,
)

internal data class MusicPartyState(
    val running: Boolean = false,
    val url: String? = null,
    val locked: Boolean = false,
    val additions: Int = 0,
    val requireApproval: Boolean = false,
    val pending: List<PartyPendingRequest> = emptyList(),
    val error: String? = null,
)

/**
 * A phone-friendly party queue. The desktop app hosts a tiny HTTP server; guests open the printed
 * URL, search YouTube Music and add songs to the shared queue. The host can lock the party or
 * require approval before a request joins the queue.
 */
internal object MusicParty {
    private const val MaxGuestQueue = 100
    private const val MaxSearchCache = 250
    private val json = Json { ignoreUnknownKeys = true }

    var state by mutableStateOf(MusicPartyState())
        private set

    private var server: HttpServer? = null
    private var token: String? = null
    private val addedBy = Collections.synchronizedMap(LinkedHashMap<String, String>())
    private val searchCache = Collections.synchronizedMap(LinkedHashMap<String, YouTubeVideoItem>())

    fun start() {
        stop()
        runCatching {
            val newToken = UUID.randomUUID().toString().replace("-", "").take(10)
            val created = HttpServer.create(InetSocketAddress(0), 0)
            created.executor = Executors.newFixedThreadPool(3) { runnable ->
                Thread(runnable, "nuvio-music-party").apply { isDaemon = true }
            }
            created.createContext("/party/$newToken") { exchange -> handle(exchange, newToken) }
            created.start()
            server = created
            token = newToken
            val url = "http://${localIpv4()}:${created.address.port}/party/$newToken/"
            state = MusicPartyState(running = true, url = url)
        }.onFailure {
            state = MusicPartyState(error = it.message ?: "Could not start the party server.")
        }
    }

    fun stop() {
        runCatching { server?.stop(0) }
        server = null
        token = null
        addedBy.clear()
        searchCache.clear()
        state = MusicPartyState()
    }

    fun setLocked(locked: Boolean) {
        state = state.copy(locked = locked)
    }

    fun setRequireApproval(required: Boolean) {
        state = state.copy(requireApproval = required)
    }

    fun approve(requestId: String) {
        val request = state.pending.firstOrNull { it.id == requestId } ?: return
        state = state.copy(pending = state.pending.filterNot { it.id == requestId })
        recordAddition(request.item, request.guestName)
        SwingUtilities.invokeLater { MusicPlaybackQueue.append(listOf(request.item)) }
    }

    fun reject(requestId: String) {
        state = state.copy(pending = state.pending.filterNot { it.id == requestId })
    }

    fun addedBy(videoId: String): String? = addedBy[videoId]

    private fun recordAddition(item: YouTubeVideoItem, guestName: String) {
        addedBy[item.videoId] = guestName
        state = state.copy(additions = addedBy.size)
    }

    private fun handle(exchange: HttpExchange, expectedToken: String) {
        try {
            if (token != expectedToken) {
                respond(exchange, 404, "text/plain", "Not found")
                return
            }
            val path = exchange.requestURI.path.removePrefix("/party/$expectedToken").trim('/')
            when {
                exchange.requestMethod == "GET" && path.isEmpty() -> {
                    respond(exchange, 200, "text/html; charset=utf-8", page())
                }
                exchange.requestMethod == "GET" && path == "state" -> {
                    respond(exchange, 200, "application/json", stateJson().toString())
                }
                exchange.requestMethod == "GET" && path == "search" -> {
                    handleSearch(exchange)
                }
                exchange.requestMethod == "POST" && path == "add" -> {
                    handleAdd(exchange)
                }
                else -> respond(exchange, 404, "text/plain", "Not found")
            }
        } catch (error: Exception) {
            runCatching {
                respond(exchange, 500, "application/json", """{"error":"${(error.message ?: "Internal error").escapeJson()}"}""")
            }
        } finally {
            exchange.close()
        }
    }

    private fun handleSearch(exchange: HttpExchange) {
        val query = exchange.requestURI.query.orEmpty()
            .split('&')
            .firstOrNull { it.startsWith("q=") }
            ?.substringAfter("q=")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            .orEmpty()
            .trim()
        if (query.isBlank()) {
            respond(exchange, 200, "application/json", "[]")
            return
        }
        val results = kotlinx.coroutines.runBlocking {
            runCatching { YouTubeEngine.search(query, com.liskovsoft.mediaserviceinterfaces.data.SearchOptions.TYPE_VIDEO) }
                .getOrDefault(emptyList())
                .filter { it.kind == YouTubeItemKind.VIDEO && it.durationMs in 20_000..900_000L }
                .take(10)
                .map { it.copy(isMusic = true) }
        }
        results.forEach { item ->
            searchCache[item.videoId] = item
        }
        synchronized(searchCache) {
            while (searchCache.size > MaxSearchCache) {
                val oldest = searchCache.keys.firstOrNull() ?: break
                searchCache.remove(oldest)
            }
        }
        val body = buildJsonArray {
            results.forEach { item ->
                add(
                    buildJsonObject {
                        put("videoId", item.videoId)
                        put("title", item.title)
                        put("author", item.author.orEmpty())
                        put("thumbnailUrl", item.thumbnailUrl.orEmpty())
                        put("durationMs", item.durationMs)
                    },
                )
            }
        }
        respond(exchange, 200, "application/json", body.toString())
    }

    private fun handleAdd(exchange: HttpExchange) {
        if (state.locked) {
            respond(exchange, 403, "application/json", """{"error":"The party is locked."}""")
            return
        }
        val body = exchange.requestBody.use { it.readNBytes(64 * 1024) }.toString(Charsets.UTF_8)
        val payload = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val videoId = payload?.get("videoId")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val guest = payload?.get("guest")?.jsonPrimitive?.contentOrNull
            ?.trim()?.take(32)?.ifBlank { null }
            ?: "A guest"
        val item = searchCache[videoId]
        if (item == null) {
            respond(exchange, 400, "application/json", """{"error":"Search for the song first."}""")
            return
        }
        if (addedBy.size >= MaxGuestQueue) {
            respond(exchange, 429, "application/json", """{"error":"The queue is full."}""")
            return
        }
        if (state.requireApproval) {
            val request = PartyPendingRequest(UUID.randomUUID().toString().take(8), item, guest)
            state = state.copy(pending = state.pending + request)
            respond(exchange, 200, "application/json", """{"status":"pending"}""")
            return
        }
        recordAddition(item, guest)
        SwingUtilities.invokeLater { MusicPlaybackQueue.append(listOf(item)) }
        respond(exchange, 200, "application/json", """{"status":"added"}""")
    }

    private fun stateJson(): JsonObject = buildJsonObject {
        put("running", state.running)
        put("locked", state.locked)
        put("requireApproval", state.requireApproval)
        put("additions", state.additions)
        val current = MusicPlaybackQueue.current
        if (current == null) {
            put("nowPlaying", kotlinx.serialization.json.JsonNull)
        } else {
            put("nowPlaying", trackJson(current, addedBy[current.videoId]))
        }
        put(
            "queue",
            buildJsonArray {
                MusicPlaybackQueue.items.forEach { item ->
                    add(trackJson(item, addedBy[item.videoId]))
                }
            },
        )
        put(
            "pending",
            buildJsonArray {
                state.pending.forEach { request ->
                    add(
                        buildJsonObject {
                            put("id", request.id)
                            put("title", request.item.title)
                            put("author", request.item.author.orEmpty())
                            put("guest", request.guestName)
                        },
                    )
                }
            },
        )
    }

    private fun trackJson(item: YouTubeVideoItem, guest: String?): JsonObject = buildJsonObject {
        put("videoId", item.videoId)
        put("title", item.title)
        put("author", item.author.orEmpty())
        put("thumbnailUrl", item.thumbnailUrl.orEmpty())
        put("durationMs", item.durationMs)
        if (guest != null) put("addedBy", guest)
    }

    private fun respond(exchange: HttpExchange, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun localIpv4(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull() ?: "127.0.0.1"

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    private fun page(): String = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>Nuvio Music Party</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  body { margin: 0; background: #0b0b0d; color: #fff; font-family: system-ui, -apple-system, "Segoe UI", sans-serif; }
  header { position: sticky; top: 0; z-index: 2; background: rgba(11,11,13,.92); backdrop-filter: blur(12px); padding: 16px 18px 10px; border-bottom: 1px solid #1e1e21; }
  h1 { font-size: 18px; margin: 0 0 2px; letter-spacing: -.2px; }
  .sub { font-size: 12px; color: #9aa8bd; }
  main { padding: 14px 16px 40px; }
  .card { background: #141416; border: 1px solid #222226; border-radius: 14px; padding: 12px; margin-bottom: 14px; }
  .row { display: flex; align-items: center; gap: 12px; }
  .art { width: 52px; height: 52px; border-radius: 9px; object-fit: cover; background: #1e1e21; flex: none; }
  .title { font-size: 14px; font-weight: 600; line-height: 1.25; }
  .meta { font-size: 11.5px; color: #a9b4c4; margin-top: 2px; }
  .pill { display: inline-block; font-size: 10px; font-weight: 700; letter-spacing: .4px; text-transform: uppercase; color: #cbd5e1; background: #1e1e21; border-radius: 999px; padding: 4px 9px; }
  .search { display: flex; gap: 8px; margin-bottom: 12px; }
  input { flex: 1; min-width: 0; background: #141416; border: 1px solid #2a2a30; color: #fff; border-radius: 11px; padding: 12px 13px; font-size: 15px; outline: none; }
  input:focus { border-color: #64748b; }
  button { background: #64748b; color: #fff; border: 0; border-radius: 11px; padding: 0 16px; font-size: 14px; font-weight: 700; }
  button:disabled { opacity: .45; }
  .result { display: flex; gap: 10px; align-items: center; padding: 9px 2px; border-bottom: 1px solid #1c1c20; }
  .result:last-child { border-bottom: 0; }
  .grow { flex: 1; min-width: 0; }
  .ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .add { background: #1e1e21; border: 1px solid #33333a; border-radius: 10px; width: 38px; height: 38px; font-size: 20px; line-height: 1; padding: 0; flex: none; }
  .queue-item { display: flex; gap: 10px; align-items: center; padding: 8px 0; border-bottom: 1px solid #1c1c20; }
  .queue-item:last-child { border-bottom: 0; }
  .muted { color: #7c8798; }
  h2 { font-size: 13px; text-transform: uppercase; letter-spacing: .7px; color: #9aa8bd; margin: 18px 2px 8px; }
  .notice { font-size: 12px; color: #cbd5e1; background: #1a1a1e; border: 1px solid #2a2a30; border-radius: 10px; padding: 9px 11px; margin-bottom: 12px; }
  .empty { font-size: 13px; color: #7c8798; padding: 8px 2px; }
</style>
</head>
<body>
<header>
  <h1>Nuvio Music Party</h1>
  <div class="sub" id="status">Connecting…</div>
</header>
<main>
  <div id="notice" class="notice" style="display:none"></div>
  <div class="card" id="now"></div>
  <div class="search">
    <input id="q" placeholder="Search songs to add" autocomplete="off">
    <button id="go">Go</button>
  </div>
  <div class="card" id="results" style="display:none"></div>
  <h2>Up next</h2>
  <div class="card" id="queue"></div>
</main>
<script>
var guest = localStorage.getItem('nuvio_party_guest') || '';
if (!guest) {
  guest = prompt('What should the host call you?', '') || 'A guest';
  guest = guest.slice(0, 32);
  localStorage.setItem('nuvio_party_guest', guest);
}
function esc(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
function ms(value) {
  if (!value) return '';
  var total = Math.floor(value / 1000);
  return Math.floor(total / 60) + ':' + String(total % 60).padStart(2, '0');
}
function trackRow(track, extra) {
  return '<div class="row"><img class="art" src="' + esc(track.thumbnailUrl) + '" alt="">' +
    '<div class="grow"><div class="title ellipsis">' + esc(track.title) + '</div>' +
    '<div class="meta ellipsis">' + esc(track.author || '') + (extra ? ' · ' + esc(extra) : '') + '</div></div>' +
    '<div class="meta">' + ms(track.durationMs) + '</div></div>';
}
var latest = [];
function render(state) {
  var status = document.getElementById('status');
  status.textContent = (state.locked ? 'Locked · ' : 'Open · ') +
    state.additions + ' added' + (state.requireApproval ? ' · approval required' : '');
  var now = document.getElementById('now');
  now.innerHTML = state.nowPlaying
    ? '<span class="pill">Now playing</span><div style="height:10px"></div>' + trackRow(state.nowPlaying, state.nowPlaying.addedBy)
    : '<div class="empty">Nothing playing yet.</div>';
  var queue = document.getElementById('queue');
  if (!state.queue || state.queue.length === 0) {
    queue.innerHTML = '<div class="empty">The queue is empty — add the first song.</div>';
  } else {
    queue.innerHTML = state.queue.map(function (track, index) {
      return '<div class="queue-item"><span class="muted" style="width:18px">' + (index + 1) + '</span>' +
        '<div class="grow"><div class="title ellipsis">' + esc(track.title) + '</div>' +
        '<div class="meta ellipsis">' + esc(track.author || '') + (track.addedBy ? ' · added by ' + esc(track.addedBy) : '') + '</div></div>' +
        '<div class="meta">' + ms(track.durationMs) + '</div></div>';
    }).join('');
  }
}
function poll() {
  fetch('state').then(function (r) { return r.json(); }).then(render).catch(function () {
    document.getElementById('status').textContent = 'Reconnecting…';
  });
}
function showNotice(text) {
  var el = document.getElementById('notice');
  el.textContent = text;
  el.style.display = text ? 'block' : 'none';
  if (text) setTimeout(function () { el.style.display = 'none'; }, 3200);
}
function search() {
  var q = document.getElementById('q').value.trim();
  if (!q) return;
  var button = document.getElementById('go');
  button.disabled = true;
  fetch('search?q=' + encodeURIComponent(q)).then(function (r) { return r.json(); }).then(function (items) {
    latest = items;
    var box = document.getElementById('results');
    box.style.display = 'block';
    if (!items.length) {
      box.innerHTML = '<div class="empty">No matches.</div>';
    } else {
      box.innerHTML = items.map(function (track, index) {
        return '<div class="result"><img class="art" src="' + esc(track.thumbnailUrl) + '" alt="">' +
          '<div class="grow"><div class="title ellipsis">' + esc(track.title) + '</div>' +
          '<div class="meta ellipsis">' + esc(track.author || '') + '</div></div>' +
          '<button class="add" data-index="' + index + '">+</button></div>';
      }).join('');
    }
    button.disabled = false;
  }).catch(function () {
    showNotice('Search failed. Try again.');
    button.disabled = false;
  });
}
document.getElementById('results').addEventListener('click', function (event) {
  var button = event.target.closest('.add');
  if (!button) return;
  var track = latest[Number(button.dataset.index)];
  if (!track) return;
  button.disabled = true;
  fetch('add', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ videoId: track.videoId, guest: guest })
  }).then(function (r) { return r.json().then(function (body) { return { ok: r.ok, body: body }; }); })
    .then(function (result) {
      if (!result.ok) { showNotice(result.body.error || 'Could not add that song.'); button.disabled = false; return; }
      showNotice(result.body.status === 'pending' ? 'Sent to the host for approval.' : 'Added to the queue.');
      poll();
    }).catch(function () { showNotice('Could not add that song.'); button.disabled = false; });
});
document.getElementById('go').addEventListener('click', search);
document.getElementById('q').addEventListener('keydown', function (event) {
  if (event.key === 'Enter') search();
});
poll();
setInterval(poll, 4000);
</script>
</body>
</html>
""".trimIndent()
}
