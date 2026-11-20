package com.nuvio.app.features.iptv

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IptvLoaderTest {
    @Test
    fun loadsPlaylistAndMatchesXmltvGuide() = runBlocking {
        val dir = Files.createTempDirectory("nuvio-iptv-test-")
        try {
            val guideFile = dir.resolve("guide.xml")
            Files.writeString(guideFile, guideXml())
            val playlistFile = dir.resolve("channels.m3u")
            Files.writeString(playlistFile, playlist())
            val result = IptvLoader.load(
                listOf(IptvSource("test", "Test source", playlistFile.toUri().toString(), guideFile.toUri().toString())),
            )
            assertEquals(emptyList(), result.errors)
            assertEquals(1, result.channels.size)
            val channel = result.channels.single()
            assertEquals("Demo Channel", channel.name)
            assertEquals("Demo", channel.group)
            assertEquals("TestAgent", channel.headers["User-Agent"])
            assertEquals("Live fixture", result.programs[channel.key]?.firstOrNull()?.title)
        } finally {
            Files.list(dir).use { files -> files.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun reportsChannelsBeforeTheGuideFinishes() = runBlocking {
        val dir = Files.createTempDirectory("nuvio-iptv-partial-test-")
        try {
            val guideFile = dir.resolve("guide.xml")
            Files.writeString(guideFile, guideXml())
            val playlistFile = dir.resolve("channels.m3u")
            Files.writeString(playlistFile, playlist())
            var partial: IptvGuide? = null
            val result = IptvLoader.load(
                listOf(IptvSource("test", "Test source", playlistFile.toUri().toString(), guideFile.toUri().toString())),
                onPlaylistsReady = { partial = it },
            )
            assertEquals(1, partial?.channels?.size)
            assertEquals(0, partial?.programs?.size)
            assertEquals(1, result.programs.size)
        } finally {
            Files.list(dir).use { files -> files.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun loadsXtreamPlaylistAndGuideFromProviderEndpoints() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requested = mutableListOf<String>()
        server.createContext("/get.php") { exchange ->
            requested += exchange.requestURI.rawQuery.orEmpty()
            val bytes = playlist().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/xmltv.php") { exchange ->
            requested += exchange.requestURI.rawQuery.orEmpty()
            val bytes = guideXml().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val source = IptvSource("xtream", "Demo provider", "http://127.0.0.1:${server.address.port}", kind = "xtream", username = "demo user", password = "test pass")
            val result = IptvLoader.load(listOf(source))
            assertEquals(emptyList(), result.errors)
            assertEquals("Live fixture", result.programs[result.channels.single().key]?.single()?.title)
            assertEquals(2, requested.size)
            assertTrue(requested.all { "username=demo+user" in it && "password=test+pass" in it })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun xtreamShowsJsonChannelsBeforeTheSlowPlaylistAndKeepsChannelKeys() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/player_api.php") { exchange ->
            val query = exchange.requestURI.rawQuery.orEmpty()
            val body = when {
                "action=get_live_categories" in query -> """[{"category_id":"7","category_name":"Demo"}]"""
                "action=get_live_streams" in query ->
                    """[{"num":1,"name":"Demo Channel","stream_id":42,"stream_icon":"","epg_channel_id":"demo.tv","category_id":"7"}]"""
                else -> "[]"
            }
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/get.php") { exchange ->
            Thread.sleep(1_500) // the big m3u_plus is the slow part
            val bytes = playlist().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/xmltv.php") { exchange ->
            val bytes = guideXml().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        // Real servers answer requests concurrently; the default test server doesn't.
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
        try {
            val source = IptvSource("xtream", "Demo provider", "http://127.0.0.1:${server.address.port}", kind = "xtream", username = "demo user", password = "test pass")
            val partials = java.util.Collections.synchronizedList(mutableListOf<IptvGuide>())
            val result = IptvLoader.load(listOf(source), onPlaylistsReady = { partials += it })

            // The quick JSON list was shown first, as a preview.
            val first = partials.first()
            assertEquals(1, first.channels.size)
            assertTrue(first.channels.single().key.contains(":preview:"))
            assertTrue(first.channels.single().url.endsWith("/live/demo+user/test+pass/42.ts"))

            // The final list is the full playlist with the same keys as before.
            val channel = result.channels.single()
            assertTrue(":preview:" !in channel.key)
            val sha = java.security.MessageDigest.getInstance("SHA-256")
                .digest("${channel.tvgId}|${channel.url}|0".toByteArray(StandardCharsets.UTF_8))
            val expectedKey = "xtream:" + sha.take(8).joinToString("") { "%02x".format(it) }
            assertEquals(expectedKey, channel.key)
            assertEquals("Live fixture", result.programs[channel.key]?.single()?.title)
            assertEquals(emptyList(), result.errors)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun followsHttpRedirectsForThePlaylist() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/channels.m3u")
            exchange.sendResponseHeaders(301, -1)
            exchange.close()
        }
        server.createContext("/channels.m3u") { exchange ->
            val bytes = playlist().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val source = IptvSource(
                "redirect",
                "Redirect source",
                "http://127.0.0.1:${server.address.port}/redirect",
            )
            val result = IptvLoader.load(listOf(source))
            assertEquals(emptyList(), result.errors)
            assertEquals(1, result.channels.size)
            assertEquals("Demo Channel", result.channels.single().name)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun collectsMoviesAndEpisodesForOnDemand() = runBlocking {
        val dir = Files.createTempDirectory("nuvio-iptv-vod-test-")
        try {
            val playlistFile = dir.resolve("mixed.m3u")
            Files.writeString(
                playlistFile,
                """
                    #EXTM3U
                    #EXTINF:-1 group-title="News",Live News
                    http://example.com/live/news.m3u8
                    #EXTINF:5400 tvg-logo="http://img/poster.jpg" group-title="Movies",Demo Movie
                    http://example.com/movies/demo.mp4
                    #EXTINF:-1 group-title="Series",Demo Episode
                    http://example.com/series/episode1.mkv
                """.trimIndent(),
            )
            val result = IptvLoader.load(listOf(IptvSource("vod", "VOD source", playlistFile.toUri().toString())))
            assertEquals(1, result.channels.size)
            assertEquals("Live News", result.channels.single().name)
            assertEquals(2, result.movies.size)
            val movie = result.movies.first { it.name == "Demo Movie" }
            assertEquals("Movies", movie.group)
            assertEquals("http://img/poster.jpg", movie.logo)
            assertEquals(5400, movie.durationSeconds)
            assertEquals("http://example.com/movies/demo.mp4", movie.url)
            assertTrue(result.movies.any { it.name == "Demo Episode" && it.group == "Series" })
        } finally {
            Files.list(dir).use { files -> files.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(dir)
        }
    }

    private fun playlist(): String = """
        #EXTM3U
        #EXTINF:-1 tvg-id="demo" tvg-name="Demo Channel" group-title="Demo",Demo Channel
        #EXTVLCOPT:http-user-agent=TestAgent
        file:///tmp/demo.m3u8
    """.trimIndent()

    private fun guideXml(): String {
        val start = System.currentTimeMillis() - 60_000
        val stop = start + 120_000
        val formatter = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return """<tv><channel id="demo"><display-name>Demo Channel</display-name></channel><programme channel="demo" start="${formatter.format(Date(start))}" stop="${formatter.format(Date(stop))}"><title>Live fixture</title></programme></tv>"""
    }
}

