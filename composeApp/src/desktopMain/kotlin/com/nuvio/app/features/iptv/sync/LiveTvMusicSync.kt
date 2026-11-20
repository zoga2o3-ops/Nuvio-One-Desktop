package com.nuvio.app.features.iptv.sync

import com.nuvio.app.features.iptv.sync.LiveTvMerge.str
import com.nuvio.app.features.youtube.MusicLibraryStore
import com.nuvio.app.features.youtube.MusicPlaylist
import com.nuvio.app.features.youtube.MusicTrack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull

/**
 * Liked Songs and Music playlists inside the Live TV setup sync, in the TV app's format
 * (MusicLibrarySync on the TV): "music_liked" (one entry per song, id = video id) and
 * "music_playlists" (one entry per playlist, songs inside), both merged item by item.
 *
 * A song is kept small: id, title "t", artist "a", length "d" in seconds; fields the TV adds
 * (album "al", ids) are kept from the shared copy. No thumbnails (rebuilt from the video id),
 * and nothing that only plays on this computer (local files, WebDAV, addon tracks).
 */
/** Where the Music library lives (swapped for a fake in tests: the real one is the person's library). */
internal interface MusicSyncLibrary {
    val liked: List<MusicTrack>
    val playlists: List<MusicPlaylist>
    fun replace(liked: List<MusicTrack>, playlists: List<MusicPlaylist>): Boolean
}

internal object LiveTvMusicSync {
    var library: MusicSyncLibrary = object : MusicSyncLibrary {
        override val liked get() = MusicLibraryStore.apply { ensureLoaded() }.liked
        override val playlists get() = MusicLibraryStore.apply { ensureLoaded() }.playlists
        override fun replace(liked: List<MusicTrack>, playlists: List<MusicPlaylist>) =
            MusicLibraryStore.replaceFromSync(liked, playlists)
    }

    const val KEY_LIKED = "music_liked"
    const val KEY_PLAYLISTS = "music_playlists"
    val KEYS = setOf(KEY_LIKED, KEY_PLAYLISTS)
    private val json = Json { ignoreUnknownKeys = true }

    /** The two entries for this computer's library, reusing the shared copy's text when nothing changed. */
    fun export(shadow: JsonObject?): Map<String, JsonElement> {
        val sharedSongs = HashMap<String, JsonObject>()
        val sharedLiked = LiveTvMerge.readSources((shadow?.get(KEY_LIKED) as? JsonObject)?.str("v"))
        sharedLiked.values.forEach { sharedSongs[it.str("id").orEmpty()] = it }
        val sharedLists = LiveTvMerge.readSources((shadow?.get(KEY_PLAYLISTS) as? JsonObject)?.str("v"))
        sharedLists.values.forEach { p ->
            (p["songs"] as? JsonArray)?.forEach { s -> (s as? JsonObject)?.let { sharedSongs.putIfAbsent(it.str("id").orEmpty(), it) } }
        }

        val liked = library.liked.filter { it.syncable() }
        val likedEntry = if (sameSongs(sharedLiked.values.toList(), liked)) shadow?.get(KEY_LIKED) else null
        val playlists = library.playlists
        val playlistsSame = sharedLists.size == playlists.size && playlists.all { p ->
            val s = sharedLists[p.id] ?: return@all false
            s.str("name") == p.name &&
                sameSongs((s["songs"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty(), p.tracks.filter { it.syncable() })
        } && sharedLists.keys.toList() == playlists.map { it.id }
        val playlistsEntry = if (playlistsSame) shadow?.get(KEY_PLAYLISTS) else null

        return mapOf(
            KEY_LIKED to (likedEntry ?: entry(JsonArray(liked.map { song(it, sharedSongs[it.videoId]) }))),
            KEY_PLAYLISTS to (playlistsEntry ?: entry(JsonArray(playlists.map { p ->
                val shared = sharedLists[p.id]
                JsonObject(
                    LinkedHashMap<String, JsonElement>(shared ?: emptyMap()).apply {
                        put("id", JsonPrimitive(p.id))
                        put("name", JsonPrimitive(p.name))
                        put("createdAt", JsonPrimitive(shared?.get("createdAt")?.let { (it as? JsonPrimitive)?.longOrNull } ?: p.createdAtMs))
                        put("songs", JsonArray(p.tracks.filter { it.syncable() }.map { song(it, sharedSongs[it.videoId]) }))
                    },
                )
            }))),
        )
    }

    fun hasMusic(data: JsonObject?): Boolean =
        data != null && KEYS.any { LiveTvMerge.readSources((data[it] as? JsonObject)?.str("v")).isNotEmpty() }

    /** Writes the shared library here, keeping this computer's own local tracks. True if anything changed. */
    fun import(data: JsonObject): Boolean {
        val likedRaw = (data[KEY_LIKED] as? JsonObject)?.str("v")
        val listsRaw = (data[KEY_PLAYLISTS] as? JsonObject)?.str("v")
        if (likedRaw == null && listsRaw == null) return false

        val liked = likedRaw?.let { raw ->
            val synced = LiveTvMerge.readSources(raw).values.mapNotNull(::track)
            val ids = synced.mapTo(HashSet()) { it.videoId }
            synced + library.liked.filter { !it.syncable() && it.videoId !in ids }
        } ?: library.liked

        val playlists = listsRaw?.let { raw ->
            val current = library.playlists.associateBy { it.id }
            LiveTvMerge.readSources(raw).values.mapNotNull { o ->
                val id = o.str("id") ?: return@mapNotNull null
                val songs = (o["songs"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::track) }.orEmpty()
                val ids = songs.mapTo(HashSet()) { it.videoId }
                val localOnly = current[id]?.tracks.orEmpty().filter { !it.syncable() && it.videoId !in ids }
                MusicPlaylist(
                    id = id,
                    name = o.str("name").orEmpty().ifBlank { "Playlist" },
                    tracks = songs + localOnly,
                    createdAtMs = (o["createdAt"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: current[id]?.createdAtMs ?: System.currentTimeMillis(),
                )
            }
        } ?: library.playlists

        return library.replace(liked, playlists)
    }

    private fun MusicTrack.syncable() =
        localUrl == null && videoId.length == 11 && videoId.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun sameSongs(shared: List<JsonObject>, tracks: List<MusicTrack>): Boolean =
        shared.size == tracks.size && shared.zip(tracks).all { (s, t) ->
            s.str("id") == t.videoId && s.str("t") == t.title && s.str("a").orEmpty() == t.author.orEmpty()
        }

    private fun song(t: MusicTrack, shared: JsonObject?): JsonObject {
        val out = LinkedHashMap<String, JsonElement>(shared ?: emptyMap())
        out["id"] = JsonPrimitive(t.videoId)
        out["t"] = JsonPrimitive(t.title)
        out["a"] = JsonPrimitive(t.author.orEmpty())
        val seconds = (t.durationMs / 1000).toInt()
        if (seconds > 0) out["d"] = JsonPrimitive(seconds)
        return JsonObject(out)
    }

    private fun track(o: JsonObject): MusicTrack? {
        val id = o.str("id")?.takeIf { it.isNotBlank() } ?: return null
        return MusicTrack(
            videoId = id,
            title = o.str("t").orEmpty().ifBlank { id },
            author = o.str("a")?.takeIf { it.isNotBlank() },
            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            durationMs = ((o["d"] as? JsonPrimitive)?.intOrNull ?: 0) * 1000L,
        )
    }

    private fun entry(list: JsonArray) = JsonObject(linkedMapOf("t" to JsonPrimitive("str"), "v" to JsonPrimitive(list.toString())))

    @Suppress("unused")
    private fun strings(e: JsonElement) = runCatching { e.jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }.getOrDefault(emptyList())
}
