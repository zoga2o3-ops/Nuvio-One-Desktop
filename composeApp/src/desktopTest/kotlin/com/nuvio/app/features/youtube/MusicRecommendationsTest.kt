package com.nuvio.app.features.youtube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MusicRecommendationsTest {
    @Test
    fun `artist normalization strips channel suffixes`() {
        assertEquals("daft punk", normalizeArtistName("Daft Punk - Topic"))
        assertEquals("daft punk", normalizeArtistName("Daft PunkVEVO"))
        assertEquals("daft punk", normalizeArtistName("Daft Punk Official"))
        assertEquals("daft punk", normalizeArtistName("Daft Punk • 38M views • 11 years ago"))
        assertEquals("", normalizeArtistName("Unknown Artist"))
    }

    @Test
    fun `clean artist name keeps display casing`() {
        assertEquals("Daft Punk", cleanArtistName("Daft Punk - Topic"))
        assertEquals("Daft Punk", cleanArtistName("Daft PunkVEVO"))
        assertEquals("Daft Punk", cleanArtistName("Daft Punk • 38M views • 11 years ago"))
    }

    @Test
    fun `title tokens drop video boilerplate`() {
        assertEquals(
            setOf("midnight", "city"),
            MusicRecommendations.significantTitleTokens("Midnight City (Official Video)"),
        )
        assertTrue(MusicRecommendations.significantTitleTokens("Get Lucky ft. Pharrell").contains("lucky"))
    }

    @Test
    fun `favorite artists come from likes and playlists`() {
        val keys = MusicRecommendations.favoriteArtistKeys(
            liked = listOf(video("like1", "Daft Punk - Topic")),
            playlists = listOf(
                MusicPlaylist(
                    id = "p1",
                    name = "Late night",
                    tracks = listOf(
                        track("p1a", "Daft PunkVEVO"),
                        track("p1b", "Unknown Artist"),
                        track("p1c", "The Weeknd"),
                    ),
                ),
            ),
        )
        assertEquals(setOf("daft punk", "the weeknd"), keys)
    }

    @Test
    fun `library key changes with likes and playlist edits`() {
        val liked = listOf(video("like1", "Daft Punk"))
        val playlist = MusicPlaylist(
            id = "p1",
            name = "Mix",
            tracks = listOf(track("p1a", "Daft Punk"), track("p1b", "The Weeknd")),
        )
        val base = MusicRecommendations.libraryKey(liked, emptyList())
        val withPlaylist = MusicRecommendations.libraryKey(liked, listOf(playlist))
        val withMoreLiked = MusicRecommendations.libraryKey(liked + video("like2", "The Weeknd"), listOf(playlist))
        val withEditedPlaylist = MusicRecommendations.libraryKey(
            liked,
            listOf(playlist.copy(tracks = playlist.tracks + track("p1c", "Daft Punk"))),
        )
        assertNotEquals(base, withPlaylist)
        assertNotEquals(withPlaylist, withMoreLiked)
        assertNotEquals(withPlaylist, withEditedPlaylist)
    }

    @Test
    fun `distinctive titles dedupe across upload channels`() {
        val official = video("a", "Daft Punk").copy(
            title = "Daft Punk - Get Lucky (Official Video) feat. Pharrell Williams",
        )
        val lyrics = video("b", "convar HUN").copy(
            title = "Daft Punk - Get Lucky (Lyrics) ft. Pharrell Williams",
        )
        val introA = video("c", "Artist A").copy(title = "Intro")
        val introB = video("d", "Artist B").copy(title = "Intro")
        assertEquals(MusicRecommendations.songKey(official), MusicRecommendations.songKey(lyrics))
        assertNotEquals(MusicRecommendations.songKey(introA), MusicRecommendations.songKey(introB))
    }

    @Test
    fun `same song survives different upload titles`() {
        val official = video("a", "convar HUN").copy(
            title = "Daft Punk - Get Lucky (Official Video) feat. Pharrell Williams",
        )
        val explicit = video("b", "greene142").copy(
            title = "Get Lucky (feat. Pharrell Williams) [Explicit]",
        )
        val mashup = video("c", "djbermusic").copy(
            title = "Daft Punk - Get Lucky vs. Bee Gees & Justin Timberlake (Mashup)",
        )
        val other = video("d", "Nile Rodgers").copy(title = "Wee Come Too Far")
        assertTrue(MusicRecommendations.sameSong(official, explicit))
        assertTrue(MusicRecommendations.sameSong(official, mashup))
        assertTrue(!MusicRecommendations.sameSong(official, other))
        val studio = video("e", "Dua Lipa").copy(title = "Dua Lipa - Don't Start Now (Official Video)")
        val live = video("f", "Chest3r").copy(
            title = "Dua Lipa - Don't Start Now (Live from the Royal Albert Hall) (Dj Smooth Remix)",
        )
        assertTrue(MusicRecommendations.sameSong(studio, live))
    }

    private fun video(id: String, author: String): YouTubeVideoItem = YouTubeVideoItem(
        videoId = id,
        title = "Song $id",
        author = author,
        thumbnailUrl = null,
        durationMs = 200_000L,
        isLive = false,
    )

    private fun track(id: String, author: String): MusicTrack = MusicTrack(
        videoId = id,
        title = "Song $id",
        author = author,
        durationMs = 200_000L,
    )
}
