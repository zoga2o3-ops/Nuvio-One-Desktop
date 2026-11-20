package com.nuvio.app.features.iptv

import kotlin.test.Test
import kotlin.test.assertEquals

class IptvTvFavoritesTest {
    private fun ch(key: String, source: String, tvg: String?, name: String) = LiveChannel(
        key = key, sourceId = source, sourceName = "TV", name = name, tvgId = tvg, tvgName = null, logo = null,
        group = "24/7", number = 1, url = "http://x/$key", headers = emptyMap(), catchup = null, drm = null,
    )

    private val channels = listOf(
        ch("pl:1", "pl", "tvg-id-channel238", "24/7: Family Matters"),
        ch("pl:2", "pl", "", "No Tvg Channel"),
        ch("pl:3", "pl", "tvg-9", "Renamed by provider"),
        ch("other:1", "other", "tvg-id-channel238", "24/7: Family Matters"),
    )

    @Test
    fun tvFavouritesFindTheSameChannelHere() {
        val tv = listOf(
            "pl|tvg-id-channel238|24/7: Family Matters",
            "pl||No Tvg Channel#2",            // the TV's second copy of a channel
            "pl|tvg-9|Old name",                 // name changed: not guessed
            "gone|tvg-1|Not in any playlist",
        )
        assertEquals(setOf("pl:1", "pl:2"), IptvTvFavorites.matching(channels, tv))
    }

    @Test
    fun favouritesFollowTheTvOrderNotTheChannelOrder() {
        // Alphabetical channel list, but the TV has Charlie, Alpha, Bravo.
        val list = listOf(
            ch("pl:a", "pl", "tvg-a", "Alpha"),
            ch("pl:b", "pl", "tvg-b", "Bravo"),
            ch("pl:c", "pl", "tvg-c", "Charlie"),
        )
        val tv = listOf("pl|tvg-c|Charlie", "pl|tvg-a|Alpha", "pl|tvg-b|Bravo")
        assertEquals(listOf("pl:c", "pl:a", "pl:b"), IptvTvFavorites.orderedMatches(list, tv))
    }

    @Test
    fun starringHereWritesTheTvForm() {
        val c = channels.first()
        assertEquals("pl|tvg-id-channel238|24/7: Family Matters", IptvTvFavorites.keyFor(c))
        assertEquals(listOf("pl||No Tvg Channel"), IptvTvFavorites.without(listOf(IptvTvFavorites.keyFor(c), "pl||No Tvg Channel"), c))
    }
}
