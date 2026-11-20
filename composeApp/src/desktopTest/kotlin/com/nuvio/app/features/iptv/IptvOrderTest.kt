package com.nuvio.app.features.iptv

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Custom channel/group ordering and the settings round-trip. */
class IptvOrderTest {
    private lateinit var directory: java.nio.file.Path

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("nuvio-iptv-order")
        System.setProperty("nuvio.iptv.storageDir", directory.toString())
    }

    @After
    fun tearDown() {
        System.clearProperty("nuvio.iptv.storageDir")
    }

    private fun channel(key: String, group: String, number: Int) = LiveChannel(
        key = key,
        sourceId = "source",
        sourceName = "Source",
        name = key,
        tvgId = null,
        tvgName = null,
        logo = null,
        group = group,
        number = number,
        url = "https://example.invalid/$key",
        headers = emptyMap(),
        catchup = null,
        drm = null,
    )

    @Test
    fun settingsRoundTripKeepsCustomOrders() {
        val settings = IptvSettings(
            channelSort = "custom",
            groupSort = "custom",
            customGroupOrder = listOf("Sports", "News"),
            customChannelOrder = mapOf(
                "Sports" to listOf("b", "a"),
                "News" to listOf("c"),
            ),
        )

        IptvSettingsStore.save(settings)
        val loaded = IptvSettingsStore.load()

        assertEquals("custom", loaded.channelSort)
        assertEquals("custom", loaded.groupSort)
        assertEquals(listOf("Sports", "News"), loaded.customGroupOrder)
        assertEquals(listOf("b", "a"), loaded.customChannelOrder["Sports"])
        assertEquals(listOf("c"), loaded.customChannelOrder["News"])
    }

    @Test
    fun customOrderSortsGroupsThenChannels() {
        val settings = IptvSettings(
            channelSort = "custom",
            groupSort = "custom",
            customGroupOrder = listOf("Sports", "News"),
            customChannelOrder = mapOf(
                "Sports" to listOf("sport-b", "sport-a"),
                "News" to listOf("news-b", "news-a"),
            ),
        )
        val channels = listOf(
            channel("news-a", "News", 11),
            channel("sport-a", "Sports", 21),
            channel("news-b", "News", 12),
            channel("sport-b", "Sports", 22),
        )

        val ordered = orderChannelsByCustom(channels, settings).map { it.key }

        assertEquals(listOf("sport-b", "sport-a", "news-b", "news-a"), ordered)
    }

    @Test
    fun unknownGroupsAndChannelsKeepProviderOrderAtTheEnd() {
        val settings = IptvSettings(
            channelSort = "custom",
            groupSort = "custom",
            customGroupOrder = listOf("Sports"),
            customChannelOrder = mapOf("Sports" to listOf("sport-b")),
        )
        val channels = listOf(
            channel("movie-a", "Movies", 31),
            channel("sport-a", "Sports", 21),
            channel("sport-b", "Sports", 22),
        )

        val ordered = orderChannelsByCustom(channels, settings).map { it.key }

        assertEquals(listOf("sport-b", "sport-a", "movie-a"), ordered)
    }

    @Test
    fun settingsFileLivesInTheConfiguredDirectory() {
        IptvSettingsStore.save(IptvSettings(customGroupOrder = listOf("A")))
        assertTrue(Files.isRegularFile(directory.resolve("settings.json")))
    }
}
