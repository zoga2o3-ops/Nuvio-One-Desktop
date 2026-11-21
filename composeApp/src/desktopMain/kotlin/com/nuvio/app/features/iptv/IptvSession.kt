package com.nuvio.app.features.iptv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide Live TV data, so leaving the section and coming back does not
 * reload the playlist and guide. Refreshes only when stale, on demand, or after
 * the sources change.
 *
 * The load runs in a session-owned scope, not the screen's: leaving Live TV
 * while a playlist is loading no longer cancels it (which used to leave
 * [loading] stuck and force a reload next time).
 */
internal object IptvSession {
    // The guide covers a day of past programmes and at least a day ahead, so a
    // two-hour refresh window keeps it accurate without re-downloading a large
    // XMLTV file every half hour.
    private const val DEFAULT_MAX_AGE_MS = 2 * 60 * 60 * 1000L
    private const val DAY_MS = 86_400_000L
    private const val HOUR_MS = 3_600_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var guide by mutableStateOf(IptvGuide())
        private set
    var loading by mutableStateOf(false)
        private set
    var progress by mutableStateOf<IptvLoadProgress?>(null)
        private set
    var lastLoadedAt by mutableStateOf(0L)
        private set
    var loadCount by mutableStateOf(0)
        private set

    private var guideWindowKey: String? = null
    /** Bumped when the sources change; a load that started before that reloads when it ends. */
    @Volatile private var sourcesVersion = 0

    val hasData: Boolean get() = lastLoadedAt > 0L

    fun isStale(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Boolean =
        !hasData || System.currentTimeMillis() - lastLoadedAt > maxAgeMs

    /** Loads only when there is no data yet or it is older than [maxAgeMs]. */
    fun ensureLoaded(
        sources: List<IptvSource>,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    ) {
        StalkerLinks.configure(sources)
        if (sources.isEmpty()) {
            clear()
            return
        }
        if (loading) return
        val window = guideWindow()
        if (hasData) {
            if (guideWindowKey != window.key || isStale(maxAgeMs)) refresh(sources)
            return
        }
        loading = true
        scope.launch {
            val cached = IptvGuideCache.load(sources, window.key)
            if (cached != null) {
                guide = cached.guide
                lastLoadedAt = cached.savedAtMs
                guideWindowKey = window.key
                loadCount += 1
            }
            if (cached == null || isStale(maxAgeMs)) {
                loadNetwork(sources, window)
            } else {
                loading = false
                progress = null
            }
        }
    }

    fun refresh(sources: List<IptvSource>) {
        StalkerLinks.configure(sources)
        if (sources.isEmpty()) {
            clear()
            return
        }
        if (loading) return
        val window = guideWindow()
        loading = true
        scope.launch {
            loadNetwork(sources, window)
        }
    }

    private suspend fun loadNetwork(sources: List<IptvSource>, window: GuideWindow) {
        val startedAt = sourcesVersion
        try {
            progress = null
            val previous = guide
            val loaded = IptvLoader.load(
                sources,
                onProgress = { progress = it },
                onPlaylistsReady = { partial ->
                    guide = partial
                    loadCount += 1
                },
                guideFromMs = window.fromMs,
                guideUntilMs = window.untilMs,
            )
            com.nuvio.app.features.youtube.OneTubeDebugLog.line(
                "iptv-load sources=${sources.size} channels=${loaded.channels.size} " +
                    "movies=${loaded.movies.size} errors=${loaded.errors.size} " +
                    "firstError=${loaded.errors.firstOrNull() ?: "none"}",
            )
            val loadedAt = System.currentTimeMillis()
            val keepPrevious = previous.channels.isNotEmpty() &&
                loaded.errors.isNotEmpty() &&
                loaded.channels.size < previous.channels.size
            if (keepPrevious) {
                // A source failed or returned fewer channels: keep the usable
                // cached list and surface the error instead of replacing it.
                guide = previous.copy(errors = loaded.errors)
            } else {
                guide = loaded
                if (loaded.channels.isNotEmpty()) {
                    lastLoadedAt = loadedAt
                    guideWindowKey = window.key
                    loadCount += 1
                    IptvGuideCache.save(sources, window.key, loaded, loadedAt)
                } else {
                    lastLoadedAt = 0L
                }
            }
            loading = false
            progress = null
        } finally {
            loading = false
            progress = null
        }
        // Sources added or removed while this was loading: load what's there now.
        if (sourcesVersion != startedAt) refresh(IptvSourceStore.load())
    }

    /**
     * No sources left (all removed): nothing to show. Without this the last list stayed on
     * screen, because loading returns straight away when there is nothing to load.
     */
    private fun clear() {
        if (!hasData && guide.channels.isEmpty() && guide.movies.isEmpty()) return
        guide = IptvGuide()
        lastLoadedAt = 0L
        guideWindowKey = null
        progress = null
        loadCount += 1
        IptvGuideCache.clear()
    }

    /** Sources changed: drop the in-memory and on-disk cache. */
    fun invalidate() {
        sourcesVersion++
        lastLoadedAt = 0L
        guideWindowKey = null
        IptvGuideCache.clear()
    }

    private fun guideWindow(): GuideWindow {
        val settings = IptvSettingsStore.load()
        val pastHours = settings.epgPastHours.coerceIn(1, 168)
        val futureDays = settings.epgFutureDays.coerceIn(1, 14)
        val now = System.currentTimeMillis()
        return GuideWindow(
            fromMs = now - pastHours * HOUR_MS - 6 * HOUR_MS,
            untilMs = now + futureDays * DAY_MS + 12 * HOUR_MS,
            key = "$pastHours:$futureDays",
        )
    }

    private data class GuideWindow(
        val fromMs: Long,
        val untilMs: Long,
        val key: String,
    )
}
