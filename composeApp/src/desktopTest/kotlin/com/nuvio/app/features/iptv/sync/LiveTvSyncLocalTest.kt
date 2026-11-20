package com.nuvio.app.features.iptv.sync

import com.nuvio.app.features.iptv.IptvGuideSourceStore
import com.nuvio.app.features.iptv.IptvSettingsStore
import com.nuvio.app.features.iptv.IptvSourceStore
import com.nuvio.app.features.iptv.sync.LiveTvMerge.str
import java.nio.file.Files
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A desktop and a TV sharing one Live TV copy (offline: temp folder, no network). */
class LiveTvSyncLocalTest {
    init {
        System.setProperty("nuvio.iptv.storageDir", Files.createTempDirectory("livetv-sync-test").toString())
        LiveTvMusicSync.library = FakeMusicLibrary()
    }

    // Written the way the TV's org.json writes it (escaped slashes, TV field order).
    private val playlistsV =
        """[{"id":"pl1","name":"My Xtream","url":"","enabled":true,"userAgent":"","useEmbeddedEpg":true,""" +
            """"xtreamServer":"http:\/\/x.example:8080","xtreamUsername":"u","xtreamPassword":"p","lastUpdatedMs":0,""" +
            """"lastError":"","channelCount":0,"importVod":true,"importLive":true,"streamFormat":"ts","serverTimezone":"",""" +
            """"catchupNoHlsSince":0,"logoSource":"playlist"},""" +
            """{"id":"pl2","name":"M3U","url":"https:\/\/m.example\/list.m3u","enabled":true,"userAgent":"VLC","useEmbeddedEpg":true,""" +
            """"xtreamServer":"","xtreamUsername":"","xtreamPassword":"","lastUpdatedMs":0,"lastError":"","channelCount":0,""" +
            """"importVod":false,"importLive":true,"streamFormat":"auto","serverTimezone":"","catchupNoHlsSince":0,"logoSource":"playlist"},""" +
            """{"id":"pl3","name":"Off one","url":"https:\/\/off.example\/a.m3u","enabled":false,"userAgent":"","useEmbeddedEpg":true,""" +
            """"xtreamServer":"","xtreamUsername":"","xtreamPassword":"","lastUpdatedMs":0,"lastError":"","channelCount":0,""" +
            """"importVod":false,"importLive":true,"streamFormat":"auto","serverTimezone":"","catchupNoHlsSince":0,"logoSource":"playlist"}]"""
    private val epgsV =
        """[{"id":"g1","name":"Big guide","url":"https:\/\/epg.example\/all.xml","enabled":true,"lastUpdatedMs":0,"lastError":"","programCount":0}]"""

    private fun tvCopy(): JsonObject = LiveTvMerge.parse(
        """{"playlists":{"t":"str","v":${quote(playlistsV)}},""" +
            """"epg_sources":{"t":"str","v":${quote(epgsV)}},""" +
            """"show_channel_names":{"t":"b","v":false},"epg_offset_minutes":{"t":"i","v":30},""" +
            """"hidden_groups":{"t":"s","v":["pl1::Adult","pl2::Adult","News"]},""" +
            """"group_order":{"t":"str","v":"[\"pl1::Sports\",\"News\"]"},""" +
            """"parental_pin":{"t":"str","v":"1234"},"favorites_p2":{"t":"str","v":"[\"x\"]"}}""",
    )!!

    private fun quote(raw: String) = kotlinx.serialization.json.JsonPrimitive(raw).toString()

    @Test
    fun tvCopyRoundTripsThroughTheDesktop() {
        val tv = tvCopy()
        assertTrue(LiveTvSyncLocal.importFromSync(tv))

        val sources = IptvSourceStore.load()
        assertEquals(listOf("pl1", "pl2"), sources.map { it.id }, "switched-off playlists stay on the TVs")
        val xtream = sources.first()
        assertEquals("xtream", xtream.kind)
        assertEquals("http://x.example:8080", xtream.url)
        assertEquals("u", xtream.username)
        assertEquals("https://m.example/list.m3u", sources[1].url)
        assertEquals(listOf("g1"), IptvGuideSourceStore.load().map { it.id })

        val settings = IptvSettingsStore.load()
        assertFalse(settings.showChannelNames)
        assertEquals(30, settings.epgOffsetMinutes)
        assertEquals(listOf("Adult", "News"), settings.hiddenGroups)
        assertEquals(listOf("Sports", "News"), settings.customGroupOrder)

        // Sending it back changes nothing the TV cares about.
        val back = LiveTvSyncLocal.exportForSync()
        for (key in tv.keys) assertEquals(tv[key].toString(), back[key].toString(), "TV setting $key came back changed")
        val merged = LiveTvMerge.merge(tv, back, tv, localWinsWithoutBase = false, removedHere = emptySet())
        // Every TV setting survives the combine unchanged (TV-only ones included).
        for (key in tv.keys) assertEquals(tv[key].toString(), merged[key].toString())
    }

    @Test
    fun removalsAndAdditionsTravel() {
        val tv = tvCopy()
        LiveTvSyncLocal.importFromSync(tv)

        // Removed on this desktop: remembered, and stays removed in the combined copy.
        IptvSourceStore.save(IptvSourceStore.load().filter { it.id != "pl2" })
        assertTrue("pl2" in LiveTvSyncLocal.removedSourceIds())
        val local = LiveTvSyncLocal.exportForSync()
        val merged = LiveTvMerge.merge(tv, local, tv, localWinsWithoutBase = false, removedHere = LiveTvSyncLocal.removedSourceIds())
        val ids = LiveTvMerge.readSources((merged["playlists"] as JsonObject).str("v")).keys
        assertEquals(setOf("pl1", "pl3"), ids, "removed here, kept off; switched-off TV playlist kept")

        // A playlist added on a TV arrives here.
        val added = playlistsV.replace("]", ",{\"id\":\"pl4\",\"name\":\"New\",\"url\":\"https:\\/\\/n.example\\/n.m3u\",\"enabled\":true}]")
        val tv2 = JsonObject(tv + ("playlists" to LiveTvMerge.parse("{\"t\":\"str\",\"v\":${quote(added)}}")!!))
        val combined = LiveTvMerge.merge(tv, LiveTvSyncLocal.exportForSync(), tv2, false, LiveTvSyncLocal.removedSourceIds())
        LiveTvSyncLocal.importFromSync(combined)
        val now = IptvSourceStore.load().map { it.id }
        assertTrue("pl4" in now)
        assertFalse("pl2" in now)
        assertNotNull(IptvSourceStore.load().firstOrNull { it.id == "pl4" && it.url == "https://n.example/n.m3u" })
    }

    @Test
    fun firstSyncKeepsBothSidesPersonalChoicesAndThisOrder() {
        val tv = tvCopy()
        val desk = LiveTvMerge.parse(
            """{"hidden_groups":{"t":"s","v":["Kids"]},"group_order":{"t":"str","v":"[\"Movies\",\"News\"]"},""" +
                """"compact_rows":{"t":"b","v":true}}""",
        )!!
        val merged = LiveTvMerge.merge(null, desk, tv, localWinsWithoutBase = true, removedHere = emptySet())
        val hidden = (merged["hidden_groups"] as JsonObject)["v"].toString()
        assertTrue("Kids" in hidden && "News" in hidden && "pl1::Adult" in hidden, hidden)
        assertEquals("[\"Movies\",\"News\"]", (merged["group_order"] as JsonObject).str("v"))
        assertEquals("1234", (merged["parental_pin"] as JsonObject).str("v"), "settings only the TV has are kept")
        assertEquals(3, LiveTvMerge.readSources((merged["playlists"] as JsonObject).str("v")).size)
    }

    @Test
    fun aReorderHereWins() {
        val tv = tvCopy()
        LiveTvSyncLocal.importFromSync(tv)
        IptvSourceStore.save(IptvSourceStore.load().reversed())
        val merged = LiveTvMerge.merge(tv, LiveTvSyncLocal.exportForSync(), tv, false, emptySet())
        assertEquals(listOf("pl2", "pl1", "pl3"), LiveTvMerge.readSources((merged["playlists"] as JsonObject).str("v")).keys.toList())
    }

    @Test
    fun anEmptyOrderHereNeverWipesTheTvsOrder() {
        val tv = tvCopy()
        LiveTvSyncLocal.importFromSync(tv)
        // This computer has no arrangement of its own.
        IptvSettingsStore.save(IptvSettingsStore.load().copy(customGroupOrder = emptyList(), hiddenGroups = emptyList()))
        val local = LiveTvSyncLocal.exportForSync()
        // First combine: this device's own arrangement wins, so it must not be an empty one.
        val merged = LiveTvMerge.merge(null, local, tv, localWinsWithoutBase = true, removedHere = emptySet())
        assertEquals("[\"pl1::Sports\",\"News\"]", (merged["group_order"] as JsonObject).str("v"))
        assertTrue("pl1::Adult" in (merged["hidden_groups"] as JsonObject)["v"].toString())
        // And an empty shared order doesn't clear this computer's.
        IptvSettingsStore.save(IptvSettingsStore.load().copy(customGroupOrder = listOf("Movies", "News")))
        val emptied = JsonObject(tv + ("group_order" to LiveTvMerge.parse("""{"t":"str","v":"[]"}""")!!))
        LiveTvSyncLocal.importFromSync(emptied)
        assertEquals(listOf("Movies", "News"), IptvSettingsStore.load().customGroupOrder)
    }

    @Test
    fun duplicatePlaylistsCollapseToTheCopyTheTvUses() {
        val tv = tvCopy()
        LiveTvSyncLocal.importFromSync(tv)
        // The same Xtream login added on this computer too, under its own id, with favourites on it.
        val mine = com.nuvio.app.features.iptv.IptvSource("desk1", "TREX", "http://x.example:8080", kind = "xtream", username = "u", password = "p")
        IptvSourceStore.save(listOf(mine) + IptvSourceStore.load())
        com.nuvio.app.features.iptv.IptvFavoritesStore.save(setOf("desk1:abc", "pl2:def"))
        assertTrue(LiveTvSyncLocal.collapseDuplicates())
        assertEquals(listOf("pl1", "pl2"), IptvSourceStore.load().map { it.id }, "kept the TVs' copy (pl1 is in their groups)")
        assertEquals(setOf("pl1:abc", "pl2:def"), com.nuvio.app.features.iptv.IptvFavoritesStore.load())
        assertTrue("desk1" in LiveTvSyncLocal.removedSourceIds(), "the extra copy is removed on the other devices too")
        assertFalse(LiveTvSyncLocal.collapseDuplicates(), "nothing left to collapse")
    }

    @Test
    fun desktopOnlySettingsTravelBetweenDesktops() {
        LiveTvSyncLocal.importFromSync(tvCopy())
        IptvSettingsStore.save(IptvSettingsStore.load().copy(cleanGroupNames = true, groupSort = "custom"))
        com.nuvio.app.features.iptv.IptvFavoritesStore.save(setOf("pl1:abc"))
        val copy = LiveTvSyncLocal.exportForSync()
        // Another desktop starting fresh.
        IptvSettingsStore.save(IptvSettingsStore.load().copy(cleanGroupNames = false, groupSort = "provider"))
        com.nuvio.app.features.iptv.IptvFavoritesStore.save(emptySet())
        LiveTvSyncLocal.importFromSync(copy)
        assertTrue(IptvSettingsStore.load().cleanGroupNames)
        assertEquals("custom", IptvSettingsStore.load().groupSort)
        assertEquals(setOf("pl1:abc"), com.nuvio.app.features.iptv.IptvFavoritesStore.load())
    }
}
