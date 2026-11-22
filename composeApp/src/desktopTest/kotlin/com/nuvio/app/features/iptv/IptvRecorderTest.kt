package com.nuvio.app.features.iptv

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IptvRecorderTest {
    @Test
    fun `keeps recording after the provider closes the stream`() {
        val dir = Files.createTempDirectory("nuvio-recorder-reconnect-")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        val requests = AtomicInteger()
        val chunk = ByteArray(64 * 1024) { 0x47 }
        server.createContext("/live.ts") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                repeat(4) { output.write(chunk) }
            }
        }
        server.start()
        try {
            IptvRecordingStore.setFolder(dir)
            IptvRecorder.start(
                "Reconnect test",
                "http://127.0.0.1:${server.address.port}/live.ts",
                emptyMap(),
            )
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline && requests.get() < 2) Thread.sleep(100)

            assertTrue(requests.get() >= 2, "the recorder should reopen a dropped stream")
            assertTrue(IptvRecorder.recording, "a dropped live stream must not end the recording")
            val file = IptvRecorder.activeFile ?: error("no active recording file")
            Thread.sleep(800)
            assertTrue(Files.size(file) >= chunk.size * 2L, "bytes from both connections should be kept")
        } finally {
            IptvRecorder.stop()
            server.stop(0)
            IptvRecordingStore.reset()
            runCatching { dir.toFile().deleteRecursively() }
        }
    }

    @Test
    fun `records every segment of a finished HLS playlist`() {
        val dir = Files.createTempDirectory("nuvio-recorder-hls-")
        val first = ByteArray(32 * 1024) { 0x11 }
        val second = ByteArray(48 * 1024) { 0x22 }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/live.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:2
                #EXTINF:2,
                first.ts
                #EXTINF:2,
                second.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/first.ts") { exchange ->
            exchange.sendResponseHeaders(200, first.size.toLong())
            exchange.responseBody.use { it.write(first) }
        }
        server.createContext("/second.ts") { exchange ->
            exchange.sendResponseHeaders(200, second.size.toLong())
            exchange.responseBody.use { it.write(second) }
        }
        server.start()
        try {
            IptvRecordingStore.setFolder(dir)
            IptvRecorder.start(
                "HLS test",
                "http://127.0.0.1:${server.address.port}/live.m3u8",
                emptyMap(),
            )
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline && IptvRecorder.recording) Thread.sleep(100)

            assertTrue(!IptvRecorder.recording, "a playlist with ENDLIST finishes recording")
            val file = IptvRecorder.activeFile ?: error("no active recording file")
            assertContentEquals(first + second, Files.readAllBytes(file))
        } finally {
            IptvRecorder.stop()
            server.stop(0)
            IptvRecordingStore.reset()
            runCatching { dir.toFile().deleteRecursively() }
        }
    }

    @Test
    fun `parses a master playlist and prefers the highest bandwidth`() {
        val text = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=900000
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4500000
            high/index.m3u8
        """.trimIndent()
        assertEquals(
            "http://example.test/streams/high/index.m3u8",
            HlsRecording.pickVariant(text, "http://example.test/streams/master.m3u8"),
        )
    }

    @Test
    fun `newest window skips the start of a long live playlist`() {
        val segments = (0 until 100).map {
            HlsRecording.Segment("http://example.test/$it.ts", 2.0, null, it.toLong())
        }
        val window = HlsRecording.newestWindow(segments, 30.0)
        assertEquals(15, window.size)
        assertEquals("http://example.test/85.ts", window.first().url)
    }
}
