package com.nuvio.app.features.youtube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YouTubeStreamProxyTest {
    @Test
    fun rangesAreReadLikeAPlayerSendsThem() {
        assertEquals(0L to 999L, YouTubeStreamProxy.parseRange(null, 1000))
        assertEquals(0L to 999L, YouTubeStreamProxy.parseRange("bytes=0-", 1000))
        assertEquals(500L to 999L, YouTubeStreamProxy.parseRange("bytes=500-", 1000))
        assertEquals(100L to 199L, YouTubeStreamProxy.parseRange("bytes=100-199", 1000))
        assertEquals(900L to 999L, YouTubeStreamProxy.parseRange("bytes=-100", 1000))
        assertEquals(10L to 999L, YouTubeStreamProxy.parseRange("bytes=10-5000", 1000), "end clamped to the file")
    }

    @Test
    fun onlyYouTubeMediaHostsGoThroughTheProxy() {
        assertTrue(YouTubeStreamProxy.shouldProxy("https://rr4---sn-abc.googlevideo.com/videoplayback?itag=137"))
        assertFalse(YouTubeStreamProxy.shouldProxy("https://manifest.googlevideo.com/api/manifest/hls_variant/x.m3u8"))
        assertFalse(YouTubeStreamProxy.shouldProxy("https://example.com/video.mp4"))
        assertFalse(YouTubeStreamProxy.shouldProxy(null))
    }
}
