package com.nuvio.app.features.youtube

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.SearchFilter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** Live: a small library produces distinct Home and Discover sections. */
class MusicCurationSmokeTest {
    @Test(timeout = 240_000)
    fun buildsDistinctSections() = runBlocking {
        val liked = listOf("Yessirr Tory Lanez", "Exchange Bryson Tiller", "Hotline Bling Drake", "Come and See Me PARTYNEXTDOOR")
            .mapNotNull { q ->
                InnertubeParser.parseSearchSongs(Innertube.search(q, SearchFilter.SONGS.params)).firstOrNull()?.let { song ->
                    YouTubeVideoItem(
                        videoId = song.videoId,
                        title = song.title,
                        author = song.artist,
                        thumbnailUrl = song.thumbnailUrl,
                        durationMs = 0L,
                        isLive = false,
                        channelId = song.artistId,
                        isMusic = true,
                    )
                }
            }
        assertTrue(liked.size >= 3, "search found only ${liked.size} seed songs")
        val c = MusicCurator.build(liked = liked, recents = liked.take(2), playlists = emptyList())
        println(
            "CURATION quick=${c.quickPicks.size} daily=${c.dailyMixes.map { "${it.name}:${it.tracks.size}:${it.artists}" }} " +
                "because=${c.because.map { "${it.title}:${it.items.size}" }} weekly=${c.discoverWeekly?.tracks?.size} " +
                "radar=${c.releaseRadar.size} fans=${c.fansAlsoLike.map { it.title }}",
        )
        assertTrue(c.quickPicks.isNotEmpty(), "no quick picks")
        assertTrue(c.dailyMixes.isNotEmpty(), "no daily mixes")
        val weekly = c.discoverWeekly?.tracks.orEmpty().map { it.videoId }.toSet()
        // Discover Weekly never repeats songs from your library.
        assertTrue(liked.none { it.videoId in weekly }, "Discover Weekly contains library songs")
    }
}
