package com.nuvio.app.features.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CastFormatsTest {
    @Test
    fun contentTypeComesFromTheAddress() {
        assertEquals("application/x-mpegURL", CastFormats.contentTypeOf("https://cdn.example/live/index.m3u8?token=1"))
        assertEquals("video/x-matroska", CastFormats.contentTypeOf("https://x.example/Movie.2024.MKV"))
        assertEquals("video/mp2t", CastFormats.contentTypeOf("http://iptv.example/12345.ts"))
        assertEquals("video/mp4", CastFormats.contentTypeOf("https://debrid.example/dl/abc"))
    }

    @Test
    fun dlnaTimesRoundTrip() {
        assertEquals("1:02:03", CastFormats.dlnaTime(3_723_000))
        assertEquals("0:00:05", CastFormats.dlnaTime(5_400))
        assertEquals(3_723_500, CastFormats.parseDlnaTime("1:02:03.500"))
        assertNull(CastFormats.parseDlnaTime("NOT_IMPLEMENTED"))
    }

    @Test
    fun relayOnlyWhenTheTvCantFetchItItself() {
        val cast = CastDevice("cc:1", "Living room", CastKind.CHROMECAST, "192.168.1.20", 8009)
        val tv = CastDevice("dlna:1", "TV", CastKind.DLNA, "192.168.1.30", 9197, controlUrl = "http://192.168.1.30:9197/ctl")
        fun media(url: String, headers: Map<String, String> = emptyMap()) = CastMedia(url, headers, "t", "video/mp4")
        assertFalse(CastRelay.needed(cast, media("https://debrid.example/file.mp4")))
        assertTrue(CastRelay.needed(tv, media("https://debrid.example/file.mp4")), "DLNA sets often can't do HTTPS")
        assertFalse(CastRelay.needed(tv, media("http://iptv.example/1.ts")))
        assertTrue(CastRelay.needed(cast, media("http://127.0.0.1:5000/stream")), "only on this PC")
        assertTrue(CastRelay.needed(cast, media("https://x.example/a.mp4", mapOf("Referer" to "https://x.example"))), "needs headers")
    }
}
