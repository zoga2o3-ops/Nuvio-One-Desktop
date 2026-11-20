package com.nuvio.app.features.youtube

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicShareServerTest {
    private val client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()

    @Test
    fun `share page lists the local library`() {
        MusicShareServer.start()
        try {
            val state = MusicShareServer.state
            assertTrue(state.running, state.error)
            val page = client.send(
                HttpRequest.newBuilder(URI(state.url!!.trimEnd('/') + "/"))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, page.statusCode())
            assertTrue(page.body().contains("Nuvio Music"))
            assertTrue(page.body().contains("Download all"))
        } finally {
            MusicShareServer.stop()
        }
    }
}
