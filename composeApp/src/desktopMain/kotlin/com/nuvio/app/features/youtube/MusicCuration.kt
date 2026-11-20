package com.nuvio.app.features.youtube

import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Random
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.exp

/** A titled, playable set of tracks (a Daily Mix, Discover Weekly). */
internal data class MusicCuratedMix(
    val id: String,
    val name: String,
    /** The artists the mix is mostly made of, lead first. */
    val artists: List<String>,
    val tracks: List<YouTubeVideoItem>,
)

/**
 * Everything the personal Music tabs show, each piece with its own job:
 *  - Home: familiar music shaped around you (quick picks, daily mixes, on
 *    repeat, forgotten favourites, "because you listened to").
 *  - Discover: music that's new to you (Discover Weekly, Release Radar, fans
 *    also like).
 * Explore stays non-personal (moods, charts, new releases).
 */
internal data class MusicCuration(
    val quickPicks: List<YouTubeVideoItem> = emptyList(),
    val dailyMixes: List<MusicCuratedMix> = emptyList(),
    val onRepeat: List<YouTubeVideoItem> = emptyList(),
    val forgotten: List<YouTubeVideoItem> = emptyList(),
    val because: List<YouTubeFeed> = emptyList(),
    val discoverWeekly: MusicCuratedMix? = null,
    val releaseRadar: List<YouTubeVideoItem> = emptyList(),
    val fansAlsoLike: List<YouTubeVideoItem> = emptyList(),
    val builtAtMs: Long = 0L,
    /** Library/taste fingerprint the curation was built for. */
    val fingerprint: String = "",
) {
    val isEmpty: Boolean
        get() = quickPicks.isEmpty() && dailyMixes.isEmpty() && onRepeat.isEmpty() &&
            forgotten.isEmpty() && because.isEmpty() && discoverWeekly == null &&
            releaseRadar.isEmpty() && fansAlsoLike.isEmpty()
}

internal object MusicCurator {
    private const val DayMs = 86_400_000L
    private val radioGate = Semaphore(4)
    private val pageGate = Semaphore(3)

    /** One artist in the listener's taste profile. */
    private data class ArtistTaste(val key: String, val name: String, val weight: Double)

    private class Taste(
        val artists: List<ArtistTaste>,
        val weightOf: Map<String, Double>,
        val heard: Set<String>,
        val liked: Set<String>,
        /** The listener's own tracks per artist, strongest first. */
        val ownByArtist: Map<String, List<YouTubeVideoItem>>,
        /** Tracks to grow radios from, strongest first, one per artist. */
        val seeds: List<YouTubeVideoItem>,
        val fingerprint: String,
    )

    private fun artistKey(item: YouTubeVideoItem): String =
        normalizeArtistName(splitArtistNames(item.author).firstOrNull() ?: item.author)

    private fun decay(lastPlayedMs: Long, now: Long, halfLifeDays: Double = 21.0): Double {
        if (lastPlayedMs <= 0L) return 0.5
        val days = (now - lastPlayedMs).coerceAtLeast(0L) / DayMs.toDouble()
        return exp(-days * 0.693 / halfLifeDays)
    }

    private fun buildTaste(
        liked: List<YouTubeVideoItem>,
        recents: List<YouTubeVideoItem>,
        playlists: List<MusicPlaylist>,
    ): Taste {
        MusicListeningStore.ensureLoaded()
        val now = System.currentTimeMillis()
        val weights = HashMap<String, Double>()
        val names = HashMap<String, String>()
        fun add(item: YouTubeVideoItem, w: Double) {
            val key = artistKey(item)
            if (key.isBlank()) return
            weights[key] = (weights[key] ?: 0.0) + w
            names.putIfAbsent(key, cleanArtistName(splitArtistNames(item.author).firstOrNull() ?: item.author))
        }
        // Plays weigh most and fade over ~3 weeks; likes and saves give a base.
        val listens = MusicListeningStore.topTracks(500)
        listens.forEach { t ->
            val item = YouTubeVideoItem(t.videoId, t.title, t.artist, null, 0L, false, channelId = t.channelId, isMusic = true)
            add(item, 3.0 * t.plays * decay(t.lastPlayedMs, now))
        }
        liked.forEachIndexed { index, item -> add(item, if (index < 60) 1.6 else 0.8) }
        playlists.forEach { p -> p.tracks.forEach { add(it.toYouTubeItem(), 0.5) } }
        recents.take(20).forEach { add(it, 1.0) }

        val artists = weights.entries.sortedByDescending { it.value }
            .map { ArtistTaste(it.key, names[it.key] ?: it.key, it.value) }

        val playsById = listens.associateBy { it.videoId }
        val own = (recents + liked + playlists.flatMap { p -> p.tracks.map { it.toYouTubeItem() } })
            .filter { it.kind == YouTubeItemKind.VIDEO }
            .distinctBy { it.videoId }
        val likedIds = liked.mapTo(HashSet()) { it.videoId }
        val ownByArtist = own.groupBy(::artistKey).mapValues { (_, items) ->
            items.sortedByDescending { item ->
                val t = playsById[item.videoId]
                (t?.let { it.plays * decay(it.lastPlayedMs, now) } ?: 0.0) + if (item.videoId in likedIds) 0.5 else 0.0
            }
        }
        // Seeds: most-played-lately tracks first, then fresh likes, one per artist.
        val seedPool = listens.sortedByDescending { it.plays * decay(it.lastPlayedMs, now) }
            .mapNotNull { t -> own.firstOrNull { it.videoId == t.videoId } } + recents + liked
        val seeds = seedPool.filter { !MusicRecommendations.isVariant(it.title) }
            .distinctBy(::artistKey)
            .take(12)
        val heard = buildSet {
            addAll(MusicListeningStore.heardIds())
            own.forEach { add(it.videoId) }
        }
        val fingerprint = artists.take(6).joinToString("|") { it.key } + "#" + seeds.take(4).joinToString("|") { it.videoId } +
            "#" + liked.size + "#" + playlists.sumOf { it.tracks.size }
        return Taste(artists, weights, heard, likedIds, ownByArtist, seeds, fingerprint)
    }

    private fun usable(item: YouTubeVideoItem): Boolean =
        item.kind == YouTubeItemKind.VIDEO && !item.isLive && !item.isShorts &&
            !MusicRecommendations.isVariant(item.title) && !MusicRecommendations.looksLikeTalk(item.title) &&
            (item.durationMs == 0L || item.durationMs in 45_000L..900_000L)

    private suspend fun radio(seed: YouTubeVideoItem): List<YouTubeVideoItem> = radioGate.withPermit {
        MusicBitChordRepository.radio(seed.videoId)
            .filter { it.videoId != seed.videoId && usable(it) }
    }

    /** Picks up to [limit] items, at most [perArtist] per artist, skipping same-song duplicates. */
    private fun pick(
        pool: List<YouTubeVideoItem>,
        limit: Int,
        perArtist: Int,
        used: MutableSet<String>,
        exclude: (YouTubeVideoItem) -> Boolean = { false },
    ): List<YouTubeVideoItem> {
        val out = ArrayList<YouTubeVideoItem>()
        val perArtistCount = HashMap<String, Int>()
        for (item in pool) {
            if (out.size >= limit) break
            if (item.videoId in used || exclude(item)) continue
            if (out.any { MusicRecommendations.sameSong(it, item) }) continue
            val key = artistKey(item)
            if ((perArtistCount[key] ?: 0) >= perArtist) continue
            perArtistCount[key] = (perArtistCount[key] ?: 0) + 1
            out += item
            used += item.videoId
        }
        return out
    }

    /** Alternates between lists: a, b, a, b… */
    private fun <T> interleave(vararg lists: List<T>): List<T> {
        val out = ArrayList<T>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { list -> list.getOrNull(i)?.let(out::add) }
        return out
    }

    private fun thumbnailFor(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    suspend fun build(
        liked: List<YouTubeVideoItem>,
        recents: List<YouTubeVideoItem>,
        playlists: List<MusicPlaylist>,
    ): MusicCuration = withContext(Dispatchers.IO) {
        val taste = buildTaste(liked, recents, playlists)
        if (taste.artists.isEmpty()) return@withContext MusicCuration()
        val today = LocalDate.now()
        val daySeed = today.toEpochDay()
        val weekSeed = today.get(IsoFields.WEEK_BASED_YEAR) * 100L + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val dayRandom = Random(daySeed * 31 + taste.fingerprint.hashCode())
        val weekRandom = Random(weekSeed * 31 + taste.artists.take(3).joinToString().hashCode())
        val now = System.currentTimeMillis()

        // ---- Network: song radios for the top seeds, artist pages for the top artists.
        val anchors = taste.artists.take(4)
        val anchorSeeds = anchors.mapNotNull { a -> taste.ownByArtist[a.key]?.firstOrNull() }
        val quickSeeds = taste.seeds.take(3)
        val recentSeeds = recents.filter { usable(it) }.distinctBy(::artistKey)
            .filter { r -> quickSeeds.none { it.videoId == r.videoId } }
            .take(2)
        // "Edge" seeds: liked songs by artists you barely play, so Discover
        // Weekly reaches past your usual circle. Rotates weekly.
        val edgeSeeds = liked.filter { (taste.weightOf[artistKey(it)] ?: 0.0) < 3.0 && usable(it) }
            .shuffled(weekRandom).distinctBy(::artistKey).take(2)
        val radioSeeds = (quickSeeds + anchorSeeds + recentSeeds + edgeSeeds).distinctBy { it.videoId }

        val (radios, overviews) = coroutineScope {
            val radioJobs = radioSeeds.associate { seed -> seed.videoId to async { runCatching { radio(seed) }.getOrDefault(emptyList()) } }
            val overviewJobs = taste.artists.take(6).map { a ->
                async {
                    pageGate.withPermit {
                        val id = artistIdFor(a.name)
                        id?.let { MusicBitChordRepository.artistOverview(it, a.name) }
                    }
                }
            }
            radioJobs.mapValues { it.value.await() } to overviewJobs.awaitAll().filterNotNull()
        }
        fun radioOf(seed: YouTubeVideoItem) = radios[seed.videoId].orEmpty()
        val known = taste.weightOf.filterValues { it >= 1.0 }.keys
        val used = HashSet<String>()

        // ---- Home: Quick picks. Fresh songs from your top radios, laced with
        // your own favourites; reshuffled daily.
        val freshQuick = pick(
            interleave(*quickSeeds.map { radioOf(it) }.toTypedArray()).shuffled(dayRandom),
            limit = 12, perArtist = 2, used = used,
        ) { it.videoId in taste.heard }
        val familiarQuick = pick(
            taste.artists.take(8).flatMap { taste.ownByArtist[it.key].orEmpty().take(3) }.shuffled(dayRandom),
            limit = 6, perArtist = 1, used = used,
        )
        val quickPicks = interleave(freshQuick.take(9), familiarQuick).take(15)

        // ---- Home: Daily Mixes. One per top artist: their songs you love, songs
        // by artists around them you already know, and new songs from their radio.
        val dailyMixes = anchors.mapIndexedNotNull { index, anchor ->
            val seed = taste.ownByArtist[anchor.key]?.firstOrNull() ?: return@mapIndexedNotNull null
            val pool = radioOf(seed)
            val mixUsed = HashSet<String>()
            val own = pick(taste.ownByArtist[anchor.key].orEmpty().shuffled(dayRandom), 8, 8, mixUsed)
            val neighbours = pool.map(::artistKey).filter { it in known && it != anchor.key }.distinct()
            val neighbourOwn = pick(
                neighbours.flatMap { taste.ownByArtist[it].orEmpty().take(2) }.shuffled(dayRandom),
                8, 2, mixUsed,
            )
            val fresh = pick(pool, 12, 2, mixUsed) { it.videoId in taste.heard }
            val tracks = interleave(own, fresh, neighbourOwn).take(30)
            if (tracks.size < 6) return@mapIndexedNotNull null
            val artists = tracks.map { cleanArtistName(splitArtistNames(it.author).firstOrNull() ?: it.author) }
                .filter { it.isNotBlank() }
                .groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }.map { it.key }
            val lead = anchor.name
            MusicCuratedMix(
                id = "daily-${index + 1}",
                name = "Daily Mix ${index + 1}",
                artists = (listOf(lead) + artists.filter { normalizeArtistName(it) != anchor.key }).take(4),
                tracks = tracks,
            )
        }
            // Number the mixes that made it, so a skipped anchor leaves no gap.
            .mapIndexed { i, mix -> mix.copy(id = "daily-${i + 1}", name = "Daily Mix ${i + 1}") }

        // ---- Home: On repeat. What you've played most in the last month.
        val ownById = (recents + liked + playlists.flatMap { p -> p.tracks.map { it.toYouTubeItem() } }).associateBy { it.videoId }
        val onRepeat = MusicListeningStore.topTracks(500)
            .filter { it.plays >= 2 && now - it.lastPlayedMs < 30 * DayMs }
            .sortedByDescending { it.plays }
            .take(20)
            .map { t ->
                ownById[t.videoId] ?: YouTubeVideoItem(
                    t.videoId, t.title, t.artist, thumbnailFor(t.videoId), 0L, false, channelId = t.channelId, isMusic = true,
                )
            }

        // ---- Home: Forgotten favourites. Liked a while ago, not played lately.
        val lastPlayed = MusicListeningStore.topTracks(1000).associate { it.videoId to it.lastPlayedMs }
        val forgotten = liked.drop(30)
            .filter { (lastPlayed[it.videoId] ?: 0L) < now - 45 * DayMs }
            .shuffled(weekRandom)
            .distinctBy(::artistKey)
            .take(20)

        // ---- Home: Because you listened to…
        val because = recentSeeds.mapNotNull { seed ->
            val items = pick(radioOf(seed), 12, 2, used) { it.videoId in taste.heard }
            if (items.size < 4) null else YouTubeFeed("Because you listened to ${cleanSongTitle(seed.title, seed.author).ifBlank { seed.title }}", items)
        }

        // ---- Discover: Discover Weekly. Songs you've never played, by artists
        // you don't already play, ranked by how many of your radios agree on
        // them (artists several of your favourites lead to rank first).
        val allRadio = radios.values.flatten()
        val consensus = allRadio.groupingBy(::artistKey).eachCount()
        val discoverPool = allRadio
            .filter { it.videoId !in taste.heard && it.videoId !in taste.liked }
            .filter { (taste.weightOf[artistKey(it)] ?: 0.0) < 1.5 }
            .distinctBy { it.videoId }
            .shuffled(weekRandom)
            .sortedByDescending { (consensus[artistKey(it)] ?: 0) + if (artistKey(it) !in known) 1 else 0 }
        val discoverTracks = pick(discoverPool, 30, 2, HashSet(used))
        val discoverWeekly = discoverTracks.takeIf { it.size >= 8 }?.let { tracks ->
            MusicCuratedMix(
                id = "discover-weekly-$weekSeed",
                name = "Discover Weekly",
                artists = tracks.map { cleanArtistName(splitArtistNames(it.author).firstOrNull() ?: it.author) }.distinct().take(4),
                tracks = tracks,
            )
        }

        // ---- Discover: Release Radar. Latest albums and singles from your artists.
        val thisYear = today.year
        val releaseRadar = overviews.flatMap { it.releases }
            .filter { (_, year) -> year == null || year >= thisYear - 1 }
            .sortedByDescending { (_, year) -> year ?: 0 }
            .map { it.first }
            .distinctBy { it.videoId }
            .take(20)

        // ---- Discover: Fans also like. Artists your artists' pages point to that
        // you don't listen to yet, most-recommended first.
        val relatedCounts = overviews.flatMap { it.related }.groupingBy { it.videoId }.eachCount()
        val fansAlsoLike = overviews.flatMap { it.related }
            .distinctBy { it.videoId }
            .filter { normalizeArtistName(it.title) !in known }
            .sortedByDescending { relatedCounts[it.videoId] ?: 0 }
            .take(15)

        MusicCuration(
            quickPicks = quickPicks,
            dailyMixes = dailyMixes,
            onRepeat = onRepeat,
            forgotten = forgotten,
            because = because,
            discoverWeekly = discoverWeekly,
            releaseRadar = releaseRadar,
            fansAlsoLike = fansAlsoLike,
            builtAtMs = now,
            fingerprint = taste.fingerprint,
        )
    }

    /** An artist's YouTube Music id: from looked-up track credits first, else a search. */
    private suspend fun artistIdFor(name: String): String? {
        val want = normalizeArtistName(name)
        MusicTrackInfoStore.ensureLoaded()
        MusicTrackInfoStore.info.values.asSequence()
            .flatMap { it.artists.asSequence() }
            .firstOrNull { normalizeArtistName(it.name) == want && it.id?.startsWith("UC") == true }
            ?.id?.let { return it }
        return MusicBitChordRepository.artistBrowseId(name)
    }

    // ------------------------------------------------------------------
    // Disk cache: the last curation, shown instantly on the next launch.
    // ------------------------------------------------------------------

    @Serializable
    private data class CachedMix(val id: String, val name: String, val artists: List<String>, val tracks: List<CachedMusicItem>)

    @Serializable
    private data class CachedCuration(
        val version: Int = 1,
        val quickPicks: List<CachedMusicItem> = emptyList(),
        val dailyMixes: List<CachedMix> = emptyList(),
        val onRepeat: List<CachedMusicItem> = emptyList(),
        val forgotten: List<CachedMusicItem> = emptyList(),
        val because: List<CachedMusicFeed> = emptyList(),
        val discoverWeekly: CachedMix? = null,
        val releaseRadar: List<CachedMusicItem> = emptyList(),
        val fansAlsoLike: List<CachedMusicItem> = emptyList(),
        val builtAtMs: Long = 0L,
        val fingerprint: String = "",
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val file get() = DesktopStorage.cacheDir.resolve("music").resolve("curation.json.gz")

    private fun MusicCuratedMix.toCached() = CachedMix(id, name, artists, tracks.map { it.toCached() })
    private fun CachedMix.toMix() = MusicCuratedMix(id, name, artists, tracks.map { it.toItem() })

    suspend fun loadCached(): MusicCuration? = withContext(Dispatchers.IO) {
        runCatching {
            val path = file
            if (!Files.isRegularFile(path)) return@runCatching null
            val text = GZIPInputStream(Files.newInputStream(path)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val c = json.decodeFromString<CachedCuration>(text)
            if (c.version != 1) return@runCatching null
            MusicCuration(
                quickPicks = c.quickPicks.map { it.toItem() },
                dailyMixes = c.dailyMixes.map { it.toMix() },
                onRepeat = c.onRepeat.map { it.toItem() },
                forgotten = c.forgotten.map { it.toItem() },
                because = c.because.map { f -> YouTubeFeed(f.title, f.items.map { it.toItem() }) },
                discoverWeekly = c.discoverWeekly?.toMix(),
                releaseRadar = c.releaseRadar.map { it.toItem() },
                fansAlsoLike = c.fansAlsoLike.map { it.toItem() },
                builtAtMs = c.builtAtMs,
                fingerprint = c.fingerprint,
            )
        }.getOrNull()
    }

    suspend fun save(curation: MusicCuration) = withContext(Dispatchers.IO) {
        runCatching {
            val c = CachedCuration(
                quickPicks = curation.quickPicks.map { it.toCached() },
                dailyMixes = curation.dailyMixes.map { it.toCached() },
                onRepeat = curation.onRepeat.map { it.toCached() },
                forgotten = curation.forgotten.map { it.toCached() },
                because = curation.because.map { f -> CachedMusicFeed(f.title, f.items.map { it.toCached() }) },
                discoverWeekly = curation.discoverWeekly?.toCached(),
                releaseRadar = curation.releaseRadar.map { it.toCached() },
                fansAlsoLike = curation.fansAlsoLike.map { it.toCached() },
                builtAtMs = curation.builtAtMs,
                fingerprint = curation.fingerprint,
            )
            val path = file
            Files.createDirectories(path.parent)
            val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
            GZIPOutputStream(Files.newOutputStream(temp)).bufferedWriter(Charsets.UTF_8).use { it.write(json.encodeToString(c)) }
            runCatching { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
                .getOrElse { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING) }
        }
    }

    fun clearCache() {
        runCatching { Files.deleteIfExists(file) }
    }

    /** True when a cached curation should be rebuilt (new day, or older than 6 hours). */
    fun isStale(curation: MusicCuration): Boolean {
        val built = curation.builtAtMs
        if (built <= 0L) return true
        val builtDay = java.time.Instant.ofEpochMilli(built).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return builtDay != LocalDate.now() || System.currentTimeMillis() - built > 6 * 3_600_000L
    }
}
