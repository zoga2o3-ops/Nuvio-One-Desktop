package com.nuvio.app.features.youtube

import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Fills the queue with related YouTube Music tracks when autoplay reaches the end. */
internal object MusicAutoplay {
    suspend fun related(item: YouTubeVideoItem): List<YouTubeVideoItem> = withContext(Dispatchers.IO) {
        val query = listOfNotNull(item.author, item.title)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            withTimeoutOrNull(8_000L) {
                YouTubeEngine.search(query, SearchOptions.TYPE_VIDEO)
            }
        }.getOrNull().orEmpty()
            .filter { MusicTrackFilter.isMusic(it) && it.videoId != item.videoId }
            .map { it.copy(isMusic = true) }
            .distinctBy { it.videoId }
            .take(10)
    }
}

/**
 * Resolves a track through an enabled music addon when "Prefer addon sources" is on. Matching is
 * deliberately loose: the addons index their own catalogues, so the title is compared without
 * punctuation and the first plausible result wins.
 */
internal object MusicAddonSource {
    suspend fun resolve(item: YouTubeVideoItem): YouTubePlayback? = withContext(Dispatchers.IO) {
        MusicPlaybackSettings.ensureLoaded()
        if (!MusicPlaybackSettings.preferAddonSources) return@withContext null
        MusicAddonRegistry.ensureLoaded()
        if (MusicAddonRegistry.configs.none { it.enabled }) return@withContext null
        val query = listOfNotNull(item.title, item.author)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val results = runCatching {
            withTimeoutOrNull(8_000L) { MusicAddonRegistry.search(query) }
        }.getOrNull().orEmpty()
        if (results.isEmpty()) return@withContext null
        val target = normalize(item.title)
        val match = results.firstOrNull { normalize(it.title) == target }
            ?: results.firstOrNull {
                val candidate = normalize(it.title)
                candidate.isNotBlank() && (candidate.contains(target) || target.contains(candidate))
            }
            ?: results.first()
        val resolved = runCatching { MusicAddonRegistry.resolve(match) }.getOrNull()?.getOrNull()
            ?: return@withContext null
        val url = resolved.localUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        YouTubePlayback(
            videoUrl = url,
            audioUrl = null,
            title = item.title,
            author = item.author,
            headers = resolved.playbackHeaders,
        )
    }

    private fun normalize(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }
}
