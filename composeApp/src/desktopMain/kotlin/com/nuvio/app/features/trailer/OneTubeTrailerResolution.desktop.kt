package com.nuvio.app.features.trailer

import com.nuvio.app.features.youtube.YouTubeEngine
import com.nuvio.app.features.youtube.YouTubeStreamProxy
import com.nuvio.app.features.youtube.YouTubeVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private val YouTubeVideoIdPattern = Regex("""(?:[?&]v=|youtu\.be/|/embed/|/shorts/)([A-Za-z0-9_-]{11})""")
private const val TrailerOneTubeTimeoutMs = 3_000L

internal actual suspend fun resolveOneTubeTrailer(youtubeUrl: String): TrailerPlaybackSource? {
    val videoId = YouTubeVideoIdPattern.find(youtubeUrl)?.groupValues?.get(1) ?: return null
    return try {
        val item = YouTubeVideoItem(
            videoId = videoId,
            title = "Trailer",
            author = null,
            thumbnailUrl = null,
            durationMs = 0L,
            isLive = false,
        )
        // Some trailers' stream info never comes back; give up after a while so the regular lookup can take over.
        val resolved = withTimeoutOrNull(TrailerOneTubeTimeoutMs) {
            YouTubeEngine.resolvePlayback(item, music = false, maxHeight = 720)
        }
        if (resolved == null) return null
        val playback = YouTubeStreamProxy.wrapPlayback(resolved)
        TrailerPlaybackSource(videoUrl = playback.videoUrl, audioUrl = playback.audioUrl)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }
}
