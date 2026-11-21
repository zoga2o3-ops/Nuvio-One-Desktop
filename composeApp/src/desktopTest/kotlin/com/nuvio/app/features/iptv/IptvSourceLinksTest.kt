package com.nuvio.app.features.iptv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IptvSourceLinksTest {
    @Test
    fun `a bare Xtream host gets a scheme`() {
        assertEquals("http://host:8080", cleanXtreamServer("host:8080"))
        assertEquals("http://host:8080", cleanXtreamServer(" http://host:8080/ "))
    }

    @Test
    fun `a pasted get php link becomes the server`() {
        assertEquals(
            "https://host:8443",
            cleanXtreamServer("https://host:8443/get.php?username=a&password=b"),
        )
        assertEquals(
            "http://host",
            cleanXtreamServer("host/player_api.php?username=a&password=b"),
        )
    }

    @Test
    fun `credentials are read from a pasted link`() {
        assertEquals(
            "a" to "b b",
            xtreamCredentialsFromLink("http://host/get.php?username=a&password=b%20b"),
        )
        assertNull(xtreamCredentialsFromLink("http://host/get.php"))
        assertNull(xtreamCredentialsFromLink("http://host/get.php?username=a"))
    }

    @Test
    fun `playlist addresses accept links bare hosts and local files`() {
        assertEquals("https://example.com/list.m3u", normalizePlaylistUrl(" https://example.com/list.m3u "))
        assertEquals("http://host:8080/list.m3u", normalizePlaylistUrl("host:8080/list.m3u"))
        assertEquals("file:///C:/Media/list.m3u", normalizePlaylistUrl("C:\\Media\\list.m3u"))
        assertEquals("file:///C:/Media/list.m3u", normalizePlaylistUrl("file:///C:/Media/list.m3u"))
    }

    @Test
    fun `playlist address shapes do not expose the address`() {
        assertEquals("http", playlistUrlShape("https://example.com/list.m3u"))
        assertEquals("host_port", playlistUrlShape("host:8080"))
        assertEquals("windows_path", playlistUrlShape("C:\\Media\\list.m3u"))
        assertEquals("file", playlistUrlShape("file:///C:/Media/list.m3u"))
        assertEquals("empty", playlistUrlShape("   "))
    }
}
