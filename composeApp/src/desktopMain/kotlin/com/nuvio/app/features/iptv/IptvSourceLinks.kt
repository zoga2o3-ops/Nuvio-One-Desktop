package com.nuvio.app.features.iptv

import java.net.URI
import java.net.URLDecoder

/**
 * A typed or pasted Xtream server made safe: a bare host gets http://, invisible characters and
 * spaces are dropped, and a pasted `get.php`/`player_api.php` path is reduced to the server itself.
 */
internal fun cleanXtreamServer(raw: String): String {
    val t = raw.trim()
    val schemeLen = Regex("^[a-zA-Z]+://").find(t)?.value?.length ?: 0
    val hostEnd = t.indexOf('/', schemeLen).let { if (it < 0) t.length else it }
    val hostPart = t.substring(schemeLen, hostEnd)
        .replace(Regex("[\\u200B-\\u200D\\u2060\\uFEFF\\u00A0\\s]"), "")
        .replace(Regex("[^\\x00-\\x7F]"), "-")
    val fixed = t.substring(0, schemeLen) + hostPart + t.substring(hostEnd)
    var s = tidyStalkerLink(fixed)
    if (s.isEmpty()) return s
    if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
    return runCatching {
        val u = URI(s)
        val host = u.host ?: return@runCatching s.trimEnd('/')
        val port = if (u.port > 0) ":${u.port}" else ""
        "${u.scheme.lowercase()}://$host$port"
    }.getOrDefault(s.trimEnd('/'))
}

/** Username / password from a pasted link like ".../get.php?username=a&password=b". */
internal fun xtreamCredentialsFromLink(raw: String): Pair<String, String>? {
    val query = raw.substringAfter('?', "")
    if (query.isEmpty()) return null
    val params = query.split('&').associate {
        it.substringBefore('=').lowercase() to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
    }
    val user = params["username"].orEmpty()
    val pass = params["password"].orEmpty()
    return if (user.isNotBlank() && pass.isNotBlank()) user to pass else null
}

/**
 * An M3U address made safe: local Windows paths become file URLs, and a bare host gets http://.
 * HTTP(S), file and any other explicit scheme are kept as typed.
 */
internal fun normalizePlaylistUrl(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return t
    val lower = t.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file://")) return t
    if ("://" in t) return t
    if (Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(t) || t.startsWith("\\\\")) {
        return runCatching { java.nio.file.Path.of(t).toUri().toString() }.getOrDefault(t)
    }
    return "http://$t"
}

/** What kind of address was typed, for diagnostics without logging the address itself. */
internal fun playlistUrlShape(raw: String): String {
    val t = raw.trim()
    return when {
        t.isEmpty() -> "empty"
        t.startsWith("http://", true) || t.startsWith("https://", true) -> "http"
        t.startsWith("file://", true) -> "file"
        Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(t) || t.startsWith("\\\\") -> "windows_path"
        "://" in t -> "other_scheme"
        ":" in t -> "host_port"
        else -> "bare"
    }
}
