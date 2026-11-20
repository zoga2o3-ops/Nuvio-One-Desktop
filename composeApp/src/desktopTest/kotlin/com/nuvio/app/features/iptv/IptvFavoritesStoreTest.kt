package com.nuvio.app.features.iptv

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IptvFavoritesStoreTest {
    private lateinit var dir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("iptv-favorites-test")
        System.setProperty("nuvio.iptv.storageDir", dir.toString())
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("nuvio.iptv.storageDir")
        dir.toFile().deleteRecursively()
    }

    @Test
    fun savedFavouritesKeepTheOrderTheyWereStarred() {
        val starred = linkedSetOf("pl:z", "pl:a", "pl:m")
        IptvFavoritesStore.save(starred)
        assertEquals(listOf("pl:z", "pl:a", "pl:m"), IptvFavoritesStore.load().toList())
    }
}
