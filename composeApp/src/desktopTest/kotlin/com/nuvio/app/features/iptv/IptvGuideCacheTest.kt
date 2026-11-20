package com.nuvio.app.features.iptv

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class IptvGuideCacheTest {
    @Test
    fun `cache round trips and rejects changed sources`() = runBlocking {
        val dir = Files.createTempDirectory("nuvio-iptv-cache-")
        System.setProperty("nuvio.iptv.storageDir", dir.toString())
        try {
            val source = IptvSource("demo", "Cache test", "http://example.com/demo.m3u")
            val guide = IptvGuide(
                channels = listOf(
                    LiveChannel(
                        key = "demo-1",
                        sourceId = source.id,
                        sourceName = source.name,
                        name = "Demo Channel",
                        tvgId = "demo.id",
                        tvgName = "Demo",
                        logo = "http://example.com/logo.png",
                        group = "News",
                        number = 1,
                        url = "http://example.com/live.m3u8",
                        headers = mapOf("User-Agent" to "Nuvio"),
                        catchup = CatchupInfo("append", null, 7),
                        drm = null,
                    ),
                ),
                programs = mapOf(
                    "demo-1" to listOf(
                        EpgProgram(1000L, 2000L, "Demo Programme", "Description"),
                    ),
                ),
                errors = listOf("warning"),
                movies = listOf(
                    VodItem(
                        key = "demo-movie",
                        sourceId = source.id,
                        sourceName = source.name,
                        name = "Demo Movie",
                        logo = null,
                        group = "Movies",
                        number = 2,
                        url = "http://example.com/movie.mp4",
                        headers = emptyMap(),
                        drm = null,
                        durationSeconds = 5400,
                    ),
                ),
            )

            IptvGuideCache.clear()
            assertTrue(IptvGuideCache.save(listOf(source), "24:1", guide, savedAtMs = 1234L))

            val loaded = IptvGuideCache.load(listOf(source), "24:1")
            assertNotNull(loaded)
            assertEquals(1234L, loaded.savedAtMs)
            assertEquals(guide, loaded.guide)

            assertNull(IptvGuideCache.load(listOf(source), "48:2"))
            assertNull(IptvGuideCache.load(listOf(source.copy(url = "http://example.com/other.m3u")), "24:1"))
        } finally {
            IptvGuideCache.clear()
            System.clearProperty("nuvio.iptv.storageDir")
            Files.list(dir).use { files -> files.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(dir)
        }
    }
}
