package com.nuvio.app.features.youtube

import com.music.bitchord.data.model.SearchFilter
import com.nuvio.app.core.storage.DesktopStorage
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Release Radar": the new albums and singles, released since the last Friday, by the artists the user listens
 * to, as one curated playlist.
 *
 * Exact release dates come from MusicBrainz ([MusicBrainzClient]). Each new release is then found on YouTube
 * Music, and kept only when it's credited to that artist. The list is built once a week, on the first use after
 * each Friday, in the background, and saved to disk, so it stays the same all week. It shows on the Music home
 * page after "Made for you" once it's ready.
 */
internal object ReleaseRadar {
    private const val MaxArtists = 15
    private const val TracksPerAlbum = 3
    private const val MaxTracks = 40
    private const val FallbackDays = 14L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file by lazy { DesktopStorage.rootDir.resolve("release-radar.tsv") }

    @Volatile private var feedCache: YouTubeFeed? = null
    @Volatile private var cacheFriday = ""
    @Volatile private var refreshing = false

    /**
     * This week's Release Radar, or null while it is being built. Returns at once: the first call after a Friday
     * starts loading or building it in the background, and the home page is refreshed when that finishes.
     */
    fun feed(artistNames: List<String>): YouTubeFeed? {
        val friday = lastFriday()
        if (cacheFriday == friday.toString()) return feedCache
        if (!refreshing && artistNames.isNotEmpty()) {
            refreshing = true
            scope.launch {
                try {
                    val saved = loadSaved(friday.toString())
                    val items = saved ?: buildWithFallback(artistNames, friday).also { save(friday.toString(), it) }
                    OneTubeDebugLog.line("release radar ${if (saved != null) "loaded" else "built"}: ${items.size} tracks since $friday")
                    feedCache = if (items.isEmpty()) null else YouTubeFeed("Release Radar", items)
                    cacheFriday = friday.toString()
                    MusicRecommendations.invalidate()
                } catch (error: Throwable) {
                    OneTubeDebugLog.line("release radar failed: ${error.message}")
                } finally {
                    refreshing = false
                }
            }
        }
        return null
    }

    /** This week's releases; when there are none, the last two weeks' instead, so the playlist never sits empty. */
    private suspend fun buildWithFallback(artistNames: List<String>, friday: LocalDate): List<YouTubeVideoItem> {
        val week = build(artistNames, friday)
        if (week.isNotEmpty()) return week
        OneTubeDebugLog.line("release radar: nothing since $friday, using the last $FallbackDays days")
        return build(artistNames, LocalDate.now().minusDays(FallbackDays))
    }

    /** The releases since [since] by the artists, found on YouTube Music. */
    private suspend fun build(artistNames: List<String>, since: LocalDate): List<YouTubeVideoItem> {
        val found = mutableListOf<YouTubeVideoItem>()
        var resolved = 0
        for (name in artistNames.take(MaxArtists)) {
            // MusicBrainz matches by name first; Last.fm's id covers names MusicBrainz won't match.
            val mbid = MusicBrainzClient.artistId(name) ?: LastFmClient.musicBrainzId(name)
            if (mbid == null) {
                OneTubeDebugLog.line("release radar: no MusicBrainz artist for $name")
                continue
            }
            resolved++
            val fresh = MusicBrainzClient.discography(mbid).filter { isSince(it.date, since) }
            for (release in fresh) {
                found += tracksFor(release.title, name)
                if (found.size >= MaxTracks) return found.distinctBy { it.videoId }
            }
        }
        OneTubeDebugLog.line("release radar: $resolved of ${minOf(artistNames.size, MaxArtists)} artists found on MusicBrainz")
        return found.distinctBy { it.videoId }
    }

    /** The tracks on YouTube Music for one release: an album's first tracks, or a single's track. */
    private suspend fun tracksFor(title: String, artist: String): List<YouTubeVideoItem> {
        val wanted = normalise(title)
        val candidates = runCatching { MusicBitChordRepository.search("$title $artist", SearchFilter.ALL) }
            .getOrNull().orEmpty()
            .flatMap { section -> section.items.mapIndexed { index, item -> item to section.labels.getOrNull(index) } }
        val album = candidates.firstOrNull { (item, label) ->
            label == "Album" && normalise(item.title) == wanted && MusicBitChordRepository.creditsArtist(item.author, artist)
        }?.first
        if (album != null) {
            return MusicBitChordRepository.albumSongs(album.videoId, album.title)
                .filter { MusicBitChordRepository.creditsArtist(it.author, artist) }
                .take(TracksPerAlbum)
        }
        val single = candidates.firstOrNull { (item, label) ->
            label != "Album" &&
                MusicBitChordRepository.creditsArtist(item.author, artist) &&
                normalise(item.title).contains(wanted)
        }?.first
        return listOfNotNull(single)
    }

    /** A MusicBrainz date (YYYY-MM-DD, or a shorter one counted from its start) on or after [since]. */
    private fun isSince(date: String, since: LocalDate): Boolean {
        if (date.length < 10) return false
        val parsed = runCatching { LocalDate.parse(date.substring(0, 10)) }.getOrNull() ?: return false
        return !parsed.isBefore(since)
    }

    /** The most recent Friday, today included. */
    private fun lastFriday(): LocalDate {
        var day = LocalDate.now()
        while (day.dayOfWeek != DayOfWeek.FRIDAY) day = day.minusDays(1)
        return day
    }

    private fun normalise(text: String): String = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")

    /** The saved list, if it was built for [friday]; null when there isn't one for that Friday. */
    private fun loadSaved(friday: String): List<YouTubeVideoItem>? = runCatching {
        val lines = Files.readAllLines(file)
        if (lines.firstOrNull() != "friday=$friday") return@runCatching null
        lines.drop(1).mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size < 5) {
                null
            } else {
                YouTubeVideoItem(
                    videoId = fields[0],
                    title = fields[1],
                    author = fields[2].ifBlank { null },
                    thumbnailUrl = fields[3].ifBlank { null },
                    durationMs = fields[4].toLongOrNull() ?: 0L,
                    isLive = false,
                    kind = YouTubeItemKind.VIDEO,
                    isMusic = true,
                )
            }
        }
    }.getOrNull()

    private fun save(friday: String, items: List<YouTubeVideoItem>) {
        // An empty build is usually a failed lookup, so it isn't kept: the next start tries again.
        if (items.isEmpty()) return
        runCatching {
            val rows = items.map { item ->
                listOf(item.videoId, item.title, item.author.orEmpty(), item.thumbnailUrl.orEmpty(), item.durationMs.toString())
                    .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
            }
            Files.write(file, listOf("friday=$friday") + rows)
        }
    }
}
