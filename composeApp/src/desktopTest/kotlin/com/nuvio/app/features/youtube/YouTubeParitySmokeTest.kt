package com.nuvio.app.features.youtube

import com.liskovsoft.sharedutils.platform.EngineContext
import com.liskovsoft.sharedutils.platform.EnginePlatform
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import java.net.URL
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertTrue

class YouTubeParitySmokeTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            val root = Files.createTempDirectory("nuvio-youtube-parity")
            EnginePlatform.configure(root.resolve("files"), root.resolve("cache"))
            GlobalPreferences.instance(EngineContext.get())
        }
    }

    @Test(timeout = 240_000)
    fun detailsAndSubtitles() = runBlocking {
        val item = YouTubeVideoItem(
            videoId = "dQw4w9WgXcQ",
            title = "Rick Astley",
            author = null,
            thumbnailUrl = null,
            durationMs = 0,
            isLive = false,
        )
        val details = YouTubeEngine.details(item.videoId)
        println(
            "parity-details: channel=${details.channelId} subs=${details.subscriberCount} " +
                "like=${details.likeStatus} subscribed=${details.isSubscribed}",
        )
        assertTrue(details.channelId != null, "video has no channel id")

        val playback = YouTubeEngine.resolvePlayback(item, music = false)
        println(
            "parity-subtitles: count=${playback.subtitles.size} " +
                "names=${playback.subtitles.take(6).map { it.name ?: it.language }}",
        )
        assertTrue(playback.subtitles.isNotEmpty(), "video has no subtitle tracks")

        val first = playback.subtitles.first()
        println("parity-subtitle-url: ${first.url.take(180)}")
        val body = URL(first.url).openStream().bufferedReader().use { it.readText() }
        println("parity-subtitle-body: ${body.take(180).replace("\n", " ")}")
        assertTrue(
            body.contains("WEBVTT") || body.contains("-->"),
            "subtitle response is not WebVTT",
        )
    }

    @Test(timeout = 240_000)
    fun nextVideoAndComments() = runBlocking {
        val details = YouTubeEngine.details("dQw4w9WgXcQ")
        val next = details.nextVideo ?: details.suggestions.firstOrNull()
        println(
            "parity-next: source=${if (details.nextVideo != null) "next" else "suggestions"} " +
                "title=${next?.title} id=${next?.videoId} suggestions=${details.suggestions.size}",
        )
        assertTrue(next != null, "details returned no next video or suggestions")

        val commentsKey = requireNotNull(details.commentsKey) { "video has no comments key" }
        val page = YouTubeEngine.comments(commentsKey)
        val first = page.comments.firstOrNull()
        println(
            "parity-comments: count=${page.comments.size} firstKey=${first?.replyKey} " +
                "liked=${first?.liked} likes=${first?.likeCount} " +
                "withKey=${page.comments.count { it.replyKey != null }} " +
                "withLikeAction=${page.comments.count { it.likeActionKey != null }}",
        )
        assertTrue(page.comments.isNotEmpty(), "comments page is empty")
        // Signed-out responses omit comment action buttons; the UI hides Like
        // until the engine provides a real action key after sign-in.

        val replyKey = first?.replyKey
        if (replyKey != null) {
            val nested = YouTubeEngine.comments(replyKey)
            val nestedFirst = nested.comments.firstOrNull()
            println(
                "parity-comment-thread: parent=${first.author} msg=${first.message?.take(40)} " +
                    "nestedCount=${nested.comments.size} nestedFirst=${nestedFirst?.author} " +
                    "nestedMsg=${nestedFirst?.message?.take(40)}",
            )
        }

        val notifications = YouTubeEngine.notifications()
        println("parity-notifications: feeds=${notifications.size}")

        val accounts = YouTubeEngine.accounts()
        println(
            "parity-accounts: count=${accounts.size} " +
                "selected=${accounts.firstOrNull { it.selected }?.name}",
        )
    }

    @Test(timeout = 240_000)
    fun channelAndPlaylist() = runBlocking {        val details = YouTubeEngine.details("dQw4w9WgXcQ")
        val channelId = requireNotNull(details.channelId) { "video has no channel id" }
        val feeds = YouTubeEngine.channel(channelId)
        println("parity-channel: feeds=${feeds.size} items=${feeds.sumOf { it.items.size }}")
        feeds.forEach { feed ->
            println(
                "parity-feed: ${feed.title} items=${feed.items.size} " +
                    "kinds=${feed.items.map { it.kind }.distinct()}",
            )
        }
        assertTrue(feeds.isNotEmpty(), "channel returned no groups")

        val channelPlaylist = feeds.asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.kind == YouTubeItemKind.PLAYLIST }
        println(
            "parity-channel-playlist: title=${channelPlaylist?.title} " +
                "id=${channelPlaylist?.videoId} playlistId=${channelPlaylist?.playlistId} " +
                "key=${channelPlaylist?.reloadPageKey}",
        )

        val search = YouTubeEngine.search("lofi playlist")
        val candidates = search.filter { it.kind == YouTubeItemKind.PLAYLIST }.take(8)
        candidates.forEach {
            println(
                "parity-playlist-card: title=${it.title} id=${it.videoId} " +
                    "playlistId=${it.playlistId} key=${it.reloadPageKey}",
            )
        }
        val playlist = channelPlaylist
            ?: candidates.firstOrNull { !it.playlistId.isNullOrBlank() }
            ?: candidates.firstOrNull { !it.reloadPageKey.isNullOrBlank() }
        println("parity-search: results=${search.size} playlist=${playlist?.title} id=${playlist?.videoId}")
        assertTrue(playlist != null, "no playlist card found")

        val feed = when {
            !playlist!!.playlistId.isNullOrBlank() -> YouTubeEngine.playlist(playlist.playlistId!!)
            !playlist.reloadPageKey.isNullOrBlank() -> YouTubeEngine.group(playlist.reloadPageKey!!)
            else -> YouTubeEngine.playlist(playlist.videoId)
        }
        println("parity-playlist: title=${feed.title} items=${feed.items.size}")
        assertTrue(feed.items.isNotEmpty(), "playlist returned no videos")
    }
}
