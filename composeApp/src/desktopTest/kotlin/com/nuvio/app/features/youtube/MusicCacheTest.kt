package com.nuvio.app.features.youtube

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MusicCacheTest {
    @Test
    fun itemsSurviveTheCacheRoundTrip() {
        val playlist = YouTubeVideoItem(
            videoId = "PL123",
            title = "Gym Playlist",
            author = "Private • 325 videos",
            thumbnailUrl = "https://i.ytimg.com/p.jpg",
            durationMs = 0L,
            isLive = false,
            kind = YouTubeItemKind.PLAYLIST,
            playlistId = "PL123",
            reloadPageKey = "key",
            badgeText = "325",
        )
        val track = YouTubeVideoItem(
            videoId = "abc",
            title = "Tory Lanez - Yessirr (Official Audio)",
            author = "Tory Lanez",
            thumbnailUrl = "https://i.ytimg.com/t.jpg",
            durationMs = 224_000L,
            isLive = false,
            channelId = "UC1",
            isMusic = true,
        )
        val snapshot = MusicCacheSnapshot(
            accountKey = "acc",
            playlists = listOf(playlist.toCached()),
            contents = mapOf("PL123" to CachedMusicPlaylist(listOf(track.toCached()), complete = true, savedAtMs = 1L)),
            home = listOf(CachedMusicFeed("Quick picks", listOf(track.toCached()))),
            savedAtMs = 1L,
        )
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val back = json.decodeFromString<MusicCacheSnapshot>(json.encodeToString(snapshot))

        assertEquals(snapshot, back)
        val restoredPlaylist = back.playlists.single().toItem()
        assertEquals(YouTubeItemKind.PLAYLIST, restoredPlaylist.kind)
        assertEquals("PL123", restoredPlaylist.playlistId)
        assertEquals("key", restoredPlaylist.reloadPageKey)
        val restoredTrack = back.contents.getValue("PL123").items.single().toItem()
        assertEquals(track.copy(sourceItem = null), restoredTrack)
    }
}
