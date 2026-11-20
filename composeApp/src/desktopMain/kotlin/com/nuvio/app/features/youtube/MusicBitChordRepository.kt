package com.nuvio.app.features.youtube

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.ShelfLayout
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class BitChordShelf(
    val title: String,
    val subtitle: String,
    val layout: ShelfLayout,
    val items: List<YouTubeVideoItem>,
)

internal data class BitChordMood(
    val title: String,
    val browseId: String,
    val params: String?,
    val thumbnailUrl: String?,
    val color: Long?,
)

internal data class BitChordMoodSection(
    val title: String,
    val moods: List<BitChordMood>,
)

/**
 * The BitChord/YouTube Music browse layer, ported from the TV app's BitChord data client.
 *
 * It is deliberately separate from the SmartTube video engine: the browse pages, mood categories,
 * charts, new releases and radio queues come from YouTube Music's WEB_REMIX responses, while the
 * SmartTube engine still resolves the audio/video stream for playback.
 */
/** A labelled group of search results, shown as one row (Top result, Songs, Albums, …). */
internal data class MusicSearchSection(
    val title: String,
    val items: List<YouTubeVideoItem>,
    /** One type label per item (Song, Album, Artist, Playlist) when the section mixes types; empty otherwise. */
    val labels: List<String> = emptyList(),
)

internal object MusicBitChordRepository {
    private const val BitChordBrowsePrefix = "bitchord:"
    private const val TimeoutMs = 20_000L

    /** Most pages of a Songs search to follow (about 20 tracks each). */
    private const val MaxSongPages = 40

    suspend fun home(): List<BitChordShelf> = safe {
        InnertubeParser.parseHome(Innertube.browse("FEmusic_home")).map { it.toShelf() }
    }

    suspend fun newReleases(): List<BitChordShelf> = safe {
        val albums = InnertubeParser.parseHome(Innertube.browse("FEmusic_new_releases_albums"))
        val all = InnertubeParser.parseHome(Innertube.browse("FEmusic_new_releases"))
        (albums + all)
            .distinctBy { it.title.ifBlank { it.items.firstOrNull()?.title.orEmpty() } }
            .map { it.toShelf() }
    }

    suspend fun charts(): List<BitChordShelf> = safe {
        InnertubeParser.parseHome(
            Innertube.browse("FEmusic_charts", "sgYPRkVtdXNpY19leHBsb3Jl"),
        ).map { it.toShelf() }
    }

    suspend fun moods(): List<BitChordMoodSection> = safe {
        InnertubeParser.parseMoodAndGenres(Innertube.browse("FEmusic_moods_and_genres"))
            .map { section ->
                BitChordMoodSection(
                    title = section.title,
                    moods = section.items.map { it.toMood() },
                )
            }
    }

    suspend fun moodShelves(mood: BitChordMood): List<BitChordShelf> = safe {
        InnertubeParser.parseHome(Innertube.browse(mood.browseId, mood.params)).map { it.toShelf() }
    }

    suspend fun browseSongs(browseId: String): List<YouTubeVideoItem> = safe {
        val response = Innertube.browse(browseId)
        InnertubeParser.collectSongsDeep(response)
            .distinctBy { it.videoId }
            .filter { it.videoId.isNotBlank() }
            .map { it.toVideoItem() }
    }

    /**
     * An album's tracks. Album pages list tracks in their own layout (and
     * often without per-track art), so read the rows first, then fall back to
     * queueing the album's playlist, which always lists every track. Every
     * track's album is recorded for the Album column as a side effect.
     */
    suspend fun albumSongs(albumId: String, albumName: String?): List<YouTubeVideoItem> = safe {
        val response = Innertube.browse(albumId)
        val header = runCatching { InnertubeParser.parseBrowseHeader(response) }.getOrNull()
        var songs = InnertubeParser.collectSongsDeep(response)
            .filter { it.videoId.isNotBlank() }
            .distinctBy { it.videoId }
        if (songs.isEmpty()) {
            val playlistId = InnertubeParser.parseAlbumPlaylistId(response)
            if (playlistId != null) {
                songs = InnertubeParser.parseWatchQueue(Innertube.nextPlaylist(playlistId), preferAudio = true)
                    .filter { it.videoId.isNotBlank() }
                    .distinctBy { it.videoId }
            }
        }
        val name = albumName ?: header?.title
        songs.map { song ->
            val item = song.toVideoItem()
            MusicTrackInfoStore.remember(
                item.videoId,
                MusicTrackInfo(
                    album = song.albumName ?: name,
                    albumId = song.albumId ?: albumId,
                    artists = song.artists.filter { it.name.isNotBlank() }
                        .map { MusicTrackArtist(it.name.trim(), it.browseId) },
                ),
            )
            if (item.thumbnailUrl == null && header?.thumbnailUrl != null) item.copy(thumbnailUrl = header.thumbnailUrl) else item
        }
    }

    /** Finds an artist's YouTube Music id by name (anonymous artist search). */
    suspend fun artistBrowseId(name: String): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TimeoutMs) {
            runCatching {
                val want = normalizeArtistName(name)
                InnertubeParser.parseSearch(Innertube.search(name, com.music.bitchord.data.model.SearchFilter.ARTISTS.params))
                    .filterIsInstance<com.music.bitchord.data.model.SearchResult.Browse>()
                    .map { it.item }
                    .filter { it.type == com.music.bitchord.data.model.BrowseType.ARTIST }
                    .firstOrNull { normalizeArtistName(it.title) == want }
                    ?.browseId
            }.getOrNull()
        }
    }

    /** An artist's releases (with their year when the card shows one) and related artists. */
    internal data class ArtistOverview(
        val releases: List<Pair<YouTubeVideoItem, Int?>>,
        val related: List<YouTubeVideoItem>,
    )

    suspend fun artistOverview(browseId: String, artistName: String): ArtistOverview? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TimeoutMs) {
            runCatching {
                val page = InnertubeParser.parseArtistPage(Innertube.browse(browseId))
                val yearPattern = Regex("""\b(19|20)\d{2}\b""")
                val releases = page.sections
                    .filter { shelf ->
                        val t = shelf.title.lowercase()
                        "album" in t || "single" in t || " ep" in t || t.startsWith("ep")
                    }
                    .flatMap { it.items }
                    .mapNotNull { shelfItem ->
                        val item = shelfItem.toVideoItem() ?: return@mapNotNull null
                        val year = yearPattern.find(shelfItem.subtitle)?.value?.toIntOrNull()
                        item.copy(author = listOf(artistName, shelfItem.subtitle).filter { it.isNotBlank() }.joinToString(" • ")) to year
                    }
                val related = page.sections
                    .filter { shelf ->
                        val t = shelf.title.lowercase()
                        "also like" in t || "similar" in t || "related" in t
                    }
                    .flatMap { it.items }
                    .mapNotNull { shelfItem ->
                        val id = shelfItem.browseId?.takeIf { it.startsWith("UC") } ?: return@mapNotNull null
                        YouTubeVideoItem(
                            videoId = id,
                            title = shelfItem.title,
                            author = shelfItem.subtitle.takeIf { it.isNotBlank() },
                            thumbnailUrl = shelfItem.thumbnailUrl,
                            durationMs = 0L,
                            isLive = false,
                            kind = YouTubeItemKind.CHANNEL,
                            channelId = id,
                            isMusic = true,
                        )
                    }
                ArtistOverview(releases, related)
            }.getOrNull()
        }
    }

    /**
     * An artist page as YouTube Music lays it out. Songs come from the page's full song list when it links
     * one (the top shelf alone is only about five songs); the other shelves keep their own titles.
     */
    suspend fun artistSections(browseId: String): List<MusicSearchSection>? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TimeoutMs) {
            runCatching {
                val page = InnertubeParser.parseArtistPage(Innertube.browse(browseId))
                val fullSongs = page.moreSongsBrowseId
                    ?.let { runCatching { browseSongs(it) }.getOrNull() }
                    .orEmpty()
                val songs = fullSongs.ifEmpty {
                    page.songs.map { it.toVideoItem() }
                }.filter { it.kind == YouTubeItemKind.VIDEO }.distinctBy { it.videoId }
                buildList {
                    if (songs.isNotEmpty()) add(MusicSearchSection("Top songs", songs))
                    page.sections.forEach { shelf ->
                        val items = shelf.items.mapNotNull { it.toVideoItem() }.distinctBy { it.videoId }
                        if (items.isNotEmpty() && shelf.title.isNotBlank()) {
                            add(MusicSearchSection(shelf.title, items))
                        }
                    }
                }
            }.getOrNull()
        }
    }

    suspend fun radio(videoId: String): List<YouTubeVideoItem> = safe {
        if (videoId.isBlank()) return@safe emptyList()
        InnertubeParser.parseWatchQueue(Innertube.next(videoId), preferAudio = true)
            .distinctBy { it.videoId }
            .filter { it.videoId.isNotBlank() }
            .map { it.toVideoItem() }
    }

    /**
     * YouTube Music search, the way its own search page groups it: a top result, then songs,
     * albums, artists and playlists. [filter] narrows to one kind (first page only). Null when the
     * request failed or YouTube Music is limiting requests.
     */
    suspend fun search(query: String, filter: SearchFilter): List<MusicSearchSection>? = withContext(Dispatchers.IO) {
        if (query.isBlank() || MusicTrackInfoStore.isPaused) return@withContext null
        val page = runCatching {
            withTimeoutOrNull(TimeoutMs) {
                InnertubeParser.parseSearchPage(
                    Innertube.search(query, filter.params),
                    includeVideos = filter == SearchFilter.VIDEOS,
                )
            }
        }.getOrNull() ?: return@withContext null
        var rows = page.rows
        // The Songs search is one page at a time (about 20 tracks). An artist's songs run on for pages, so follow
        // the continuation until the list ends, up to a cap.
        if (filter == SearchFilter.SONGS) {
            var token = page.continuation
            var pages = 1
            while (token != null && pages < MaxSongPages) {
                val current = token
                val next = runCatching {
                    withTimeoutOrNull(TimeoutMs) {
                        InnertubeParser.parseSearchPage(Innertube.searchContinuation(current))
                    }
                }.getOrNull() ?: break
                if (next.rows.isEmpty()) break
                rows = rows + next.rows
                token = next.continuation
                pages++
            }
        }
        buildSearchSections(rows, filter, query)
    }

    /** The All search, the way YouTube Music shows it: the top result, its songs, then everything else in order. */
    /** Whether [author]'s credit names [artist] (letters and digits only, so punctuation and case don't matter). */
    internal fun creditsArtist(author: String?, artist: String): Boolean {
        val want = artist.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        if (want.isEmpty()) return false
        val have = author.orEmpty().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        return have.contains(want)
    }

    /**
     * Whether a result is about the query: its title or artist has a word of the query (three letters or
     * more). Stops unrelated uploads from filling the list.
     */
    private fun relevantTo(query: String, item: YouTubeVideoItem): Boolean {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }
        if (words.isEmpty()) return true
        val text = (item.title + " " + item.author.orEmpty()).lowercase()
        return words.any { text.contains(it) }
    }

    private fun buildAllSections(rows: List<SearchResult>, query: String): List<MusicSearchSection> {
        val first = rows.firstOrNull()
        val topRaw = when (first) {
            is SearchResult.TopTrack -> first.song.toVideoItem() to "Song"
            is SearchResult.Browse -> first.item.toSearchItem() to when (first.item.type) {
                BrowseType.ALBUM -> "Album"
                BrowseType.ARTIST -> "Artist"
                BrowseType.PLAYLIST, BrowseType.OTHER -> "Playlist"
            }
            else -> null
        }
        val top = topRaw?.takeIf { relevantTo(query, it.first) }
        // When the top result is an artist, the list is about that artist: keep only what they are credited on.
        val topArtist = topRaw?.takeIf { it.second == "Artist" }?.first?.title
        val mixed = rows.drop(if (topRaw != null) 1 else 0).map { row ->
            when (row) {
                is SearchResult.TopTrack -> row.song.toVideoItem() to "Song"
                is SearchResult.Track -> row.song.toVideoItem() to "Song"
                is SearchResult.Browse -> row.item.toSearchItem() to when (row.item.type) {
                    BrowseType.ALBUM -> "Album"
                    BrowseType.ARTIST -> "Artist"
                    BrowseType.PLAYLIST, BrowseType.OTHER -> "Playlist"
                }
            }
        }.distinctBy { it.first.videoId }
            .filter { relevantTo(query, it.first) }
            .filter { topArtist == null || creditsArtist(it.first.author, topArtist) }
        val songs = mixed.filter { it.second == "Song" }.map { it.first }
        return buildList {
            top?.let { add(MusicSearchSection("Top result", listOf(it.first), listOf(it.second))) }
            if (songs.isNotEmpty()) add(MusicSearchSection("Songs", songs.take(5)))
            if (mixed.isNotEmpty()) {
                add(MusicSearchSection("More results", mixed.map { it.first }, mixed.map { it.second }))
            }
        }
    }

    private fun buildSearchSections(rows: List<SearchResult>, filter: SearchFilter, query: String): List<MusicSearchSection> {
        if (filter == SearchFilter.ALL) return buildAllSections(rows, query)
        val songs = mutableListOf<YouTubeVideoItem>()
        val albums = mutableListOf<YouTubeVideoItem>()
        val artists = mutableListOf<YouTubeVideoItem>()
        val playlists = mutableListOf<YouTubeVideoItem>()
        // The parser puts the promoted top result first; it gets its own row.
        val first = rows.firstOrNull()
        val top = when (first) {
            is SearchResult.TopTrack -> first.song.toVideoItem()
            is SearchResult.Browse -> first.item.toSearchItem()
            else -> null
        }
        rows.drop(if (top != null) 1 else 0).forEach { row ->
            when (row) {
                is SearchResult.TopTrack -> songs += row.song.toVideoItem()
                is SearchResult.Track -> songs += row.song.toVideoItem()
                is SearchResult.Browse -> when (row.item.type) {
                    BrowseType.ALBUM -> albums += row.item.toSearchItem()
                    BrowseType.ARTIST -> artists += row.item.toSearchItem()
                    BrowseType.PLAYLIST, BrowseType.OTHER -> playlists += row.item.toSearchItem()
                }
            }
        }
        return buildList {
            top?.let { add(MusicSearchSection("Top result", listOf(it))) }
            if (songs.isNotEmpty()) add(MusicSearchSection(if (filter == SearchFilter.VIDEOS) "Videos" else "Songs", songs.distinctBy { it.videoId }))
            if (albums.isNotEmpty()) add(MusicSearchSection("Albums", albums.distinctBy { it.videoId }))
            if (artists.isNotEmpty()) add(MusicSearchSection("Artists", artists.distinctBy { it.videoId }))
            if (playlists.isNotEmpty()) add(MusicSearchSection("Playlists", playlists.distinctBy { it.videoId }))
        }
    }

    /** Albums, singles and playlists open through their browse id; artists open by channel id. */
    private fun BrowseItem.toSearchItem(): YouTubeVideoItem = when (type) {
        BrowseType.ARTIST -> YouTubeVideoItem(
            videoId = browseId,
            title = title,
            author = subtitle.takeIf { it.isNotBlank() },
            thumbnailUrl = thumbnailUrl,
            durationMs = 0L,
            isLive = false,
            kind = YouTubeItemKind.CHANNEL,
            channelId = browseId,
            isMusic = true,
        )
        else -> YouTubeVideoItem(
            videoId = BitChordBrowsePrefix + browseId,
            title = title,
            author = subtitle.takeIf { it.isNotBlank() },
            thumbnailUrl = thumbnailUrl,
            durationMs = 0L,
            isLive = false,
            kind = YouTubeItemKind.PLAYLIST,
            playlistId = browseId,
            isMusic = true,
        )
    }

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TimeoutMs) {
            runCatching { block() }.getOrElse { emptyList() }
        }.orEmpty()
    }

    private fun HomeShelf.toShelf(): BitChordShelf = BitChordShelf(
        title = title,
        subtitle = subtitle,
        layout = layout,
        items = items.mapNotNull { it.toVideoItem() },
    )

    private fun ShelfItem.toVideoItem(): YouTubeVideoItem? {
        val cleanTitle = title.trim().ifBlank { return null }
        val video = videoId?.takeIf { it.isNotBlank() }
        if (video != null) {
            return YouTubeVideoItem(
                videoId = video,
                title = cleanTitle,
                author = subtitle.takeIf { it.isNotBlank() },
                thumbnailUrl = thumbnailUrl,
                durationMs = 0L,
                isLive = false,
                kind = YouTubeItemKind.VIDEO,
                isMusic = true,
            )
        }
        val browse = browseId?.takeIf { it.isNotBlank() } ?: return null
        return YouTubeVideoItem(
            videoId = BitChordBrowsePrefix + browse,
            title = cleanTitle,
            author = subtitle.takeIf { it.isNotBlank() },
            thumbnailUrl = thumbnailUrl,
            durationMs = 0L,
            isLive = false,
            kind = YouTubeItemKind.PLAYLIST,
            playlistId = browse,
            isMusic = true,
        )
    }

    private fun Song.toVideoItem(): YouTubeVideoItem = YouTubeVideoItem(
        videoId = videoId,
        title = title,
        author = artist.takeIf { it.isNotBlank() },
        thumbnailUrl = thumbnailUrl,
        durationMs = parseDuration(durationText),
        isLive = false,
        kind = YouTubeItemKind.VIDEO,
        channelId = artistId,
        isMusic = true,
    )

    private fun MoodGenre.toMood(): BitChordMood = BitChordMood(
        title = title,
        browseId = browseId,
        params = params,
        thumbnailUrl = thumbnailUrl,
        color = stripeColor,
    )

    internal fun bitChordBrowseId(item: YouTubeVideoItem): String? =
        item.videoId.removePrefix(BitChordBrowsePrefix).takeIf {
            item.videoId.startsWith(BitChordBrowsePrefix) && it.isNotBlank()
        }

    internal fun isBitChordBrowse(item: YouTubeVideoItem): Boolean = isBitChordBrowse(item.videoId)

    private fun isBitChordBrowse(videoId: String): Boolean = videoId.startsWith(BitChordBrowsePrefix)

    private fun parseDuration(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val parts = text.split(':').mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> ((parts[0] * 60L + parts[1]) * 60L + parts[2]) * 1000L
            else -> 0L
        }
    }
}
