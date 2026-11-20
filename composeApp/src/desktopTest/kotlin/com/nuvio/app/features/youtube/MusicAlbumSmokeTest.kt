package com.nuvio.app.features.youtube

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.SearchFilter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** Live: an album found through search opens with its tracks. */
class MusicAlbumSmokeTest {
    @Test(timeout = 120_000)
    fun albumOpensWithTracks() = runBlocking {
        val song = InnertubeParser.parseSearchSongs(Innertube.search("Attention Charlie Puth", SearchFilter.SONGS.params))
            .firstOrNull { !it.albumId.isNullOrBlank() }
        assertTrue(song != null, "no song with an album in search results")
        val tracks = MusicBitChordRepository.albumSongs(song!!.albumId!!, song.albumName)
        println("ALBUM ${song.albumName} (${song.albumId}) -> ${tracks.size} tracks: ${tracks.take(3).map { it.title }}")
        assertTrue(tracks.isNotEmpty(), "album ${song.albumId} returned no tracks")
    }

    @Test(timeout = 120_000)
    fun fullAlbumListsAllTracks() = runBlocking {
        val song = InnertubeParser.parseSearchSongs(Innertube.search("Yessirr Tory Lanez", SearchFilter.SONGS.params))
            .firstOrNull { !it.albumId.isNullOrBlank() }
        assertTrue(song != null, "no song with an album in search results")
        val tracks = MusicBitChordRepository.albumSongs(song!!.albumId!!, song.albumName)
        println("ALBUM ${song.albumName} (${song.albumId}) -> ${tracks.size} tracks")
        assertTrue(tracks.size >= 5, "album ${song.albumName} returned only ${tracks.size} tracks")
        assertTrue(tracks.all { MusicTrackInfoStore.info[it.videoId]?.album != null }, "album names not recorded")
    }
}
