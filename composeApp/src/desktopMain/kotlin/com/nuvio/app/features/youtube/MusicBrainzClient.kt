package com.nuvio.app.features.youtube

import com.nuvio.app.core.storage.DesktopStorage
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * MusicBrainz: an artist's albums, EPs and singles with exact release dates, for Release Radar. The API is open
 * (no key), asks for one request per second and an identifying User-Agent. Discographies are cached on this
 * machine for [CacheDays] days, so a list costs requests only the first time.
 */
internal object MusicBrainzClient {
    private const val Base = "https://musicbrainz.org/ws/2/"
    private const val UserAgent = "NuvioOne/0.1 ( nuvio-one )"
    private const val MinGapMs = 1100L
    private const val CacheDays = 30L

    private val gate = Mutex()
    private var lastCallAt = 0L

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cacheDir by lazy {
        DesktopStorage.rootDir.resolve("musicbrainz-cache").also { Files.createDirectories(it) }
    }

    /** One album, EP or single. [date] is YYYY, YYYY-MM or YYYY-MM-DD, or empty. */
    data class Release(val title: String, val type: String, val date: String)

    /** The MusicBrainz id of an artist with this exact name, or null when none is a confident match. */
    suspend fun artistId(name: String): String? = withContext(Dispatchers.IO) {
        val body = get("artist/?query=${enc("artist:\"$name\"")}&fmt=json&limit=5") ?: return@withContext null
        val artists = Json.parseToJsonElement(body).jsonObject["artists"]?.jsonArray ?: return@withContext null
        // Names compare with punctuation and case ignored, so "Manila Grey" matches "MANILA GREY".
        fun key(text: String) = text.lowercase().filter { it.isLetterOrDigit() }
        artists.map { it.jsonObject }
            .firstOrNull { artist ->
                val named = artist["name"]?.jsonPrimitive?.content.orEmpty()
                key(named) == key(name) && key(name).isNotEmpty()
            }
            ?.get("id")?.jsonPrimitive?.content
    }

    /** The artist's albums, EPs and singles, newest first. Cached on this machine; empty when MusicBrainz can't be reached. */
    suspend fun discography(mbid: String): List<Release> = withContext(Dispatchers.IO) {
        val file = cacheDir.resolve(sha1(mbid) + ".tsv")
        if (Files.exists(file) && ageDays(file) < CacheDays) {
            return@withContext runCatching {
                Files.readAllLines(file).mapNotNull { line ->
                    line.split('\t').takeIf { it.size == 3 }?.let { Release(title = it[2], type = it[1], date = it[0]) }
                }
            }.getOrDefault(emptyList())
        }
        val found = mutableListOf<Release>()
        var offset = 0
        while (true) {
            val body = get("release-group?artist=$mbid&type=album|ep|single&limit=100&offset=$offset&fmt=json") ?: break
            val root = Json.parseToJsonElement(body).jsonObject
            val groups = root["release-groups"]?.jsonArray ?: break
            groups.forEach { element ->
                val group = element.jsonObject
                found += Release(
                    title = group["title"]?.jsonPrimitive?.content.orEmpty(),
                    type = group["primary-type"]?.jsonPrimitive?.content.orEmpty(),
                    date = group["first-release-date"]?.jsonPrimitive?.content.orEmpty(),
                )
            }
            val total = root["release-group-count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            offset += groups.size
            if (groups.isEmpty() || offset >= total) break
        }
        val releases = found
            .filter { it.title.isNotBlank() }
            .distinctBy { it.title.lowercase() + "|" + it.type }
            .sortedByDescending { it.date }
        // Only a complete answer is worth keeping; a failed request would otherwise stay cached as empty.
        if (releases.isNotEmpty()) {
            runCatching {
                Files.write(
                    file,
                    releases.map { "${it.date}\t${it.type}\t${it.title.replace('\t', ' ').replace('\n', ' ')}" },
                )
            }
        }
        releases
    }

    /** One GET, after waiting out the one-second gap. Null on any failure. */
    private suspend fun get(path: String): String? = gate.withLock {
        val wait = MinGapMs - (System.currentTimeMillis() - lastCallAt)
        if (wait > 0) delay(wait)
        lastCallAt = System.currentTimeMillis()
        runCatching {
            val request = Request.Builder()
                .url(Base + path)
                .header("User-Agent", UserAgent)
                .header("Accept", "application/json")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    private fun enc(text: String): String = URLEncoder.encode(text, StandardCharsets.UTF_8)

    private fun ageDays(file: java.nio.file.Path): Double =
        (System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis()) / 86_400_000.0

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
