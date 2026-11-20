package com.nuvio.app.features.youtube

/**
 * Keeps non-music videos out of the music queue. YouTube's search and related results mix in live
 * streams, 24/7 channels, Shorts, reactions, podcasts and promo clips, which a music player shouldn't play.
 */
internal object MusicTrackFilter {
    private const val MinMs = 30_000L
    private const val MaxMs = 20L * 60_000L

    private val nonMusicWords = Regex(
        "(?i)\\b(live|24/7|24-7|compilation|reaction|reacts?|tutorial|how to|unboxing|review|gameplay|" +
            "podcast|episode|vlog|trailer|interview|news|asmr|shorts?)\\b",
    )

    private val songMarkers = Regex(
        "(?i)official (music )?(video|audio)|\\baudio\\b|lyric|visuali[sz]er|[(\\[](feat|ft)\\.|\\(prod|music video",
    )

    /**
     * Stricter than [isMusic], for lists that mix in ordinary videos (Liked videos): the title has to read as a song,
     * or the upload comes from an artist's "- Topic" channel.
     */
    fun looksLikeMusic(item: YouTubeVideoItem): Boolean {
        if (!isMusic(item)) return false
        if (item.localUrl != null) return true
        if (songMarkers.containsMatchIn(item.title)) return true
        return item.author.orEmpty().contains("- Topic", ignoreCase = true)
    }

    fun isMusic(item: YouTubeVideoItem): Boolean {
        if (item.kind != YouTubeItemKind.VIDEO) return false
        // Files saved on this computer are the user's own choice.
        if (item.localUrl != null) return true
        if (item.isLive || item.isShorts) return false
        if (item.durationMs in 1 until MinMs) return false
        if (item.durationMs > MaxMs) return false
        if (item.title.contains('#')) return false
        if (nonMusicWords.containsMatchIn(item.title)) return false
        return true
    }
}
