package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.Locale
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
internal data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
)

@Serializable
internal data class NavidromeConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
)

internal data class WebDavEntry(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
)

internal data class NavidromeAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val coverArt: String?,
    val songCount: Int,
)

internal data class NavidromePlaylist(
    val id: String,
    val name: String,
    val coverArt: String?,
    val songCount: Int,
)

internal data class RemoteTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverArt: String?,
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
)

internal object MusicRemoteStore {
    private val store = DesktopStorage.store("nuvio_music_remote")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    var webDav by mutableStateOf(WebDavConfig())
        private set
    var navidrome by mutableStateOf(NavidromeConfig())
        private set

    fun load() {
        runCatching {
            store.getString("webdav")?.let { webDav = json.decodeFromString<WebDavConfig>(it) }
            store.getString("navidrome")?.let { navidrome = json.decodeFromString<NavidromeConfig>(it) }
        }
    }

    fun saveWebDav(config: WebDavConfig) {
        webDav = config
        runCatching { store.putString("webdav", json.encodeToString(config)) }
    }

    fun clearWebDav() = saveWebDav(WebDavConfig())

    fun saveNavidrome(config: NavidromeConfig) {
        navidrome = config
        runCatching { store.putString("navidrome", json.encodeToString(config)) }
    }

    fun clearNavidrome() = saveNavidrome(NavidromeConfig())
}

internal object MusicWebDavClient {
    private val audioExtensions = setOf("mp3", "m4a", "m4b", "flac", "wav", "ogg", "opus", "aac", "webm", "wma")
    private const val Agent = "NuvioOneDesktop/0.1"

    fun isAudio(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in audioExtensions

    fun headersFor(config: WebDavConfig, url: String): Map<String, String> {
        if (config.username.isBlank()) return emptyMap()
        val server = runCatching { URI(config.url) }.getOrNull() ?: return emptyMap()
        val target = runCatching { URI(url) }.getOrNull() ?: return emptyMap()
        if (!target.host.equals(server.host, ignoreCase = true)) return emptyMap()
        val token = Base64.getEncoder().encodeToString("${config.username}:${config.password}".toByteArray())
        return mapOf("Authorization" to "Basic $token")
    }

    suspend fun list(config: WebDavConfig, url: String): Result<List<WebDavEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(config.url.isNotBlank()) { "Add a WebDAV server first." }
                val body = """
                    <?xml version="1.0" encoding="utf-8" ?>
                    <d:propfind xmlns:d="DAV:">
                      <d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/></d:prop>
                    </d:propfind>
                """.trimIndent()
                val builder = HttpRequest.newBuilder(URI(url))
                    .timeout(Duration.ofSeconds(30))
                    .method("PROPFIND", HttpRequest.BodyPublishers.ofString(body))
                    .header("Depth", "1")
                    .header("Content-Type", "application/xml; charset=utf-8")
                    .header("User-Agent", Agent)
                headersFor(config, url).forEach { (k, v) -> builder.header(k, v) }
                val response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString())
                require(response.statusCode() in 200..299) { "WebDAV returned HTTP ${response.statusCode()}." }
                parse(response.body(), url)
            }
        }

    private fun parse(xml: String, baseUrl: String): List<WebDavEntry> {
        val factory = XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }
        val reader = factory.createXMLStreamReader(xml.reader())
        val entries = ArrayList<WebDavEntry>()
        var href: String? = null
        var name: String? = null
        var directory = false
        val base = baseUrl.trimEnd('/')
        try {
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                        "response" -> {
                            href = null
                            name = null
                            directory = false
                        }
                        "href" -> href = reader.elementText
                        "displayname" -> name = reader.elementText
                        "collection" -> directory = true
                    }
                    XMLStreamConstants.END_ELEMENT -> if (reader.localName == "response") {
                        val raw = href
                        if (!raw.isNullOrBlank()) {
                            val resolved = runCatching { URI(baseUrl).resolve(raw.trim()).toString() }
                                .getOrDefault(raw)
                            if (resolved.trimEnd('/') != base && resolved != baseUrl) {
                                val display = name?.takeIf { it.isNotBlank() }
                                    ?: java.net.URLDecoder.decode(resolved.substringAfterLast('/'), "UTF-8")
                                if (directory || display.isNotBlank()) {
                                    entries += WebDavEntry(display.ifBlank { "Folder" }, resolved, directory)
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            reader.close()
        }
        return entries.sortedWith(compareByDescending<WebDavEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }
}

internal object MusicNavidromeClient {
    private const val Version = "1.16.1"
    private const val Client = "NuvioOneDesktop"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ping(config: NavidromeConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = request(config, "ping")
            val response = root["subsonic-response"]?.jsonObject
            require(response?.get("status")?.jsonPrimitive?.contentOrNull == "ok") {
                response?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                    ?: "Navidrome did not accept these credentials."
            }
        }
    }

    suspend fun albums(config: NavidromeConfig): Result<List<NavidromeAlbum>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = request(config, "getAlbumList2", mapOf("type" to "alphabeticalByName", "size" to "100"))
            val list = root["subsonic-response"]?.jsonObject?.get("albumList2")?.jsonObject
                ?.get("album") as? JsonArray
            list.orEmpty().mapNotNull { it as? JsonObject }.map { item ->
                NavidromeAlbum(
                    id = item.string("id").orEmpty(),
                    name = item.string("name") ?: "Album",
                    artist = item.string("artist") ?: "Unknown Artist",
                    coverArt = item.string("coverArt"),
                    songCount = item.int("songCount") ?: 0,
                )
            }
        }
    }

    suspend fun playlists(config: NavidromeConfig): Result<List<NavidromePlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = request(config, "getPlaylists")
            val list = root["subsonic-response"]?.jsonObject?.get("playlists")?.jsonObject
                ?.get("playlist") as? JsonArray
            list.orEmpty().mapNotNull { it as? JsonObject }.map { item ->
                NavidromePlaylist(
                    id = item.string("id").orEmpty(),
                    name = item.string("name") ?: "Playlist",
                    coverArt = item.string("coverArt"),
                    songCount = item.int("songCount") ?: 0,
                )
            }
        }
    }

    suspend fun albumTracks(config: NavidromeConfig, albumId: String): Result<List<RemoteTrack>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = request(config, "getAlbum", mapOf("id" to albumId))
                val album = root["subsonic-response"]?.jsonObject?.get("album")?.jsonObject
                val songs = album?.get("song") as? JsonArray
                songs.orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { item ->
                    val id = item.string("id") ?: return@mapNotNull null
                    RemoteTrack(
                        id = "navidrome:$id",
                        title = item.string("title") ?: "Track",
                        artist = item.string("artist") ?: "Unknown Artist",
                        album = item.string("album") ?: "",
                        durationMs = (item.int("duration") ?: 0) * 1000L,
                        coverArt = item.string("coverArt"),
                        streamUrl = streamUrl(config, id),
                    )
                }
            }
        }

    suspend fun playlistTracks(config: NavidromeConfig, playlistId: String): Result<List<RemoteTrack>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = request(config, "getPlaylist", mapOf("id" to playlistId))
                val playlist = root["subsonic-response"]?.jsonObject?.get("playlist")?.jsonObject
                val entries = playlist?.get("entry") as? JsonArray
                entries.orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { item ->
                    val id = item.string("id") ?: return@mapNotNull null
                    RemoteTrack(
                        id = "navidrome:$id",
                        title = item.string("title") ?: "Track",
                        artist = item.string("artist") ?: "Unknown Artist",
                        album = item.string("album") ?: "",
                        durationMs = (item.int("duration") ?: 0) * 1000L,
                        coverArt = item.string("coverArt"),
                        streamUrl = streamUrl(config, id),
                    )
                }
            }
        }

    fun streamUrl(config: NavidromeConfig, trackId: String): String =
        apiUrl(config, "stream", mapOf("id" to trackId, "format" to "raw"))

    fun coverUrl(config: NavidromeConfig, coverId: String?, size: Int = 480): String? {
        if (coverId.isNullOrBlank()) return null
        return apiUrl(config, "getCoverArt", mapOf("id" to coverId, "size" to size.toString()))
    }

    fun toVideoItem(config: NavidromeConfig, track: RemoteTrack): YouTubeVideoItem = YouTubeVideoItem(
        videoId = track.id,
        title = track.title,
        author = track.artist,
        thumbnailUrl = coverUrl(config, track.coverArt),
        durationMs = track.durationMs,
        isLive = false,
        kind = YouTubeItemKind.VIDEO,
        isMusic = true,
        localUrl = track.streamUrl,
        playbackHeaders = track.headers,
    )

    fun toVideoItem(config: WebDavConfig, entry: WebDavEntry): YouTubeVideoItem = YouTubeVideoItem(
        videoId = "webdav:" + entry.url.hashCode().toUInt().toString(16),
        title = entry.name.substringBeforeLast('.').ifBlank { entry.name },
        author = "WebDAV",
        thumbnailUrl = null,
        durationMs = 0L,
        isLive = false,
        kind = YouTubeItemKind.VIDEO,
        isMusic = true,
        localUrl = entry.url,
        playbackHeaders = MusicWebDavClient.headersFor(config, entry.url),
    )

    private fun request(config: NavidromeConfig, endpoint: String, params: Map<String, String> = emptyMap()): JsonObject {
        val url = apiUrl(config, endpoint, params)
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "NuvioOneDesktop/0.1")
            require(connection.responseCode in 200..299) { "Navidrome returned HTTP ${connection.responseCode}." }
            json.parseToJsonElement(connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)).jsonObject
        } finally {
            connection.disconnect()
        }
    }

    private fun apiUrl(config: NavidromeConfig, endpoint: String, params: Map<String, String>): String {
        val salt = (1..12).map { ('a'..'z').random() }.joinToString("")
        val token = md5(config.password + salt)
        val query = buildList {
            add("u=${enc(config.username)}")
            add("t=$token")
            add("s=$salt")
            add("v=$Version")
            add("c=${enc(Client)}")
            add("f=json")
            params.forEach { (k, v) -> add("${enc(k)}=${enc(v)}") }
        }.joinToString("&")
        return config.url.trimEnd('/') + "/rest/$endpoint?$query"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
}
