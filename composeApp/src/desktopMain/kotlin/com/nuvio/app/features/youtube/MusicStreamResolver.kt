package com.nuvio.app.features.youtube

import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds a stream Music can actually play, quickly.
 *
 * YouTube hands most songs over in well under a second, but for some music
 * videos it returns no audio stream at all: the engine then retries for up to a
 * minute and falls back to a combined video stream that answers 403. For
 * those, the same song's YouTube Music audio version (the "song" rather than the
 * "video") almost always plays, so that's tried next.
 *
 * Answers are kept for a while and the next track in the queue is looked up
 * ahead of time, so skipping forward starts at once.
 */
internal object MusicStreamResolver {
    // The engine handles one lookup at a time, so a stuck one holds up everything
    // after it; waiting it out beats abandoning it.
    private const val DirectTimeoutMs = 30_000L
    private const val CacheMs = 30L * 60 * 1000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ConcurrentHashMap<String, Pair<Long, YouTubePlayback>>()
    private val inflight = ConcurrentHashMap<String, Deferred<YouTubePlayback>>()
    @Volatile private var warmed = false

    /** Prepares YouTube's player decoder in the background so the first song doesn't wait for it. */
    fun warmUp() {
        if (warmed) return
        warmed = true
        scope.launch { runCatching { YouTubeEngine.warmPlaybackData() } }
    }

    // Background lookups must not hold up the track the user picked: the engine runs one lookup at a time.
    // So at most one prefetch runs, and none starts while a user lookup is waiting.
    private val userLookups = java.util.concurrent.atomic.AtomicInteger(0)
    private val prefetches = java.util.concurrent.atomic.AtomicInteger(0)

    /** Looks the track up in the background; [resolve] then answers from the cache. */
    fun prefetch(item: YouTubeVideoItem, maxAudioKbps: Int?) {
        if (item.localUrl != null || cached(item.videoId) != null) return
        if (userLookups.get() > 0 || prefetches.get() > 0) return
        prefetches.incrementAndGet()
        scope.launch {
            try {
                lookup(item, maxAudioKbps, skipDirect = false).await()
            } catch (_: Throwable) {
            } finally {
                prefetches.decrementAndGet()
            }
        }
    }

    /**
     * Background playlist pages wait here while a track the user picked is being looked up, so the
     * track isn't queued behind the rest of the playlist. Capped so a stuck lookup can't stall a playlist.
     */
    suspend fun waitForTrackLookups() {
        var waited = 0L
        while (userLookups.get() > 0 && waited < 20_000L) {
            delay(100L)
            waited += 100L
        }
    }

    /** Forget a stream the player couldn't open, so the next try looks again. */
    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    suspend fun resolve(item: YouTubeVideoItem, maxAudioKbps: Int?, skipDirect: Boolean = false): YouTubePlayback {
        if (!skipDirect) cached(item.videoId)?.let { return it }
        userLookups.incrementAndGet()
        try {
            return lookup(item, maxAudioKbps, skipDirect).await()
        } finally {
            userLookups.decrementAndGet()
        }
    }

    private fun lookup(item: YouTubeVideoItem, maxAudioKbps: Int?, skipDirect: Boolean): Deferred<YouTubePlayback> {
        val key = item.videoId + if (skipDirect) "#alt" else ""
        return inflight.computeIfAbsent(key) {
            scope.async {
                try {
                    find(item, maxAudioKbps, skipDirect).also {
                        cache[item.videoId] = System.currentTimeMillis() to it
                    }
                } finally {
                    inflight.remove(key)
                }
            }
        }
    }

    private fun cached(videoId: String): YouTubePlayback? {
        val (at, playback) = cache[videoId] ?: return null
        if (System.currentTimeMillis() - at > CacheMs) {
            cache.remove(videoId)
            return null
        }
        return playback
    }

    private suspend fun find(item: YouTubeVideoItem, maxAudioKbps: Int?, skipDirect: Boolean): YouTubePlayback {
        var direct = if (skipDirect) null else direct(item.videoId, item, maxAudioKbps)
        if (direct != null && isAudioStream(direct)) return direct

        // YouTube answered but without audio. That's usually a passing hiccup,
        // so ask once more. (Switching the engine's client was tried and made
        // things worse: it moves every later track off the client that works.)
        if (!skipDirect && direct != null) {
            val again = direct(item.videoId, item, maxAudioKbps)
            if (again != null && isAudioStream(again)) return again
            direct = again ?: direct
        }

        // No audio stream for this upload: try the YouTube Music song version.
        songVersion(item, maxAudioKbps)?.let { return it }

        // Keep the original upload if it opens. This is the 0.1.200 behaviour
        // and must stay ahead of the extra network search: a playable video
        // stream should start immediately, not wait for alternate-upload
        // probes that can each take half a minute.
        if (direct != null && opens(direct)) return direct

        // A region-blocked/removed upload often has another playable upload.
        alternateUpload(item, maxAudioKbps)?.let { return it }

        error("This track isn't available to stream right now.")
    }

    /** Another upload of the same song, chosen by title and duration. */
    private suspend fun alternateUpload(item: YouTubeVideoItem, maxAudioKbps: Int?): YouTubePlayback? {
        val query = listOf(item.title, cleanArtistName(item.author))
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isBlank()) return null
        val results = withTimeoutOrNull(10_000L) {
            runCatching { YouTubeEngine.search(query, SearchOptions.TYPE_VIDEO) }
                .getOrDefault(emptyList())
        }.orEmpty()
        val candidates = results.asSequence()
            .filter { it.kind == YouTubeItemKind.VIDEO && !it.isLive && !it.isShorts }
            .filter { it.videoId != item.videoId }
            .filter { it.durationMs in 20_000L..900_000L }
            .filter { MusicRecommendations.sameSong(item, it) }
            .sortedBy { candidate ->
                if (item.durationMs > 0 && candidate.durationMs > 0) {
                    abs(item.durationMs - candidate.durationMs)
                } else {
                    Long.MAX_VALUE
                }
            }
            .take(4)
            .toList()
        for (candidate in candidates) {
            val playback = direct(candidate.videoId, item, maxAudioKbps) ?: continue
            if (isAudioStream(playback) || opens(playback)) return playback
        }
        return null
    }

    /** The engine's own lookup, cut short when YouTube keeps it waiting. */
    private suspend fun direct(videoId: String, item: YouTubeVideoItem, maxAudioKbps: Int?): YouTubePlayback? {
        val lookup = scope.async {
            YouTubeEngine.resolvePlayback(
                item = if (item.videoId == videoId) item else item.copy(videoId = videoId),
                music = true,
                maxAudioKbps = maxAudioKbps,
            )
        }
        // The engine call can't be interrupted; stop waiting for it instead.
        return withTimeoutOrNull(DirectTimeoutMs) { runCatching { lookup.await() }.getOrNull() }
    }

    private suspend fun songVersion(item: YouTubeVideoItem, maxAudioKbps: Int?): YouTubePlayback? {
        val song = runCatching {
            MusicTrackInfoStore.searchCatalogueSong(item, splitArtistNames(item.author).firstOrNull())
        }.getOrNull() ?: return null
        if (song.videoId.isBlank() || song.videoId == item.videoId) return null
        val playback = direct(song.videoId, item, maxAudioKbps) ?: return null
        return playback.takeIf { isAudioStream(it) || opens(it) }
    }

    /** An audio-only adaptive stream: these are what play reliably for music. */
    private fun isAudioStream(playback: YouTubePlayback): Boolean {
        val url = playback.videoUrl
        if (url.contains("mime=audio")) return true
        val itag = Regex("[?&]itag=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()
        return itag in AudioItags
    }

    /** A tiny ranged request: does the server hand the stream over? */
    private suspend fun opens(playback: YouTubePlayback): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(playback.videoUrl).openConnection() as HttpURLConnection
            playback.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.setRequestProperty("Range", "bytes=0-1023")
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000
            try {
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    private val AudioItags = setOf(139, 140, 141, 171, 172, 249, 250, 251, 256, 258, 327, 338, 380, 599, 600, 773, 774)
}
