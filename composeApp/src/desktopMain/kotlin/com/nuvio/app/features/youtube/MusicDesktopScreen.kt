package com.nuvio.app.features.youtube

import com.nuvio.app.core.ui.SectionPalette

import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.nuvio_one_logo
import nuvio.composeapp.generated.resources.Res
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerHoverIcon
import kotlinx.coroutines.delay
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import com.music.bitchord.data.model.SearchFilter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val MusicBackground: Color get() = SectionPalette.background
private val MusicPanel: Color get() = SectionPalette.surface
private val MusicPanelRaised: Color get() = SectionPalette.raised
private val MusicBar: Color get() = SectionPalette.surface
private val MusicAccent = Color(0xFF9AA8BD)
private val MusicAccentFill = Color(0xFF64748B)
private val MusicMuted: Color get() = SectionPalette.muted
private val MusicDim: Color get() = SectionPalette.text

private enum class MusicTab(val label: String) {
    HOME("Home"),
    EXPLORE("Explore"),
    DISCOVER("Discover"),
    LIBRARY("Library"),
    LOCAL("Local"),
    DOWNLOADS("Downloads"),
    SEARCH("Search"),
    PARTY("Party"),
    INTEGRATIONS("Integrations"),
    SETTINGS("Settings"),
}

private data class MusicDetail(
    val title: String,
    val items: List<YouTubeVideoItem>,
    val loading: Boolean = false,
    val error: String? = null,
    /** Show as a Spotify-style playlist page instead of the generic list. */
    val playlist: Boolean = false,
    /** Liked Songs: rendered from the live liked list. */
    val liked: Boolean = false,
    /** The playlist was cut off by the page limit. */
    val incomplete: Boolean = false,
    /** Header label on the playlist page ("Playlist", "Album"). */
    val kindLabel: String = "Playlist",
    /** The account playlist row this page shows, for deleting it. */
    val accountPlaylist: YouTubeVideoItem? = null,
    /** An artist page as YouTube Music lays it out: Top songs, then Albums, Singles & EPs, Videos, and so on. */
    val sections: List<MusicSearchSection> = emptyList(),
)

private data class MusicMood(
    val label: String,
    val query: String,
    val color: Color,
)

/** A local library station: one playlist, or the liked songs, as a mix card. */
private data class MusicMix(
    val id: String,
    val name: String,
    val description: String,
    val tracks: List<YouTubeVideoItem>,
)

private val MusicMoods = listOf(
    MusicMood("Chill", "chill music playlist", Color(0xFF3A4454)),
    MusicMood("Focus", "focus study music playlist", Color(0xFF3E4A3E)),
    MusicMood("Workout", "workout gym music playlist", Color(0xFF4E3A3A)),
    MusicMood("Party", "party hits music playlist", Color(0xFF4A4232)),
    MusicMood("Sleep", "sleep relaxing music playlist", Color(0xFF3A3A4E)),
    MusicMood("Jazz", "jazz music playlist", Color(0xFF4A3A44)),
    MusicMood("Hip-Hop", "hip hop music playlist", Color(0xFF44403A)),
    MusicMood("Rock", "rock music playlist", Color(0xFF4E4242)),
    MusicMood("Pop", "pop hits music playlist", Color(0xFF3A4A4A)),
    MusicMood("Classical", "classical music playlist", Color(0xFF42403E)),
    MusicMood("R&B", "r&b soul music playlist", Color(0xFF463A4A)),
    MusicMood("Electronic", "electronic dance music playlist", Color(0xFF34424E)),
)

private fun initialMusicTab(): MusicTab {
    val requested = (
        System.getProperty("nuvio.desktop.musicTab")
            ?: System.getenv("NUVIO_DESKTOP_MUSIC_TAB")
        )
        ?.trim()
        ?.lowercase()
        ?: return MusicTab.HOME
    return MusicTab.entries.firstOrNull { it.name.lowercase() == requested } ?: MusicTab.HOME
}

@Composable
internal fun MusicDesktopScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        MusicShellState.inMusic = true
        onDispose { MusicShellState.inMusic = false }
    }
    var tab by remember { mutableStateOf(initialMusicTab()) }
    var feeds by remember { mutableStateOf<List<YouTubeFeed>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<MusicDetail?>(null) }
    var localPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var recommendationFeeds by remember { mutableStateOf<List<YouTubeFeed>>(emptyList()) }
    // Personal curation for Home and Discover (see MusicCuration.kt).
    var curation by remember { mutableStateOf(MusicCuration()) }
    var curationLoading by remember { mutableStateOf(false) }
    var curationLibraryKey by remember { mutableStateOf("") }
    var recommendationsLoading by remember { mutableStateOf(false) }
    var recommendationsBuiltAt by remember { mutableStateOf(0L) }
    var lastRecommendationInvalidation by remember { mutableStateOf(MusicRecommendations.invalidationVersion) }
    var lastRecommendationLibraryKey by remember { mutableStateOf("") }
    var bitChordMoods by remember { mutableStateOf<List<BitChordMoodSection>>(emptyList()) }
    var bitChordReleases by remember { mutableStateOf<List<BitChordShelf>>(emptyList()) }
    var bitChordCharts by remember { mutableStateOf<List<BitChordShelf>>(emptyList()) }
    var accountPlaylists by remember { mutableStateOf<List<YouTubeVideoItem>>(emptyList()) }
    // Full track lists of the account playlists, keyed by playlist id. Loaded in
    // the background so the sidebar can show song counts and pages open at once.
    val accountPlaylistCache = remember { mutableStateMapOf<String, YouTubeEngine.FullPlaylist>() }
    // Which Music profile the on-disk cache (MusicCache) belongs to.
    var cacheAccountKey by remember { mutableStateOf<String?>(null) }
    // Playlists refreshed from YouTube this session (cached ones refresh once too).
    val refreshedPlaylists = remember { mutableSetOf<String>() }
    var selectedAccountPlaylistId by remember { mutableStateOf<String?>(null) }
    var bitChordLoading by remember { mutableStateOf(false) }
    var mixLoading by remember { mutableStateOf(false) }
    var bitChordRefresh by remember { mutableStateOf(0) }
    var discoveryMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { MusicAccountScope.exit() }
    }

    /** Writes playlists, their tracks and Home to disk shortly after a change. */
    fun saveMusicCache() {
        val playlistRows = accountPlaylists
        val contents = accountPlaylistCache.toMap()
        val home = feeds
        val account = cacheAccountKey ?: MusicAccountScope.activeKey
        MusicCache.saveSoon {
            val now = System.currentTimeMillis()
            MusicCacheSnapshot(
                accountKey = account,
                playlists = playlistRows.map { it.toCached() },
                contents = contents
                    .filterValues { it.items.isNotEmpty() }
                    .mapValues { (_, full) -> CachedMusicPlaylist(full.items.map { it.toCached() }, full.complete, now) },
                home = home.map { feed -> CachedMusicFeed(feed.title, feed.items.map { it.toCached() }) },
                savedAtMs = now,
            )
        }
    }

    // Prepare YouTube's player decoder now so the first song starts promptly.
    LaunchedEffect(Unit) { MusicStreamResolver.warmUp() }

    LaunchedEffect(Unit) {
        // Show last session's playlists, tracks and Home straight away; the
        // network refresh below replaces them as fresh data arrives.
        MusicCache.load()?.let { snapshot ->
            cacheAccountKey = snapshot.accountKey
            if (feeds.isEmpty() && snapshot.home.isNotEmpty()) {
                feeds = snapshot.home.map { feed -> YouTubeFeed(feed.title, feed.items.map { it.toItem() }) }
                loading = false
            }
            if (accountPlaylists.isEmpty() && snapshot.playlists.isNotEmpty()) {
                accountPlaylists = snapshot.playlists.map { it.toItem() }
            }
            snapshot.contents.forEach { (id, cached) ->
                if (id !in accountPlaylistCache) {
                    accountPlaylistCache[id] = YouTubeEngine.FullPlaylist(cached.items.map { it.toItem() }, cached.complete)
                }
            }
        }
        MusicLibraryStore.ensureLoaded()
        MusicPlaybackSettings.ensureLoaded()
        MusicListeningStore.ensureLoaded()
        MusicAccountScope.enter()
        runCatching { YouTubeEngine.music() }
            .onSuccess {
                if (it.isNotEmpty()) {
                    feeds = it
                    saveMusicCache()
                }
            }
            .onFailure { if (feeds.isEmpty()) error = it.message ?: "Could not load YouTube Music." }
        loading = false
    }

    // Reload the sidebar's account playlists whenever the active Music profile changes
    // (startup, sign-in, pin/unpin, profile refresh), not just once at launch.
    val activeAccountKey = MusicAccountScope.activeKey
    LaunchedEffect(activeAccountKey) {
        // Another profile than the cached one: its playlists don't apply.
        // (A null key just means the account list hasn't loaded yet.)
        if (activeAccountKey != null && cacheAccountKey != null && cacheAccountKey != activeAccountKey) {
            accountPlaylistCache.clear()
            accountPlaylists = emptyList()
            refreshedPlaylists.clear()
            MusicCache.clear()
        }
        if (activeAccountKey != null) cacheAccountKey = activeAccountKey
        // Opening Music straight after launch can ask before the account session
        // is ready and get nothing back; keep retrying for about four minutes.
        for (wait in listOf(0L, 3_000L, 6_000L, 10_000L, 15_000L, 20_000L) + List(6) { 30_000L }) {
            if (wait > 0) delay(wait)
            // The engine call blocks and can hang on a cold session; await it
            // with a timeout so a stuck attempt doesn't stall the retries.
            val found = withTimeoutOrNull(20_000L) {
                scope.async(Dispatchers.IO) { runCatching { YouTubeEngine.playlists() }.getOrNull() }.await()
            }
                ?.flatMap { it.items }
                ?.filter { it.kind == YouTubeItemKind.PLAYLIST }
                ?.distinctBy { it.videoId }
                .orEmpty()
            if (found.isNotEmpty()) {
                accountPlaylists = found
                saveMusicCache()
                break
            }
        }
    }

    LaunchedEffect(accountPlaylists) {
        // Uncached playlists first, then refresh the cached ones once per session.
        val ordered = accountPlaylists.sortedBy { if ((it.playlistId ?: it.videoId) in accountPlaylistCache) 1 else 0 }
        ordered.forEach { item ->
            val id = item.playlistId ?: item.videoId
            if (id !in refreshedPlaylists) {
                // YouTube sometimes answers a playlist with an empty first page;
                // retry before believing it, and never cache an empty answer.
                var last: YouTubeEngine.FullPlaylist? = null
                for (wait in listOf(0L, 2_000L, 5_000L)) {
                    if (wait > 0) delay(wait)
                    last = runCatching { YouTubeEngine.playlistAll(item) }.getOrNull() ?: last
                    if (last != null && last.items.isNotEmpty()) break
                }
                // An empty result is shown as "0 songs" but opening the playlist
                // reloads it; it never replaces a cached list that had songs.
                last?.let { full ->
                    if (full.items.isNotEmpty() || id !in accountPlaylistCache) accountPlaylistCache[id] = full
                    if (full.items.isNotEmpty()) refreshedPlaylists += id
                    saveMusicCache()
                }
            }
        }
    }

    fun accountPlaylistSongs(item: YouTubeVideoItem): String {
        val full = accountPlaylistCache[item.playlistId ?: item.videoId]
            ?: return accountPlaylistCountHint(item) ?: "Loading…"
        // Account lists (Liked videos) count only what the list shows: the songs.
        val count = full.items.count {
            it.kind == YouTubeItemKind.VIDEO && (!isLikedVideos(item) || MusicTrackFilter.looksLikeMusic(it))
        }
        return "$count${if (full.complete) "" else "+"} ${if (count == 1) "song" else "songs"}"
    }

    fun playFrom(items: List<YouTubeVideoItem>, start: Int) {
        val videos = items.filter { it.kind == YouTubeItemKind.VIDEO }
        val target = items.getOrNull(start) ?: return
        val index = videos.indexOfFirst { it.videoId == target.videoId }.coerceAtLeast(0)
        if (videos.isNotEmpty()) MusicPlaybackQueue.play(videos, index)
    }

    fun openArtist(channelId: String, name: String) {
        selectedAccountPlaylistId = null
        // Not read from the flat cache: the page is loaded fresh so its sections (Top songs, Albums, Videos…) are shown.
        detail = MusicDetail(name, emptyList(), loading = true)
        scope.launch {
            val directSongs = runCatching { YouTubeEngine.channel(channelId) }
                .getOrNull()
                ?.flatMap { it.items }
                ?.filter { it.kind == YouTubeItemKind.VIDEO }
                .orEmpty()
            // YouTube Music artist pages also carry the release shelves and a
            // longer song list than the YouTube channel's "Top songs" shelf.
            var musicId = channelId
            var overview = runCatching {
                MusicBitChordRepository.artistOverview(musicId, name)
            }.getOrNull()
            if (overview == null) {
                musicId = runCatching { MusicBitChordRepository.artistBrowseId(name) }
                    .getOrNull()
                    .orEmpty()
                if (musicId.isNotBlank() && musicId != channelId) {
                    overview = runCatching {
                        MusicBitChordRepository.artistOverview(musicId, name)
                    }.getOrNull()
                }
            }
            val musicSongs = if (musicId.isNotBlank() || channelId.isNotBlank()) {
                runCatching {
                    MusicBitChordRepository.browseSongs(musicId.ifBlank { channelId })
                }.getOrNull().orEmpty()
            } else {
                emptyList()
            }
            var songs = (directSongs + musicSongs).distinctBy { it.videoId }
            // Channel ids from some credit lookups only resolve through search.
            if (songs.isEmpty()) {
                val channel = runCatching { YouTubeEngine.search(name, SearchOptions.TYPE_CHANNEL) }
                    .getOrNull()
                    ?.firstOrNull { it.kind == YouTubeItemKind.CHANNEL && (it.channelId ?: it.videoId) != channelId }
                if (channel != null) {
                    songs = runCatching { YouTubeEngine.channel(channel.channelId ?: channel.videoId) }
                        .getOrNull()
                        ?.flatMap { it.items }
                        ?.filter { it.kind == YouTubeItemKind.VIDEO }
                        .orEmpty()
                }
            }
            val releases = overview?.releases.orEmpty().map { it.first }
            val related = overview?.related.orEmpty()
            val allItems = (releases + songs + related)
                .distinctBy { "${it.kind}:${it.videoId}" }
            MusicSessionCache.put("artist:$channelId", allItems)
            val sections = musicId.takeIf { it.isNotBlank() }
                ?.let { runCatching { MusicBitChordRepository.artistSections(it) }.getOrNull() }
                .orEmpty()
                .let { rankByLastFm(it, name) }
            detail = MusicDetail(
                name,
                allItems,
                error = if (allItems.isEmpty()) "Could not load $name." else null,
                sections = sections,
            )
        }
    }

    /** Opens an artist by channel id, or finds the channel by name first. */
    fun openArtistNamed(name: String, channelId: String?) {
        if (!channelId.isNullOrBlank()) {
            openArtist(channelId, name)
            return
        }
        selectedAccountPlaylistId = null
        detail = MusicDetail(name, emptyList(), loading = true)
        scope.launch {
            val channel = runCatching { YouTubeEngine.search(name, SearchOptions.TYPE_CHANNEL) }
                .getOrNull()
                ?.firstOrNull { it.kind == YouTubeItemKind.CHANNEL }
            if (channel != null) {
                openArtist(channel.channelId ?: channel.videoId, name)
            } else {
                detail = MusicDetail(name, emptyList(), error = "Could not find $name.")
            }
        }
    }

    /** Deletes an account playlist on YouTube, then drops it from the sidebar. */
    fun deleteAccountPlaylist(item: YouTubeVideoItem) {
        val id = item.playlistId ?: return
        scope.launch {
            runCatching { YouTubeEngine.deletePlaylist(id) }
                .onSuccess {
                    accountPlaylists = accountPlaylists.filterNot { it.videoId == item.videoId }
                    accountPlaylistCache.remove(id)
                    selectedAccountPlaylistId = null
                    detail = null
                    saveMusicCache()
                }
                .onFailure { e ->
                    detail = detail?.copy(error = "Could not delete this playlist: ${e.message ?: "YouTube refused the request."}")
                }
        }
    }

    fun openAlbum(name: String, albumId: String) {
        selectedAccountPlaylistId = null
        MusicSessionCache.get("album:$albumId")?.let { cached ->
            detail = MusicDetail(name, cached, playlist = true, kindLabel = "Album")
            return
        }
        detail = MusicDetail(name, emptyList(), loading = true, playlist = true, kindLabel = "Album")
        scope.launch {
            val songs = MusicBitChordRepository.albumSongs(albumId, name)
            MusicSessionCache.put("album:$albumId", songs)
            detail = MusicDetail(
                name,
                songs,
                playlist = true,
                kindLabel = "Album",
                error = if (songs.isEmpty()) "Could not load this album." else null,
            )
        }
    }

    fun openItem(item: YouTubeVideoItem, items: List<YouTubeVideoItem>, index: Int) {
        selectedAccountPlaylistId = null
        if (MusicBitChordRepository.isBitChordBrowse(item)) {
            val browseId = MusicBitChordRepository.bitChordBrowseId(item) ?: return
            val byline = item.author.orEmpty()
            val label = when {
                byline.contains("Single", ignoreCase = true) -> "Single"
                byline.contains("EP") -> "EP"
                byline.contains("Album", ignoreCase = true) || browseId.startsWith("MPRE") -> "Album"
                else -> "Playlist"
            }
            MusicSessionCache.get("browse:$browseId")?.let { cached ->
                detail = MusicDetail(item.title, cached, playlist = true, kindLabel = label)
                return
            }
            detail = MusicDetail(item.title, emptyList(), loading = true, playlist = true, kindLabel = label)
            scope.launch {
                val songs = if (browseId.startsWith("MPRE")) {
                    MusicBitChordRepository.albumSongs(browseId, item.title)
                } else {
                    MusicBitChordRepository.browseSongs(browseId)
                }
                MusicSessionCache.put("browse:$browseId", songs)
                detail = MusicDetail(
                    item.title,
                    songs,
                    playlist = true,
                    kindLabel = label,
                    error = if (songs.isEmpty()) "Could not load this release." else null,
                )
            }
            return
        }
        when (item.kind) {
            YouTubeItemKind.VIDEO -> playFrom(items, index)
            YouTubeItemKind.PLAYLIST -> {
                selectedAccountPlaylistId = item.videoId
                val id = item.playlistId ?: item.videoId
                val ownRow = item.takeIf { it in accountPlaylists }
                fun show(full: YouTubeEngine.FullPlaylist) = MusicDetail(
                    item.title,
                    // Liked videos and similar lists hold all kinds of videos; keep only what reads as music.
                    full.items.filter { it.kind == YouTubeItemKind.VIDEO && (ownRow == null || !isLikedVideos(ownRow) || MusicTrackFilter.looksLikeMusic(it)) },
                    playlist = true,
                    incomplete = !full.complete,
                    accountPlaylist = ownRow,
                )
                val cached = accountPlaylistCache[id]?.takeIf { it.items.isNotEmpty() }
                if (cached != null) {
                    detail = show(cached)
                } else {
                    detail = MusicDetail(item.title, emptyList(), loading = true, playlist = true, accountPlaylist = ownRow)
                    scope.launch {
                        detail = runCatching {
                            YouTubeEngine.playlistAll(item) { partial ->
                                // Show the songs so far; ignore pages that arrive after the user has moved on.
                                if (partial.items.isNotEmpty()) {
                                    scope.launch(Dispatchers.Main) {
                                        if (selectedAccountPlaylistId == item.videoId) detail = show(partial)
                                    }
                                }
                            }
                        }
                            .onSuccess {
                                if (it.items.isNotEmpty()) {
                                    accountPlaylistCache[id] = it
                                    refreshedPlaylists += id
                                    saveMusicCache()
                                }
                            }
                            .map(::show)
                            .getOrElse { e ->
                                MusicDetail(item.title, emptyList(), error = e.message ?: "Could not load playlist.", playlist = true)
                            }
                    }
                }
            }
            YouTubeItemKind.CHANNEL -> openArtist(item.channelId ?: item.videoId, item.title)
        }
    }

    fun openMood(mood: MusicMood) {
        detail = MusicDetail(mood.label, emptyList(), loading = true)
        scope.launch {
            detail = runCatching { YouTubeEngine.search(mood.query, SearchOptions.TYPE_PLAYLIST) }
                .map { results ->
                    MusicDetail(
                        mood.label,
                        results.filter { it.kind == YouTubeItemKind.PLAYLIST }.ifEmpty { results },
                    )
                }
                .getOrElse { e ->
                    MusicDetail(mood.label, emptyList(), error = e.message ?: "Could not load ${mood.label}.")
                }
        }
    }

    LaunchedEffect(MusicNavigation.artistRequest) {
        val request = MusicNavigation.artistRequest ?: return@LaunchedEffect
        MusicNavigation.artistRequest = null
        openArtistNamed(request.name, request.channelId)
    }

    val liked = MusicLibraryStore.liked
    val recents = MusicLibraryStore.recents
    val playlists = MusicLibraryStore.playlists
    val likedItems = remember(liked) { liked.map { it.toYouTubeItem() } }
    val recentItems = remember(recents) { recents.map { it.toYouTubeItem() } }
    val likedIds = remember(liked) { liked.mapTo(HashSet()) { it.videoId } }
    val libraryMixes = remember(likedItems, playlists) {
        buildList {
            if (likedItems.isNotEmpty()) {
                add(
                    MusicMix(
                        id = "liked",
                        name = "Liked Songs",
                        description = "Made from ${likedItems.size} liked songs",
                        tracks = likedItems,
                    ),
                )
            }
            playlists.sortedByDescending { it.createdAtMs }.forEach { playlist ->
                val tracks = playlist.tracks.map { it.toYouTubeItem() }
                if (tracks.isNotEmpty()) {
                    add(
                        MusicMix(
                            id = "playlist:${playlist.id}",
                            name = playlist.name,
                            description = "${tracks.size} songs · Playlist mix",
                            tracks = tracks,
                        ),
                    )
                }
            }
        }.take(10)
    }
    val recommendationLibraryKey = remember(likedItems, playlists) {
        MusicRecommendations.libraryKey(likedItems, playlists)
    }
    val accountLiked = remember(feeds) {
        feeds.firstOrNull { it.title.contains("Liked", ignoreCase = true) }?.items.orEmpty()
    }

    LaunchedEffect(Unit) {
        // Last curation first, so Home and Discover show something while it rebuilds.
        MusicCurator.loadCached()?.let { cached -> if (curation.isEmpty) curation = cached }
    }

    fun rebuildCuration(force: Boolean) {
        if (curationLoading) return
        if (!MusicPlaybackSettings.personalizedRecommendations) {
            curation = MusicCuration()
            return
        }
        val key = recommendationLibraryKey
        if (!force && !curation.isEmpty && key == curationLibraryKey && !MusicCurator.isStale(curation)) return
        curationLoading = true
        scope.launch {
            val built = runCatching { MusicCurator.build(likedItems, recentItems, playlists) }.getOrNull()
            if (built != null && !built.isEmpty) {
                curation = built
                MusicCurator.save(built)
            }
            curationLibraryKey = key
            curationLoading = false
        }
    }

    LaunchedEffect(
        tab,
        MusicPlaybackSettings.personalizedRecommendations,
        MusicRecommendations.invalidationVersion,
        recommendationLibraryKey,
    ) {
        if (tab != MusicTab.HOME && tab != MusicTab.DISCOVER) return@LaunchedEffect
        val invalidated = MusicRecommendations.invalidationVersion != lastRecommendationInvalidation
        if (invalidated) lastRecommendationInvalidation = MusicRecommendations.invalidationVersion
        // First pass after launch: a fresh cached curation is kept as is.
        if (curationLibraryKey.isEmpty() && !curation.isEmpty && !MusicCurator.isStale(curation) && !invalidated) {
            curationLibraryKey = recommendationLibraryKey
            return@LaunchedEffect
        }
        rebuildCuration(force = invalidated)
    }

    // "Made for you", "Because you like…", and Release Radar: built from the listener's history and library.
    // The artists the listener actually plays: their library, likes and recents. YouTube's own shelves are kept to these.
    val tasteArtists = remember(likedItems, recentItems) {
        (likedItems + recentItems)
            .flatMap { splitArtistNames(it.author) }
            .map { normalizeArtistName(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }
    var personalFeeds by remember { mutableStateOf<List<YouTubeFeed>>(emptyList()) }
    LaunchedEffect(tab, MusicRecommendations.invalidationVersion, recommendationLibraryKey) {
        if (tab != MusicTab.HOME && tab != MusicTab.DISCOVER) return@LaunchedEffect
        personalFeeds = runCatching { MusicRecommendations.build(feeds, recentItems, likedItems, playlists) }
            .getOrNull()
            .orEmpty()
    }

    fun refreshRecommendations() = rebuildCuration(force = true)

    fun openCuratedMix(mix: MusicCuratedMix) {
        selectedAccountPlaylistId = null
        detail = MusicDetail(mix.name, mix.tracks, playlist = true, kindLabel = "Mix")
    }

    fun playCuratedMix(mix: MusicCuratedMix) {
        if (mix.tracks.isNotEmpty()) MusicPlaybackQueue.play(mix.tracks, 0)
    }

    val homeFeeds = feeds

    LaunchedEffect(tab, bitChordRefresh) {
        when (tab) {
            MusicTab.EXPLORE -> {
                if ((bitChordMoods.isEmpty() || bitChordReleases.isEmpty()) && !bitChordLoading) {
                    bitChordLoading = true
                    if (bitChordMoods.isEmpty()) bitChordMoods = MusicBitChordRepository.moods()
                    if (bitChordReleases.isEmpty()) bitChordReleases = MusicBitChordRepository.newReleases()
                    if (bitChordCharts.isEmpty()) bitChordCharts = MusicBitChordRepository.charts()
                    bitChordLoading = false
                }
            }
            else -> Unit
        }
    }

    fun openBitChordMood(mood: BitChordMood) {
        detail = MusicDetail(mood.title, emptyList(), loading = true)
        scope.launch {
            val items = MusicBitChordRepository.moodShelves(mood)
                .flatMap { it.items }
                .distinctBy { it.videoId }
            detail = MusicDetail(
                mood.title,
                items,
                error = if (items.isEmpty()) "No music in this mood yet." else null,
            )
        }
    }

    fun startDiscoveryStation() {
        val playlistTracks = playlists.flatMap { playlist -> playlist.tracks.map { it.toYouTubeItem() } }
        val libraryPool = (likedItems + playlistTracks).distinctBy { it.videoId }
        val fallbackSeed = MusicPlaybackQueue.current
            ?: recentItems.firstOrNull()
            ?: MusicLibraryStore.recents.firstOrNull()?.toYouTubeItem()
        if (libraryPool.isEmpty() && fallbackSeed == null) {
            discoveryMessage = "Like songs or add a playlist first so Discover has a seed."
            return
        }
        scope.launch {
            discoveryMessage = null
            bitChordLoading = true
            val seeds = (libraryPool.shuffled().take(3) + listOfNotNull(fallbackSeed))
                .distinctBy { it.videoId }
                .take(3)
            val radios = coroutineScope {
                seeds.map { seed -> async { MusicBitChordRepository.radio(seed.videoId) } }.awaitAll()
            }.flatten()
            // A discovery station leaves out everything the listener already
            // saved or played, like BitChord's Discover Mix filter.
            val heard = buildSet {
                addAll(libraryPool.map { it.videoId })
                addAll(recentItems.map { it.videoId })
                addAll(MusicListeningStore.heardIds())
            }
            val discovery = radios
                .filter { it.kind == YouTubeItemKind.VIDEO && !it.isLive && !it.isShorts }
                .filter { it.videoId !in heard }
                .distinctBy { it.videoId }
                .shuffled()
                .take(60)
            val queue = discovery.ifEmpty {
                seeds.firstOrNull()?.let { MusicAutoplay.related(it) }.orEmpty()
            }
            if (queue.isNotEmpty()) {
                MusicPlaybackQueue.play(queue, 0)
                MusicPlaybackQueue.expanded = true
            } else {
                discoveryMessage = "Could not start a station from your library."
            }
            bitChordLoading = false
        }
    }

    fun startLibraryMix(mix: MusicMix) {
        if (mix.tracks.isEmpty() || mixLoading) return
        scope.launch {
            discoveryMessage = null
            mixLoading = true
            val seeds = mix.tracks.shuffled().take(2)
            val radios = coroutineScope {
                seeds.map { seed -> async { MusicBitChordRepository.radio(seed.videoId) } }.awaitAll()
            }.flatten()
            // Keep a few favourites from the mix, then let the radio widen it,
            // as BitChord blends a mix with track radios for its stations.
            val own = mix.tracks.shuffled().take(8)
            val queue = if (radios.isEmpty()) {
                mix.tracks.shuffled()
            } else {
                (own + radios)
                    .filter { it.kind == YouTubeItemKind.VIDEO && !it.isLive && !it.isShorts }
                    .distinctBy { it.videoId }
                    .shuffled()
                    .take(60)
            }
            if (queue.isNotEmpty()) {
                MusicPlaybackQueue.play(queue, 0)
                MusicPlaybackQueue.expanded = true
            } else {
                discoveryMessage = "Could not build a mix from \"${mix.name}\"."
            }
            mixLoading = false
        }
    }
    val localPlaylist = localPlaylistId?.let { id -> playlists.firstOrNull { it.id == id } }

    fun closeOverlays() {
        detail = null
        localPlaylistId = null
        selectedAccountPlaylistId = null
    }

    val currentTrack = MusicPlaybackQueue.current

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MusicAccentFill,
            onPrimary = Color.White,
            background = MusicBackground,
            onBackground = Color.White,
            surface = MusicPanel,
            onSurface = Color.White,
            surfaceVariant = MusicPanelRaised,
            onSurfaceVariant = MusicMuted,
            outline = Color(0xFF3A3A42),
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(Modifier.fillMaxSize().background(MusicBackground)) {
        Row(Modifier.fillMaxSize()) {
            MusicSidebar(
                tab = tab,
                onTab = { entry -> closeOverlays(); tab = entry },
                onBack = onBack,
                likedCount = likedItems.size,
                recentCount = recentItems.size,
                playlists = playlists,
                accountPlaylists = accountPlaylists.filterNot { isWatchLater(it) },
                selectedPlaylistId = localPlaylistId,
                selectedAccountPlaylistId = selectedAccountPlaylistId,
                accountPlaylistSubtitle = ::accountPlaylistSongs,
                onOpenLiked = {
                    closeOverlays()
                    tab = MusicTab.LIBRARY
                    detail = MusicDetail("Liked Songs", emptyList(), playlist = true, liked = true)
                },
                onOpenPlaylist = { playlist ->
                    closeOverlays()
                    tab = MusicTab.LIBRARY
                    localPlaylistId = playlist.id
                },
                onOpenAccountPlaylist = { item ->
                    closeOverlays()
                    tab = MusicTab.LIBRARY
                    openItem(item, listOf(item), 0)
                },
                modifier = Modifier.width(224.dp).fillMaxHeight(),
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.width(340.dp).height(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MusicPanel)
                            .border(1.dp, Color(0xFF393942), RoundedCornerShape(999.dp))
                            .clickable { closeOverlays(); tab = MusicTab.SEARCH }
                            .padding(horizontal = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MusicDim, modifier = Modifier.size(17.dp))
                        Text("Search songs, artists, albums…", fontSize = 13.5.sp, color = MusicDim)
                    }
                    Spacer(Modifier.weight(1f))
                    if (currentTrack != null) {
                        Text("Playing", fontSize = 12.sp, color = MusicAccent, fontWeight = FontWeight.Bold)
                    }
                    // Settings lives here, next to the profile selector (moved from the sidebar).
                    val settingsActive = tab == MusicTab.SETTINGS
                    Box(
                        Modifier.size(38.dp)
                            .clip(CircleShape)
                            .background(if (settingsActive) MusicPanelRaised else MusicPanel)
                            .border(1.dp, if (settingsActive) MusicAccent else Color(0xFF393942), CircleShape)
                            .clickable { closeOverlays(); tab = MusicTab.SETTINGS },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Music settings",
                            tint = if (settingsActive) Color.White else MusicMuted,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    MusicProfileButton()
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val showPanel = maxWidth > 1080.dp
                    Row(Modifier.fillMaxSize().padding(bottom = 120.dp)) {
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            when {
                                localPlaylist != null -> MusicPlaylistPage(
                                    title = localPlaylist.name,
                                    tracks = localPlaylist.tracks.map { MusicPageTrack(it.toYouTubeItem(), it.addedAtMs) },
                                    likedIds = likedIds,
                                    onBack = { localPlaylistId = null },
                                    onPlay = { items, index -> MusicPlaybackQueue.play(items, index) },
                                    onToggleLike = { item -> MusicLibraryStore.toggleLike(item) },
                                    onAddToQueue = { item -> MusicPlaybackQueue.append(listOf(item)) },
                                    onOpenArtist = ::openArtistNamed,
                                    onOpenAlbum = ::openAlbum,
                                    onRemoveTrack = { item ->
                                        MusicLibraryStore.removeFromPlaylist(localPlaylist.id, item.videoId)
                                    },
                                    onDeletePlaylist = {
                                        MusicLibraryStore.deletePlaylist(localPlaylist.id)
                                        localPlaylistId = null
                                    },
                                    deleteWarning = "This removes the playlist from Nuvio. The songs stay in your library.",
                                )
                                detail?.playlist == true -> {
                                    val page = detail!!
                                    MusicPlaylistPage(
                                        title = page.title,
                                        tracks = if (page.liked) {
                                            liked.map { MusicPageTrack(it.toYouTubeItem(), it.addedAtMs) }
                                        } else {
                                            page.items.map { MusicPageTrack(it, null) }
                                        },
                                        likedIds = likedIds,
                                        onBack = { detail = null },
                                        onPlay = { items, index -> MusicPlaybackQueue.play(items, index) },
                                        onToggleLike = { item -> MusicLibraryStore.toggleLike(item) },
                                        onAddToQueue = { item -> MusicPlaybackQueue.append(listOf(item)) },
                                        onOpenArtist = ::openArtistNamed,
                                        onOpenAlbum = ::openAlbum,
                                        kindLabel = page.kindLabel,
                                        likedHeader = page.liked,
                                        loading = page.loading,
                                        error = page.error,
                                        incomplete = page.incomplete,
                                        onDeletePlaylist = page.accountPlaylist
                                            ?.takeIf { canDeleteAccountPlaylist(it) }
                                            ?.let { row -> { deleteAccountPlaylist(row) } },
                                        deleteWarning = "This deletes the playlist from your YouTube account. It can't be undone.",
                                        onRemoveTrack = if (page.liked) {
                                            { item -> MusicLibraryStore.toggleLike(item) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                                detail != null -> MusicDetailPane(
                                    detail = detail!!,
                                    likedIds = likedIds,
                                    onBack = { detail = null },
                                    onPlay = { items, index -> playFrom(items, index) },
                                    onOpenItem = ::openItem,
                                    onToggleLike = { item -> MusicLibraryStore.toggleLike(item) },
                                    onAddToQueue = { item -> MusicPlaybackQueue.append(listOf(item)) },
                                )
                                else -> when (tab) {
                                    MusicTab.HOME -> MusicHomePane(
                                        curation = curation,
                                        curationLoading = curationLoading,
                                        personalized = MusicPlaybackSettings.personalizedRecommendations,
                                        feeds = homeFeeds,
                                        personalFeeds = personalFeeds,
                                        tasteArtists = tasteArtists,
                                        loading = loading,
                                        error = error,
                                        recentItems = recentItems,
                                        playlists = playlists,
                                        onRefresh = ::refreshRecommendations,
                                        onOpen = ::openItem,
                                        onPlay = { items, index -> playFrom(items, index) },
                                        onPlayRecent = { index -> playFrom(recentItems, index) },
                                        onPlayMix = ::playCuratedMix,
                                        onOpenMix = ::openCuratedMix,
                                        onOpenPlaylist = { playlist -> closeOverlays(); localPlaylistId = playlist.id },
                                    )
                                    MusicTab.EXPLORE -> MusicExplorePane(
                                        bitChordMoods = bitChordMoods,
                                        releases = bitChordReleases,
                                        charts = bitChordCharts,
                                        loading = bitChordLoading,
                                        onRefresh = {
                                            bitChordMoods = emptyList()
                                            bitChordReleases = emptyList()
                                            bitChordCharts = emptyList()
                                            bitChordRefresh++
                                        },
                                        onOpen = ::openItem,
                                        onMood = ::openMood,
                                        onBitChordMood = ::openBitChordMood,
                                    )
                                    MusicTab.DISCOVER -> MusicDiscoverPane(
                                        // Release Radar here is the weekly list, the same one the Home page shows.
                                        curation = curation.copy(
                                            releaseRadar = personalFeeds.firstOrNull { it.title == "Release Radar" }?.items.orEmpty(),
                                        ),
                                        curationLoading = curationLoading,
                                        personalized = MusicPlaybackSettings.personalizedRecommendations,
                                        stationLoading = bitChordLoading,
                                        message = discoveryMessage,
                                        onStartStation = ::startDiscoveryStation,
                                        onPlayMix = ::playCuratedMix,
                                        onOpenMix = ::openCuratedMix,
                                        onRefresh = {
                                            discoveryMessage = null
                                            refreshRecommendations()
                                        },
                                        onOpen = ::openItem,
                                        onPlay = { items, index -> playFrom(items, index) },
                                    )
                                    MusicTab.LIBRARY -> MusicLibraryPane(
                                        liked = likedItems,
                                        recents = recentItems,
                                        accountLiked = accountLiked,
                                        onPlayLiked = { index -> playFrom(likedItems, index) },
                                        onPlayRecents = { index -> playFrom(recentItems, index) },
                                        onPlayAccountLiked = { index -> playFrom(accountLiked, index) },
                                        onOpenLiked = {
                                            closeOverlays()
                                            tab = MusicTab.LIBRARY
                                            detail = MusicDetail("Liked Songs", emptyList(), playlist = true, liked = true)
                                        },
                                        onToggleLike = { item -> MusicLibraryStore.toggleLike(item) },
                                        onCreatePlaylist = {
                                            newPlaylistName = ""
                                            showCreatePlaylist = true
                                        },
                                    )
                                    MusicTab.LOCAL -> MusicLocalPane()
                                    MusicTab.DOWNLOADS -> MusicDownloadsPane()
                                    MusicTab.SEARCH -> MusicSearchPane(onOpen = ::openItem)
                                    MusicTab.PARTY -> MusicPartyPane()
                                    MusicTab.INTEGRATIONS -> MusicIntegrationsPane()
                                    MusicTab.SETTINGS -> MusicSettingsPane(onOpenParty = { tab = MusicTab.PARTY })
                                }
                            }
                        }
                        if (showPanel) {
                            MusicNowPlayingPanel(
                                item = currentTrack,
                                queue = MusicPlaybackQueue.items,
                                activeIndex = MusicPlaybackQueue.index,
                                onExpand = { MusicPlaybackQueue.expanded = true },
                                onJump = MusicPlaybackQueue::jumpTo,
                                onExplore = { closeOverlays(); tab = MusicTab.EXPLORE },
                                modifier = Modifier.width(340.dp).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }

        if (currentTrack == null) {
            MusicIdlePlayerBar(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                onExplore = { closeOverlays(); tab = MusicTab.EXPLORE },
            )
        }
            }
        }
    }

    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val playlist = MusicLibraryStore.createPlaylist(newPlaylistName)
                        newPlaylistName = ""
                        showCreatePlaylist = false
                        localPlaylistId = playlist.id
                    },
                    enabled = newPlaylistName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MusicPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (active) MusicAccentFill else MusicPanel)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) Color.White else MusicMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun MusicSidebar(
    tab: MusicTab,
    onTab: (MusicTab) -> Unit,
    onBack: () -> Unit,
    likedCount: Int,
    recentCount: Int,
    playlists: List<MusicPlaylist>,
    accountPlaylists: List<YouTubeVideoItem>,
    selectedPlaylistId: String?,
    selectedAccountPlaylistId: String?,
    accountPlaylistSubtitle: (YouTubeVideoItem) -> String,
    onOpenLiked: () -> Unit,
    onOpenPlaylist: (MusicPlaylist) -> Unit,
    onOpenAccountPlaylist: (YouTubeVideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(Color(0xFF1B1B1F))
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 128.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Nuvio",
                    tint = MusicMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
            androidx.compose.foundation.Image(
                painter = org.jetbrains.compose.resources.painterResource(Res.drawable.nuvio_one_logo),
                contentDescription = "Nuvio One",
                modifier = Modifier.size(34.dp),
            )
            Column {
                Text("NUVIO ONE", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Text("MUSIC", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = MusicAccent)
            }
        }
        Spacer(Modifier.height(14.dp))
        MusicSidebarItem("Home", Icons.Filled.Home, tab == MusicTab.HOME) { onTab(MusicTab.HOME) }
        MusicSidebarItem("Explore", Icons.Filled.Explore, tab == MusicTab.EXPLORE) { onTab(MusicTab.EXPLORE) }
        MusicSidebarItem("Discover", Icons.Filled.AutoAwesome, tab == MusicTab.DISCOVER) { onTab(MusicTab.DISCOVER) }
        MusicSidebarItem("Library", Icons.Filled.LibraryMusic, tab == MusicTab.LIBRARY) { onTab(MusicTab.LIBRARY) }
        MusicSidebarItem("Local", Icons.Filled.Folder, tab == MusicTab.LOCAL) { onTab(MusicTab.LOCAL) }
        MusicSidebarItem("Downloads", Icons.Filled.LibraryMusic, tab == MusicTab.DOWNLOADS, tag = MusicDownloads.entries.size.toString().takeIf { MusicDownloads.entries.isNotEmpty() }) { onTab(MusicTab.DOWNLOADS) }
        MusicSidebarItem("Search", Icons.Filled.Search, tab == MusicTab.SEARCH) { onTab(MusicTab.SEARCH) }
        MusicSidebarItem("Party", Icons.Filled.Groups, tab == MusicTab.PARTY) { onTab(MusicTab.PARTY) }
        MusicSidebarItem("Integrations", Icons.Filled.Extension, tab == MusicTab.INTEGRATIONS) { onTab(MusicTab.INTEGRATIONS) }
        Spacer(Modifier.height(22.dp))
        Text("YOUR LIBRARY", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp, color = MusicDim, modifier = Modifier.padding(horizontal = 10.dp))
        Spacer(Modifier.height(7.dp))
        MusicSidebarItem("Liked Songs", Icons.Filled.Favorite, false, tag = likedCount.toString(), onClick = onOpenLiked)
        MusicSidebarItem("Recently played", Icons.Filled.PlayArrow, false, tag = recentCount.toString()) { onTab(MusicTab.LIBRARY) }
        if (playlists.isNotEmpty() || accountPlaylists.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "PLAYLISTS",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
                color = MusicDim,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Spacer(Modifier.height(5.dp))
            playlists.forEach { playlist ->
                MusicSidebarPlaylistItem(
                    title = playlist.name,
                    subtitle = "${playlist.tracks.size} songs",
                    cover = playlist.tracks.firstOrNull()?.thumbnailUrl,
                    active = selectedPlaylistId == playlist.id,
                    onClick = { onOpenPlaylist(playlist) },
                )
            }
            accountPlaylists.forEach { item ->
                MusicSidebarPlaylistItem(
                    title = item.title,
                    subtitle = accountPlaylistSubtitle(item),
                    cover = item.thumbnailUrl,
                    active = selectedAccountPlaylistId == item.videoId,
                    onClick = { onOpenAccountPlaylist(item) },
                )
            }
        }
    }
}

@Composable
private fun MusicSidebarItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean = true,
    tag: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MusicPanel else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> MusicDim.copy(alpha = 0.55f)
                selected -> MusicAccent
                else -> MusicMuted
            },
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                !enabled -> MusicDim.copy(alpha = 0.55f)
                selected -> Color.White
                else -> MusicMuted
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        tag?.let {
            Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, color = MusicDim)
        }
    }
}

/** Song count from YouTube's own text (e.g. "Private • 45 videos"), when present. */
private fun accountPlaylistCountHint(item: YouTubeVideoItem): String? {
    val text = listOfNotNull(item.badgeText, item.metadata, item.author).joinToString(" | ")
    val match = Regex("""(\d[\d,.]*)\s*(videos?|songs?|tracks?|items?)""", RegexOption.IGNORE_CASE).find(text)
        ?: return null
    val count = match.groupValues[1].replace(",", "").replace(".", "").toIntOrNull() ?: return null
    return "$count ${if (count == 1) "song" else "songs"}"
}

@Composable
private fun MusicSidebarPlaylistItem(
    title: String,
    subtitle: String,
    cover: String?,
    active: Boolean,
    onClick: () -> Unit,
) {    Row(
        Modifier.fillMaxWidth().height(46.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) MusicPanel else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(MusicPanelRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("♫", fontSize = 14.sp, color = MusicDim)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) Color.White else MusicMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                fontSize = 10.5.sp,
                color = MusicDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MusicProfileButton() {
    val accounts = MusicAccountScope.accounts
    val effectiveKey = MusicAccountScope.pinnedKey ?: accounts.firstOrNull { it.selected }?.key
    val current = accounts.firstOrNull { it.key == effectiveKey } ?: accounts.firstOrNull()
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box {
        Row(
            Modifier.height(38.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MusicPanel)
                .border(1.dp, Color(0xFF393942), RoundedCornerShape(999.dp))
                .clickable {
                    expanded = true
                    if (accounts.isEmpty()) scope.launch { MusicAccountScope.refreshAccounts() }
                }
                .padding(start = 5.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            MusicProfileAvatar(current, 28.dp)
            Text(
                current?.name ?: "Music profile",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Switch profile",
                tint = MusicDim,
                modifier = Modifier.size(15.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                "MUSIC PROFILE",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MusicDim,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (accounts.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No YouTube profiles yet", fontSize = 12.5.sp) },
                    onClick = { scope.launch { MusicAccountScope.refreshAccounts() } },
                )
            } else {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(account.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                account.email?.let { Text(it, fontSize = 12.5.sp, color = MusicDim) }
                            }
                        },
                        leadingIcon = { MusicProfileAvatar(account, 26.dp) },
                        trailingIcon = {
                            if (account.key == effectiveKey) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MusicAccent, modifier = Modifier.size(15.dp))
                            }
                        },
                        onClick = {
                            expanded = false
                            scope.launch { MusicAccountScope.pin(account.key) }
                        },
                    )
                }
            }
            HorizontalDivider(color = Color(0xFF393942))
            DropdownMenuItem(
                text = { Text("Refresh profiles", fontSize = 12.5.sp) },
                leadingIcon = {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = MusicDim, modifier = Modifier.size(16.dp))
                },
                onClick = { scope.launch { MusicAccountScope.refreshAccounts() } },
            )
            if (MusicAccountScope.pinnedKey != null) {
                DropdownMenuItem(
                    text = { Text("Use Nuvio's current profile", fontSize = 12.5.sp) },
                    onClick = {
                        MusicAccountScope.unpin()
                        scope.launch { MusicAccountScope.refreshAccounts() }
                    },
                )
            }
        }
    }
}

@Composable
private fun MusicProfileAvatar(account: YouTubeAccountItem?, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(999.dp)).background(MusicPanelRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (account?.avatarUrl != null) {
            AsyncImage(
                model = account.avatarUrl,
                contentDescription = account.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = account?.name ?: "Music profile",
                tint = MusicDim,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

@Composable
private fun MusicIdlePlayerBar(modifier: Modifier = Modifier, onExplore: () -> Unit) {
    Row(
        modifier.height(120.dp).background(MusicBar)
            .border(1.dp, Color(0xFF393942))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(10.dp))
                .background(MusicPanelRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", fontSize = 22.sp, color = MusicDim)
        }
        Column {
            Text("Nothing playing", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Choose a song from your library", fontSize = 12.5.sp, color = MusicDim)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shuffle, contentDescription = null, tint = MusicDim.copy(alpha = 0.5f), modifier = Modifier.size(17.dp))
            Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = MusicDim.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(999.dp)).background(MusicPanelRaised), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MusicDim, modifier = Modifier.size(22.dp))
            }
            Icon(Icons.Filled.SkipNext, contentDescription = null, tint = MusicDim.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            Icon(Icons.Filled.Repeat, contentDescription = null, tint = MusicDim.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MusicSectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.5.sp, color = MusicDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun MusicRefreshButton(loading: Boolean, description: String, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MusicAccent, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = description, tint = MusicDim, modifier = Modifier.size(17.dp))
        }
    }
}

/** A compact song tile for Quick picks: artwork, title and artists in one row. */
@Composable
private fun MusicQuickPickTile(item: YouTubeVideoItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (hovered) MusicPanelRaised else MusicPanel)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MusicArtwork(item, 56.dp, 10.dp)
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MusicArtistLinks(item, fontSize = 12.5.sp, color = MusicDim)
        }
        if (hovered) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** A mix card: a 2×2 collage of its first covers, its name and its artists. */
@Composable
private fun MusicCuratedMixCard(
    mix: MusicCuratedMix,
    accent: Color,
    width: androidx.compose.ui.unit.Dp = 176.dp,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier.width(width).hoverable(interaction).clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width).clip(RoundedCornerShape(14.dp)).background(MusicPanelRaised)) {
            val covers = mix.tracks.distinctBy { it.thumbnailUrl ?: it.videoId }.take(4)
            Column(Modifier.fillMaxSize()) {
                listOf(0, 2).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        listOf(0, 1).forEach { col ->
                            val track = covers.getOrNull(row + col) ?: covers.firstOrNull()
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (track != null) {
                                    val url = DeArrowDesktop.thumbnailUrl(track)
                                    if (url != null) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Colour band + label so each mix reads as its own thing.
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, accent.copy(alpha = 0.55f), accent.copy(alpha = 0.92f)),
                    ),
                ),
            )
            Text(
                mix.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
            if (hovered) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(10.dp).size(42.dp).clip(CircleShape)
                        .background(MusicAccent).clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${mix.name}", tint = Color(0xFF101014), modifier = Modifier.size(24.dp))
                }
            }
        }
        Text(
            mix.artists.joinToString(", ").ifBlank { "${mix.tracks.size} songs" },
            fontSize = 12.5.sp,
            color = MusicDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val MusicMixAccents = listOf(
    Color(0xFF7C3AED), Color(0xFF0EA5E9), Color(0xFFE11D48), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF6366F1),
)

/** A round artist card (Fans also like). */
@Composable
private fun MusicArtistCircleCard(item: YouTubeVideoItem, onClick: () -> Unit) {
    Column(
        Modifier.width(140.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(124.dp).clip(CircleShape).background(MusicPanelRaised), contentAlignment = Alignment.Center) {
            if (item.thumbnailUrl != null) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(item.title.take(1).uppercase(), fontSize = 40.sp, fontWeight = FontWeight.Black, color = MusicDim)
            }
        }
        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Artist", fontSize = 12.sp, color = MusicDim)
    }
}

private fun musicGreeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

/**
 * Home: familiar music shaped around you. Quick picks, Daily Mixes, Jump back
 * in, On repeat, "Because you listened to", Forgotten favourites, your
 * playlists, then YouTube Music's own shelves at the bottom.
 */
@Composable
private fun MusicHomePane(
    curation: MusicCuration,
    curationLoading: Boolean,
    personalized: Boolean,
    feeds: List<YouTubeFeed>,
    personalFeeds: List<YouTubeFeed> = emptyList(),
    tasteArtists: Set<String> = emptySet(),
    loading: Boolean,
    error: String?,
    recentItems: List<YouTubeVideoItem>,
    playlists: List<MusicPlaylist>,
    onRefresh: () -> Unit,
    onOpen: (YouTubeVideoItem, List<YouTubeVideoItem>, Int) -> Unit,
    onPlay: (List<YouTubeVideoItem>, Int) -> Unit,
    onPlayRecent: (Int) -> Unit,
    onPlayMix: (MusicCuratedMix) -> Unit,
    onOpenMix: (MusicCuratedMix) -> Unit,
    onOpenPlaylist: (MusicPlaylist) -> Unit,
) {
    val nothingPersonal = curation.isEmpty && recentItems.isEmpty()
    if (nothingPersonal && loading) return MusicLoading()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "greeting") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(musicGreeting(), fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                    Text(
                        if (personalized) "Shaped by what you play, like and save" else "Turn on personalized picks in Music settings",
                        fontSize = 12.5.sp,
                        color = MusicDim,
                    )
                }
                if (personalized) MusicRefreshButton(curationLoading, "Refresh your picks", onRefresh)
            }
        }

        if (curation.quickPicks.isNotEmpty()) {
            item(key = "h:quick") { MusicSectionHeader("Quick picks", "New finds mixed with songs you love — fresh every day") }
            item(key = "r:quick") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    curation.quickPicks.take(12).chunked(3).forEachIndexed { rowIndex, rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItems.forEachIndexed { colIndex, item ->
                                val index = rowIndex * 3 + colIndex
                                MusicQuickPickTile(item, Modifier.weight(1f)) { onPlay(curation.quickPicks, index) }
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        } else if (curationLoading) {
            item(key = "h:building") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MusicAccent, strokeWidth = 2.dp)
                    Text("Building your picks from what you play and like…", fontSize = 12.5.sp, color = MusicDim)
                }
            }
        }

        if (curation.dailyMixes.isNotEmpty()) {
            item(key = "h:daily") { MusicSectionHeader("Your daily mixes", "Your favourite artists, blended with music like theirs") }
            item(key = "r:daily") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(curation.dailyMixes, key = { _, mix -> mix.id }) { index, mix ->
                        MusicCuratedMixCard(
                            mix,
                            accent = MusicMixAccents[index % MusicMixAccents.size],
                            onPlay = { onPlayMix(mix) },
                            onOpen = { onOpenMix(mix) },
                        )
                    }
                }
            }
        }

        if (recentItems.isNotEmpty()) {
            item(key = "h:recents") { MusicSectionHeader("Jump back in") }
            item(key = "r:recents") {
                MusicCardGrid(recentItems.take(12)) { _, index -> onPlayRecent(index) }
            }
        }

        // Made for you, Because you like…, and Release Radar (one curated row each).
        personalFeeds.forEachIndexed { feedIndex, feed ->
            if (feed.items.isNotEmpty()) {
                item(key = "h:personal:$feedIndex") { MusicSectionHeader(feed.title) }
                item(key = "r:personal:$feedIndex") {
                    MusicCardGrid(feed.items) { _, index -> onPlay(feed.items, index) }
                }
            }
        }

        if (curation.onRepeat.isNotEmpty()) {
            item(key = "h:repeat") { MusicSectionHeader("On repeat", "Songs you've played most this month") }
            item(key = "r:repeat") {
                MusicCardGrid(curation.onRepeat) { _, index -> onPlay(curation.onRepeat, index) }
            }
        }

        curation.because.forEachIndexed { feedIndex, feed ->
            item(key = "h:because:$feedIndex") { MusicSectionHeader(feed.title) }
            item(key = "r:because:$feedIndex") {
                MusicCardGrid(feed.items) { _, index -> onPlay(feed.items, index) }
            }
        }

        if (curation.forgotten.isNotEmpty()) {
            item(key = "h:forgotten") { MusicSectionHeader("Forgotten favourites", "Songs you liked but haven't played in a while") }
            item(key = "r:forgotten") {
                MusicCardGrid(curation.forgotten) { _, index -> onPlay(curation.forgotten, index) }
            }
        }

        if (playlists.isNotEmpty()) {
            item(key = "h:playlists") { MusicSectionHeader("Your playlists") }
            item(key = "r:playlists") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(playlists, key = { _, playlist -> "home:${playlist.id}" }) { _, playlist ->
                        MusicPlaylistCard(playlist) { onOpenPlaylist(playlist) }
                    }
                }
            }
        }

        // YouTube Music's own shelves, below everything personal.
        val shelves = feeds
            .map { feed -> YouTubeFeed(feed.title, feed.items.filter { isTasteFit(it, tasteArtists) }) }
            .filter { it.items.isNotEmpty() }
        if (shelves.isNotEmpty()) {
            item(key = "h:yt") {
                Text(
                    "FROM YOUTUBE MUSIC",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = MusicDim,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            shelves.forEachIndexed { shelfIndex, feed ->
                item(key = "h:yt:$shelfIndex") { MusicSectionHeader(feed.title.ifBlank { "Recommended" }) }
                item(key = "r:yt:$shelfIndex") {
                    MusicCardGrid(feed.items) { item, index -> onOpen(item, feed.items, index) }
                }
            }
        } else if (error != null && nothingPersonal) {
            item(key = "h:error") { MusicMessage(error) }
        }
    }
}

/**
 * Explore: everything, not just you. Moods and genres, new releases and
 * charts from YouTube Music. Nothing here is personalized.
 */
@Composable
private fun MusicExplorePane(
    bitChordMoods: List<BitChordMoodSection>,
    releases: List<BitChordShelf>,
    charts: List<BitChordShelf>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpen: (YouTubeVideoItem, List<YouTubeVideoItem>, Int) -> Unit,
    onMood: (MusicMood) -> Unit,
    onBitChordMood: (BitChordMood) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Explore", fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("Moods, genres, new releases and charts from everywhere", fontSize = 12.5.sp, color = MusicDim)
                }
                MusicRefreshButton(loading, "Refresh Explore", onRefresh)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { MusicSectionHeader("Moods & genres") }
        if (bitChordMoods.isNotEmpty()) {
            bitChordMoods.forEach { section ->
                if (section.moods.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(section.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MusicMuted, modifier = Modifier.padding(top = 6.dp))
                    }
                    items(section.moods, key = { mood -> "mood:${mood.browseId}:${mood.title}" }) { mood ->
                        MusicBitChordMoodTile(mood) { onBitChordMood(mood) }
                    }
                }
            }
        } else {
            items(MusicMoods, key = { it.label }) { mood -> MusicMoodTile(mood) { onMood(mood) } }
        }
        fun shelves(title: String, list: List<BitChordShelf>, prefix: String) {
            if (list.none { it.items.isNotEmpty() }) return
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(top = 10.dp)) { MusicSectionHeader(title) }
            }
            list.forEachIndexed { shelfIndex, shelf ->
                if (shelf.items.isEmpty()) return@forEachIndexed
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(shelf.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MusicMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MusicCardGrid(shelf.items, minCard = 160.dp) { item, index -> onOpen(item, shelf.items, index) }
                    }
                }
            }
        }
        shelves("New releases", releases, "rel")
        shelves("Charts", charts, "chart")
        if (loading && releases.isEmpty() && charts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MusicAccent, strokeWidth = 2.dp)
                    Text("Loading new releases and charts…", fontSize = 12.sp, color = MusicDim)
                }
            }
        }
    }
}

/**
 * Discover: music that's new to you. Discover Weekly, Release Radar from your
 * artists, artists their fans also like, and the endless Discovery Station.
 */
@Composable
private fun MusicDiscoverPane(
    curation: MusicCuration,
    curationLoading: Boolean,
    personalized: Boolean,
    stationLoading: Boolean,
    message: String?,
    onStartStation: () -> Unit,
    onPlayMix: (MusicCuratedMix) -> Unit,
    onOpenMix: (MusicCuratedMix) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (YouTubeVideoItem, List<YouTubeVideoItem>, Int) -> Unit,
    onPlay: (List<YouTubeVideoItem>, Int) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Discover", fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("Music that's new to you, picked from what you love", fontSize = 12.5.sp, color = MusicDim)
                }
                MusicRefreshButton(curationLoading, "Refresh discovery", onRefresh)
            }
        }
        if (!personalized) {
            item { MusicMessage("Turn on personalized picks in Music settings to get Discover Weekly and Release Radar.") }
        }
        curation.discoverWeekly?.let { weekly ->
            item(key = "dw") {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFF4C1D95), Color(0xFF1E1B4B), MusicPanel),
                            ),
                        )
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    MusicCuratedMixCard(weekly, accent = Color(0xFF6D28D9), width = 150.dp, onPlay = { onPlayMix(weekly) }, onOpen = { onOpenMix(weekly) })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DISCOVER WEEKLY", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Color(0xFFC4B5FD))
                        Text("Your weekly mixtape of fresh music", fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${weekly.tracks.size} songs you haven't played, from artists your favourites lead to. New every Monday.",
                            fontSize = 12.5.sp,
                            color = MusicDim,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MusicPrimaryButton(label = "Play", enabled = true, onClick = { onPlayMix(weekly) })
                            TextButton(onClick = { onOpenMix(weekly) }) { Text("See all songs", color = MusicMuted) }
                        }
                    }
                }
            }
            item(key = "dw-row") {
                MusicCardGrid(weekly.tracks.take(12)) { _, index -> onPlay(weekly.tracks, index) }
            }
        }
        if (curation.releaseRadar.isNotEmpty()) {
            item(key = "h:radar") { MusicSectionHeader("Release Radar", "The latest albums and singles from artists you play") }
            item(key = "r:radar") {
                MusicCardGrid(curation.releaseRadar, minCard = 160.dp) { item, index -> onOpen(item, curation.releaseRadar, index) }
            }
        }
        if (curation.fansAlsoLike.isNotEmpty()) {
            item(key = "h:fans") { MusicSectionHeader("Fans also like", "Artists close to the ones you play, that you haven't played yet") }
            item(key = "r:fans") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(curation.fansAlsoLike, key = { _, item -> "fans:${item.videoId}" }) { index, item ->
                        MusicArtistCircleCard(item) { onOpen(item, curation.fansAlsoLike, index) }
                    }
                }
            }
        }
        if (personalized && curationLoading && curation.discoverWeekly == null && curation.releaseRadar.isEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), color = MusicAccent, strokeWidth = 2.dp)
                    Text("Finding new music for you…", fontSize = 12.5.sp, color = MusicDim)
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E2633), MusicPanel)),
                    )
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("DISCOVERY STATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = MusicAccent)
                Text("An endless station that keeps finding new music", fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(
                    "Starts from a song in your library, then keeps going through YouTube Music radio.",
                    fontSize = 12.5.sp,
                    color = MusicDim,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MusicPrimaryButton(
                        label = if (stationLoading) "Building station…" else "Start station",
                        enabled = !stationLoading,
                        onClick = onStartStation,
                    )
                    if (stationLoading) CircularProgressIndicator(modifier = Modifier.size(15.dp), color = MusicAccent, strokeWidth = 2.dp)
                }
                message?.let { Text(it, fontSize = 12.5.sp, color = Color(0xFFFF8A9B)) }
            }
        }
    }
}

@Composable
private fun MusicHomeHero(item: YouTubeVideoItem, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(196.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF241218), Color(0xFF17151C), Color(0xFF101012)),
                ),
            )
            .clickable(onClick = onClick),
    ) {
        val imageUrl = DeArrowDesktop.thumbnailUrl(item)
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.18f),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color(0xF0121016), Color(0xD0121016), Color(0x55121016)),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.CenterStart).padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("FEATURED", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp, color = MusicAccent)
            Text(
                item.title,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(520.dp),
            )
            MusicArtistLinks(item, fontSize = 13.5.sp, color = MusicMuted, fallback = "YouTube Music")
            Row(
                Modifier.padding(top = 10.dp)
                    .clip(RoundedCornerShape(999.dp)).background(Color.White)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF121216), modifier = Modifier.size(16.dp))
                Text("Play", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF121216))
            }
        }
        MusicArtwork(
            item,
            154.dp,
            14.dp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 52.dp),
        )
    }
}

@Composable
private fun MusicNowPlayingPanel(
    item: YouTubeVideoItem?,
    queue: List<YouTubeVideoItem>,
    activeIndex: Int,
    onExpand: () -> Unit,
    onJump: (Int) -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (item == null) {
        Column(
            modifier.background(MusicPanel).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("NOW PLAYING", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = MusicDim)
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF312A33), Color(0xFF29292F)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("♪", fontSize = 64.sp, color = MusicDim.copy(alpha = 0.5f))
            }
            Text("Nothing playing", fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("Choose a song from your library to start listening.", fontSize = 13.sp, color = MusicMuted)
            TextButton(onClick = onExplore, modifier = Modifier.fillMaxWidth()) {
                Text("Explore music", color = MusicAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text("UP NEXT", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = MusicDim)
            Text("Your queue is empty.", fontSize = 12.5.sp, color = MusicDim)
        }
        return
    }
    val duration = item.durationMs
    val liked = MusicLibraryStore.liked.any { it.videoId == item.videoId }
    Column(
        modifier.background(MusicPanel).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NOW PLAYING", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = MusicDim)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onExpand, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Open now playing", tint = MusicMuted, modifier = Modifier.size(16.dp))
            }
        }
        MusicArtwork(item, 260.dp, 14.dp, Modifier.align(Alignment.CenterHorizontally))
        Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        MusicArtistLinks(item, fontSize = 13.sp, color = MusicMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = { MusicLibraryStore.toggleLike(item) }, modifier = Modifier.size(28.dp).likeBurst(liked)) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (liked) "Unlike" else "Like",
                    tint = if (liked) MusicLikeRed else MusicMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(formatMusicDuration(duration), fontSize = 12.5.sp, color = MusicDim)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Text("UP NEXT", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = MusicDim)
            Spacer(Modifier.weight(1f))
            Text("${queue.size} tracks", fontSize = 12.5.sp, color = MusicDim)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(queue, key = { index, track -> "$index:${track.videoId}" }) { index, track ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (index == activeIndex) MusicAccent.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onJump(index) }
                        .padding(horizontal = 6.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MusicArtwork(track, 42.dp, 7.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            fontSize = 13.sp,
                            fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (index == activeIndex) Color.White else MusicMuted,
                        )
                        MusicArtistLinks(track, fontSize = 12.5.sp, color = MusicDim)
                    }
                    Text(formatMusicDuration(track.durationMs), fontSize = 12.sp, color = MusicDim)
                }
            }
        }
        TextButton(onClick = onExpand, modifier = Modifier.fillMaxWidth()) {
            Text("Open full player", color = MusicAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MusicBitChordMoodTile(mood: BitChordMood, onClick: () -> Unit) {
    // Nuvio's own tile: the cover fills the right side and melts into a deep
    // version of the mood colour; a bright accent bar and a white title sit on
    // the left. Cover comes from the mood's own playlists, loaded once on screen.
    LaunchedEffect(mood.browseId, mood.params) { MusicMoodCovers.load(mood) }
    val cover = mood.thumbnailUrl ?: MusicMoodCovers.cover(mood)
    val accent = mood.color?.let { Color(it) } ?: MusicAccent
    val base = androidx.compose.ui.graphics.lerp(accent, Color(0xFF0E0E12), 0.62f)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.fillMaxWidth().height(108.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(base)
            .border(1.dp, if (hovered) accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick),
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.62f)
                    .graphicsLayer {
                        val zoom = if (hovered) 1.08f else 1f
                        scaleX = zoom
                        scaleY = zoom
                    },
            )
            // The art melts into the tile colour from the left.
            Box(
                Modifier.matchParentSize().background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to base,
                        0.38f to base,
                        0.72f to base.copy(alpha = 0.55f),
                        1f to accent.copy(alpha = 0.18f),
                    ),
                ),
            )
        } else {
            Box(
                Modifier.matchParentSize().background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(base, androidx.compose.ui.graphics.lerp(accent, base, 0.35f)),
                    ),
                ),
            )
            Text(
                mood.title.take(1).uppercase(),
                fontSize = 92.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = 6.dp).padding(end = 12.dp),
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 12.dp).fillMaxWidth(0.62f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier.width(if (hovered) 34.dp else 22.dp).height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent),
            )
            Text(
                mood.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun MusicMoodTile(mood: MusicMood, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(mood.color, MusicPanel)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(mood.label, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
private fun MusicLibraryPane(
    liked: List<YouTubeVideoItem>,
    recents: List<YouTubeVideoItem>,
    accountLiked: List<YouTubeVideoItem>,
    onPlayLiked: (Int) -> Unit,
    onPlayRecents: (Int) -> Unit,
    onPlayAccountLiked: (Int) -> Unit,
    onOpenLiked: () -> Unit,
    onToggleLike: (YouTubeVideoItem) -> Unit,
    onCreatePlaylist: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your Library", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(MusicAccentFill)
                        .clickable(onClick = onCreatePlaylist)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text("New playlist", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        if (liked.isNotEmpty()) {
            item(key = "liked-header") {
                LibraryListHeader(
                    title = "Liked songs",
                    subtitle = "${liked.size} tracks",
                    onPlay = { onPlayLiked(0) },
                    onShowAll = onOpenLiked,
                )
            }
            itemsIndexed(
                liked.take(6),
                key = { _, item -> "liked:${item.videoId}" },
            ) { index, item ->
                LibrarySongRow(
                    index = index + 1,
                    item = item,
                    liked = true,
                    onClick = { onPlayLiked(index) },
                    onToggleLike = { onToggleLike(item) },
                )
            }
            if (liked.size > 6) {
                item(key = "liked-show-all") {
                    TextButton(onClick = onOpenLiked) {
                        Text("Show all ${liked.size} tracks", color = MusicAccent, fontSize = 12.sp)
                    }
                }
            }
        }
        if (accountLiked.isNotEmpty()) {
            item(key = "account-liked-header") {
                LibraryListHeader(
                    title = "Liked on YouTube",
                    subtitle = "${accountLiked.size} tracks",
                    onPlay = { onPlayAccountLiked(0) },
                    onShowAll = null,
                )
            }
            itemsIndexed(
                accountLiked.take(5),
                key = { _, item -> "account-liked:${item.videoId}" },
            ) { index, item ->
                LibrarySongRow(
                    index = index + 1,
                    item = item,
                    liked = true,
                    onClick = { onPlayAccountLiked(index) },
                    onToggleLike = null,
                )
            }
        }
        if (recents.isNotEmpty()) {
            item {
                LibraryRow(
                    title = "Recently played",
                    subtitle = "${recents.size} tracks",
                    items = recents,
                    onPlay = onPlayRecents,
                    onOpen = null,
                )
            }
        }
    }
}

@Composable
private fun LibraryListHeader(
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onShowAll: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = MusicDim)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MusicAccent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
            Text("Play all", color = MusicAccent, fontSize = 12.sp)
        }
        if (onShowAll != null) {
            TextButton(onClick = onShowAll) {
                Text("Show all", color = MusicDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LibrarySongRow(
    index: Int,
    item: YouTubeVideoItem,
    liked: Boolean,
    onClick: () -> Unit,
    onToggleLike: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            index.toString(),
            fontSize = 11.5.sp,
            color = MusicDim,
            modifier = Modifier.width(20.dp),
        )
        MusicArtwork(item, 40.dp, 7.dp)
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MusicArtistLinks(item, fontSize = 12.5.sp, color = MusicDim)
        }
        Text(formatMusicDuration(item.durationMs), fontSize = 12.sp, color = MusicDim)
        if (onToggleLike != null) {
            IconButton(onClick = onToggleLike, modifier = Modifier.size(28.dp).likeBurst(liked)) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (liked) "Unlike" else "Like",
                    tint = if (liked) MusicLikeRed else MusicDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    items: List<YouTubeVideoItem>,
    onPlay: (Int) -> Unit,
    onOpen: ((YouTubeVideoItem) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 12.5.sp, color = MusicDim)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onPlay(0) }, enabled = items.isNotEmpty()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MusicAccent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play all", color = MusicAccent, fontSize = 12.sp)
            }
        }
        MusicCardGrid(items, minCard = 132.dp) { item, index ->
            if (onOpen != null) onOpen(item) else onPlay(index)
        }
    }
}

@Composable
private fun MusicPlaylistCard(playlist: MusicPlaylist, onClick: () -> Unit) {
    Column(
        Modifier.width(160.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MusicPanelRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text("♫", fontSize = 36.sp, color = MusicMuted)
        }
        Text(playlist.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${playlist.tracks.size} tracks", fontSize = 12.5.sp, color = MusicDim)
    }
}

@Composable
private fun MusicLocalPane() {
    LaunchedEffect(Unit) {
        MusicLocalRepository.ensureLoaded()
        if (MusicLocalRepository.folders.isNotEmpty() && MusicLocalRepository.tracks.isEmpty()) {
            MusicLocalRepository.scan()
        }
    }
    var query by remember { mutableStateOf("") }
    val folders = MusicLocalRepository.folders
    val tracks = MusicLocalRepository.tracks
    val scanning = MusicLocalRepository.scanning
    val error = MusicLocalRepository.error
    val allItems = remember(tracks) { tracks.map(MusicLocalRepository::toVideoItem) }
    val filtered = remember(allItems, query) {
        val q = query.trim()
        if (q.isEmpty()) allItems else allItems.filter {
            it.title.contains(q, true) || (it.author?.contains(q, true) == true)
        }
    }

    fun addFolder() {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "Add a music folder"
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            MusicLocalRepository.addFolder(chooser.selectedFile.toPath())
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text("Local Music", fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("${tracks.size} tracks from ${folders.size} folders", fontSize = 12.5.sp, color = MusicDim)
            }
            Spacer(Modifier.weight(1f))
            if (allItems.isNotEmpty()) {
                TextButton(onClick = { MusicPlaybackQueue.play(allItems, 0) }) {
                    Text("Play all", color = MusicAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = { MusicLocalRepository.scan() }, enabled = !scanning && folders.isNotEmpty()) {
                Text(if (scanning) "Scanning…" else "Scan", color = MusicMuted, fontSize = 12.sp)
            }
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(MusicAccentFill)
                    .clickable(onClick = ::addFolder)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Text("Add folder", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        if (folders.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                folders.forEach { folder ->
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(MusicPanel)
                            .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = MusicMuted, modifier = Modifier.size(13.dp))
                        Text(
                            folder.substringAfterLast('\\').ifBlank { folder },
                            fontSize = 12.5.sp,
                            color = MusicMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(150.dp),
                        )
                        IconButton(onClick = { MusicLocalRepository.removeFolder(folder) }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove folder", tint = MusicDim, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8A9B), fontSize = 12.sp) }
        if (scanning) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MusicAccent, strokeWidth = 2.dp)
                Text("Scanning your music folders…", fontSize = 12.sp, color = MusicDim)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search local tracks", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MusicMuted) },
        )
        when {
            folders.isEmpty() -> MusicMessage("Add a folder to scan local music. MP3, FLAC, M4A, WAV, OGG and Opus are supported.")
            tracks.isEmpty() && !scanning -> MusicMessage("No music found yet. Add another folder or rescan.")
            filtered.isEmpty() -> MusicMessage("No local tracks match “$query”.")
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                itemsIndexed(filtered, key = { _, item -> item.videoId }) { index, item ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { MusicPlaybackQueue.play(filtered, index) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MusicArtwork(item, 44.dp, 8.dp)
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MusicArtistLinks(
                                    item,
                                    fontSize = 12.sp,
                                    color = MusicDim,
                                    fallback = "Unknown Artist",
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Text(" · ${formatMusicDuration(item.durationMs)}", fontSize = 12.sp, color = MusicDim, maxLines = 1)
                            }
                        }
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MusicMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * The search pane's query and results, kept outside the pane. Opening a result takes the pane off screen; going
 * back should return to these results, not a blank search.
 */
private class MusicSearchMemory {
    val query = mutableStateOf("")
    val filter = mutableStateOf(SearchFilter.ALL)
    val sections = mutableStateOf<List<MusicSearchSection>>(emptyList())
    val searched = mutableStateOf(false)
    val searchedQuery = mutableStateOf("")
    val error = mutableStateOf<String?>(null)
}

private val musicSearchMemory = MusicSearchMemory()

@Composable
private fun MusicSearchPane(onOpen: (YouTubeVideoItem, List<YouTubeVideoItem>, Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by musicSearchMemory.query
    var filter by musicSearchMemory.filter
    var sections by musicSearchMemory.sections
    var loading by remember { mutableStateOf(false) }
    var searched by musicSearchMemory.searched
    var searchedQuery by musicSearchMemory.searchedQuery
    var error by musicSearchMemory.error

    // Each filter is its own YouTube Music query, so a chip shows that kind's first page.
    fun runSearch(next: SearchFilter = filter) {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        filter = next
        scope.launch {
            loading = true
            error = null
            val found = MusicBitChordRepository.search(q, next)
            if (found == null) {
                sections = emptyList()
                error = "YouTube Music is limiting requests right now. Try again in a minute."
            } else {
                sections = found
            }
            searchedQuery = q
            searched = true
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search songs, artists and albums", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MusicMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            )
            Button(onClick = { runSearch() }, enabled = query.isNotBlank() && !loading) {
                Text(if (loading) "Searching…" else "Search")
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchFilter.entries.forEach { chip ->
                FilterChip(
                    selected = chip == filter,
                    onClick = { if (searched && chip != filter) runSearch(chip) else filter = chip },
                    label = { Text(chip.label, fontSize = 12.5.sp) },
                )
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8A9B), fontSize = 12.sp) }
        when {
            loading -> MusicLoading()
            !searched -> MusicMessage("Search YouTube Music without an account.")
            sections.isEmpty() -> MusicMessage("No results for “$searchedQuery”.")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                sections.forEach { section ->
                    item(key = "title:${section.title}") {
                        Text(section.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }
                    if (section.title == "Top result" && section.items.isNotEmpty()) {
                        item(key = "top:${section.title}") {
                            MusicTopResultCard(section.items.first(), section.labels.firstOrNull()) {
                                onOpen(section.items.first(), section.items, 0)
                            }
                        }
                        return@forEach
                    }
                    if (section.labels.isNotEmpty() || section.title == "Songs") {
                        itemsIndexed(section.items, key = { index, item -> "${section.title}:$index:${item.videoId}" }) { index, result ->
                            MusicSearchRow(result, section.labels.getOrNull(index)) { onOpen(result, section.items, index) }
                        }
                        return@forEach
                    }
                    item(key = "row:${section.title}") {
                        // As many columns as fit, each card stretched to fill the row (no gap at the right).
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val gap = 18.dp
                            val minCard = 168.dp
                            val columns = ((maxWidth + gap) / (minCard + gap)).toInt().coerceAtLeast(1)
                            val cardWidth = (maxWidth - gap * (columns - 1)) / columns
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(gap),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                section.items.forEachIndexed { index, result ->
                                    MusicCard(result, size = cardWidth) { onOpen(result, section.items, index) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Puts Last.fm's most-played tracks first in the artist's Top songs; the rest of the list follows. */
private suspend fun rankByLastFm(sections: List<MusicSearchSection>, artist: String): List<MusicSearchSection> {
    val top = sections.firstOrNull { it.title == "Top songs" } ?: return sections
    fun normalise(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
    val ranked = LastFmClient.topTracks(artist, 10).mapNotNull { track ->
        runCatching { MusicBitChordRepository.search("${track.title} $artist", SearchFilter.ALL) }
            .getOrNull()
            ?.firstOrNull { it.title == "Songs" }
            ?.items
            ?.firstOrNull { song ->
                MusicBitChordRepository.creditsArtist(song.author, artist) &&
                    normalise(song.title).contains(normalise(track.title))
            }
    }
    if (ranked.isEmpty()) return sections
    val rest = top.items.filter { candidate -> ranked.none { it.videoId == candidate.videoId } }
    return sections.map { section ->
        if (section.title == "Top songs") section.copy(items = (ranked + rest).distinctBy { it.videoId }) else section
    }
}

/**
 * An artist page laid out the way YouTube Music does: Top songs first (five, with Show all for the full
 * list), then each shelf (Albums, Singles & EPs, Videos, …) as a grid.
 */
@Composable
private fun MusicArtistSections(
    detail: MusicDetail,
    onBack: () -> Unit,
    onPlay: (List<YouTubeVideoItem>, Int) -> Unit,
    onOpenItem: (YouTubeVideoItem, List<YouTubeVideoItem>, Int) -> Unit,
) {
    var showAllSongs by remember(detail.title) { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MusicMuted, modifier = Modifier.size(18.dp))
                }
                Text(detail.title, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        detail.sections.forEach { section ->
            item(key = "title:${section.title}") {
                Text(section.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
            if (section.title == "Top songs") {
                val shown = if (showAllSongs) section.items else section.items.take(5)
                itemsIndexed(shown, key = { index, song -> "song:$index:${song.videoId}" }) { index, song ->
                    MusicSearchRow(song, null) { onPlay(section.items, index) }
                }
                if (section.items.size > 5) {
                    item(key = "showall:${section.title}") {
                        TextButton(onClick = { showAllSongs = !showAllSongs }) {
                            Text(if (showAllSongs) "Show less" else "Show all", color = MusicDim)
                        }
                    }
                }
            } else {
                item(key = "row:${section.title}") {
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val gap = 18.dp
                        val minCard = 168.dp
                        val columns = ((maxWidth + gap) / (minCard + gap)).toInt().coerceAtLeast(1)
                        val cardWidth = (maxWidth - gap * (columns - 1)) / columns
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            section.items.forEachIndexed { index, result ->
                                MusicCard(result, size = cardWidth) { onOpenItem(result, section.items, index) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The top search result as a large card (YouTube Music's "Top result"). */
@Composable
private fun MusicTopResultCard(item: YouTubeVideoItem, label: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF29292F))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MusicArtwork(item, 96.dp, 12.dp)
        Column(Modifier.weight(1f)) {
            if (label != null) Text(label, fontSize = 12.sp, color = MusicDim)
            Text(
                item.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MusicArtistLinks(item, fontSize = 13.sp, color = MusicDim)
        }
    }
}

/** One result in a mixed list: artwork, title, then the type and artist (YouTube Music's "More results"). */
@Composable
private fun MusicSearchRow(item: YouTubeVideoItem, label: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MusicArtwork(item, 48.dp, 8.dp)
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (label != null) {
                    Text(label, fontSize = 12.5.sp, color = MusicDim)
                    Text("  •  ", fontSize = 12.5.sp, color = MusicDim)
                }
                MusicArtistLinks(item, fontSize = 12.5.sp, color = MusicDim)
            }
        }
        if (item.kind == YouTubeItemKind.VIDEO && item.durationMs > 0L) {
            Text(formatMusicDuration(item.durationMs), fontSize = 12.5.sp, color = MusicDim)
        }
    }
}

@Composable
private fun MusicDetailPane(
    detail: MusicDetail,
    likedIds: Set<String>,
    onBack: () -> Unit,
    onPlay: (List<YouTubeVideoItem>, Int) -> Unit,
    onOpenItem: (YouTubeVideoItem, List<YouTubeVideoItem>, Int) -> Unit,
    onToggleLike: (YouTubeVideoItem) -> Unit,
    onAddToQueue: (YouTubeVideoItem) -> Unit,
) {
    if (detail.loading) return MusicLoading()
    if (detail.error != null) return MusicMessage(detail.error)
    if (detail.sections.isNotEmpty()) {
        MusicArtistSections(detail, onBack, onPlay, onOpenItem)
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MusicMuted, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(detail.title, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (detail.items.isNotEmpty() && detail.items.none { it.kind == YouTubeItemKind.VIDEO }) {
                            "${detail.items.size} playlists"
                        } else {
                            "${detail.items.size} tracks"
                        },
                        fontSize = 12.sp,
                        color = MusicDim,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (detail.items.any { it.kind == YouTubeItemKind.VIDEO }) {
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(MusicAccentFill)
                            .clickable { onPlay(detail.items, 0) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Play all", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        itemsIndexed(detail.items, key = { index, item -> "$index:${item.videoId}" }) { index, item ->
            val isVideo = item.kind == YouTubeItemKind.VIDEO
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        if (isVideo) onPlay(detail.items, index) else onOpenItem(item, detail.items, index)
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MusicArtwork(item, 54.dp, 8.dp)
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MusicArtistLinks(item, fontSize = 12.5.sp, color = MusicDim)
                }
                if (isVideo) {
                    Text(formatMusicDuration(item.durationMs), fontSize = 12.5.sp, color = MusicDim)
                    IconButton(onClick = { onToggleLike(item) }, modifier = Modifier.size(30.dp).likeBurst(item.videoId in likedIds)) {
                        Icon(
                            if (item.videoId in likedIds) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (item.videoId in likedIds) "Unlike" else "Like",
                            tint = if (item.videoId in likedIds) MusicLikeRed else MusicDim,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    IconButton(onClick = { onAddToQueue(item) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to queue", tint = MusicDim, modifier = Modifier.size(17.dp))
                    }
                } else {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MusicDim, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MusicMixCard(
    mix: MusicMix,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(160.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MusicPanelRaised)
                .clickable(enabled = !loading, onClick = onClick),
        ) {
            val cover = mix.tracks.firstOrNull()
            val url = cover?.let { DeArrowDesktop.thumbnailUrl(it) }
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("♪", fontSize = 34.sp, color = MusicDim, modifier = Modifier.align(Alignment.Center))
            }
            Box(
                Modifier.align(Alignment.Center).size(42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xB3000000)),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play ${mix.name}",
                        tint = Color.White,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
        Text(
            mix.name,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            mix.description,
            fontSize = 12.5.sp,
            color = MusicDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Seasonal and compilation titles that YouTube's home shelves are full of, and that aren't a listener's music. */
private val seasonalOrCompilation = Regex(
    "(?i)christmas|xmas|holiday|birthday|top \\d+|top hits|playlist|compilation|best of|greatest|" +
        "fireplace|relaxing|study|sleep|lo-?fi|24/7|folk songs|hits of|mix\\b",
)

/** Whether a YouTube shelf item is a real track by one of the listener's artists. */
private fun isTasteFit(item: YouTubeVideoItem, tasteArtists: Set<String>): Boolean {
    if (!MusicTrackFilter.isMusic(item)) return false
    if (seasonalOrCompilation.containsMatchIn(item.title)) return false
    return splitArtistNames(item.author).any { normalizeArtistName(it) in tasteArtists }
}

/**
 * Cards that fit the width and wrap onto more rows, like the search results: no sideways scrolling and no card cut
 * off at the edge. [onOpen] gets the card and its index in [items].
 */
@Composable
private fun MusicCardGrid(
    items: List<YouTubeVideoItem>,
    minCard: androidx.compose.ui.unit.Dp = 150.dp,
    onOpen: (YouTubeVideoItem, Int) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 14.dp
        val columns = ((maxWidth + gap) / (minCard + gap)).toInt().coerceAtLeast(1)
        val cardWidth = (maxWidth - gap * (columns - 1)) / columns
        val measuredWidth = maxWidth.value.toInt()
        remember(measuredWidth, columns) {
            OneTubeDebugLog.line("music grid: width ${measuredWidth}dp, $columns columns, ${items.size} cards")
            measuredWidth
        }
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items.forEachIndexed { index, item ->
                MusicCard(item, size = cardWidth) { onOpen(item, index) }
            }
        }
    }
}

@Composable
private fun MusicCard(
    item: YouTubeVideoItem,
    size: androidx.compose.ui.unit.Dp? = 160.dp,
    showLabels: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        if (size != null) Modifier.width(size) else Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MusicPanelRaised)
                .clickable(onClick = onClick),
        ) {
            val url = DeArrowDesktop.thumbnailUrl(item)
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("♪", fontSize = 34.sp, color = MusicDim, modifier = Modifier.align(Alignment.Center))
            }
            if (item.kind == YouTubeItemKind.PLAYLIST) {
                val browseId = MusicBitChordRepository.bitChordBrowseId(item).orEmpty()
                val byline = item.author.orEmpty()
                Text(
                    when {
                        byline.contains("Single", ignoreCase = true) -> "SINGLE"
                        byline.contains("EP") -> "EP"
                        browseId.startsWith("MPRE") || byline.contains("Album", ignoreCase = true) -> "ALBUM"
                        else -> "PLAYLIST"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(7.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        if (showLabels) {
            Text(
                item.title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MusicArtistLinks(
                item,
                fontSize = 12.5.sp,
                color = MusicDim,
                fallback = if (item.kind == YouTubeItemKind.PLAYLIST) "Playlist" else "Music",
            )
        }
    }
}

@Composable
private fun MusicArtwork(
    item: YouTubeVideoItem,
    size: androidx.compose.ui.unit.Dp,
    rounded: androidx.compose.ui.unit.Dp = 10.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(rounded)).background(MusicPanelRaised),
        contentAlignment = Alignment.Center,
    ) {
        val url = DeArrowDesktop.thumbnailUrl(item)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("♪", fontSize = (size.value * 0.4f).sp, color = MusicMuted)
        }
    }
}

@Composable
private fun MusicLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MusicAccent)
    }
}

@Composable
private fun MusicMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.5.sp, color = MusicMuted, maxLines = 3)
    }
}

private fun formatMusicDuration(ms: Long): String {
    if (ms <= 0) return ""
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return String.format(java.util.Locale.US, "%d:%02d", m, s)
}

// ---------------------------------------------------------------------------
// Playlist page (Liked Songs, local playlists, account playlists, albums).
// Nuvio's own look: a framed header card over the blurred cover, pill actions,
// and the track table inside a panel. Album and per-artist credits come from
// MusicTrackInfoStore; every artist name opens that artist's page.
// ---------------------------------------------------------------------------

private data class MusicPageTrack(val item: YouTubeVideoItem, val addedAtMs: Long?)

private enum class MusicPlaylistSort(val label: String) {
    CUSTOM("Playlist order"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    ADDED("Date added"),
    DURATION("Length"),
}

private val PageSecondary: Color get() = SectionPalette.muted
private val PageFaint: Color get() = SectionPalette.muted
private val PageTablePanel: Color get() = SectionPalette.surface
private val PageRowStripe = Color(0x08FFFFFF)
private val PageDateFormat = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US)

private fun pageArtistLine(item: YouTubeVideoItem): String =
    item.author?.substringBefore(" • ")?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown artist"

private fun pageTotalDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "$hours hr $minutes min"
        totalMinutes > 0 -> "$minutes min"
        else -> "${(ms / 1000L).coerceAtLeast(0)} sec"
    }
}

@Composable
private fun PagePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    filled: Boolean,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    filled && enabled -> MusicAccentFill
                    active -> MusicAccent.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
            )
            .border(1.dp, if (filled) Color.Transparent else Color.White.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (active) MusicAccent else Color.White, modifier = Modifier.size(17.dp))
        }
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = if (active) MusicAccent else Color.White)
    }
}

@Composable
private fun PageStatChip(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        color = PageSecondary,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun MusicPlaylistPage(
    title: String,
    tracks: List<MusicPageTrack>,
    likedIds: Set<String>,
    onBack: () -> Unit,
    onPlay: (List<YouTubeVideoItem>, Int) -> Unit,
    onToggleLike: (YouTubeVideoItem) -> Unit,
    onAddToQueue: (YouTubeVideoItem) -> Unit,
    onOpenArtist: (name: String, id: String?) -> Unit,
    onOpenAlbum: (name: String, id: String) -> Unit,
    kindLabel: String = "Playlist",
    likedHeader: Boolean = false,
    loading: Boolean = false,
    error: String? = null,
    incomplete: Boolean = false,
    onRemoveTrack: ((YouTubeVideoItem) -> Unit)? = null,
    onDeletePlaylist: (() -> Unit)? = null,
    deleteWarning: String = "",
) {
    LaunchedEffect(Unit) { MusicTrackInfoStore.ensureLoaded() }
    var confirmDelete by remember(title) { mutableStateOf(false) }
    if (confirmDelete && onDeletePlaylist != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “$title”?", fontWeight = FontWeight.Bold) },
            text = { Text(deleteWarning, color = PageSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeletePlaylist()
                }) { Text("Delete", color = Color(0xFFFF8A9B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Color.White) }
            },
            containerColor = MusicPanelRaised,
        )
    }
    val infos = MusicTrackInfoStore.info
    // Prefetch album/artist info for the start of the playlist in the background
    // so scrolling finds it already loaded. Rows on screen look themselves up
    // first through their own lane.
    LaunchedEffect(tracks) {
        // Gentle: one song at a time with a pause, only the first 150 songs,
        // and nothing while YouTube Music is asking us to slow down.
        tracks.take(150).forEach { track ->
            if (MusicTrackInfoStore.isPaused) return@LaunchedEffect
            if (track.item.videoId !in MusicTrackInfoStore.info) {
                MusicTrackInfoStore.load(track.item, priority = false)
                delay(1_200L)
            }
        }
    }
    var sort by remember(title) { mutableStateOf(MusicPlaylistSort.CUSTOM) }
    var descending by remember(title) { mutableStateOf(false) }
    var query by remember(title) { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    val hasDates = remember(tracks) { tracks.any { it.addedAtMs != null } }
    val shown = remember(tracks, sort, descending, query, if (sort == MusicPlaylistSort.ALBUM || query.isNotBlank()) infos.size else 0) {
        val q = query.trim().lowercase()
        fun albumOf(t: MusicPageTrack) = infos[t.item.videoId]?.album.orEmpty()
        val filtered = if (q.isEmpty()) {
            tracks
        } else {
            tracks.filter {
                it.item.title.lowercase().contains(q) ||
                    pageArtistLine(it.item).lowercase().contains(q) ||
                    albumOf(it).lowercase().contains(q)
            }
        }
        val sorted = when (sort) {
            MusicPlaylistSort.CUSTOM -> filtered
            MusicPlaylistSort.TITLE -> filtered.sortedBy { it.item.title.lowercase() }
            MusicPlaylistSort.ARTIST -> filtered.sortedBy { pageArtistLine(it.item).lowercase() }
            MusicPlaylistSort.ALBUM -> filtered.sortedBy { albumOf(it).lowercase().ifEmpty { "￿" } }
            MusicPlaylistSort.ADDED -> filtered.sortedBy { it.addedAtMs ?: 0L }
            MusicPlaylistSort.DURATION -> filtered.sortedBy { it.item.durationMs }
        }
        if (descending) sorted.reversed() else sorted
    }
    val shownItems = remember(shown) { shown.map { it.item } }
    val totalMs = remember(tracks) { tracks.sumOf { it.item.durationMs.coerceAtLeast(0L) } }
    val currentId = MusicPlaybackQueue.current?.videoId
    val sortOptions = remember(hasDates) {
        MusicPlaylistSort.entries.filter { it != MusicPlaylistSort.ADDED || hasDates }
    }
    val cover = tracks.firstOrNull()?.item

    fun choose(next: MusicPlaylistSort) {
        if (sort == next && next != MusicPlaylistSort.CUSTOM) {
            descending = !descending
        } else {
            sort = next
            descending = next == MusicPlaylistSort.ADDED
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
    ) {
        item(key = "page:header") {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MusicPanel)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
            ) {
                // Blurred cover as the card's backdrop.
                if (!likedHeader && cover != null) {
                    val url = DeArrowDesktop.thumbnailUrl(cover)
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize().blur(48.dp).graphicsLayer { alpha = 0.45f },
                        )
                    }
                } else if (likedHeader) {
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.linearGradient(listOf(MusicAccentFill.copy(alpha = 0.55f), Color.Transparent)),
                        ),
                    )
                }
                Box(
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f))),
                    ),
                )
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        Box(
                            Modifier.size(164.dp)
                                .shadow(18.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp)),
                        ) {
                            when {
                                likedHeader -> Box(
                                    Modifier.fillMaxSize().background(
                                        Brush.linearGradient(listOf(Color(0xFF475569), Color(0xFF94A3B8))),
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
                                }
                                cover != null -> MusicArtwork(cover, 164.dp, 16.dp)
                                else -> Box(
                                    Modifier.fillMaxSize().background(MusicPanelRaised),
                                    contentAlignment = Alignment.Center,
                                ) { Text("♫", fontSize = 48.sp, color = PageSecondary) }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                kindLabel.uppercase(),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.6.sp,
                                color = MusicAccent,
                            )
                            Text(
                                title,
                                fontSize = if (title.length <= 24) 34.sp else 26.sp,
                                lineHeight = if (title.length <= 24) 38.sp else 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (loading && tracks.isEmpty()) {
                                    PageStatChip("Loading songs…")
                                } else {
                                    val count = tracks.size
                                    PageStatChip("$count${if (incomplete) "+" else ""} ${if (count == 1) "song" else "songs"}")
                                    if (totalMs > 0) PageStatChip(pageTotalDuration(totalMs))
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                PagePill("Play", Icons.Filled.PlayArrow, filled = true, enabled = shownItems.isNotEmpty()) {
                                    val start = if (MusicPlaybackQueue.shuffle) shownItems.indices.random() else 0
                                    onPlay(shownItems, start)
                                }
                                PagePill(
                                    if (MusicPlaybackQueue.shuffle) "Shuffle on" else "Shuffle",
                                    Icons.Filled.Shuffle,
                                    filled = false,
                                    active = MusicPlaybackQueue.shuffle,
                                ) { MusicPlaybackQueue.setShuffleEnabled(!MusicPlaybackQueue.shuffle) }
                                if (onDeletePlaylist != null) {
                                    PagePill("Delete", Icons.Filled.Delete, filled = false) { confirmDelete = true }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (error != null && tracks.isNotEmpty()) {
            item(key = "page:notice") {
                Text(
                    error,
                    fontSize = 13.sp,
                    color = Color(0xFFFF8A9B),
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
            }
        }
        item(key = "page:toolbar") {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.width(280.dp).height(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MusicPanel)
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = PageFaint, modifier = Modifier.size(16.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Filter songs, artists, albums", fontSize = 13.sp, color = PageFaint)
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear filter",
                            tint = PageFaint,
                            modifier = Modifier.size(15.dp).clickable { query = "" },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Box {
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(MusicPanel)
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                            .clickable { sortMenu = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = PageSecondary, modifier = Modifier.size(16.dp))
                        Text("Sort: ${sort.label}", fontSize = 13.sp, color = Color.White)
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = PageSecondary, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        sortOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(option.label, fontSize = 13.5.sp, color = if (option == sort) MusicAccent else Color.White)
                                },
                                trailingIcon = {
                                    if (option == sort) {
                                        Icon(
                                            when {
                                                option == MusicPlaylistSort.CUSTOM -> Icons.Filled.Check
                                                descending -> Icons.Filled.KeyboardArrowDown
                                                else -> Icons.Filled.KeyboardArrowUp
                                            },
                                            contentDescription = null,
                                            tint = MusicAccent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                },
                                onClick = {
                                    choose(option)
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "page:columns") {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(PageTablePanel),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PageColumnLabel("#", sort == MusicPlaylistSort.CUSTOM, null, Modifier.width(30.dp), TextAlign.End) {
                        sort = MusicPlaylistSort.CUSTOM
                        descending = false
                    }
                    Spacer(Modifier.width(40.dp))
                    PageColumnLabel("Title", sort == MusicPlaylistSort.TITLE, descending, Modifier.weight(1.5f)) {
                        choose(MusicPlaylistSort.TITLE)
                    }
                    PageColumnLabel("Album", sort == MusicPlaylistSort.ALBUM, descending, Modifier.weight(1f)) {
                        choose(MusicPlaylistSort.ALBUM)
                    }
                    if (hasDates) {
                        PageColumnLabel("Added", sort == MusicPlaylistSort.ADDED, descending, Modifier.width(110.dp)) {
                            choose(MusicPlaylistSort.ADDED)
                        }
                    }
                    Spacer(Modifier.width(28.dp))
                    Row(
                        Modifier.width(52.dp).clip(RoundedCornerShape(4.dp)).clickable { choose(MusicPlaylistSort.DURATION) },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = "Length",
                            tint = if (sort == MusicPlaylistSort.DURATION) MusicAccent else PageFaint,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Spacer(Modifier.width(28.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            }
        }

        val message = when {
            loading && tracks.isEmpty() -> null
            error != null && tracks.isEmpty() -> error
            tracks.isEmpty() -> if (likedHeader) "Songs you like will appear here." else "This playlist is empty."
            shown.isEmpty() -> "No songs match “$query”."
            else -> null
        }
        if ((loading && tracks.isEmpty()) || message != null) {
            item(key = "page:status") {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                        .background(PageTablePanel)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (message == null) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = MusicAccent,
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Text(message, fontSize = 13.5.sp, color = PageSecondary)
                    }
                }
            }
        }

        itemsIndexed(shown, key = { index, track -> "page:$index:${track.item.videoId}" }) { index, track ->
            MusicPlaylistRow(
                number = index + 1,
                track = track,
                info = infos[track.item.videoId],
                last = index == shown.lastIndex,
                playing = track.item.videoId == currentId,
                liked = track.item.videoId in likedIds,
                showDate = hasDates,
                onPlay = { onPlay(shownItems, index) },
                onToggleLike = { onToggleLike(track.item) },
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
                trailingIcon = if (onRemoveTrack != null) Icons.Filled.Delete else Icons.Filled.PlaylistAdd,
                trailingLabel = if (onRemoveTrack != null) "Remove" else "Add to queue",
                onTrailing = {
                    if (onRemoveTrack != null) onRemoveTrack(track.item) else onAddToQueue(track.item)
                },
            )
        }
    }
}

@Composable
private fun PageColumnLabel(
    label: String,
    active: Boolean,
    descending: Boolean?,
    modifier: Modifier,
    align: TextAlign = TextAlign.Start,
    onClick: () -> Unit,
) {
    Row(
        modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (align == TextAlign.End) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = if (active) MusicAccent else PageFaint,
            maxLines = 1,
        )
        if (active && descending != null) {
            Icon(
                if (descending) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = MusicAccent,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun PageLink(text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit = 13.sp, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text,
        fontSize = fontSize,
        color = if (hovered) Color.White else color,
        textDecoration = if (hovered) androidx.compose.ui.text.style.TextDecoration.Underline else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.hoverable(interaction)
            .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    )
}

@Composable
private fun MusicPlaylistRow(
    number: Int,
    track: MusicPageTrack,
    info: MusicTrackInfo?,
    last: Boolean,
    playing: Boolean,
    liked: Boolean,
    showDate: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onOpenAlbum: (String, String) -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    trailingLabel: String,
    onTrailing: () -> Unit,
) {
    // Only rows on screen look up their album/artists; leaving the screen cancels.
    LaunchedEffect(track.item.videoId) { MusicTrackInfoStore.load(track.item) }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = if (last) RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp) else RoundedCornerShape(0.dp)
    Box(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(PageTablePanel)
            .background(
                when {
                    playing -> MusicAccent.copy(alpha = 0.14f)
                    hovered -> Color.White.copy(alpha = 0.06f)
                    number % 2 == 0 -> PageRowStripe
                    else -> Color.Transparent
                },
            ),
    ) {
        if (playing) {
            Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(36.dp).background(MusicAccent))
        }
        Row(
            Modifier.fillMaxWidth()
                .hoverable(interaction)
                .clickable(onClick = onPlay)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterEnd) {
                if (hovered) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        number.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (playing) MusicAccent else PageFaint,
                    )
                }
            }
            MusicArtwork(track.item, 40.dp, 8.dp)
            Column(Modifier.weight(1.5f)) {
                Text(
                    track.item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (playing) MusicAccent else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MusicArtistLinks(track.item, fontSize = 13.sp, color = PageSecondary)
            }
            Box(Modifier.weight(1f)) {
                val album = info?.album
                val albumId = info?.albumId
                when {
                    album != null && albumId != null -> PageLink(album, PageSecondary) { onOpenAlbum(album, albumId) }
                    album != null -> Text(album, fontSize = 13.sp, color = PageSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else -> Text(if (info == null && track.item.localUrl == null) "…" else "", fontSize = 13.sp, color = PageFaint)
                }
            }
            if (showDate) {
                Text(
                    track.addedAtMs?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(PageDateFormat)
                    }.orEmpty(),
                    fontSize = 13.sp,
                    color = PageSecondary,
                    maxLines = 1,
                    modifier = Modifier.width(110.dp),
                )
            }
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (liked || hovered) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (liked) "Unlike" else "Like",
                        tint = if (liked) MusicLikeRed else PageSecondary,
                        modifier = Modifier.size(17.dp).likeBurst(liked).clickable(onClick = onToggleLike),
                    )
                }
            }
            Text(
                if (track.item.durationMs > 0) formatMusicDuration(track.item.durationMs) else "-:--",
                fontSize = 13.sp,
                color = PageSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(52.dp),
            )
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (hovered) {
                    Icon(
                        trailingIcon,
                        contentDescription = trailingLabel,
                        tint = PageSecondary,
                        modifier = Modifier.size(17.dp).clickable(onClick = onTrailing),
                    )
                }
            }
        }
    }
}

/** YouTube's Liked videos list, which mixes in ordinary videos (only it is filtered down to songs). */
private fun isLikedVideos(item: YouTubeVideoItem): Boolean =
    item.playlistId == "LL" || item.videoId == "LL" || item.title.equals("Liked videos", ignoreCase = true)

/** Watch later is YouTube video watching, not music, so it stays out of the Music playlists. */
private fun isWatchLater(item: YouTubeVideoItem): Boolean =
    item.playlistId == "WL" || item.videoId == "WL" || item.title.equals("Watch later", ignoreCase = true)

/** YouTube's own lists (Watch later, Liked videos) can't be deleted; only real playlist ids can. */
private fun canDeleteAccountPlaylist(item: YouTubeVideoItem): Boolean {
    val id = item.playlistId?.takeIf { it.isNotBlank() } ?: return false
    return id != "WL" && id != "LL" && id != "LM" && !id.startsWith("RD")
}
