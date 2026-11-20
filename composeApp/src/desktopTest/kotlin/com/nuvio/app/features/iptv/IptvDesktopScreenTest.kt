package com.nuvio.app.features.iptv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nuvio.app.core.ui.NuvioTheme
import java.nio.file.Files
import org.junit.Rule
import kotlin.test.Test

class IptvDesktopScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun savedPlaylistAppearsInLiveTvSection() {
        val dir = Files.createTempDirectory("nuvio-iptv-ui-test-")
        System.setProperty("nuvio.iptv.storageDir", dir.toString())
        try {
            IptvSession.invalidate()
            val playlist = dir.resolve("demo.m3u")
            Files.writeString(playlist, "#EXTM3U\n#EXTINF:-1 tvg-id=\"demo\" group-title=\"Demo\",Demo Channel\nfile:///tmp/demo.m3u8\n")
            IptvSourceStore.save(listOf(IptvSource("demo", "Local test", playlist.toUri().toString())))
            compose.setContent { NuvioTheme { IptvDesktopScreen(onBack = {}) } }
            compose.waitUntil(timeoutMillis = 20_000) {
                compose.onAllNodesWithText("Demo Channel").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithText("Demo Channel")[0].assertExists()
        } finally {
            System.clearProperty("nuvio.iptv.storageDir")
            Files.list(dir).use { files -> files.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun moviesTabShowsPlaylistMovies() {
        val dir = Files.createTempDirectory("nuvio-iptv-vod-ui-test-")
        System.setProperty("nuvio.iptv.storageDir", dir.toString())
        try {
            IptvSession.invalidate()
            val playlist = dir.resolve("mixed.m3u")
            Files.writeString(
                playlist,
                """
                    #EXTM3U
                    #EXTINF:-1 group-title="News",Live News
                    http://example.com/live/news.m3u8
                    #EXTINF:5400 group-title="Movies",Demo Movie
                    http://example.com/movies/demo.mp4
                """.trimIndent(),
            )
            IptvSourceStore.save(listOf(IptvSource("demo", "Local test", playlist.toUri().toString())))
            compose.setContent { NuvioTheme { IptvDesktopScreen(onBack = {}) } }
            compose.waitUntil(timeoutMillis = 20_000) {
                compose.onAllNodesWithText("Live News").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithText("Movies")[0].performClick()
            compose.waitUntil(timeoutMillis = 20_000) {
                compose.onAllNodesWithText("Demo Movie").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Demo Movie").assertExists()
            compose.onNodeWithText("On-demand titles from your playlists").assertExists()
        } finally {
            System.clearProperty("nuvio.iptv.storageDir")
            Files.list(dir).use { files -> files.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(dir)
        }
    }
}
