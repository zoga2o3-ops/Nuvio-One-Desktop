package com.nuvio.app.features.youtube

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MusicRemoteSourcesTest {
    @Test
    fun `webdav lists folders and audio with basic auth`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            assertEquals("PROPFIND", exchange.requestMethod)
            assertTrue(exchange.requestHeaders.getFirst("Authorization").startsWith("Basic "))
            val xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:multistatus xmlns:d="DAV:">
                  <d:response><d:href>/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
                  <d:response><d:href>/Album/</d:href><d:propstat><d:prop><d:displayname>Album</d:displayname><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
                  <d:response><d:href>/Album/song.mp3</d:href><d:propstat><d:prop><d:displayname>song.mp3</d:displayname><d:resourcetype/></d:prop></d:propstat></d:response>
                </d:multistatus>
            """.trimIndent()
            val bytes = xml.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(207, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val config = WebDavConfig("http://127.0.0.1:${server.address.port}/", "user", "pass")
            val entries = MusicWebDavClient.list(config, config.url).getOrThrow()
            assertEquals(listOf("Album", "song.mp3"), entries.map { it.name })
            assertTrue(entries.first().isDirectory)
            assertTrue(MusicWebDavClient.isAudio("song.mp3"))
            assertTrue(MusicWebDavClient.headersFor(config, config.url + "Album/song.mp3")["Authorization"].orEmpty().startsWith("Basic "))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `navidrome parses albums and album tracks`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun respond(exchange: com.sun.net.httpserver.HttpExchange, json: String) {
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/rest/ping") { exchange ->
            respond(exchange, """{"subsonic-response":{"status":"ok"}}""")
        }
        server.createContext("/rest/getAlbumList2") { exchange ->
            respond(
                exchange,
                """{"subsonic-response":{"status":"ok","albumList2":{"album":[{"id":"a1","name":"Afterglow","artist":"Neon Harbor","coverArt":"c1","songCount":2}]}}}""",
            )
        }
        server.createContext("/rest/getAlbum") { exchange ->
            respond(
                exchange,
                """{"subsonic-response":{"status":"ok","album":{"song":[{"id":"s1","title":"Midnight Drive","artist":"Neon Harbor","album":"Afterglow","duration":228,"coverArt":"c1"}]}}}""",
            )
        }
        server.start()
        try {
            val config = NavidromeConfig("http://127.0.0.1:${server.address.port}", "user", "pass")
            MusicNavidromeClient.ping(config).getOrThrow()
            val albums = MusicNavidromeClient.albums(config).getOrThrow()
            assertEquals("Afterglow", albums.single().name)
            val tracks = MusicNavidromeClient.albumTracks(config, "a1").getOrThrow()
            assertEquals("Midnight Drive", tracks.single().title)
            assertEquals(228_000L, tracks.single().durationMs)
            assertTrue(tracks.single().streamUrl.contains("/rest/stream"))
        } finally {
            server.stop(0)
        }
    }
}
