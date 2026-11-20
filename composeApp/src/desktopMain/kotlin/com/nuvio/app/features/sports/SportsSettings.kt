package com.nuvio.app.features.sports

import com.nuvio.app.core.storage.DesktopStorage

/**
 * Sports settings. The section reads ESPN and the shared NuvioOne data files;
 * no API key is needed or stored.
 */
internal object SportsSettings {
    private val store = DesktopStorage.store("nuvio_sports")

    /** Teams the user pinned, stored as `id\u001Fname\u001Fbadge` lines. */
    fun pinnedTeams(): List<PinnedTeam> =
        store.getString("pinnedTeams")
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split('\u001F')
                val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PinnedTeam(
                    id = id,
                    name = parts.getOrNull(1).orEmpty().ifBlank { "Team" },
                    badgeUrl = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                )
            }
            ?.toList()
            .orEmpty()

    fun setPinnedTeams(teams: List<PinnedTeam>) {
        store.putString(
            "pinnedTeams",
            teams.joinToString("\n") { "${it.id}\u001F${it.name}\u001F${it.badgeUrl.orEmpty()}" },
        )
    }

    /** Spoiler-free mode: hide scores until a match is opened. */
    fun hideScores(): Boolean = store.getBoolean("hideScores") ?: false

    fun setHideScores(value: Boolean) {
        store.putBoolean("hideScores", value)
    }

    /** Minutes between automatic hub refreshes; 0 turns it off. */
    fun autoRefreshMinutes(): Int = store.getInt("autoRefreshMinutes") ?: 0

    fun setAutoRefreshMinutes(value: Int) {
        store.putInt("autoRefreshMinutes", value)
    }

    /**
     * Score delay: IPTV runs behind the live game, so scores, clocks and
     * results wait this many minutes before showing (0 = off).
     */
    fun scoreDelayMinutes(): Int = store.getInt("scoreDelayMinutes") ?: 0

    fun setScoreDelayMinutes(value: Int) {
        store.putInt("scoreDelayMinutes", value)
    }

    /** Channel groups that go to the top of every game's channel list. */
    fun preferredGroups(): List<String> =
        store.getString("preferredGroups").orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun setPreferredGroups(groups: List<String>) {
        store.putString("preferredGroups", groups.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n"))
    }

    /** Region or language whose channels a game lists first ("" = any). */
    fun channelRegion(): String = store.getString("channelRegion").orEmpty()

    fun setChannelRegion(value: String) {
        store.putString("channelRegion", value)
    }
}

/** The channel regions offered in Sports settings, with the tags that mark them in channel names. */
internal val SportsChannelRegions: List<Pair<String, List<String>>> = listOf(
    "US" to listOf("us", "usa"),
    "UK" to listOf("uk", "gb"),
    "Canada" to listOf("ca", "canada"),
    "Australia" to listOf("au", "aus"),
    "Spanish" to listOf("es", "esp", "spain", "latino", "mx", "spanish"),
    "French" to listOf("fr", "france", "french"),
    "German" to listOf("de", "ger", "germany"),
    "Arabic" to listOf("ar", "arab", "arabic"),
    "Portuguese" to listOf("pt", "br", "portugal", "brazil"),
)

/**
 * Score delay (Sports settings): shows each live game as it stood
 * [SportsSettings.scoreDelayMinutes] ago, so a stream that runs behind isn't
 * spoiled. A game that ended within the delay still shows as live.
 */
internal object SportsScoreDelay {
    private data class Snapshot(val atMs: Long, val home: Int?, val away: Int?, val status: String?, val state: String?)

    private val history = java.util.concurrent.ConcurrentHashMap<String, MutableList<Snapshot>>()

    fun apply(events: List<SportsEvent>): List<SportsEvent> = events.map(::apply)

    fun applyOrNull(event: SportsEvent?): SportsEvent? = event?.let { apply(it) }

    fun apply(event: SportsEvent): SportsEvent {
        val delayMs = SportsSettings.scoreDelayMinutes() * 60_000L
        val now = System.currentTimeMillis()
        val live = event.state == "in" || (event.isLive && event.state != "post")
        if (live || event.state == "post") record(event, now)
        if (delayMs <= 0L) return event
        val snaps = history[event.id] ?: return event
        val target = now - delayMs
        val endedAt = synchronized(snaps) { snaps.firstOrNull { it.state == "post" }?.atMs }
        val inDelayWindow = live || (endedAt != null && endedAt > target)
        if (!inDelayWindow) return event
        val shown = synchronized(snaps) { snaps.lastOrNull { it.atMs <= target } }
        return if (shown == null || shown.state == "pre") {
            // Nothing old enough yet: show it as live without a score.
            event.copy(homeScore = null, awayScore = null, status = "Live", state = "in")
        } else {
            event.copy(homeScore = shown.home, awayScore = shown.away, status = shown.status, state = "in")
        }
    }

    private fun record(event: SportsEvent, now: Long) {
        val snaps = history.getOrPut(event.id) { mutableListOf() }
        synchronized(snaps) {
            val last = snaps.lastOrNull()
            if (last == null || last.home != event.homeScore || last.away != event.awayScore ||
                last.status != event.status || last.state != event.state
            ) {
                snaps += Snapshot(now, event.homeScore, event.awayScore, event.status, event.state)
                if (snaps.size > 400) snaps.removeAt(0)
            }
        }
    }
}

internal data class PinnedTeam(
    val id: String,
    val name: String,
    val badgeUrl: String? = null,
)
