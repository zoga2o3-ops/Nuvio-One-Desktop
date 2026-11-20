package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopSecretStore
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The signed-in Last.fm account. Only the username and session key are stored. */
@Serializable
internal data class LastFmConfig(
    val username: String = "",
    val sessionKey: String = "",
    val enabled: Boolean = false,
) {
    val configured: Boolean get() = sessionKey.isNotBlank()
    val available: Boolean
        get() = LastFmBuildConfig.API_KEY.isNotBlank() && LastFmBuildConfig.SECRET.isNotBlank()
}

/**
 * Last.fm scrobbling for the Music section.
 *
 * The user signs in once with their Last.fm username and password; [login] exchanges those for a
 * session key through `auth.getMobileSession` and only that key is kept. A track is scrobbled once
 * it passes half its duration (or four minutes, whichever comes first) and lasts at least 30s.
 */
internal object MusicScrobbler {
    private const val Key = "payload"
    private const val Endpoint = "https://ws.audioscrobbler.com/2.0/"
    private const val Agent = "NuvioOneDesktop/0.1"

    private val store = DesktopStorage.store("nuvio_music_lastfm")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var config by mutableStateOf(LastFmConfig())
        private set

    private var loaded = false
    private var announcedId: String? = null
    private var scrobbledId: String? = null
    private var lastSongId: String? = null
    private var lastPositionMs = 0L

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val text = store.getString(Key) ?: return@runCatching
            val saved = json.decodeFromString<LastFmConfig>(text)
            val sessionKey = DesktopSecretStore.unprotect(saved.sessionKey)
            // Keys saved without an account name came from an older credential and are unusable.
            config = if (saved.username.isBlank()) LastFmConfig() else saved.copy(sessionKey = sessionKey)
        }
    }

    suspend fun login(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = LastFmBuildConfig.API_KEY
            val secret = LastFmBuildConfig.SECRET
            if (apiKey.isBlank() || secret.isBlank()) {
                error("Last.fm login isn't configured for this build.")
            }
            val name = username.trim()
            if (name.isBlank() || password.isBlank()) error("Enter your Last.fm username and password.")
            val body = execute(
                params = linkedMapOf(
                    "method" to "auth.getMobileSession",
                    "api_key" to apiKey,
                    "username" to name,
                    "password" to password,
                ),
                secret = secret,
            )
            val session = json.parseToJsonElement(body).jsonObject["session"]?.jsonObject
                ?: error("Last.fm did not return a session.")
            val account = session["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: name
            val key = session["key"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: error("Last.fm did not return a session key.")
            save(LastFmConfig(account, key, enabled = true))
            account
        }
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        if (enabled && !config.configured) return
        save(config.copy(enabled = enabled))
    }

    fun logout() {
        ensureLoaded()
        save(LastFmConfig())
    }

    /**
     * Feeds the player state into the scrobbler. Safe to call on every playback tick; it only
     * sends a request when a track starts or crosses the scrobble threshold.
     */
    fun observe(item: YouTubeVideoItem?, snapshot: PlayerPlaybackSnapshot) {
        ensureLoaded()
        val active = config
        if (!active.enabled || !active.configured) return
        if (item == null || item.author.isNullOrBlank()) return
        if (item.videoId != lastSongId) {
            lastSongId = item.videoId
            lastPositionMs = 0L
            announcedId = null
            scrobbledId = null
        }
        if (snapshot.positionMs + 5_000L < lastPositionMs) {
            announcedId = null
            scrobbledId = null
        }
        lastPositionMs = snapshot.positionMs
        if (snapshot.isPlaying && announcedId != item.videoId) {
            announcedId = item.videoId
            updateNowPlaying(active, item)
        }
        if (scrobbledId == item.videoId || !snapshot.isPlaying) return
        val duration = snapshot.durationMs.takeIf { it > 0 } ?: item.durationMs
        if (duration < 30_000L) return
        val threshold = min(duration / 2L, 4 * 60_000L)
        if (snapshot.positionMs >= threshold) {
            scrobbledId = item.videoId
            scrobble(active, item, duration)
        }
    }

    private fun updateNowPlaying(active: LastFmConfig, item: YouTubeVideoItem) {
        val params = linkedMapOf(
            "method" to "track.updateNowPlaying",
            "api_key" to LastFmBuildConfig.API_KEY,
            "sk" to active.sessionKey,
            "artist" to (item.author ?: "Unknown Artist"),
            "track" to item.title,
        )
        post(params)
    }

    private fun scrobble(active: LastFmConfig, item: YouTubeVideoItem, durationMs: Long) {
        val params = linkedMapOf(
            "method" to "track.scrobble",
            "api_key" to LastFmBuildConfig.API_KEY,
            "sk" to active.sessionKey,
            "artist" to (item.author ?: "Unknown Artist"),
            "track" to item.title,
            "timestamp" to (System.currentTimeMillis() / 1000L).toString(),
            "duration" to (durationMs / 1000L).coerceAtLeast(1L).toString(),
        )
        post(params)
    }

    private fun post(params: Map<String, String>) {
        val secret = LastFmBuildConfig.SECRET
        if (secret.isBlank()) return
        scope.launch { runCatching { execute(params, secret) } }
    }

    private fun execute(params: Map<String, String>, secret: String): String {
        val signed = params.toMutableMap()
        signed["api_sig"] = signature(params, secret)
        signed["format"] = "json"
        val form = signed.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8.name())}=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        val connection = URI(Endpoint).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("User-Agent", Agent)
            connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val body = connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            val error = runCatching {
                json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            if (error != null) {
                val message = runCatching {
                    json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Last.fm rejected the request."
                throw IOException(message)
            }
            if (connection.responseCode !in 200..299) {
                throw IOException("Last.fm returned HTTP ${connection.responseCode}.")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun save(updated: LastFmConfig) {
        config = updated
        announcedId = null
        scrobbledId = null
        runCatching {
            store.putString(
                Key,
                json.encodeToString(updated.copy(sessionKey = DesktopSecretStore.protect(updated.sessionKey))),
            )
        }
    }

    /** Last.fm's signature is MD5 over sorted `key + value` pairs followed by the shared secret. */
    internal fun signature(params: Map<String, String>, secret: String): String {
        val raw = params.toSortedMap().entries.joinToString("") { (key, value) -> key + value } + secret
        return MessageDigest.getInstance("MD5")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
