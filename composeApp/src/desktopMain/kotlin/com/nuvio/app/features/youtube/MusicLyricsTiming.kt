package com.nuvio.app.features.youtube

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * How far to shift a track's lyrics so they line up with what is actually playing.
 *
 * Lyrics are timed to the album recording, but a lot of what plays is the music video, which
 * often opens with a scene before the song starts ("rockstar": 4:02 video, 3:38 song, the words
 * come ~24 s late). When the playing video is longer than its album version, the difference is
 * taken as an intro and the lyrics are shifted by it; the − / + buttons fine-tune from there.
 * Kept per video for this session.
 */
internal object MusicLyricsTiming {
    /** Video id -> milliseconds the lyrics are moved later. */
    val offsets = mutableStateMapOf<String, Long>()
    /** Video id -> why it was shifted automatically, for the note under the lyrics. */
    val notes = mutableStateMapOf<String, String>()
    private val checked = HashSet<String>()

    fun offset(videoId: String): Long = offsets[videoId] ?: 0L

    fun nudge(videoId: String, deltaMs: Long) {
        offsets[videoId] = offset(videoId) + deltaMs
    }

    fun reset(videoId: String) {
        offsets.remove(videoId)
        notes.remove(videoId)
    }

    /** Compares the playing video with its album version once, and shifts for an intro. */
    suspend fun align(item: YouTubeVideoItem, playingDurationMs: Long) {
        if (item.localUrl != null || item.videoId.length != 11) return
        if (!synchronized(checked) { checked.add(item.videoId) }) return
        val duration = playingDurationMs.takeIf { it > 0 } ?: item.durationMs
        if (duration <= 0) return
        val song = withTimeoutOrNull(12_000) {
            runCatching {
                MusicTrackInfoStore.searchCatalogueSong(item, splitArtistNames(item.author).firstOrNull())
            }.getOrNull()
        } ?: return
        if (song.videoId == item.videoId) return
        val albumMs = parseDuration(song.durationText) ?: return
        val extra = duration - albumMs
        // Only a clearly longer video (an intro or outro scene); small gaps are encoding padding.
        if (extra < 3_000 || abs(extra) > 3 * 60_000) return
        if (offsets.containsKey(item.videoId)) return
        offsets[item.videoId] = extra
        notes[item.videoId] = "Video is ${formatSeconds(extra)} longer than the song; lyrics shifted to match"
    }

    private fun parseDuration(text: String?): Long? {
        val parts = text?.trim()?.split(':')?.map { it.toLongOrNull() ?: return null } ?: return null
        if (parts.isEmpty() || parts.size > 3) return null
        return parts.fold(0L) { acc, n -> acc * 60 + n } * 1000
    }

    fun formatSeconds(ms: Long): String {
        val s = ms / 1000.0
        return if (abs(s - Math.round(s)) < 0.05) "${Math.round(s)} s" else "%.1f s".format(s)
    }
}
