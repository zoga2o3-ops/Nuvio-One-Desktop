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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class MusicPhoneLinkState(
    val running: Boolean = false,
    val url: String? = null,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Lets a phone on the same network type server credentials instead of using the desktop keyboard.
 * The page only submits WebDAV and Subsonic/Navidrome details; it is a setup helper, not a remote
 * control, and it stops with [stop] when the host is done.
 */
internal object MusicPhoneLink {
    private val json = Json { ignoreUnknownKeys = true }
    var state by mutableStateOf(MusicPhoneLinkState())
        private set

    private var server: HttpServer? = null
    private var token: String? = null

    fun start() {
        stop()
        runCatching {
            val newToken = UUID.randomUUID().toString().replace("-", "").take(12)
            val created = HttpServer.create(InetSocketAddress(0), 0)
            created.executor = Executors.newFixedThreadPool(2) { runnable ->
                Thread(runnable, "nuvio-music-phone").apply { isDaemon = true }
            }
            created.createContext("/phone/$newToken") { exchange -> handle(exchange, newToken) }
            created.start()
            server = created
            token = newToken
            val url = "http://${localIpv4()}:${created.address.port}/phone/$newToken/"
            state = MusicPhoneLinkState(running = true, url = url)
        }.onFailure {
            state = MusicPhoneLinkState(error = it.message ?: "Could not start the phone link.")
        }
    }

    fun stop() {
        runCatching { server?.stop(0) }
        server = null
        token = null
        state = MusicPhoneLinkState()
    }

    private fun handle(exchange: HttpExchange, expectedToken: String) {
        try {
            if (token != expectedToken) {
                respond(exchange, 404, "text/plain", "Not found")
                return
            }
            val path = exchange.requestURI.path.removePrefix("/phone/$expectedToken").trim('/')
            when {
                exchange.requestMethod == "GET" && path.isEmpty() ->
                    respond(exchange, 200, "text/html; charset=utf-8", page())
                exchange.requestMethod == "POST" && path == "webdav" -> handleWebDav(exchange)
                exchange.requestMethod == "POST" && path == "navidrome" -> handleNavidrome(exchange)
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

    private fun handleWebDav(exchange: HttpExchange) {
        val payload = readPayload(exchange) ?: return
        val url = payload["url"].orEmpty().trim()
        if (url.isBlank()) {
            respond(exchange, 400, "application/json", """{"error":"Enter the WebDAV server URL."}""")
            return
        }
        val normalized = if (url.endsWith('/')) url else "$url/"
        val config = WebDavConfig(normalized, payload["username"].orEmpty().trim(), payload["password"].orEmpty())
        val result = runBlocking { MusicWebDavClient.list(config, normalized) }
        result.onSuccess {
            MusicRemoteStore.saveWebDav(config)
            respond(exchange, 200, "application/json", """{"status":"connected","type":"WebDAV"}""")
        }.onFailure {
            respond(exchange, 400, "application/json", """{"error":"${(it.message ?: "Could not connect.").escapeJson()}"}""")
        }
    }

    private fun handleNavidrome(exchange: HttpExchange) {
        val payload = readPayload(exchange) ?: return
        val url = payload["url"].orEmpty().trim().trimEnd('/')
        if (url.isBlank()) {
            respond(exchange, 400, "application/json", """{"error":"Enter the Navidrome server URL."}""")
            return
        }
        val config = NavidromeConfig(url, payload["username"].orEmpty().trim(), payload["password"].orEmpty())
        val result = runBlocking { MusicNavidromeClient.ping(config) }
        result.onSuccess {
            MusicRemoteStore.saveNavidrome(config)
            respond(exchange, 200, "application/json", """{"status":"connected","type":"Navidrome"}""")
        }.onFailure {
            respond(exchange, 400, "application/json", """{"error":"${(it.message ?: "Could not connect.").escapeJson()}"}""")
        }
    }

    private fun readPayload(exchange: HttpExchange): Map<String, String>? {
        val body = exchange.requestBody.use { it.readNBytes(32 * 1024) }.toString(Charsets.UTF_8)
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (root == null) {
            respond(exchange, 400, "application/json", """{"error":"Invalid request."}""")
            return null
        }
        return root.mapValues { (_, value) -> value.jsonPrimitive.contentOrNull.orEmpty() }
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
<title>Nuvio Music setup</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin: 0; background: #0b0b0d; color: #fff; font-family: system-ui, -apple-system, "Segoe UI", sans-serif; padding: 20px 16px 48px; }
  h1 { font-size: 20px; margin: 0 0 3px; }
  .sub { font-size: 12.5px; color: #9aa8bd; margin-bottom: 18px; }
  .card { background: #141416; border: 1px solid #222226; border-radius: 14px; padding: 14px; margin-bottom: 14px; }
  h2 { font-size: 15px; margin: 0 0 10px; }
  label { display: block; font-size: 11px; text-transform: uppercase; letter-spacing: .6px; color: #9aa8bd; margin: 10px 0 4px; }
  input { width: 100%; background: #1a1a1e; border: 1px solid #2a2a30; color: #fff; border-radius: 10px; padding: 11px 12px; font-size: 15px; outline: none; }
  input:focus { border-color: #64748b; }
  button { width: 100%; margin-top: 14px; background: #64748b; color: #fff; border: 0; border-radius: 11px; padding: 13px; font-size: 15px; font-weight: 700; }
  button:disabled { opacity: .5; }
  .status { font-size: 12.5px; margin-top: 10px; color: #cbd5e1; min-height: 16px; }
  .status.error { color: #ff8a9b; }
</style>
</head>
<body>
<h1>Nuvio Music</h1>
<div class="sub">Add a music server from your phone. The desktop app tests the connection before saving it.</div>
<form class="card" id="webdav">
  <h2>WebDAV</h2>
  <label>Server URL</label><input name="url" placeholder="https://example.com/dav/" autocomplete="off">
  <label>Username</label><input name="username" autocomplete="username">
  <label>Password</label><input name="password" type="password" autocomplete="current-password">
  <button type="submit">Connect WebDAV</button>
  <div class="status"></div>
</form>
<form class="card" id="navidrome">
  <h2>Navidrome / Subsonic</h2>
  <label>Server URL</label><input name="url" placeholder="https://music.example.com" autocomplete="off">
  <label>Username</label><input name="username" autocomplete="username">
  <label>Password</label><input name="password" type="password" autocomplete="current-password">
  <button type="submit">Connect Navidrome</button>
  <div class="status"></div>
</form>
<script>
function submit(form, path) {
  form.addEventListener('submit', function (event) {
    event.preventDefault();
    var button = form.querySelector('button');
    var status = form.querySelector('.status');
    status.className = 'status';
    status.textContent = 'Testing the connection…';
    button.disabled = true;
    var data = {};
    new FormData(form).forEach(function (value, key) { data[key] = value; });
    fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    }).then(function (response) {
      return response.json().then(function (body) { return { ok: response.ok, body: body }; });
    }).then(function (result) {
      if (result.ok) {
        status.textContent = result.body.type + ' connected.';
        form.reset();
      } else {
        status.className = 'status error';
        status.textContent = result.body.error || 'Could not connect.';
      }
      button.disabled = false;
    }).catch(function () {
      status.className = 'status error';
      status.textContent = 'Could not reach the desktop app.';
      button.disabled = false;
    });
  });
}
submit(document.getElementById('webdav'), 'webdav');
submit(document.getElementById('navidrome'), 'navidrome');
</script>
</body>
</html>
""".trimIndent()
}
