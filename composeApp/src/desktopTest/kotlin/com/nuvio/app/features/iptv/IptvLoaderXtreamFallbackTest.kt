package com.nuvio.app.features.iptv

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class IptvLoaderXtreamFallbackTest {
    @Test
    fun `imports live channels through the API when get php is blocked`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/get.php") { exchange ->
            exchange.sendResponseHeaders(884, -1)
            exchange.close()
        }
        server.createContext("/player_api.php") { exchange ->
            val query = exchange.requestURI.query.orEmpty()
            val body = when {
                query.contains("action=get_live_categories") ->
                    """[{"category_id":"1","category_name":"News"}]"""
                query.contains("action=get_live_streams") ->
                    """[{"stream_id":42,"name":"Test Channel","stream_icon":"http://logo","category_id":"1","epg_channel_id":"test.1","num":"7","tv_archive":1,"tv_archive_duration":3}]"""
                else ->
                    """{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["ts"]},"server_info":{}}"""
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/xmltv.php") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        try {
            val source = IptvSource(
                id = "strong8k",
                name = "Strong8k",
                url = "http://127.0.0.1:${server.address.port}",
                kind = "xtream",
                username = "user",
                password = "pass",
            )
            val guide = IptvLoader.load(listOf(source))
            assertEquals(1, guide.channels.size)
            val channel = guide.channels.single()
            assertEquals("Test Channel", channel.name)
            assertEquals("News", channel.group)
            assertEquals("test.1", channel.tvgId)
            assertEquals(7, channel.number)
            assertTrue(channel.url.endsWith("/live/user/pass/42.ts"))
            assertNotNull(channel.catchup)
            assertEquals(3, channel.catchup?.days)
        } finally {
            server.stop(0)
        }
    }
}
