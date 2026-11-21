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
}
