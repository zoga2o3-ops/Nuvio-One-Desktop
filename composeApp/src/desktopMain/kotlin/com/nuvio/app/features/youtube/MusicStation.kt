package com.nuvio.app.features.youtube

import java.util.Locale

/**
 * Builds the music that plays on once the queue runs short (shuffle, radio, the end of a list). Follows
 * the station rules BitChord uses:
 *
 *  - The same recording is never played twice. A track's audio cut and its music-video cut have different
 *    ids and near-identical titles, so a match is on a normalised title plus a shared artist.
 *  - One batch carries at most [PerArtistLimit] tracks by any one artist, and [SeedArtistLimit] for the
 *    artist the station started from, so it doesn't turn into one artist's discography.
 *  - Songs come before music-video cuts.
 */
internal object MusicStation {
    private const val PerArtistLimit = 2
    private const val SeedArtistLimit = 4

    /** Up to [limit] tracks from [candidates] to add after [existing]. */
    fun extend(existing: List<YouTubeVideoItem>, candidates: List<YouTubeVideoItem>, limit: Int): List<YouTubeVideoItem> {
        val taken = existing.toMutableList()
        val perArtist = mutableMapOf<String, Int>()
        val seedArtists = existing.lastOrNull()?.let { artistSet(it.author) }.orEmpty()
        val ordered = candidates.filterNot { isVideoCut(it) }.ifEmpty { candidates }
        val out = mutableListOf<YouTubeVideoItem>()
        for (candidate in ordered) {
            if (out.size >= limit) break
            if (taken.any { sameRecording(it, candidate) }) continue
            val artists = artistSet(candidate.author)
            val key = artists.minOrNull()
            if (key != null) {
                val cap = if (artists.any { it in seedArtists }) SeedArtistLimit else PerArtistLimit
                val played = perArtist[key] ?: 0
                if (played >= cap) continue
                perArtist[key] = played + 1
            }
            taken += candidate
            out += candidate
        }
        return out
    }

    /** Whether two tracks are the same recording (see the note above). */
    fun sameRecording(a: YouTubeVideoItem, b: YouTubeVideoItem): Boolean {
        if (a.videoId == b.videoId) return true
        val title = normalisedTitle(a.title)
        if (title.isEmpty() || title != normalisedTitle(b.title)) return false
        val left = artistSet(a.author)
        val right = artistSet(b.author)
        return left.isEmpty() || right.isEmpty() || left.any { it in right }
    }

    private fun isVideoCut(item: YouTubeVideoItem): Boolean {
        val title = item.title.lowercase(Locale.ROOT)
        return "official video" in title || "music video" in title || "lyric video" in title || "lyrical video" in title
    }

    private fun normalisedTitle(raw: String): String = raw.lowercase(Locale.ROOT)
        .substringBefore(" | ")
        .replace(NOISE, " ")
        .replace(PUNCTUATION, " ")
        .replace(SPACES, " ")
        .trim()

    /** The artists behind a credit, split out so billing order doesn't matter. */
    private fun artistSet(raw: String?): Set<String> = raw.orEmpty().lowercase(Locale.ROOT)
        .replace(TOPIC, " ")
        .split(",", "&", "·", "•", ";", " feat", " ft.", " ft ", " x ", " with ")
        .map { it.replace(PUNCTUATION, " ").replace(SPACES, " ").trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    private val NOISE = Regex(
        """\((?:official|lyric|lyrics|lyrical|audio|video|visuali[sz]er|full song|hd|4k)[^)]*\)""" +
            """|\(from[^)]*\)""" +
            """|\[[^]]*]""" +
            """|\b(?:official (?:video|audio|music video)|lyrical video|full video|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TOPIC = Regex("""\s*-\s*topic\b""", RegexOption.IGNORE_CASE)
    private val PUNCTUATION = Regex("""[^\p{L}\p{N}]+""")
    private val SPACES = Regex("""\s+""")
}
