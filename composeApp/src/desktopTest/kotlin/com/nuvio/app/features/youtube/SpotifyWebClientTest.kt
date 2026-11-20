package com.nuvio.app.features.youtube

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SpotifyWebClientTest {
    @Test
    fun `source links parse`() {
        val playlist = SpotifySource.parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
        assertTrue(playlist is SpotifySource.Playlist)
        assertTrue(SpotifySource.parse("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M") is SpotifySource.Playlist)
        assertTrue(SpotifySource.parse("https://open.spotify.com/collection/tracks") == SpotifySource.Liked)
    }

    @Test
    fun `public playlist loads from the web player api`() = runBlocking {
        val client = SpotifyWebClient.connect(null).getOrThrow()
        val playlist = client.playlist("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M").getOrThrow()
        assertTrue(playlist.name.isNotBlank())
        assertTrue(playlist.tracks.isNotEmpty())
        assertTrue(playlist.tracks.first().name.isNotBlank())
    }
}
