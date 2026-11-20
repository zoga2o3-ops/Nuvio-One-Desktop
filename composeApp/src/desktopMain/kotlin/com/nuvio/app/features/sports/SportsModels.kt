package com.nuvio.app.features.sports

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A sport shown in the sports navigation. [apiName] is the shared sport name. */
internal data class SportCategory(
    val apiName: String,
    val label: String,
    val icon: String,
)

/**
 * A league in the curated catalog. The id is stable; all live data comes from
 * ESPN or the shared NuvioOne files.
 */
internal data class CuratedLeague(
    val id: String,
    val name: String,
    val shortName: String,
    val sport: String,
    val country: String,
    val badgeUrl: String? = null,
    val espnPath: String? = null,
    val searchTerms: List<String> = emptyList(),
)

internal data class SportsLeague(
    val id: String,
    val name: String,
    val shortName: String,
    val sport: String,
    val country: String,
    val badgeUrl: String? = null,
    val logoUrl: String? = null,
    val description: String? = null,
    val currentSeason: String? = null,
)

internal data class SportsTeam(
    val id: String,
    val name: String,
    val shortName: String? = null,
    val badgeUrl: String? = null,
    val leagueId: String? = null,
)

internal data class SportsEvent(
    val id: String,
    val leagueId: String? = null,
    val leagueName: String? = null,
    val sport: String? = null,
    val title: String,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val homeTeamId: String? = null,
    val awayTeamId: String? = null,
    val homeBadge: String? = null,
    val awayBadge: String? = null,
    val homeColor: String? = null,
    val awayColor: String? = null,
    val homeAltColor: String? = null,
    val awayAltColor: String? = null,
    val leagueBadge: String? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val date: String? = null,
    val time: String? = null,
    val timestamp: String? = null,
    val status: String? = null,
    val venue: String? = null,
    val thumbUrl: String? = null,
    val description: String? = null,
    val state: String? = null,
) {
    val startEpochMs: Long? get() = parseEventTime(timestamp, date, time)
    val hasScore: Boolean get() = homeScore != null || awayScore != null

    /**
     * Live now. Some feeds leave the state empty even after kick-off, so fall
     * back to the start time and the sport's usual running time.
     */
    val isLive: Boolean
        get() {
            if (state == "post") return false
            if (state == "in") return true
            if (status?.contains("live", ignoreCase = true) == true) return true
            val start = startEpochMs ?: return false
            val now = System.currentTimeMillis()
            if (now < start) return false
            val windowMs = when (sport) {
                "Cricket", "Golf" -> 6 * 60 * 60 * 1000L
                "Tennis" -> 4 * 60 * 60 * 1000L
                else -> 3 * 60 * 60 * 1000L
            }
            return now - start <= windowMs
        }

    val isFinished: Boolean get() = state == "post"
}

/** A team row from ESPN or the NuvioOne league files. */
internal data class SportsTeamInfo(
    val id: String,
    val name: String,
    val badgeUrl: String? = null,
    val leagueName: String? = null,
    val sport: String? = null,
    val country: String? = null,
    val stadium: String? = null,
    val formed: String? = null,
    val description: String? = null,
    val color: String? = null,
    val altColor: String? = null,
    val abbr: String? = null,
)

/** A team in the Sports search, with the league it was found in. */
internal data class SportsTeamSearchResult(
    val team: SportsTeamInfo,
    val leagueName: String?,
    val sport: String?,
)

internal data class TeamPageData(
    val team: SportsTeamInfo? = null,
    val next: List<SportsEvent> = emptyList(),
    val last: List<SportsEvent> = emptyList(),
    val errors: List<String> = emptyList(),
)

internal data class SportsTableRow(
    val position: Int,
    val team: String,
    val badgeUrl: String? = null,
    val color: String? = null,
    val altColor: String? = null,
    val played: Int? = null,
    val wins: Int? = null,
    val draws: Int? = null,
    val losses: Int? = null,
    val goalsFor: Int? = null,
    val goalsAgainst: Int? = null,
    val goalDifference: Int? = null,
    val points: Int? = null,
)

internal data class SportsTvListing(
    val eventId: String? = null,
    val event: String,
    val sport: String? = null,
    val channel: String,
    val country: String? = null,
    val logoUrl: String? = null,
    val date: String? = null,
    val time: String? = null,
    val timestamp: String? = null,
) {
    val startEpochMs: Long? get() = parseEventTime(timestamp, date, time)
}

internal data class LeaguePageData(
    val league: SportsLeague,
    val upcoming: List<SportsEvent> = emptyList(),
    val results: List<SportsEvent> = emptyList(),
    val table: List<SportsTableRow> = emptyList(),
    val errors: List<String> = emptyList(),
)

internal data class EventPageData(
    val event: SportsEvent? = null,
    val tv: List<SportsTvListing> = emptyList(),
    val stats: List<SportsTeamStat> = emptyList(),
    val leaders: List<SportsLeader> = emptyList(),
    val scoring: List<SportsScoringPlay> = emptyList(),
    val quarters: List<SportsQuarterScore> = emptyList(),
    val errors: List<String> = emptyList(),
)

internal data class SportsTeamStat(
    val label: String,
    val home: String,
    val away: String,
)

internal data class SportsLeader(
    val team: String,
    val category: String,
    val player: String,
    val statLine: String,
)

internal data class SportsScoringPlay(
    val period: String,
    val clock: String,
    val team: String,
    val description: String,
    val score: String,
)

internal data class SportsQuarterScore(
    val label: String,
    val home: String,
    val away: String,
)

internal data class SportsGameDetails(
    val stats: List<SportsTeamStat> = emptyList(),
    val leaders: List<SportsLeader> = emptyList(),
    val scoring: List<SportsScoringPlay> = emptyList(),
    val quarters: List<SportsQuarterScore> = emptyList(),
)

internal class SportsApiException(
    message: String,
    val isRateLimit: Boolean = false,
) : Exception(message)

private val isoLocalDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

internal fun parseEventTime(timestamp: String?, date: String?, time: String?): Long? {
    timestamp
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { value ->
            runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
            runCatching {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            }.getOrNull()?.let { return it }
            runCatching {
                LocalDateTime.parse(value, isoLocalDateTime).toInstant(ZoneOffset.UTC).toEpochMilli()
            }.getOrNull()?.let { return it }
            runCatching {
                LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli()
            }.getOrNull()?.let { return it }
        }

    if (date.isNullOrBlank()) return null
    val localDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull() ?: return null
    val localTime = runCatching { LocalTime.parse(time?.trim().orEmpty()) }
        .getOrNull()
        ?: LocalTime.MIDNIGHT
    return LocalDateTime.of(localDate, localTime).toInstant(ZoneOffset.UTC).toEpochMilli()
}
