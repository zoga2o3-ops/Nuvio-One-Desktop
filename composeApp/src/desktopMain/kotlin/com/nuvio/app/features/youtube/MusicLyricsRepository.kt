package com.nuvio.app.features.youtube

import com.music.bitchord.data.lyrics.LyricsRepository
import java.util.concurrent.ConcurrentHashMap

internal data class MusicLyricLine(
    val timeMs: Long,
    val text: String,
)

/**
 * Lyrics through upstream BitChord's LyricsRepository (github.com/kushagrasinghx/BitChord,
 * GPL-3.0): up to 15 sources, the ones ticked in Music settings → Lyrics, asked at once and taken
 * in the chosen order (lrc.red, BiniLyrics, BetterLyrics, PaxSenix, LyricsPlus, Unison, YouTube,
 * KuGou, LRCLIB, Musixmatch, Genius…).
 */
internal object MusicLyricsRepository {
    private val cache = ConcurrentHashMap<String, List<MusicLyricLine>>()
    private val sources = ConcurrentHashMap<String, String>()

    fun cached(videoId: String): List<MusicLyricLine>? = cache[videoId]

    /** Where the lyrics for [videoId] came from ("lrc.red", "LRCLIB"…), once found. */
    fun source(videoId: String): String? = sources[videoId]

    /** Settings changed: look again next time. */
    fun clear() {
        cache.clear()
        sources.clear()
    }

    suspend fun lyrics(item: YouTubeVideoItem): List<MusicLyricLine>? {
        cache[item.videoId]?.let { return it }
        val settings = MusicLyricsSettings
        val isYouTube = item.localUrl == null && item.videoId.length == 11
        val result = runCatching {
            LyricsRepository.lyrics(
                // Not a YouTube video (local file, WebDAV): the sources keyed on the video id find nothing.
                videoId = if (isYouTube) item.videoId else "",
                title = item.title,
                artist = (item.author ?: "").removeSuffix(" - Topic"),
                durationMs = item.durationMs,
                album = runCatching { MusicTrackInfoStore.info[item.videoId]?.album }.getOrNull(),
                sources = settings.enabled,
                order = settings.order,
                prioritizeSyllableSync = settings.preferWordSynced,
            )
        }.getOrNull() ?: return null
        val lines = result.lines.filter { it.text.isNotBlank() || it.timeMs > 0 }
            .takeIf { list -> list.any { it.text.isNotBlank() } } ?: return null
        return lines.map { MusicLyricLine(it.timeMs, it.text) }.also {
            cache[item.videoId] = it
            sources[item.videoId] = result.source.label
        }
    }
}
