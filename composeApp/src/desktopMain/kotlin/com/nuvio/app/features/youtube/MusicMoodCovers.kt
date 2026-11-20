package com.nuvio.app.features.youtube

import androidx.compose.runtime.mutableStateMapOf
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Cover art for Explore's mood & genre tiles: the cover of the first playlist
 * or album YouTube Music lists under each one (Chill → a chill playlist,
 * Hip-hop → a hip-hop artist's album). Looked up only for tiles on screen, two
 * at a time, and kept on disk for a week so Explore doesn't re-ask every visit.
 */
internal object MusicMoodCovers {
    private const val MaxAgeMs = 7L * 24 * 60 * 60 * 1000
    private val store = DesktopStorage.store("nuvio_music_mood_covers")
    private val gate = Semaphore(2)
    private val pending = HashSet<String>()

    /** Mood key -> cover URL ("" = looked up, nothing usable). */
    val covers = mutableStateMapOf<String, String>()

    private fun key(mood: BitChordMood) = mood.browseId + "|" + mood.params.orEmpty()

    fun cover(mood: BitChordMood): String? = covers[key(mood)]?.takeIf { it.isNotEmpty() }

    suspend fun load(mood: BitChordMood) {
        val key = key(mood)
        if (key in covers) return
        // Fresh enough on disk: use it without a request.
        val saved = store.getString(key)
        val savedAt = store.getString("$key#at")?.toLongOrNull() ?: 0L
        if (saved != null && System.currentTimeMillis() - savedAt < MaxAgeMs) {
            covers[key] = saved
            return
        }
        if (!synchronized(pending) { pending.add(key) }) return
        try {
            gate.withPermit {
                // Don't add to YouTube Music's load while it's throttling us.
                if (MusicTrackInfoStore.isPaused) {
                    saved?.let { covers[key] = it }
                    return
                }
                val url = MusicBitChordRepository.moodShelves(mood)
                    .asSequence()
                    .flatMap { it.items.asSequence() }
                    .mapNotNull { it.thumbnailUrl?.takeIf { url -> url.isNotBlank() } }
                    .firstOrNull()
                    ?.let(::largerCover)
                if (url != null) {
                    covers[key] = url
                    store.putString(key, url)
                    store.putString("$key#at", System.currentTimeMillis().toString())
                } else {
                    // Keep an older cover rather than going blank on a failed refresh.
                    covers[key] = saved.orEmpty()
                }
                delay(300L)
            }
        } finally {
            synchronized(pending) { pending.remove(key) }
        }
    }

    /** YouTube Music thumbnails take a size suffix; ask for one big enough for a tile. */
    private fun largerCover(url: String): String =
        url.replace(Regex("""=w\d+-h\d+"""), "=w360-h360")
}
