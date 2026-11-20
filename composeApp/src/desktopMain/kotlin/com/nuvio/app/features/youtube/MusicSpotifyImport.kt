package com.nuvio.app.features.youtube

import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class SpotifyImportResult(
    val playlistName: String,
    val imported: Int,
    val matched: Int,
    val total: Int,
)

/**
 * Imports a public Spotify playlist by reading the track list from its embed page and matching
 * each entry to a YouTube Music video. No Spotify account, API key or token is involved.
 */
internal object MusicSpotifyImport {
    private const val UserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    private val idPattern = Regex("""(?:playlist/|playlist:)([A-Za-z0-9]+)""")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun import(
        playlistUrl: String,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<SpotifyImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val id = idPattern.find(playlistUrl.trim())?.groupValues?.getOrNull(1)
                ?: error("Paste a Spotify playlist link, for example https://open.spotify.com/playlist/…")
            val html = fetch("https://open.spotify.com/embed/playlist/$id")
            val entity = parseEntity(html)
            val name = entity.string("name") ?: "Spotify playlist"
            val tracks = (entity["trackList"] as? JsonArray) ?: error("This playlist has no tracks.")
            val total = tracks.size.coerceAtMost(100)
            val matched = mutableListOf<YouTubeVideoItem>()
            for (index in 0 until total) {
                val track = tracks[index] as? JsonObject ?: continue
                val title = track.string("title") ?: continue
                val artist = track.string("subtitle").orEmpty()
                val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
                val found = runCatching { YouTubeEngine.search(query, SearchOptions.TYPE_VIDEO) }
                    .getOrNull()
                    .orEmpty()
                    .firstOrNull { it.kind == YouTubeItemKind.VIDEO && it.videoId.isNotBlank() }
                if (found != null) {
                    matched += found.copy(isMusic = true)
                }
                onProgress(index + 1, total)
            }
            if (matched.isEmpty()) {
                error("None of those tracks could be matched in the music catalogue.")
            }
            val playlist = MusicLibraryStore.createPlaylist(name)
            val added = matched.distinctBy { it.videoId }.count { item ->
                MusicLibraryStore.addToPlaylist(playlist.id, item)
            }
            if (added == 0) {
                MusicLibraryStore.deletePlaylist(playlist.id)
                error("The playlist was already imported.")
            }
            SpotifyImportResult(
                playlistName = playlist.name,
                imported = added,
                matched = matched.size,
                total = total,
            )
        }
    }

    private fun fetch(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.setRequestProperty("User-Agent", UserAgent)
            require(connection.responseCode in 200..299) {
                "Spotify returned HTTP ${connection.responseCode}."
            }
            connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEntity(html: String): JsonObject {
        val match = Regex(
            """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).find(html) ?: error("Spotify didn't return playlist details.")
        val root = json.parseToJsonElement(match.groupValues[1]).jsonObject
        return root["props"]?.jsonObject
            ?.get("pageProps")?.jsonObject
            ?.get("state")?.jsonObject
            ?.get("data")?.jsonObject
            ?.get("entity")?.jsonObject
            ?: error("Spotify didn't return playlist details.")
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
