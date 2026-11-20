package com.nuvio.app.features.iptv

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IptvSessionTest {
    @Test
    fun freshDataIsReusedWithoutReloading() = runBlocking {
        val dir = Files.createTempDirectory("nuvio-iptv-session-")
        System.setProperty("nuvio.iptv.storageDir", dir.toString())
        try {
            val playlist = dir.resolve("demo.m3u")
            Files.writeString(
                playlist,
                "#EXTM3U\n#EXTINF:-1 group-title=\"News\",Live News\nhttp://example.com/live.m3u8\n",
            )
            val source = IptvSource("demo", "Session test", playlist.toUri().toString())

            IptvSession.invalidate()
            IptvSession.ensureLoaded(listOf(source))
            withTimeout(20_000) {
                while (IptvSession.loading || !IptvSession.hasData) delay(50)
            }
            val loads = IptvSession.loadCount
            assertTrue(IptvSession.guide.channels.any { it.name == "Live News" })

            IptvSession.ensureLoaded(listOf(source))
            delay(500)
            assertEquals(loads, IptvSession.loadCount, "a fresh session was loaded again")
        } finally {
            IptvSession.invalidate()
            System.clearProperty("nuvio.iptv.storageDir")
            Files.list(dir).use { files -> files.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(dir)
        }
    }
}
