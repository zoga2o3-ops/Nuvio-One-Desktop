package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class MusicTrackListen(
    val videoId: String,
    val title: String,
    val artist: String,
    val channelId: String? = null,
    val plays: Int = 1,
    val lastPlayedMs: Long = 0L,
)

@Serializable
internal data class MusicArtistListen(
    val normalized: String,
    val name: String,
    val plays: Int = 1,
    val lastPlayedMs: Long = 0L,
)

@Serializable
private data class MusicListeningPayload(
    val tracks: List<MusicTrackListen> = emptyList(),
    val artists: List<MusicArtistListen> = emptyList(),
)

/**
 * Local play history for the Music recommender.
 *
 * A track is counted after it has actually been listened to (30 seconds, or half of a shorter
 * track), not when it is merely selected, so skipping through a queue does not skew the taste
 * profile. Everything stays in a local properties file; only artist/title names are sent to
 * YouTube when the recommender searches for related music.
 */
internal object MusicListeningStore {
    private const val Key = "payload"
    private const val MaxTracks = 600
    private const val MaxArtists = 240
    private val store = DesktopStorage.store("nuvio_music_listening")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var tracks: List<MusicTrackListen> = emptyList()
    private var artists: List<MusicArtistListen> = emptyList()
    private var loaded = false

    var version by mutableStateOf(0)
        private set
    var totalPlays by mutableStateOf(0)
        private set
    var artistCount by mutableStateOf(0)
        private set
    var trackCount by mutableStateOf(0)
        private set

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val text = store.getString(Key) ?: return@runCatching
            val payload = json.decodeFromString<MusicListeningPayload>(text)
            tracks = payload.tracks
            artists = payload.artists
            recalculateStats()
        }
    }

    /** Records one real listen of [item]. Safe to call from the player's main thread. */
    fun record(item: YouTubeVideoItem) {
        ensureLoaded()
        val now = System.currentTimeMillis()
        val title = item.title.trim().ifBlank { return }
        val artist = cleanArtistName(item.author)
        val artistKey = normalizeArtistName(item.author)
        val existingTrack = tracks.firstOrNull { it.videoId == item.videoId }
        val updatedTrack = MusicTrackListen(
            videoId = item.videoId,
            title = title,
            artist = artist.ifBlank { "Unknown Artist" },
            channelId = item.channelId ?: existingTrack?.channelId,
            plays = (existingTrack?.plays ?: 0) + 1,
            lastPlayedMs = now,
        )
        tracks = (listOf(updatedTrack) + tracks.filterNot { it.videoId == item.videoId })
            .sortedWith(compareByDescending<MusicTrackListen> { it.plays }.thenByDescending { it.lastPlayedMs })
            .take(MaxTracks)

        if (artistKey.isNotBlank()) {
            val existingArtist = artists.firstOrNull { it.normalized == artistKey }
            val updatedArtist = MusicArtistListen(
                normalized = artistKey,
                name = artist.ifBlank { existingArtist?.name ?: artistKey },
                plays = (existingArtist?.plays ?: 0) + 1,
                lastPlayedMs = now,
            )
            artists = (listOf(updatedArtist) + artists.filterNot { it.normalized == artistKey })
                .sortedWith(compareByDescending<MusicArtistListen> { it.plays }.thenByDescending { it.lastPlayedMs })
                .take(MaxArtists)
        }
        recalculateStats()
        version++
        persist()
    }

    fun heardIds(): Set<String> {
        ensureLoaded()
        return tracks.mapTo(HashSet()) { it.videoId }
    }

    fun topArtists(limit: Int = 6): List<MusicArtistListen> {
        ensureLoaded()
        return artists.take(limit)
    }

    fun topTracks(limit: Int = 8): List<MusicTrackListen> {
        ensureLoaded()
        return tracks.take(limit)
    }

    fun clear() {
        ensureLoaded()
        tracks = emptyList()
        artists = emptyList()
        recalculateStats()
        version++
        runCatching { store.putString(Key, "") }
    }

    private fun recalculateStats() {
        totalPlays = tracks.sumOf { it.plays }
        trackCount = tracks.size
        artistCount = artists.size
    }

    private fun persist() {
        runCatching {
            store.putString(Key, json.encodeToString(MusicListeningPayload(tracks, artists)))
        }
    }
}

internal fun cleanArtistName(author: String?): String {
    var value = author.orEmpty().substringBefore(" • ").trim()
    if (value.isBlank()) return ""
    if (value.endsWith("VEVO", ignoreCase = true) && value.length > 4) {
        value = value.dropLast(4).trim()
    }
    val suffixes = listOf(
        " - Topic", " - topic", " VEVO", " vevo", " Official", " official",
        " Official Audio", " Official Video", " - Official",
    )
    var changed = true
    while (changed) {
        changed = false
        for (suffix in suffixes) {
            if (value.endsWith(suffix)) {
                value = value.removeSuffix(suffix).trim()
                changed = true
            }
        }
    }
    return value.ifBlank { author.orEmpty().substringBefore(" • ").trim() }
}

/** Artist matching key: lowercase, no channel suffixes, punctuation collapsed. */
internal fun normalizeArtistName(author: String?): String {
    var value = author.orEmpty().substringBefore(" • ").lowercase().trim()
    if (value.isBlank()) return ""
    value = value.removeSuffix(" - topic").removeSuffix(" topic").trim()
    if (value.endsWith("vevo")) value = value.removeSuffix("vevo").trim()
    value = value.removeSuffix(" official").removeSuffix(" official audio").removeSuffix(" official video").trim()
    value = value.replace(Regex("[^a-z0-9]+"), " ").replace(Regex("\\s+"), " ").trim()
    return if (value in setOf("unknown artist", "music", "youtube music", "various artists", "vevo")) {
        ""
    } else {
        value
    }
}
