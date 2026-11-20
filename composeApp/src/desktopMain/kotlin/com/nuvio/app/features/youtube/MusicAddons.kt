package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class MusicAddonConfig(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
)

internal data class MusicAddonTrack(
    val configId: String,
    val configName: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int?,
    val artworkUrl: String?,
    val streamUrl: String?,
)

internal data class MusicAddonManifest(
    val id: String,
    val name: String,
    val settings: Map<String, String> = emptyMap(),
)

internal class MusicAddonClient(rawBaseUrl: String) {
    val baseUrl: String = normalizeBase(rawBaseUrl)
    private val json = Json { ignoreUnknownKeys = true }

    fun manifest(): Result<MusicAddonManifest> = runCatching {
        val body = get("$baseUrl/manifest.json")
        val root = json.parseToJsonElement(body).jsonObject
        val id = root.string("id") ?: error("That URL did not return an addon manifest.")
        val settings = LinkedHashMap<String, String>()
        (root["settings"] as? JsonArray)?.forEach { element ->
            val setting = element as? JsonObject ?: return@forEach
            val key = setting.string("key") ?: return@forEach
            val default = setting.string("default")
                ?: (setting["options"] as? JsonArray)?.firstOrNull()?.jsonObject?.string("value")
            if (default != null) settings[key] = default
        }
        if ("quality" !in settings) settings["quality"] = "LOSSLESS"
        MusicAddonManifest(
            id = id,
            name = root.string("name") ?: id,
            settings = settings,
        )
    }

    fun search(query: String, settings: Map<String, String>): Result<List<MusicAddonTrack>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        val params = settings.toMutableMap()
        params["q"] = query
        val body = get(endpoint("search", params))
        val root = json.parseToJsonElement(body).jsonObject
        (root["tracks"] as? JsonArray).orEmpty().mapNotNull { element ->
            val track = element as? JsonObject ?: return@mapNotNull null
            val id = track.string("id") ?: return@mapNotNull null
            MusicAddonTrack(
                configId = "",
                configName = "",
                id = id,
                title = track.string("title") ?: "Untitled track",
                artist = track.string("artist") ?: "",
                album = track.string("album"),
                durationSeconds = track["duration"]?.jsonPrimitive?.intOrNull
                    ?: track["durationSeconds"]?.jsonPrimitive?.intOrNull,
                artworkUrl = absolute(track.string("artworkURL") ?: track.string("albumArtworkURL")),
                streamUrl = absolute(track.string("streamURL")),
            )
        }
    }

    fun stream(trackId: String, settings: Map<String, String>): Result<String> = runCatching {
        val body = get(endpoint("stream/$trackId", settings))
        val root = json.parseToJsonElement(body).jsonObject
        val encrypted = root["encrypted"]?.jsonPrimitive?.contentOrNull
        if (encrypted == "true") error("This addon returned a DRM-protected stream.")
        absolute(root.string("url")) ?: error(root.string("error") ?: "This addon did not return a stream.")
    }

    private fun get(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "NuvioOneDesktop/0.1")
            val status = connection.responseCode
            require(status in 200..299) {
                when (status) {
                    401, 403 -> "This addon needs a sign-in Nuvio does not support."
                    404 -> "That addon does not have this track."
                    429 -> "This addon is rate limiting requests. Try again shortly."
                    else -> "The addon returned HTTP $status."
                }
            }
            connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun endpoint(path: String, params: Map<String, String>): String {
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, Charsets.UTF_8.name())}=${URLEncoder.encode(v, Charsets.UTF_8.name())}"
        }
        return "$baseUrl/$path${if (query.isEmpty()) "" else "?$query"}"
    }

    private fun absolute(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) return value
        return runCatching { URI(baseUrl + "/").resolve(value).toString() }.getOrNull()
    }

    companion object {
        fun normalizeBase(raw: String): String {
            var value = raw.trim().trimEnd('/')
            if (!value.contains("://")) {
                val host = value.substringBefore('/')
                val local = host.startsWith("localhost") || host.startsWith("127.") ||
                    host.startsWith("192.168.") || host.startsWith("10.")
                value = if (local) "http://$value" else "https://$value"
            }
            val withoutQuery = value.substringBefore('?')
            val last = withoutQuery.substringAfterLast('/')
            if (!last.endsWith(".json", true)) return value
            return withoutQuery.dropLast(last.length).trimEnd('/') +
                value.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

internal object MusicAddonRegistry {
    private val store = DesktopStorage.store("nuvio_music_addons")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val clients = mutableMapOf<String, MusicAddonClient>()
    private val settings = mutableMapOf<String, Map<String, String>>()

    var configs by mutableStateOf<List<MusicAddonConfig>>(emptyList())
        private set
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        load()
    }

    fun load() {
        loaded = true
        runCatching {
            val text = store.getString("configs") ?: return
            configs = json.decodeFromString<List<MusicAddonConfig>>(text)
        }
    }

    fun add(rawUrl: String): Result<MusicAddonConfig> = runCatching {
        ensureLoaded()
        val client = MusicAddonClient(rawUrl)
        val manifest = client.manifest().getOrThrow()
        val exists = configs.any { it.url.equals(client.baseUrl, true) }
        if (exists) error("That addon is already added.")
        val config = MusicAddonConfig(
            id = manifest.id,
            name = manifest.name,
            url = client.baseUrl,
        )
        clients[config.id] = client
        settings[config.id] = manifest.settings
        configs = configs + config
        persist()
        config
    }

    fun remove(config: MusicAddonConfig) {
        ensureLoaded()
        configs = configs.filterNot { it.id == config.id }
        clients.remove(config.id)
        settings.remove(config.id)
        persist()
    }

    fun setEnabled(config: MusicAddonConfig, enabled: Boolean) {
        ensureLoaded()
        configs = configs.map { if (it.id == config.id) it.copy(enabled = enabled) else it }
        persist()
    }

    suspend fun search(query: String): List<MusicAddonTrack> {
        ensureLoaded()
        if (query.isBlank() || configs.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            coroutineScope {
                configs.filter { it.enabled }.map { config ->
                    async {
                        val client = clients.getOrPut(config.id) { MusicAddonClient(config.url) }
                        val values = settings[config.id]
                            ?: client.manifest().getOrNull()?.settings?.also { settings[config.id] = it }.orEmpty()
                        client.search(query, values).getOrNull().orEmpty().map { track ->
                            track.copy(configId = config.id, configName = config.name)
                        }
                    }
                }.awaitAll().flatten()
            }
        }
    }

    suspend fun resolve(track: MusicAddonTrack): Result<YouTubeVideoItem> = withContext(Dispatchers.IO) {
        ensureLoaded()
        runCatching {
            val config = configs.firstOrNull { it.id == track.configId }
                ?: error("The addon was removed.")
            val client = clients.getOrPut(config.id) { MusicAddonClient(config.url) }
            val url = track.streamUrl ?: client.stream(track.id, settings[config.id].orEmpty()).getOrThrow()
            YouTubeVideoItem(
                videoId = "addon:${track.configId}:${track.id}",
                title = track.title,
                author = track.artist,
                thumbnailUrl = track.artworkUrl,
                durationMs = (track.durationSeconds ?: 0) * 1000L,
                isLive = false,
                kind = YouTubeItemKind.VIDEO,
                isMusic = true,
                localUrl = url,
            )
        }
    }

    private fun persist() {
        runCatching { store.putString("configs", json.encodeToString(configs)) }
    }
}
