package com.nuvio.app.features.youtube

/** One chapter of a YouTube video: where it starts and its title. */
internal data class YouTubeChapter(val startMs: Long, val title: String)

internal object YouTubeChapters {
    /**
     * The chapters to show: YouTube's own list when it sent one, else the timestamps in the
     * description (the way YouTube itself makes chapters). Sorted, one per start time.
     */
    fun resolve(fromYouTube: List<YouTubeChapter>, description: String?): List<YouTubeChapter> {
        val clean = fromYouTube.filter { it.title.isNotBlank() && it.startMs >= 0 }
            .sortedBy { it.startMs }
            .distinctBy { it.startMs }
        return if (clean.size >= 2) clean else fromDescription(description)
    }

    private val timestampLine = Regex("""^\s*[\[(]?((?:\d{1,2}:)?\d{1,2}:\d{2})[\])]?\s*[-–—:|.]?\s*(.+?)\s*$""")
    private val timestampAtEnd = Regex("""^\s*(.+?)\s*[-–—:|]?\s*[\[(]?((?:\d{1,2}:)?\d{1,2}:\d{2})[\])]?\s*$""")

    /**
     * YouTube's rule for description chapters: a line per chapter with a timestamp, the first at
     * 0:00, at least three of them, in increasing order. Anything else is not treated as chapters.
     */
    fun fromDescription(description: String?): List<YouTubeChapter> {
        if (description.isNullOrBlank()) return emptyList()
        val found = description.lines().mapNotNull { line ->
            timestampLine.matchEntire(line)?.let { m -> toMs(m.groupValues[1])?.let { YouTubeChapter(it, m.groupValues[2]) } }
                ?: timestampAtEnd.matchEntire(line)?.let { m -> toMs(m.groupValues[2])?.let { YouTubeChapter(it, m.groupValues[1]) } }
        }.map { it.copy(title = it.title.trim().trimStart('-', '–', '—', ':', '|', ' ').trim()) }
            .filter { it.title.isNotBlank() }
        if (found.size < 3 || found.first().startMs != 0L) return emptyList()
        if (found.zipWithNext().any { (a, b) -> b.startMs <= a.startMs }) return emptyList()
        return found
    }

    private fun toMs(stamp: String): Long? {
        val parts = stamp.split(':').map { it.toLongOrNull() ?: return null }
        if (parts.drop(1).any { it >= 60 }) return null
        return parts.fold(0L) { acc, n -> acc * 60 + n } * 1000
    }
}
