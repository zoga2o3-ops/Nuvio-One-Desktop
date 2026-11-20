package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class MusicTrack(
    val videoId: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
    val channelId: String? = null,
    val localUrl: String? = null,
    val playbackHeaders: Map<String, String> = emptyMap(),
    /** When the track was liked / added to a playlist; null for older entries. */
    val addedAtMs: Long? = null,
)

@Serializable
internal data class MusicPlaylist(
    val id: String,
    val name: String,
    val tracks: List<MusicTrack> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
)

@Serializable
private data class MusicLibraryPayload(
    val liked: List<MusicTrack> = emptyList(),
    val recents: List<MusicTrack> = emptyList(),
    val playlists: List<MusicPlaylist> = emptyList(),
)

internal fun YouTubeVideoItem.toMusicTrack(): MusicTrack = MusicTrack(
    videoId = videoId,
    title = title,
    author = cleanArtistName(author),
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    channelId = channelId,
    localUrl = localUrl,
    playbackHeaders = playbackHeaders,
    addedAtMs = System.currentTimeMillis(),
)

internal fun MusicTrack.toYouTubeItem(): YouTubeVideoItem = YouTubeVideoItem(
    videoId = videoId,
    title = title,
    author = author,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    isLive = false,
    kind = YouTubeItemKind.VIDEO,
    channelId = channelId,
    isMusic = true,
    localUrl = localUrl,
    playbackHeaders = playbackHeaders,
)

/** Device-local liked songs, recents and user playlists. */
internal object MusicLibraryStore {
    private const val Key = "payload"
    private const val MaxRecents = 100
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val store = DesktopStorage.store("nuvio_music_library")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var liked by mutableStateOf<List<MusicTrack>>(emptyList())
        private set
    var recents by mutableStateOf<List<MusicTrack>>(emptyList())
        private set
    var playlists by mutableStateOf<List<MusicPlaylist>>(emptyList())
        private set

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val payload = store.getString(Key) ?: return@runCatching
            val decoded = json.decodeFromString<MusicLibraryPayload>(payload)
            liked = decoded.liked
            recents = decoded.recents
            playlists = decoded.playlists
        }
    }

    fun isLiked(videoId: String): Boolean {
        ensureLoaded()
        return liked.any { it.videoId == videoId }
    }

    fun toggleLike(item: YouTubeVideoItem): Boolean {
        ensureLoaded()
        val track = item.toMusicTrack()
        val existing = liked.any { it.videoId == track.videoId }
        liked = if (existing) {
            noteRemoved(setOf(track.videoId))
            liked.filterNot { it.videoId == track.videoId }
        } else {
            listOf(track) + liked
        }
        save()
        syncAccountLike(item, !existing)
        return !existing
    }

    /** Adds a track to local likes without unliking it, syncing the account like when possible. */
    fun ensureLiked(item: YouTubeVideoItem): Boolean {
        ensureLoaded()
        if (liked.any { it.videoId == item.videoId }) return false
        liked = listOf(item.toMusicTrack()) + liked
        save()
        syncAccountLike(item, true)
        return true
    }

    /**
     * Mirrors a like to YouTube when the track came from the signed-in account. Local files,
     * WebDAV/Navidrome and addon tracks have no YouTube counterpart to like.
     */
    private fun syncAccountLike(item: YouTubeVideoItem, likedNow: Boolean) {
        if (item.localUrl != null) return
        if (item.videoId.length != 11 || !item.videoId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            return
        }
        scope.launch {
            runCatching { YouTubeEngine.setLike(item.videoId, item.channelId, likedNow) }
        }
    }

    fun recordPlayed(item: YouTubeVideoItem) {
        if (item.kind != YouTubeItemKind.VIDEO) return
        ensureLoaded()
        val track = item.toMusicTrack()
        recents = (listOf(track) + recents.filterNot { it.videoId == track.videoId }).take(MaxRecents)
        save()
    }

    fun createPlaylist(name: String): MusicPlaylist {
        ensureLoaded()
        val clean = name.trim().ifBlank { "New playlist" }
        val playlist = MusicPlaylist(
            id = UUID.randomUUID().toString(),
            name = clean,
        )
        playlists = playlists + playlist
        save()
        return playlist
    }

    fun createPlaylist(name: String, item: YouTubeVideoItem): MusicPlaylist {
        val playlist = createPlaylist(name)
        addToPlaylist(playlist.id, item)
        return playlist
    }

    fun createPlaylist(name: String, items: List<YouTubeVideoItem>): MusicPlaylist {
        val playlist = createPlaylist(name)
        items.forEach { addToPlaylist(playlist.id, it) }
        return playlist(playlist.id) ?: playlist
    }

    fun playlist(id: String): MusicPlaylist? {
        ensureLoaded()
        return playlists.firstOrNull { it.id == id }
    }

    fun addToPlaylist(playlistId: String, item: YouTubeVideoItem): Boolean {
        ensureLoaded()
        val track = item.toMusicTrack()
        var added = false
        playlists = playlists.map { playlist ->
            if (playlist.id != playlistId || playlist.tracks.any { it.videoId == track.videoId }) {
                playlist
            } else {
                added = true
                playlist.copy(tracks = playlist.tracks + track)
            }
        }
        save()
        return added
    }

    fun removeFromPlaylist(playlistId: String, videoId: String) {
        ensureLoaded()
        playlists = playlists.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(tracks = playlist.tracks.filterNot { it.videoId == videoId })
            } else {
                playlist
            }
        }
        save()
    }

    fun renamePlaylist(playlistId: String, name: String) {
        ensureLoaded()
        val clean = name.trim().ifBlank { return }
        playlists = playlists.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(name = clean) else playlist
        }
        save()
    }

    fun deletePlaylist(playlistId: String) {
        ensureLoaded()
        noteRemoved(setOf(playlistId))
        playlists = playlists.filterNot { it.id == playlistId }
        save()
    }

    /**
     * Writes Liked Songs and playlists from Live TV / Music sync. Details this computer already
     * knows (art, length, channel, when it was added) are kept. True if anything changed.
     */
    @Synchronized
    fun replaceFromSync(nextLiked: List<MusicTrack>, nextPlaylists: List<MusicPlaylist>): Boolean {
        ensureLoaded()
        val known = HashMap<String, MusicTrack>()
        (liked + playlists.flatMap { it.tracks } + recents).forEach { known.putIfAbsent(it.videoId, it) }
        fun keep(t: MusicTrack): MusicTrack {
            val k = known[t.videoId] ?: return t
            return t.copy(
                thumbnailUrl = k.thumbnailUrl ?: t.thumbnailUrl,
                // Synced lengths are whole seconds: keep the exact one known here.
                durationMs = if (t.durationMs <= 0 || t.durationMs / 1000 == k.durationMs / 1000) k.durationMs else t.durationMs,
                channelId = k.channelId,
                author = t.author ?: k.author,
                addedAtMs = k.addedAtMs ?: t.addedAtMs,
            )
        }
        val l = nextLiked.map(::keep)
        val p = nextPlaylists.map { it.copy(tracks = it.tracks.map(::keep)) }
        if (l == liked && p == playlists) return false
        syncing = true
        try {
            liked = l
            playlists = p
            save()
        } finally {
            syncing = false
        }
        return true
    }

    /** True while a synced library is being written (not a change made here). */
    @Volatile private var syncing = false

    private fun noteRemoved(ids: Set<String>) {
        if (!syncing) com.nuvio.app.features.iptv.sync.LiveTvSyncLocal.noteRemoved(ids)
    }

    private fun save() {
        if (!syncing) com.nuvio.app.features.iptv.sync.LiveTvSyncLocal.noteChanged()
        runCatching {
            store.putString(
                Key,
                json.encodeToString(
                    MusicLibraryPayload(
                        liked = liked,
                        recents = recents,
                        playlists = playlists,
                    ),
                ),
            )
        }
    }
}
