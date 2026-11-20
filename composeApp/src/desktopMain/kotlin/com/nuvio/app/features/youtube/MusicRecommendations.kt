package com.nuvio.app.features.youtube

import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.exp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Builds a local-first "Made for you" feed from the user's real listening history and library.
 *
 * The recommender combines four signals:
 *  - play counts and recency per artist (from [MusicListeningStore]),
 *  - liked tracks and recent plays,
 *  - every device-local playlist, so a listener who saves music but has not
 *    played much yet still gets a taste profile,
 *  - YouTube Music's own shelves as a discovery pool.
 *
 * It then adds a small number of artist-radio and track-radio searches, ranks every candidate by
 * artist affinity, title overlap, duration fit and novelty, and turns the ranked list into a main
 * "Made for you" row plus "Because you like …", "More like …" and playlist rows.
 */
internal object MusicRecommendations {
    private const val CacheTtlMs = 10 * 60_000L

    private data class ArtistSeed(val normalized: String, val name: String, val weight: Double)
    private data class TrackSeed(
        val videoId: String,
        val title: String,
        val artist: String,
        val channelId: String?,
        val weight: Double,
    )

    private data class Source(
        val items: List<YouTubeVideoItem>,
        val artist: String? = null,
        val track: String? = null,
        val boost: Double = 0.0,
    )

    private data class Candidate(
        val item: YouTubeVideoItem,
        val sourceArtist: String? = null,
        val sourceTrack: String? = null,
        val score: Double = 0.0,
    )

    private var cache: List<YouTubeFeed> = emptyList()
    private var cacheKey: String = ""
    private var cacheAt: Long = 0L

    var invalidationVersion by mutableStateOf(0)
        private set

    fun invalidate() {
        cache = emptyList()
        cacheKey = ""
        cacheAt = 0L
        invalidationVersion++
    }

    suspend fun build(
        feeds: List<YouTubeFeed>,
        recents: List<YouTubeVideoItem>,
        liked: List<YouTubeVideoItem>,
        playlists: List<MusicPlaylist> = emptyList(),
        force: Boolean = false,
    ): List<YouTubeFeed> = withContext(Dispatchers.IO) {
        MusicPlaybackSettings.ensureLoaded()
        if (!MusicPlaybackSettings.personalizedRecommendations) return@withContext emptyList()
        MusicListeningStore.ensureLoaded()
        val artists = artistSeeds(recents, liked, playlists)
        if (artists.isEmpty()) return@withContext emptyList()
        // This week's new releases by the listener's artists; built in the background, null until it's ready.
        val radar = ReleaseRadar.feed(artists.take(25).map { it.name })
        val tracks = trackSeeds(recents, liked, playlists)
        val key = artists.take(4).joinToString("|") { it.normalized } +
            "#" + tracks.take(3).joinToString("|") { it.videoId } +
            "#" + libraryKey(liked, playlists) +
            "#" + feeds.size
        if (!force && key == cacheKey && System.currentTimeMillis() - cacheAt < CacheTtlMs) {
            return@withContext cache
        }

        val heard = buildSet {
            addAll(MusicListeningStore.heardIds())
            recents.forEach { add(it.videoId) }
            liked.forEach { add(it.videoId) }
            playlists.forEach { playlist -> playlist.tracks.forEach { add(it.videoId) } }
        }
        val (lastFm, similarKeys) = lastFmSources(artists)
        val sources = collectSources(feeds, artists, tracks) + lastFm
        val ranked = rank(sources, artists, tracks, heard)
        if (ranked.isEmpty()) return@withContext emptyList()

        val main = mainMix(ranked)
        val used = main.mapTo(HashSet()) { it.item.videoId }
        val favorites = favoriteArtistKeys(liked, playlists)
        val playlistFeed = playlistMixFeed(playlists, ranked, used)
        playlistFeed?.items?.forEach { used += it.videoId }
        val artistFeeds = artists.take(2).mapNotNull { seed ->
            val items = ranked.asSequence()
                .filter { it.item.videoId !in used }
                .filter {
                    normalizeArtistName(it.item.author) == seed.normalized ||
                        it.sourceArtist == seed.normalized
                }
                .map { it.item }
                .distinctBy { it.videoId }
                .take(8)
                .toList()
            if (items.size >= 3) {
                used += items.map { it.videoId }
                val title = if (seed.normalized in favorites) {
                    "Because you like ${seed.name}"
                } else {
                    "Because you listened to ${seed.name}"
                }
                YouTubeFeed(title, items)
            } else {
                null
            }
        }
        // Last.fm's similar artists: songs by artists the listener has not played yet, but Last.fm links to their taste.
        val similarFeed = if (similarKeys.isEmpty()) {
            null
        } else {
            ranked.asSequence()
                .filter { it.item.videoId !in used && it.sourceArtist in similarKeys }
                .map { it.item }
                .distinctBy { it.videoId }
                .take(10)
                .toList()
                .takeIf { it.size >= 3 }
                ?.also { items -> used += items.map { it.videoId } }
                ?.let { YouTubeFeed("Similar to your artists", it) }
        }
        val trackFeed = tracks.firstOrNull()?.let { seed ->
            val items = ranked.asSequence()
                .filter { it.sourceTrack == seed.videoId && it.item.videoId !in used }
                .map { it.item }
                .distinctBy { it.videoId }
                .take(8)
                .toList()
            if (items.size >= 3) YouTubeFeed("More like ${seed.title}", items) else null
        }
        val result = buildList {
            if (main.isNotEmpty()) add(YouTubeFeed("Made for you", main.map { it.item }))
            radar?.let(::add)
            playlistFeed?.let(::add)
            addAll(artistFeeds)
            similarFeed?.let(::add)
            trackFeed?.let(::add)
        }
        cache = result
        cacheKey = key
        cacheAt = System.currentTimeMillis()
        result
    }

    private suspend fun collectSources(
        feeds: List<YouTubeFeed>,
        artists: List<ArtistSeed>,
        tracks: List<TrackSeed>,
    ): List<Source> = coroutineScope {
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<Source>>()
        jobs += async { Source(feeds.flatMap { it.items }, boost = 0.2) }
        artists.take(2).forEach { seed ->
            jobs += async {
                Source(radioTracks(seed.name), artist = seed.normalized, boost = 1.8)
            }
            jobs += async {
                Source(search("${seed.name} songs"), artist = seed.normalized, boost = 1.0)
            }
        }
        tracks.take(3).forEach { seed ->
            jobs += async {
                val query = listOf(seed.title, seed.artist)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                Source(search(query), track = seed.videoId, boost = 1.3)
            }
        }
        jobs.awaitAll()
    }

    /**
     * Last.fm's view of the listener's top two artists: a few of their most-played tracks, and a few artists
     * Last.fm finds similar. Returns the sources, and the similar artists' normalized names. Both are empty
     * without a Last.fm key, so the home page is unchanged.
     */
    private suspend fun lastFmSources(artists: List<ArtistSeed>): Pair<List<Source>, Set<String>> =
        coroutineScope {
            val similarKeys = mutableSetOf<String>()
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<Source>>()
            artists.take(2).forEach { seed ->
                LastFmClient.topTracks(seed.name, 3).forEach { track ->
                    jobs += async {
                        Source(search("${track.title} ${seed.name}"), artist = seed.normalized, boost = 1.2)
                    }
                }
                LastFmClient.similarArtists(seed.name, 3).forEach { similar ->
                    val normalized = normalizeArtistName(similar)
                    if (normalized.isBlank() || !similarKeys.add(normalized)) return@forEach
                    jobs += async {
                        Source(search("$similar songs"), artist = normalized, boost = 0.8)
                    }
                }
            }
            jobs.awaitAll() to similarKeys
        }

    private suspend fun search(query: String): List<YouTubeVideoItem> =
        withTimeoutOrNull(8_000L) {
            runCatching { YouTubeEngine.search(query, SearchOptions.TYPE_VIDEO) }.getOrDefault(emptyList())
        }.orEmpty()
            .filter { it.kind == YouTubeItemKind.VIDEO && !it.isLive && !it.isShorts }
            .filter { it.durationMs in 20_000L..900_000L }

    private suspend fun radioTracks(artist: String): List<YouTubeVideoItem> {
        val playlist = withTimeoutOrNull(8_000L) {
            runCatching {
                YouTubeEngine.search("$artist radio", SearchOptions.TYPE_PLAYLIST)
            }.getOrDefault(emptyList())
        }.orEmpty().firstOrNull {
            it.kind == YouTubeItemKind.PLAYLIST && (it.playlistId != null || it.videoId.isNotBlank())
        } ?: return emptyList()
        val id = playlist.playlistId ?: playlist.videoId
        return withTimeoutOrNull(12_000L) {
            runCatching { YouTubeEngine.playlist(id).items }.getOrDefault(emptyList())
        }.orEmpty()
            .filter { it.kind == YouTubeItemKind.VIDEO && !it.isLive && !it.isShorts }
            .filter { it.durationMs in 20_000L..900_000L }
    }

    private fun rank(
        sources: List<Source>,
        artists: List<ArtistSeed>,
        tracks: List<TrackSeed>,
        heard: Set<String>,
    ): List<Candidate> {
        val weights = artists.associate { it.normalized to it.weight }
        val artistKeys = weights.keys
        val titleTokens = tracks.flatMap { significantTitleTokens(it.title) }.toSet()
        val candidates = LinkedHashMap<String, Candidate>()
        sources.forEach { source ->
            source.items.forEach { item ->
                if (item.kind != YouTubeItemKind.VIDEO || item.videoId in heard || item.isLive) {
                    return@forEach
                }
                if (isVariant(item.title) || looksLikeTalk(item.title)) return@forEach
                val normalizedArtist = normalizeArtistName(item.author)
                var score = (weights[normalizedArtist] ?: 0.0).coerceAtMost(40.0) * 0.35
                if (normalizedArtist in artistKeys) score += 4.0
                score += significantTitleTokens(item.title).count { it in titleTokens } * 1.3
                if (source.artist != null) score += 2.2 + source.boost
                if (source.track != null) score += 1.8 + source.boost
                score += durationFit(item.durationMs)
                if (item.thumbnailUrl != null) score += 0.3
                // A candidate with no artist affinity, no title overlap and no
                // seed behind it is filler from a generic shelf, not a pick.
                if (score < 2.5) return@forEach
                val candidate = Candidate(
                    item = item.copy(
                        author = cleanArtistName(item.author).ifBlank { item.author },
                        isMusic = true,
                    ),
                    sourceArtist = source.artist,
                    sourceTrack = source.track,
                    score = score,
                )
                val existing = candidates[item.videoId]
                if (existing == null || score > existing.score) {
                    candidates[item.videoId] = candidate
                }
            }
        }
        return bestBySong(candidates.values.sortedByDescending { it.score })
    }

    private fun bestBySong(ranked: List<Candidate>): List<Candidate> {
        val best = mutableListOf<Candidate>()
        ranked.forEach { candidate ->
            if (songKey(candidate.item).isBlank()) return@forEach
            val index = best.indexOfFirst { sameSong(it.item, candidate.item) }
            if (index < 0) {
                best += candidate
            } else if (candidate.score > best[index].score) {
                best[index] = candidate
            }
        }
        return best.sortedByDescending { it.score }
    }

    /**
     * True when two results are the same song under different uploads. YouTube
     * credits lyric channels, radio uploads and re-uploads with different
     * author names, so the artist cannot be part of this check; a distinctive
     * title overlap is what identifies the song. Single-word titles still use
     * artist + title so two different songs called "Intro" stay apart.
     */
    internal fun sameSong(left: YouTubeVideoItem, right: YouTubeVideoItem): Boolean {
        val a = significantTitleTokens(left.title)
        val b = significantTitleTokens(right.title)
        if (a.size < 2 || b.size < 2) return songKey(left) == songKey(right)
        val shared = a.count { it in b }
        return shared.toDouble() / minOf(a.size, b.size) >= 0.6
    }

    internal fun songKey(item: YouTubeVideoItem): String {
        val tokens = significantTitleTokens(item.title)
        val titleKey = if (tokens.isEmpty()) {
            item.title.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        } else {
            tokens.sorted().joinToString(" ")
        }
        if (titleKey.isBlank()) return ""
        val artist = normalizeArtistName(item.author)
        // A distinctive multi-word title identifies the song even when uploads
        // credit different channels — lyric videos, radio uploads and covers of
        // "Get Lucky" would otherwise fill a whole row as separate songs.
        return if (tokens.size >= 2) titleKey else "$artist|$titleKey"
    }

    internal fun isVariant(title: String): Boolean {
        val lower = title.lowercase()
        return listOf(
            "cover", "karaoke", "instrumental", "nightcore", "slowed", "reverb",
            "8d audio", "sped up", "tribute", "made famous", "in the style of",
            "backing track", "live at", "live from", "live on", "remix",
            "choreography", "dance studio", "guitar lesson", "how to play",
            "tiktok", "fortnite", "mashup", "nonstop", "medley", "compilation",
            "party mix", "mega mix", "dj mix", "playlist",
        ).any { it in lower }
    }

    private fun mainMix(ranked: List<Candidate>): List<Candidate> {
        val selected = mutableListOf<Candidate>()
        val perArtist = mutableMapOf<String, Int>()
        for (candidate in ranked) {
            if (selected.size >= 14) break
            val artist = normalizeArtistName(candidate.item.author)
            val count = perArtist[artist] ?: 0
            if (artist.isNotBlank() && count >= 3) continue
            perArtist[artist] = count + 1
            selected += candidate
        }
        if (selected.size < 10) {
            for (candidate in ranked) {
                if (selected.size >= 10) break
                if (selected.none { it.item.videoId == candidate.item.videoId }) selected += candidate
            }
        }
        return selected
    }

    private fun artistSeeds(
        recents: List<YouTubeVideoItem>,
        liked: List<YouTubeVideoItem>,
        playlists: List<MusicPlaylist>,
    ): List<ArtistSeed> {
        val now = System.currentTimeMillis()
        val weights = mutableMapOf<String, Double>()
        val names = mutableMapOf<String, String>()
        MusicListeningStore.topArtists(12).forEach { stat ->
            val days = (now - stat.lastPlayedMs).coerceAtLeast(0L) / 86_400_000.0
            val recency = 1.0 + 2.0 * exp(-days / 14.0)
            weights[stat.normalized] = (weights[stat.normalized] ?: 0.0) + stat.plays * recency
            names.putIfAbsent(stat.normalized, stat.name)
        }
        recents.forEachIndexed { index, item ->
            val normalized = normalizeArtistName(item.author)
            if (normalized.isNotBlank()) {
                weights[normalized] = (weights[normalized] ?: 0.0) + 1.2 / (1.0 + index * 0.25)
                names.putIfAbsent(normalized, cleanArtistName(item.author))
            }
        }
        liked.groupBy { normalizeArtistName(it.author) }.forEach { (normalized, items) ->
            if (normalized.isNotBlank()) {
                weights[normalized] = (weights[normalized] ?: 0.0) + minOf(items.size, 3) * 1.6
                names.putIfAbsent(normalized, cleanArtistName(items.first().author))
            }
        }
        // User playlists are the strongest hand-written signal available on
        // desktop: every distinct playlist that contains an artist adds a
        // bonus, and its tracks add a smaller share each.
        playlists.forEach { playlist ->
            val perPlaylist = mutableMapOf<String, Int>()
            playlist.tracks.forEach { track ->
                val normalized = normalizeArtistName(track.author)
                if (normalized.isNotBlank()) {
                    perPlaylist[normalized] = (perPlaylist[normalized] ?: 0) + 1
                    names.putIfAbsent(normalized, cleanArtistName(track.author))
                }
            }
            perPlaylist.forEach { (normalized, count) ->
                weights[normalized] = (weights[normalized] ?: 0.0) +
                    1.4 + minOf(count, 4) * 0.9
            }
        }
        return weights
            .filterValues { it > 0.0 }
            .map { (normalized, weight) -> ArtistSeed(normalized, names[normalized] ?: normalized, weight) }
            .sortedByDescending { it.weight }
            .take(6)
    }

    private fun trackSeeds(
        recents: List<YouTubeVideoItem>,
        liked: List<YouTubeVideoItem>,
        playlists: List<MusicPlaylist>,
    ): List<TrackSeed> {
        val seen = mutableSetOf<String>()
        val seeds = mutableListOf<TrackSeed>()
        MusicListeningStore.topTracks(10).forEach { stat ->
            if (stat.videoId.isBlank() || !seen.add(stat.videoId)) return@forEach
            seeds += TrackSeed(
                videoId = stat.videoId,
                title = stat.title,
                artist = stat.artist,
                channelId = stat.channelId,
                weight = stat.plays.toDouble() + 0.5,
            )
        }
        liked.take(12).forEach { item ->
            if (seen.add(item.videoId)) {
                seeds += TrackSeed(
                    videoId = item.videoId,
                    title = item.title,
                    artist = cleanArtistName(item.author),
                    channelId = item.channelId,
                    weight = 2.4,
                )
            }
        }
        playlists.sortedByDescending { it.createdAtMs }.take(4).forEach { playlist ->
            // Newest additions first, so a refreshed playlist moves the taste.
            playlist.tracks.asReversed().take(6).forEachIndexed { index, track ->
                if (track.videoId.isBlank() || !seen.add(track.videoId)) return@forEachIndexed
                seeds += TrackSeed(
                    videoId = track.videoId,
                    title = track.title,
                    artist = cleanArtistName(track.author),
                    channelId = track.channelId,
                    weight = 2.0 - index * 0.1,
                )
            }
        }
        recents.take(8).forEachIndexed { index, item ->
            if (!seen.add(item.videoId)) return@forEachIndexed
            seeds += TrackSeed(
                videoId = item.videoId,
                title = item.title,
                artist = cleanArtistName(item.author),
                channelId = item.channelId,
                weight = 1.0 / (1.0 + index * 0.3),
            )
        }
        val sorted = seeds.sortedByDescending { it.weight }
        val diverse = sorted.distinctBy { normalizeArtistName(it.artist) }
        return (diverse + sorted).distinctBy { it.videoId }.take(5)
    }

    /**
     * A stable fingerprint of the device library, so a new like or playlist
     * edit rebuilds the cached recommendations instead of waiting out the TTL.
     */
    internal fun libraryKey(
        liked: List<YouTubeVideoItem>,
        playlists: List<MusicPlaylist>,
    ): String {
        val likedPart = liked.joinToString(",") { it.videoId }.hashCode().toString()
        val playlistPart = playlists.take(8).joinToString("|") { playlist ->
            val trackHash = playlist.tracks.joinToString(",") { it.videoId }.hashCode()
            "${playlist.id}:${playlist.tracks.size}:$trackHash"
        }
        return "${liked.size}#$likedPart#$playlistPart"
    }

    /** Artists the listener has explicitly saved, through likes or playlists. */
    internal fun favoriteArtistKeys(
        liked: List<YouTubeVideoItem>,
        playlists: List<MusicPlaylist>,
    ): Set<String> = buildSet {
        liked.forEach { item ->
            val normalized = normalizeArtistName(item.author)
            if (normalized.isNotBlank()) add(normalized)
        }
        playlists.forEach { playlist ->
            playlist.tracks.forEach { track ->
                val normalized = normalizeArtistName(track.author)
                if (normalized.isNotBlank()) add(normalized)
            }
        }
    }

    /** A "More like <playlist>" row, built from the newest playlist that has enough songs. */
    private fun playlistMixFeed(
        playlists: List<MusicPlaylist>,
        ranked: List<Candidate>,
        used: Set<String>,
    ): YouTubeFeed? {
        val playlist = playlists
            .filter { it.tracks.size >= 3 }
            .maxByOrNull { it.createdAtMs }
            ?: return null
        val ids = playlist.tracks.mapTo(HashSet()) { it.videoId }
        val artists = playlist.tracks
            .mapNotNull { track ->
                normalizeArtistName(track.author).takeIf { name -> name.isNotBlank() }
            }
            .toSet()
        val items = ranked.asSequence()
            .filter { it.item.videoId !in used && it.item.videoId !in ids }
            .filter { candidate ->
                normalizeArtistName(candidate.item.author) in artists ||
                    candidate.sourceArtist in artists
            }
            .map { it.item }
            .distinctBy { it.videoId }
            .take(10)
            .toList()
        return if (items.size >= 3) YouTubeFeed("More like ${playlist.name}", items) else null
    }

    private fun durationFit(durationMs: Long): Double = when {
        durationMs in 120_000L..600_000L -> 1.2
        durationMs in 45_000L..900_000L -> 0.3
        durationMs > 900_000L -> -1.5
        durationMs in 20_000L..45_000L -> -0.4
        else -> 0.0
    }

    internal fun looksLikeTalk(title: String): Boolean {
        val lower = title.lowercase()
        return listOf(
            "interview", "reaction", "podcast", "behind the scenes", "making of",
            "tutorial", "unboxing", "review", "trailer", "teaser", "performs",
            "late late show", "homefest", "choreography", "dance studio",
            "guitar lesson", "how to play",
        ).any { it in lower }
    }

    private val titleStopWords = setOf(
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with", "from",
        "official", "video", "audio", "lyrics", "lyric", "music", "feat", "ft",
        "remix", "version", "edit", "hd", "hq", "visualizer", "visualiser",
        "topic", "live", "performance", "full", "album", "explicit", "remaster",
        "remastered", "legendado", "traducao", "tradução", "clipe", "oficial",
    )

    internal fun significantTitleTokens(title: String): Set<String> =
        title.lowercase()
            // Decorate-free titles: "(Official Video)", "[Live]", "(Remix)" etc.
            .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .split(' ')
            .filter { it.length >= 3 && it !in titleStopWords }
            .toSet()
}
