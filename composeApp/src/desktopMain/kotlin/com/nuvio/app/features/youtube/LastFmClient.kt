package com.nuvio.app.features.youtube

import com.nuvio.app.core.storage.DesktopStorage
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Last.fm catalogue data for the music section: an artist's most-played tracks and similar artists. Needs an
 * API key in %APPDATA%\Nuvio\lastfm.properties (a line `lastfm.api_key=...`). Without one, every call returns
 * nothing and the music section works as before.
 */
internal object LastFmClient {
    private const val Endpoint = "https://ws.audioscrobbler.com/2.0/"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // A key in lastfm.properties overrides the build's key; otherwise the build's key is used, the same one sign-in uses.
    private val apiKey: String? by lazy {
        runCatching {
            DesktopStorage.rootDir.resolve("lastfm.properties").toFile().readLines()
                .map { it.trim() }
                .firstOrNull { it.startsWith("lastfm.api_key=") }
                ?.substringAfter('=')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull() ?: LastFmBuildConfig.API_KEY.takeIf { it.isNotBlank() }
    }

    /** A track from Last.fm's most-played list, in its rank order. */
    data class TopTrack(val title: String, val rank: Int, val playcount: Long)

    /** The artist's most-played tracks on Last.fm, best first. Empty when there's no key or no answer. */
    suspend fun topTracks(artist: String, limit: Int = 30): List<TopTrack> = withContext(Dispatchers.IO) {
        val json = call(mapOf("method" to "artist.gettoptracks", "artist" to artist, "limit" to limit.toString()))
            ?: return@withContext emptyList()
        val tracks: List<JsonElement> = json["toptracks"]?.jsonObject?.get("track")?.jsonArray ?: emptyList()
        tracks.mapNotNull { element ->
            val obj = element.jsonObject
            val title = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val rank = obj["@attr"]?.jsonObject?.get("rank")?.jsonPrimitive?.content?.toIntOrNull() ?: Int.MAX_VALUE
            val plays = obj["playcount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            TopTrack(title, rank, plays)
        }.sortedBy { it.rank }
    }

    /** Names of artists Last.fm considers similar to [artist]. Empty when there's no key or no answer. */
    suspend fun similarArtists(artist: String, limit: Int = 10): List<String> = withContext(Dispatchers.IO) {
        val json = call(mapOf("method" to "artist.getsimilar", "artist" to artist, "limit" to limit.toString()))
            ?: return@withContext emptyList()
        val artists: List<JsonElement> = json["similarartists"]?.jsonObject?.get("artist")?.jsonArray ?: emptyList()
        artists.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content?.takeIf { name -> name.isNotBlank() } }
    }

    /** The MusicBrainz id Last.fm holds for [artist], when it knows one. Null without a key or an answer. */
    suspend fun musicBrainzId(artist: String): String? = withContext(Dispatchers.IO) {
        val json = call(mapOf("method" to "artist.getinfo", "artist" to artist, "autocorrect" to "1"))
            ?: return@withContext null
        json["artist"]?.jsonObject?.get("mbid")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }

    /** One Last.fm API call, parsed. Returns null on a network failure, a missing key, or a Last.fm error. */
    private fun call(params: Map<String, String>): kotlinx.serialization.json.JsonObject? {
        val key = apiKey ?: return null
        val query = (params + ("api_key" to key) + ("format" to "json")).entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
        }
        return runCatching {
            val request = Request.Builder().url("$Endpoint?$query").get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = Json.parseToJsonElement(body).jsonObject
                if (json["error"] != null) null else json
            }
        }.getOrNull()
    }
}
