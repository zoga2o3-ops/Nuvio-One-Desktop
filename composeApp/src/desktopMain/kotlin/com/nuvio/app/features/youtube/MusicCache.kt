package com.nuvio.app.features.youtube

import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** A playlist row or track, without the engine object that can't be stored. */
@Serializable
internal data class CachedMusicItem(
    val videoId: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
    val kind: String = "VIDEO",
    val channelId: String? = null,
    val playlistId: String? = null,
    val reloadPageKey: String? = null,
    val metadata: String? = null,
    val badgeText: String? = null,
    val isMusic: Boolean = false,
)

@Serializable
internal data class CachedMusicPlaylist(
    val items: List<CachedMusicItem>,
    val complete: Boolean,
    val savedAtMs: Long,
)

@Serializable
internal data class CachedMusicFeed(val title: String, val items: List<CachedMusicItem>)

@Serializable
internal data class MusicCacheSnapshot(
    val version: Int = MusicCache.Version,
    /** The Music profile this was saved for; another profile ignores it. */
    val accountKey: String? = null,
    val playlists: List<CachedMusicItem> = emptyList(),
    val contents: Map<String, CachedMusicPlaylist> = emptyMap(),
    val home: List<CachedMusicFeed> = emptyList(),
    val savedAtMs: Long = 0L,
)

internal fun YouTubeVideoItem.toCached(): CachedMusicItem = CachedMusicItem(
    videoId = videoId,
    title = title,
    author = author,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    kind = kind.name,
    channelId = channelId,
    playlistId = playlistId,
    reloadPageKey = reloadPageKey,
    metadata = metadata,
    badgeText = badgeText,
    isMusic = isMusic,
)

internal fun CachedMusicItem.toItem(): YouTubeVideoItem = YouTubeVideoItem(
    videoId = videoId,
    title = title,
    author = author,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    isLive = false,
    kind = runCatching { YouTubeItemKind.valueOf(kind) }.getOrDefault(YouTubeItemKind.VIDEO),
    channelId = channelId,
    playlistId = playlistId,
    reloadPageKey = reloadPageKey,
    metadata = metadata,
    badgeText = badgeText,
    isMusic = isMusic,
)

/**
 * Music's on-disk cache: the account's playlists, each playlist's full track
 * list and the Home feed. Shown straight away on the next launch while the
 * Music screen refreshes everything from YouTube in the background.
 */
internal object MusicCache {
    const val Version = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private val file get() = DesktopStorage.cacheDir.resolve("music").resolve("music-cache.json.gz")

    suspend fun load(): MusicCacheSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val path = file
            if (!Files.isRegularFile(path)) return@runCatching null
            val text = GZIPInputStream(Files.newInputStream(path)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            json.decodeFromString<MusicCacheSnapshot>(text).takeIf { it.version == Version }
        }.getOrNull()
    }

    /** Saves after a short pause, so a burst of updates writes once. */
    fun saveSoon(snapshot: () -> MusicCacheSnapshot) {
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(2_500L)
            runCatching {
                val path = file
                Files.createDirectories(path.parent)
                val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
                GZIPOutputStream(Files.newOutputStream(temp)).bufferedWriter(Charsets.UTF_8).use {
                    it.write(json.encodeToString(snapshot()))
                }
                runCatching {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                }.getOrElse {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    fun clear() {
        saveJob?.cancel()
        runCatching { Files.deleteIfExists(file) }
    }
}

/**
 * Artist and album pages already opened this session, so going back to one is
 * instant. Lives as long as the app, across leaving and re-entering Music.
 */
internal object MusicSessionCache {
    private const val MaxEntries = 60
    private val pages = object : LinkedHashMap<String, List<YouTubeVideoItem>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<YouTubeVideoItem>>?): Boolean =
            size > MaxEntries
    }

    fun get(key: String): List<YouTubeVideoItem>? = synchronized(pages) { pages[key] }

    fun put(key: String, items: List<YouTubeVideoItem>) {
        if (items.isEmpty()) return
        synchronized(pages) { pages[key] = items }
    }
}
