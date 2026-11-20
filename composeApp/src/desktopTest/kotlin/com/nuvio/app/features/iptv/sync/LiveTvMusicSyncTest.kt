package com.nuvio.app.features.iptv.sync

import com.nuvio.app.features.iptv.sync.LiveTvMerge.str
import com.nuvio.app.features.youtube.MusicPlaylist
import com.nuvio.app.features.youtube.MusicTrack
import java.nio.file.Files
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A stand-in Music library: tests never touch the person's real one. */
internal class FakeMusicLibrary(
    override var liked: List<MusicTrack> = emptyList(),
    override var playlists: List<MusicPlaylist> = emptyList(),
) : MusicSyncLibrary {
    override fun replace(liked: List<MusicTrack>, playlists: List<MusicPlaylist>): Boolean {
        if (liked == this.liked && playlists == this.playlists) return false
        this.liked = liked
        this.playlists = playlists
        return true
    }
}

class LiveTvMusicSyncTest {
    private val lib = FakeMusicLibrary()

    init {
        System.setProperty("nuvio.iptv.storageDir", Files.createTempDirectory("music-sync-test").toString())
        LiveTvMusicSync.library = lib
    }

    // As the TV's org.json writes it (MusicLibrarySync on the TV).
    private val tvLiked =
        """[{"id":"aaaaaaaaaaa","t":"Song A","a":"Artist A","d":215,"al":"Album A","alid":"MPREb_1"},""" +
            """{"id":"bbbbbbbbbbb","t":"Song B","a":"Artist B"}]"""
    private val tvLists =
        """[{"id":"p1","name":"Road trip","createdAt":1700000000000,"songs":[{"id":"ccccccccccc","t":"Song C","a":"Artist C","d":180}]}]"""

    private fun tvCopy() = JsonObject(
        mapOf(
            "music_liked" to entry(tvLiked),
            "music_playlists" to entry(tvLists),
            "playlists" to entry("[]"),
        ),
    )

    private fun entry(v: String) = JsonObject(linkedMapOf("t" to JsonPrimitive("str"), "v" to JsonPrimitive(v)))

    private fun track(id: String, title: String, local: String? = null) =
        MusicTrack(videoId = id, title = title, author = "X", durationMs = 200_500, localUrl = local)

    @Test
    fun tvLibraryArrivesAndGoesBackUnchanged() {
        lib.liked = listOf(track("zzzzzzzzzzz", "My file", local = "file:///C:/m.mp3"))
        val tv = tvCopy()
        assertTrue(LiveTvSyncLocal.syncHasSources(tv), "music alone counts as something to sync")
        assertTrue(LiveTvMusicSync.import(tv))
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb", "zzzzzzzzzzz"), lib.liked.map { it.videoId }, "local files stay")
        assertEquals(215_000L, lib.liked.first().durationMs)
        assertEquals("Road trip", lib.playlists.single().name)
        assertEquals("ccccccccccc", lib.playlists.single().tracks.single().videoId)

        // Exported again: exactly the TV's text (album fields and all), nothing to send back.
        val back = LiveTvMusicSync.export(tv)
        assertEquals(tv["music_liked"].toString(), back["music_liked"].toString())
        assertEquals(tv["music_playlists"].toString(), back["music_playlists"].toString())
    }

    @Test
    fun likesAndUnlikesTravel() {
        val tv = tvCopy()
        LiveTvMusicSync.import(tv)
        // Liked a new song here and unliked Song B.
        lib.liked = listOf(track("ddddddddddd", "Song D")) + lib.liked.filter { it.videoId != "bbbbbbbbbbb" }
        val local = JsonObject(tv + LiveTvMusicSync.export(tv))
        val merged = LiveTvMerge.merge(tv, local, tv, localWinsWithoutBase = false, removedHere = setOf("bbbbbbbbbbb"))
        val ids = LiveTvMerge.readSources((merged["music_liked"] as JsonObject).str("v"))
        assertEquals(setOf("aaaaaaaaaaa", "ddddddddddd"), ids.keys)
        assertEquals("Album A", ids.getValue("aaaaaaaaaaa").str("al"), "the TV's album name survives")

        // Without a removal made here, a song missing here is kept (never "remove everything").
        val kept = LiveTvMerge.merge(tv, local, tv, false, emptySet())
        assertTrue("bbbbbbbbbbb" in LiveTvMerge.readSources((kept["music_liked"] as JsonObject).str("v")))
    }

    @Test
    fun playlistEditsOnBothSidesCombine() {
        val tv = tvCopy()
        LiveTvMusicSync.import(tv)
        // Here: a new playlist. On the TV: Road trip renamed.
        lib.playlists = lib.playlists + MusicPlaylist("p2", "Gym", listOf(track("eeeeeeeeeee", "Song E")), 1L)
        val local = JsonObject(tv + LiveTvMusicSync.export(tv))
        val tvLists2 = tvLists.replace("Road trip", "Road trip 2026")
        val remote = JsonObject(tv + ("music_playlists" to entry(tvLists2)))
        val merged = LiveTvMerge.merge(tv, local, remote, false, emptySet())
        LiveTvMusicSync.import(merged)
        assertEquals(listOf("Road trip 2026", "Gym"), lib.playlists.map { it.name })
        assertFalse(LiveTvMusicSync.import(merged), "applying the same copy twice changes nothing")
    }

    @Suppress("unused")
    private fun arr(vararg s: String) = JsonArray(s.map(::JsonPrimitive))
}
