package com.nuvio.app.features.youtube

import androidx.compose.runtime.mutableStateMapOf
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class MusicTrackArtist(val name: String, val id: String? = null)

@Serializable
internal data class MusicTrackInfo(
    val album: String? = null,
    val albumId: String? = null,
    val artists: List<MusicTrackArtist> = emptyList(),
)

/**
 * Album and per-artist credits for tracks, which YouTube playlist rows don't
 * carry. Looked up from YouTube Music's watch queue (anonymous BitChord, no
 * cookie) one track at a time, only for rows on screen, and cached on disk so
 * every track is looked up once.
 */
internal object MusicTrackInfoStore {
    private val store = DesktopStorage.store("nuvio_music_track_info")
    private val json = Json { ignoreUnknownKeys = true }
    private val visibleGate = Semaphore(3)
    private val prefetchGate = Semaphore(1)

    /**
     * Circuit breaker: when YouTube Music refuses a request (403/429, its
     * "too many requests from you" answers), every lookup pauses for a while
     * instead of retrying into a longer block.
     */
    @Volatile
    private var pausedUntilMs = 0L
    private const val PauseMs = 5 * 60_000L

    val isPaused: Boolean get() = System.currentTimeMillis() < pausedUntilMs

    internal fun noteFailure(error: Throwable) {
        val text = error.toString()
        if ("403" in text || "429" in text || "Too Many Requests" in text) {
            pausedUntilMs = System.currentTimeMillis() + PauseMs
        }
    }
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private var loaded = false

    val info = mutableStateMapOf<String, MusicTrackInfo>()

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = store.getString(Key) ?: return
        runCatching { json.decodeFromString<Map<String, MusicTrackInfo>>(raw) }
            .onSuccess { info.putAll(it) }
    }

    /** Records info already known from another source (e.g. an album's track list). */
    fun remember(videoId: String, entry: MusicTrackInfo) {
        ensureLoaded()
        if (videoId.isBlank()) return
        val existing = info[videoId]
        if (existing == null || (existing.album == null && entry.album != null)) {
            info[videoId] = if (existing != null && entry.artists.isEmpty()) entry.copy(artists = existing.artists) else entry
            scheduleSave()
        }
    }

    /**
     * Fetches info for [item] unless cached. Cancellable: call it from the
     * row's effect. [priority] rows (on screen) get their own wider lane so a
     * background prefetch of the rest of the playlist never delays them.
     */
    suspend fun load(item: YouTubeVideoItem, priority: Boolean = true) {
        ensureLoaded()
        val id = item.videoId
        if (id.isBlank() || item.localUrl != null || id in info || isPaused) return
        (if (priority) visibleGate else prefetchGate).withPermit {
            if (id in info || isPaused) return
            val entry = lookup(item) ?: return
            info[id] = entry
            scheduleSave()
        }
    }

    /**
     * Watch-queue credits and the catalogue-song search run side by side; the
     * search is only waited for when the queue has no album (video uploads).
     */
    private suspend fun lookup(item: YouTubeVideoItem): MusicTrackInfo? = coroutineScope {
        val id = item.videoId
        val queue = async(Dispatchers.IO) {
            withTimeoutOrNull(12_000L) {
                // preferAudio: a music-video upload has no album, but its
                // paired audio track (which the queue swaps in) may.
                runCatching { InnertubeParser.parseWatchQueue(Innertube.next(id), preferAudio = true) }
                    .onFailure(::noteFailure).getOrNull()
            }
        }
        val search = async(Dispatchers.IO) { searchCatalogueSong(item, splitArtistNames(item.author).firstOrNull()) }
        val songs = queue.await()
        val song = songs?.firstOrNull { it.videoId == id } ?: songs?.firstOrNull()
        var artists = song?.artists.orEmpty()
            .filter { it.name.isNotBlank() }
            .map { MusicTrackArtist(it.name.trim(), it.browseId?.takeIf { b -> b.isNotBlank() }) }
            .ifEmpty {
                song?.artist?.takeIf { it.isNotBlank() }
                    ?.let { listOf(MusicTrackArtist(it.trim(), song.artistId)) }
                    .orEmpty()
            }
        var album = song?.albumName?.trim()?.takeIf { it.isNotBlank() }
        var albumId = song?.albumId?.takeIf { it.isNotBlank() }
        if (album != null) {
            search.cancel()
        } else {
            search.await()?.let { match ->
                album = match.albumName?.trim()?.takeIf { it.isNotBlank() }
                albumId = match.albumId?.takeIf { it.isNotBlank() }
                val matchArtists = match.artists.filter { it.name.isNotBlank() }
                    .map { MusicTrackArtist(it.name.trim(), it.browseId?.takeIf { b -> b.isNotBlank() }) }
                if (matchArtists.size > artists.size) artists = matchArtists
            }
        }
        // Nothing came back at all (offline, timeout): don't cache, try again later.
        if (song == null && album == null) return@coroutineScope null
        MusicTrackInfo(album = album, albumId = albumId, artists = artists)
    }

    internal suspend fun searchCatalogueSong(item: YouTubeVideoItem, leadArtist: String?): com.music.bitchord.data.model.Song? {
        val artist = leadArtist?.takeIf { it.isNotBlank() } ?: splitArtistNames(item.author).firstOrNull() ?: return null
        val title = cleanSongTitle(item.title, artist)
        if (title.isBlank()) return null
        val results = withContext(Dispatchers.IO) {
            withTimeoutOrNull(15_000L) {
                runCatching {
                    InnertubeParser.parseSearchSongs(
                        Innertube.search("$title $artist", com.music.bitchord.data.model.SearchFilter.SONGS.params),
                    )
                }.onFailure(::noteFailure).getOrNull()
            }
        }.orEmpty()
        val wantArtist = normalizeArtistName(artist)
        val wantTitle = title.lowercase()
        return results.take(8).firstOrNull { song ->
            val songArtists = (song.artists.map { it.name } + song.artist).map(::normalizeArtistName)
            songArtists.any { it.isNotBlank() && (it.contains(wantArtist) || wantArtist.contains(it)) } &&
                song.title.lowercase().let { it.contains(wantTitle) || wantTitle.contains(it) }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(2_000L)
            val snapshot = HashMap(info)
            store.putString(Key, json.encodeToString(snapshot))
        }
    }

    private const val Key = "v3"
}

/**
 * "Post Malone - rockstar (Official Music Video) ft. 21 Savage" -> "rockstar":
 * drops the "Artist - " prefix, bracketed upload tags and featuring credits.
 */
internal fun cleanSongTitle(title: String, artist: String?): String {
    var t = title
    if (artist != null) {
        val dash = Regex("""^\s*(.+?)\s+[-â€“â€”]\s+(.+)$""").find(t)
        if (dash != null && normalizeArtistName(dash.groupValues[1]).let { a ->
                val want = normalizeArtistName(artist)
                a.contains(want) || want.contains(a)
            }
        ) {
            t = dash.groupValues[2]
        }
    }
    t = t.replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
    t = t.replace(Regex("""\s+(?:ft\.?|feat\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("""["'â€œâ€â€˜â€™]"""), "")
    return t.replace(Regex("""\s+"""), " ").trim()
}

/** Splits a byline like "A, B & C feat. D" into separate artist names. */
internal fun splitArtistNames(byline: String?): List<String> {
    val clean = byline?.substringBefore(" • ")?.trim().orEmpty()
    if (clean.isBlank()) return emptyList()
    return clean.split(Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|\band\b)\s*""", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}
