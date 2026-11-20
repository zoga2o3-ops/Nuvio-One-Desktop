package com.nuvio.app.features.iptv

import kotlin.test.Test
import kotlin.test.assertEquals

class IptvNameCleanupTest {
    @Test
    fun cleansGroupPrefixes() {
        assertEquals("Sports", cleanIptvGroupName("US| Sports"))
        assertEquals("News", cleanIptvGroupName("[UK] News"))
        assertEquals("Movies", cleanIptvGroupName("|EN| Movies"))
        assertEquals("Entertainment", cleanIptvGroupName("CA: Entertainment"))
        // Nothing to strip, or nothing left after stripping: unchanged.
        assertEquals("Sports", cleanIptvGroupName("Sports"))
        assertEquals("UK|", cleanIptvGroupName("UK|"))
    }

    @Test
    fun removesCleanupWordsFromChannelNames() {
        val words = listOf("USA", "FHD", "24/7")
        assertEquals("ESPN", cleanIptvChannelName("USA: ESPN FHD", words))
        assertEquals("Friends", cleanIptvChannelName("24/7 Friends", words))
        assertEquals("Sky Sports (Main)", cleanIptvChannelName("Sky Sports (Main) FHD", words))
        // Words inside the name stay; a name made only of cleanup words stays as is.
        assertEquals("USA Network", cleanIptvChannelName("USA Network", listOf("FHD")))
        assertEquals("FHD", cleanIptvChannelName("FHD", words))
        assertEquals("BBC One", cleanIptvChannelName("BBC One", emptyList()))
    }
}
