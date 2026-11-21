package com.nuvio.app.features.iptv

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StalkerPortalTest {
    @Test
    fun `a MAC address is tidied up and checked`() {
        assertEquals("00:1A:79:AB:CD:EF", StalkerClient.normalizeMac("00-1a-79-ab-cd-ef"))
        assertEquals("00:1A:79:AB:CD:EF", StalkerClient.normalizeMac(" 001a79abcdef "))
        assertTrue(StalkerClient.isValidMac("00:1a:79:ab:cd:ef"))
        assertFalse(StalkerClient.isValidMac("00:1A:79"))
        assertFalse(StalkerClient.isValidMac("hello"))
    }

    @Test
    fun `portal addresses give the places to try`() {
        assertEquals(
            listOf(
                "http://host:8080/portal.php",
                "http://host:8080/server/load.php",
                "http://host:8080/stalker_portal/server/load.php",
            ),
            StalkerClient.endpointCandidates("http://host:8080/c/"),
        )
        assertEquals(
            listOf("http://host/stalker_portal/portal.php", "http://host/stalker_portal/server/load.php"),
            StalkerClient.endpointCandidates("host/stalker_portal/c"),
        )
        assertEquals("http://host/portal.php", StalkerClient.endpointCandidates("http://host/portal.php").first())
        assertEquals("https://secure.example", StalkerClient.splitPortal("https://secure.example/c/").first)
    }

    @Test
    fun `stream links lose the player name in front`() {
        assertEquals("http://x.tv/live/1.ts?token=a", StalkerClient.cleanLink("ffmpeg http://x.tv/live/1.ts?token=a"))
        assertEquals("http://x.tv/s", StalkerClient.cleanLink("auto http://x.tv/s"))
        assertEquals("http://x.tv/s", StalkerClient.cleanLink("http://x.tv/s"))
        assertEquals("", StalkerClient.cleanLink("nothing"))
        assertEquals("hls", StalkerClient.formatOf("http://x/play/1.m3u8?t=1"))
        assertEquals("ts", StalkerClient.formatOf("http://x/play/live.php?stream=1&extension=ts"))
    }

    @Test
    fun `channels are read from the portal's answer`() {
        val js = JSONObject().put(
            "data",
            JSONArray()
                .put(
                    JSONObject().put("id", "11").put("name", "News One").put("number", "5")
                        .put("cmd", "ffrt http://localhost/ch/11").put("logo", "11.png")
                        .put("tv_genre_id", "3").put("xmltv_id", "news.one")
                        .put("tv_archive", "1").put("tv_archive_duration", "3"),
                )
                .put(
                    JSONObject().put("id", "12").put("name", "Films").put("cmd", "ffrt http://localhost/ch/12")
                        .put("logo", "http://cdn/f.png"),
                )
                .put(JSONObject().put("id", "").put("cmd", "x")),
        )
        val list = StalkerClient.parseChannels(js, "http://host/misc/logos/320/")
        assertEquals(2, list.size)
        assertEquals("http://host/misc/logos/320/11.png", list[0].logo)
        assertEquals("news.one", list[0].epgId)
        assertEquals(3, list[0].archiveDays)
        assertEquals("http://cdn/f.png", list[1].logo)
        assertEquals(2, list[1].number)
        assertEquals("stalker.12", StalkerClient.tvgId(list[1]))
    }

    @Test
    fun `guide entries are grouped by channel`() {
        val js = JSONObject().put(
            "data",
            JSONObject()
                .put(
                    "11",
                    JSONArray().put(
                        JSONObject().put("start_timestamp", "1000").put("stop_timestamp", "2000")
                            .put("name", "News").put("descr", "The news"),
                    ),
                )
                .put("12", JSONArray()),
        )
        val guide = StalkerClient.parseGuide(js)
        assertEquals(1, guide.size)
        assertEquals("News", guide["11"]?.single()?.title)
        assertEquals(1000L, guide["11"]?.single()?.startSec)
        assertNull(guide["12"])
    }

    @Test
    fun `saved links round trip through the resolver format`() {
        val channel = StalkerChannel(
            id = "11", name = "News", number = 1, cmd = "ffrt http://host/ch/11",
            logo = null, genreId = "1", epgId = "news.one", archiveDays = 0,
        )
        val url = StalkerClient.channelUrl("source-1", channel)
        assertTrue(StalkerLinks.isStalker(url))
        val parsed = StalkerLinks.parse(url)
        assertEquals("source-1", parsed?.first)
        assertEquals("ffrt http://host/ch/11", parsed?.second)
        assertEquals("application/x-mpegURL", StalkerLinks.mimeHint("stalker://source-1/11?fmt=hls"))
        assertEquals("http://example/x", StalkerLinks.resolveOrSelf("http://example/x"))
    }
}
