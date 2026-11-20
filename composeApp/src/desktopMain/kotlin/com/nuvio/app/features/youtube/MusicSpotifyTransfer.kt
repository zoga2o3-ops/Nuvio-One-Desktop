package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions
import com.nuvio.app.core.storage.DesktopStorage
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal enum class SpotifyMatchState { PENDING, FOUND, UNSURE, MISSING, SKIPPED }
internal enum class SpotifyPhase { IDLE, LOADING, MATCHING, REVIEW, IMPORTING, DONE, ERROR }

internal data class SpotifyMatch(
    val track: SpotifyTrack,
    val state: SpotifyMatchState = SpotifyMatchState.PENDING,
    val chosen: YouTubeVideoItem? = null,
    val alternatives: List<YouTubeVideoItem> = emptyList(),
)

internal data class SpotifyTransferState(
    val phase: SpotifyPhase = SpotifyPhase.IDLE,
    val sourceIsLiked: Boolean = false,
    val playlistName: String = "",
    val accountName: String? = null,
    val library: List<SpotifyEntry> = emptyList(),
    val total: Int = 0,
    val loaded: Int = 0,
    val searched: Int = 0,
    val found: Int = 0,
    val unsure: Int = 0,
    val missing: Int = 0,
    val matches: List<SpotifyMatch> = emptyList(),
    val added: Int = 0,
    val liked: Int = 0,
    val message: String? = null,
    val error: String? = null,
    val resumed: Boolean = false,
)

@Serializable
private data class SpotifyStoredItem(
    val videoId: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
)

@Serializable
private data class SpotifyStoredMatch(
    val track: SpotifyTrack,
    val state: String = SpotifyMatchState.PENDING.name,
    val chosen: SpotifyStoredItem? = null,
    val alternatives: List<SpotifyStoredItem> = emptyList(),
)

@Serializable
private data class SpotifyTransferPayload(
    val sourceKey: String = "",
    val sourceIsLiked: Boolean = false,
    val name: String = "",
    val phase: String = SpotifyPhase.REVIEW.name,
    val matches: List<SpotifyStoredMatch> = emptyList(),
    val added: Int = 0,
    val liked: Int = 0,
)

/**
 * Spotify-to-YouTube transfer modelled on BitChord PC's spot2ytm:
 *  - reads the whole playlist/Liked Songs from Spotify's web-player GraphQL,
 *  - matches every song to YouTube with several searches in flight,
 *  - keeps doubtful matches for review instead of silently taking the first hit,
 *  - imports into a local Nuvio playlist or the local liked songs (and the
 *    signed-in YouTube account's liked videos), with progress persisted.
 */
internal object MusicSpotifyTransfer {
    private const val Key = "payload"
    private const val SearchChunk = 8
    private val store = DesktopStorage.store("nuvio_music_spotify_transfer")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var state by mutableStateOf(SpotifyTransferState())
        private set

    private var job: Job? = null
    private var loaded = false
    private var sourceKey: String = ""

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val text = store.getString(Key)?.takeIf { it.isNotBlank() } ?: return@runCatching
            val payload = json.decodeFromString<SpotifyTransferPayload>(text)
            if (payload.matches.isEmpty()) return@runCatching
            val matches = payload.matches.map { stored ->
                SpotifyMatch(
                    track = stored.track,
                    state = runCatching { SpotifyMatchState.valueOf(stored.state) }
                        .getOrDefault(SpotifyMatchState.PENDING),
                    chosen = stored.chosen?.toItem(),
                    alternatives = stored.alternatives.map { it.toItem() },
                )
            }
            state = SpotifyTransferState(
                phase = SpotifyPhase.REVIEW,
                sourceIsLiked = payload.sourceIsLiked,
                playlistName = payload.name,
                matches = matches,
                total = matches.size,
                searched = matches.size,
                found = matches.count { it.state == SpotifyMatchState.FOUND },
                unsure = matches.count { it.state == SpotifyMatchState.UNSURE },
                missing = matches.count { it.state == SpotifyMatchState.MISSING },
                added = payload.added,
                liked = payload.liked,
                message = "Resume the last Spotify transfer.",
                resumed = true,
            )
            sourceKey = payload.sourceKey
        }
    }

    fun loadAccount() {
        MusicSpotifySession.ensureLoaded()
        val cookie = MusicSpotifySession.cookie
        if (cookie.isBlank()) {
            state = state.copy(error = "Sign in to Spotify first.")
            return
        }
        job?.cancel()
        job = scope.launch {
            state = state.copy(phase = SpotifyPhase.LOADING, error = null, message = null)
            SpotifyWebClient.connect(cookie)
                .onSuccess { client ->
                    client.account()
                        .onSuccess { account ->
                            MusicSpotifySession.saveAccount(account.name)
                            state = state.copy(
                                phase = SpotifyPhase.IDLE,
                                accountName = account.name,
                                library = account.library,
                                message = if (account.library.isEmpty()) "No Spotify playlists found." else null,
                            )
                        }
                        .onFailure { state = state.copy(phase = SpotifyPhase.ERROR, error = it.message) }
                }
                .onFailure { state = state.copy(phase = SpotifyPhase.ERROR, error = it.message) }
        }
    }

    fun importPlaylist(link: String) {
        val source = SpotifySource.parse(link)
        if (source == null) {
            state = state.copy(error = "Paste a Spotify playlist link.")
            return
        }
        start(source)
    }

    fun importLiked() = start(SpotifySource.Liked)

    fun resume() {
        ensureLoaded()
        if (state.matches.isNotEmpty() && state.phase == SpotifyPhase.REVIEW) {
            state = state.copy(resumed = false)
        }
    }

    fun choose(matchIndex: Int, item: YouTubeVideoItem) {
        val matches = state.matches.toMutableList()
        val match = matches.getOrNull(matchIndex) ?: return
        matches[matchIndex] = match.copy(state = SpotifyMatchState.FOUND, chosen = item)
        state = state.copy(matches = matches, found = state.found + 1, unsure = (state.unsure - 1).coerceAtLeast(0))
        save()
    }

    fun skip(matchIndex: Int) {
        val matches = state.matches.toMutableList()
        val match = matches.getOrNull(matchIndex) ?: return
        matches[matchIndex] = match.copy(state = SpotifyMatchState.SKIPPED, chosen = null)
        state = state.copy(matches = matches, unsure = (state.unsure - 1).coerceAtLeast(0))
        save()
    }

    fun useBestGuesses() {
        val matches = state.matches.map { match ->
            if (match.state == SpotifyMatchState.UNSURE && match.alternatives.isNotEmpty()) {
                match.copy(state = SpotifyMatchState.FOUND, chosen = match.alternatives.first())
            } else {
                match
            }
        }
        state = state.copy(
            matches = matches,
            found = matches.count { it.state == SpotifyMatchState.FOUND },
            unsure = matches.count { it.state == SpotifyMatchState.UNSURE },
            missing = matches.count { it.state == SpotifyMatchState.MISSING },
        )
        save()
    }

    fun searchAlternatives(matchIndex: Int, query: String) {
        val match = state.matches.getOrNull(matchIndex) ?: return
        if (query.isBlank()) return
        scope.launch {
            val results = searchCandidates(query, match.track)
            val matches = state.matches.toMutableList()
            val current = matches.getOrNull(matchIndex) ?: return@launch
            matches[matchIndex] = current.copy(alternatives = results.take(8))
            state = state.copy(matches = matches)
        }
    }

    fun importMatches() {
        if (state.phase == SpotifyPhase.IMPORTING) return
        val chosen = state.matches.mapNotNull { match ->
            if (match.state == SpotifyMatchState.FOUND) match.chosen else null
        }
        if (chosen.isEmpty()) {
            state = state.copy(error = "No matched tracks to import.")
            return
        }
        job?.cancel()
        job = scope.launch {
            state = state.copy(phase = SpotifyPhase.IMPORTING, error = null, message = null)
            runCatching {
                if (state.sourceIsLiked) {
                    var liked = 0
                    chosen.forEach { item ->
                        if (MusicLibraryStore.ensureLiked(item)) liked++
                    }
                    state = state.copy(
                        phase = SpotifyPhase.DONE,
                        liked = liked,
                        added = chosen.size,
                        message = if (liked == 0) {
                            "${chosen.size} songs were already liked."
                        } else {
                            "Liked $liked songs; ${chosen.size - liked} were already liked."
                        },
                    )
                } else {
                    val name = state.playlistName.ifBlank { "Spotify import" }
                    val playlist = MusicLibraryStore.createPlaylist(name, chosen)
                    state = state.copy(
                        phase = SpotifyPhase.DONE,
                        added = playlist.tracks.size,
                        message = "Added ${playlist.tracks.size} tracks to \"${playlist.name}\".",
                    )
                }
                clearPayload()
            }.onFailure {
                state = state.copy(phase = SpotifyPhase.ERROR, error = it.message ?: "Could not import the matched tracks.")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        if (state.phase in setOf(SpotifyPhase.LOADING, SpotifyPhase.MATCHING, SpotifyPhase.IMPORTING)) {
            state = state.copy(phase = SpotifyPhase.IDLE, message = "Transfer cancelled.")
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        state = SpotifyTransferState()
        sourceKey = ""
        loaded = true
        clearPayload()
    }

    private fun start(source: SpotifySource) {
        MusicSpotifySession.ensureLoaded()
        job?.cancel()
        sourceKey = source.key
        job = scope.launch {
            state = SpotifyTransferState(
                phase = SpotifyPhase.LOADING,
                sourceIsLiked = source is SpotifySource.Liked,
            )
            val cookie = MusicSpotifySession.cookie.takeIf { it.isNotBlank() }
            if (source is SpotifySource.Liked && cookie == null) {
                state = state.copy(
                    phase = SpotifyPhase.ERROR,
                    error = "Sign in to Spotify to copy your Liked Songs.",
                )
                return@launch
            }
            SpotifyWebClient.connect(cookie)
                .onSuccess { client ->
                    if (!client.isSignedIn && cookie == null) {
                        // Public playlists are fine anonymously.
                    }
                    val playlist = if (source is SpotifySource.Liked) {
                        client.liked { done, total ->
                            state = state.copy(loaded = done, total = total)
                        }
                    } else {
                        client.playlist((source as SpotifySource.Playlist).id) { done, total ->
                            state = state.copy(loaded = done, total = total)
                        }
                    }
                    playlist.onSuccess { loadedPlaylist ->
                        state = state.copy(
                            phase = SpotifyPhase.MATCHING,
                            playlistName = loadedPlaylist.name,
                            total = loadedPlaylist.tracks.size,
                            loaded = loadedPlaylist.tracks.size,
                            matches = loadedPlaylist.tracks.map(::pendingMatch),
                            message = null,
                        )
                        matchAll(loadedPlaylist.tracks)
                    }.onFailure {
                        state = state.copy(phase = SpotifyPhase.ERROR, error = it.message ?: "Could not read that Spotify playlist.")
                    }
                }
                .onFailure {
                    state = state.copy(phase = SpotifyPhase.ERROR, error = it.message ?: "Could not connect to Spotify.")
                }
        }
    }

    private suspend fun matchAll(tracks: List<SpotifyTrack>) {
        val results = mutableListOf<SpotifyMatch>()
        tracks.chunked(SearchChunk).forEach { chunk ->
            val matched = coroutineScope {
                chunk.map { track -> async { matchTrack(track) } }.awaitAll()
            }
            results += matched
            state = state.copy(
                matches = results.toList(),
                searched = results.size,
                found = results.count { it.state == SpotifyMatchState.FOUND },
                unsure = results.count { it.state == SpotifyMatchState.UNSURE },
                missing = results.count { it.state == SpotifyMatchState.MISSING },
            )
        }
        state = state.copy(
            phase = SpotifyPhase.REVIEW,
            matches = results,
            searched = results.size,
            found = results.count { it.state == SpotifyMatchState.FOUND },
            unsure = results.count { it.state == SpotifyMatchState.UNSURE },
            missing = results.count { it.state == SpotifyMatchState.MISSING },
            message = if (results.isEmpty()) "Spotify returned no songs." else null,
        )
        save()
    }

    private suspend fun matchTrack(track: SpotifyTrack): SpotifyMatch {
        val queries = buildList {
            add(listOfNotNull(track.name, track.artistLine).filter { it.isNotBlank() }.joinToString(" "))
            add(listOfNotNull(track.name, track.artists.firstOrNull()).filter { it.isNotBlank() }.joinToString(" "))
        }.distinct()
        val candidates = linkedMapOf<String, YouTubeVideoItem>()
        queries.forEach { query ->
            searchCandidates(query, track).forEach { candidate ->
                candidates.putIfAbsent(candidate.videoId, candidate)
            }
            if (candidates.size >= 8) return@forEach
        }
        if (candidates.isEmpty()) return SpotifyMatch(track, SpotifyMatchState.MISSING)
        val scored = candidates.values
            .map { it to score(track, it) }
            .filter { it.second.total > 0 }
            .sortedByDescending { it.second.total }
        if (scored.isEmpty()) return SpotifyMatch(track, SpotifyMatchState.MISSING)
        val alternatives = scored.take(5).map { it.first }
        val best = scored.first()
        val exactTitle = best.second.exactTitle
        val artistMatch = best.second.artistMatch
        val closeDuration = best.second.closeDuration
        val state = when {
            exactTitle && artistMatch -> SpotifyMatchState.FOUND
            exactTitle && closeDuration -> SpotifyMatchState.FOUND
            artistMatch && best.second.titleScore >= 6.0 -> SpotifyMatchState.FOUND
            best.second.titleScore >= 5.0 -> SpotifyMatchState.UNSURE
            artistMatch -> SpotifyMatchState.UNSURE
            else -> SpotifyMatchState.UNSURE
        }
        return SpotifyMatch(
            track = track,
            state = state,
            chosen = if (state == SpotifyMatchState.FOUND) best.first else null,
            alternatives = alternatives,
        )
    }

    private suspend fun searchCandidates(query: String, track: SpotifyTrack): List<YouTubeVideoItem> {
        if (query.isBlank()) return emptyList()
        val results = runCatching {
            withTimeoutOrNull(12_000L) {
                YouTubeEngine.search(query, SearchOptions.TYPE_VIDEO)
            }
        }.getOrNull().orEmpty()
        val trackIsLive = "live" in track.name.lowercase()
        return results.asSequence()
            .filter { it.kind == YouTubeItemKind.VIDEO && !it.isLive && !it.isShorts }
            .filter { it.durationMs in 20_000L..900_000L }
            .filter { candidate -> isNotVariant(candidate.title, trackIsLive) }
            .distinctBy { it.videoId }
            .toList()
    }

    private data class Score(
        val total: Double,
        val titleScore: Double,
        val exactTitle: Boolean,
        val artistMatch: Boolean,
        val closeDuration: Boolean,
    )

    private fun score(track: SpotifyTrack, candidate: YouTubeVideoItem): Score {
        val spotifyTitle = normalizeTitle(track.name)
        val candidateTitle = normalizeTitle(candidate.title)
        val titleScore = when {
            spotifyTitle.isBlank() || candidateTitle.isBlank() -> 0.0
            spotifyTitle == candidateTitle -> 10.0
            candidateTitle.contains(spotifyTitle) || spotifyTitle.contains(candidateTitle) -> 7.0
            else -> tokenOverlap(spotifyTitle, candidateTitle) * 6.0
        }
        val exactTitle = titleScore >= 10.0
        val artistMatch = track.artists.any { artist ->
            val normalized = normalizeArtistName(artist)
            val candidateArtist = normalizeArtistName(candidate.author)
            normalized.isNotBlank() && candidateArtist.isNotBlank() &&
                (candidateArtist.contains(normalized) || normalized.contains(candidateArtist))
        }
        val durationDiff = if (track.durationMs > 0 && candidate.durationMs > 0) {
            abs(track.durationMs - candidate.durationMs)
        } else {
            Long.MAX_VALUE
        }
        val closeDuration = durationDiff <= 5_000L
        var total = titleScore
        if (artistMatch) total += 6.0
        total += when {
            durationDiff <= 5_000L -> 3.0
            durationDiff <= 15_000L -> 2.0
            durationDiff <= 30_000L -> 1.0
            else -> 0.0
        }
        if (candidate.thumbnailUrl != null) total += 0.2
        return Score(total, titleScore, exactTitle, artistMatch, closeDuration)
    }

    private fun isNotVariant(title: String, trackIsLive: Boolean): Boolean {
        val lower = title.lowercase()
        val variants = listOf(
            "cover", "karaoke", "instrumental", "nightcore", "slowed", "reverb",
            "sped up", "8d audio", "tribute", "made famous", "in the style of",
        )
        if (variants.any { it in lower }) return false
        if ("live" in lower && !trackIsLive) return false
        return true
    }

    private fun normalizeTitle(value: String): String =
        value.lowercase()
            .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
            .replace(Regex("\\b(official|video|audio|lyrics|lyric|visualizer|visualiser|hd|hq)\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun tokenOverlap(left: String, right: String): Double {
        val a = left.split(' ').filter { it.isNotBlank() }.toSet()
        val b = right.split(' ').filter { it.isNotBlank() }.toSet()
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }

    private fun pendingMatch(track: SpotifyTrack): SpotifyMatch = SpotifyMatch(
        track = track,
        state = SpotifyMatchState.PENDING,
    )

    private fun save() {
        if (state.matches.isEmpty()) return
        runCatching {
            val payload = SpotifyTransferPayload(
                sourceKey = sourceKey,
                sourceIsLiked = state.sourceIsLiked,
                name = state.playlistName,
                phase = state.phase.name,
                matches = state.matches.map { match ->
                    SpotifyStoredMatch(
                        track = match.track,
                        state = match.state.name,
                        chosen = match.chosen?.toStored(),
                        alternatives = match.alternatives.map { it.toStored() },
                    )
                },
                added = state.added,
                liked = state.liked,
            )
            store.putString(Key, json.encodeToString(payload))
        }
    }

    private fun clearPayload() {
        runCatching { store.putString(Key, "") }
    }

    private fun YouTubeVideoItem.toStored(): SpotifyStoredItem = SpotifyStoredItem(
        videoId = videoId,
        title = title,
        author = author,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
    )

    private fun SpotifyStoredItem.toItem(): YouTubeVideoItem = YouTubeVideoItem(
        videoId = videoId,
        title = title,
        author = author,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
        isLive = false,
        kind = YouTubeItemKind.VIDEO,
        isMusic = true,
    )
}
