package com.nuvio.app.features.youtube

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicPartyTest {
    private val client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()

    private fun get(url: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI(url)).timeout(java.time.Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `party serves the guest page and state`() {
        MusicParty.start()
        try {
            val state = MusicParty.state
            assertTrue(state.running)
            val localUrl = state.url!!.trimEnd('/')
            val page = get("$localUrl/")
            assertEquals(200, page.statusCode())
            assertTrue(page.body().contains("Nuvio Music Party"))
            assertTrue(page.body().contains("Search songs to add"))

            val stateResponse = get("$localUrl/state")
            assertEquals(200, stateResponse.statusCode())
            assertTrue(stateResponse.body().contains("\"running\":true"))
        } finally {
            MusicParty.stop()
        }
    }

    @Test
    fun `unknown party url returns 404`() {
        MusicParty.start()
        try {
            val base = MusicParty.state.url!!.substringBeforeLast('/').substringBeforeLast('/')
            val response = get("$base/not-a-party/")
            assertEquals(404, response.statusCode())
        } finally {
            MusicParty.stop()
        }
    }
}
