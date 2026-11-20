package com.nuvio.app.features.sports

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Reads the NuvioOne sports data files.
 *
 * Base: https://raw.githubusercontent.com/zoga2o3-ops/nuvioone-sports-data/data/
 *  - espn/<safe league id>.json        shared ESPN scoreboard cache (days[yyyyMMdd].raw)
 *  - sportsdb/index.json               97 extra FotMob football leagues
 *  - sportsdb/<id>.json                events[] + table[] for those leagues
 *  - sportsdb/games/<id>.json          recent match details (not used yet)
 *  - wrestling.json                    WWE, AEW, TNA and boxing schedules
 */
internal object SportsDataClient {
    const val BASE_URL = "https://raw.githubusercontent.com/zoga2o3-ops/nuvioone-sports-data/data/"

    const val INDEX_TTL_MS = 6 * 60 * 60 * 1000L
    const val LEAGUE_TTL_MS = 6 * 60 * 60 * 1000L
    const val ESPN_TTL_MS = 5 * 60 * 1000L
    const val ESPN_LIVE_TTL_MS = 90 * 1000L
    const val WRESTLING_TTL_MS = 5 * 60 * 60 * 1000L

    val credits: List<String> = listOf(
        "ESPN",
        "FotMob (unofficial)",
        "WWE.com",
        "All Elite Wrestling",
        "TNA",
        "Box.live",
        "BoxingScene",
    )

    /** ESPN ids become file names: soccer/eng.1 -> soccer_eng_1. */
    fun safeFileId(id: String): String = id.replace(Regex("[^a-zA-Z0-9]+"), "_")

    suspend fun index(): List<SportsExtraLeague> {
        val root = fetchJson("sportsdb/index.json", INDEX_TTL_MS) ?: return emptyList()
        return root.arr("leagues")?.mapNotNull { element ->
            (element as? JsonObject)?.let { parseExtraLeague(it) }
        }.orEmpty()
    }

    suspend fun league(tsdbId: String): SportsExtraLeagueData? {
        val root = fetchJson("sportsdb/$tsdbId.json", LEAGUE_TTL_MS) ?: return null
        val events = root.arr("events")?.mapNotNull { element ->
            (element as? JsonObject)?.let { parseLeagueEvent(it, tsdbId) }
        }.orEmpty()
        val table = root.arr("table")?.mapNotNull { element ->
            (element as? JsonObject)?.let { parseTableRow(it) }
        }.orEmpty()
        return SportsExtraLeagueData(
            season = root.str("season"),
            events = events,
            table = table,
        )
    }

    /** The shared ESPN scoreboard for a day, or null when the file/day is missing. */
    suspend fun espnScoreboard(espnPath: String, day: LocalDate): JsonObject? {
        val file = safeFileId(espnPath)
        val key = day.format(DateTimeFormatter.BASIC_ISO_DATE)
        val root = fetchJson("espn/$file.json", ESPN_TTL_MS) ?: return null
        val days = root.obj("days") ?: return null
        val dayObject = days.obj(key) ?: return null
        return dayObject.obj("raw")
    }

    suspend fun wrestling(): List<SportsEvent> {
        val root = fetchJson("wrestling.json", WRESTLING_TTL_MS) ?: return emptyList()
        return root.arr("events")?.mapNotNull { element ->
            (element as? JsonObject)?.let { parseWrestlingEvent(it) }
        }.orEmpty()
    }

    /* ------------------------------------------------------------- parsing */

    private fun parseExtraLeague(item: JsonObject): SportsExtraLeague? {
        val id = item.str("id") ?: return null
        val name = item.str("name") ?: return null
        val teams = item.arr("teams")?.mapNotNull { element ->
            val team = element as? JsonObject ?: return@mapNotNull null
            val teamId = team.str("id") ?: return@mapNotNull null
            SportsTeamInfo(
                id = teamId,
                name = team.str("name") ?: "Team",
                badgeUrl = team.str("badge"),
                leagueName = name,
            )
        }.orEmpty()
        return SportsExtraLeague(
            id = "tsdb/$id",
            sourceId = id,
            name = name,
            sport = mapSport(item.str("sport")),
            shortName = item.str("short") ?: name.take(6).uppercase(Locale.US),
            badgeUrl = item.str("badge"),
            teams = teams,
            words = item.arr("words")?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
        )
    }

    private fun parseLeagueEvent(item: JsonObject, leagueId: String): SportsEvent? {
        val id = item.str("id") ?: return null
        val start = item.str("start")
        val startMs = runCatching { Instant.parse(start).toEpochMilli() }.getOrNull()
        val home = item.str("home")
        val away = item.str("away")
        val state = item.str("state")
        val hs = item.str("hs")?.toIntOrNull()
        val `as` = item.str("as")?.toIntOrNull()
        val date = startMs?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        }
        val time = if (item.str("hasTime")?.toBoolean() == true && startMs != null) {
            Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US))
        } else {
            null
        }
        return SportsEvent(
            id = id,
            leagueId = leagueId,
            leagueName = null,
            sport = "Soccer",
            title = listOfNotNull(home, away).joinToString(" vs ").ifBlank { "Match" },
            homeTeam = home,
            awayTeam = away,
            homeTeamId = item.str("homeId"),
            awayTeamId = item.str("awayId"),
            homeScore = hs,
            awayScore = `as`,
            date = date,
            time = time,
            timestamp = start,
            status = item.str("status"),
            state = state,
            venue = item.str("venue"),
        )
    }

    private fun parseTableRow(item: JsonObject): SportsTableRow? {
        val team = item.str("name") ?: return null
        return SportsTableRow(
            position = item.int("pos") ?: 0,
            team = team,
            badgeUrl = item.str("badge"),
            played = item.int("p"),
            wins = item.int("w"),
            draws = item.int("d"),
            losses = item.int("l"),
            goalDifference = item.str("gd")?.toIntOrNull(),
            points = item.int("pts"),
        )
    }

    private fun parseWrestlingEvent(item: JsonObject): SportsEvent? {
        val id = item.str("id") ?: return null
        val start = item.str("start")
        val startMs = runCatching { Instant.parse(start).toEpochMilli() }.getOrNull()
        val leagueKey = item.str("league").orEmpty()
        val leagueName = when (leagueKey.lowercase(Locale.US)) {
            "wwe" -> "WWE"
            "aew" -> "AEW"
            "tna" -> "TNA Wrestling"
            "boxing" -> "Boxing"
            else -> leagueKey.replaceFirstChar { it.uppercase(Locale.US) }
        }
        val sport = if (leagueKey.equals("boxing", ignoreCase = true)) "Fighting" else "Wrestling"
        val name = item.str("name") ?: "Event"
        return SportsEvent(
            id = id,
            leagueId = "guide/$leagueKey",
            leagueName = leagueName,
            sport = sport,
            title = name,
            homeTeam = name,
            awayTeam = null,
            date = startMs?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            },
            time = if (item.str("hasTime")?.toBoolean() == true && startMs != null) {
                Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US))
            } else {
                null
            },
            timestamp = start,
            status = null,
            state = if (startMs != null && startMs < System.currentTimeMillis()) "post" else "pre",
            venue = listOfNotNull(item.str("venue"), item.str("city"), item.str("country"))
                .joinToString(", ")
                .ifBlank { null },
        )
    }

    private fun mapSport(raw: String?): String = when (raw?.uppercase(Locale.US)) {
        "FOOTBALL" -> "Soccer"
        "BASKETBALL" -> "Basketball"
        "AMERICAN FOOTBALL" -> "American Football"
        "BASEBALL" -> "Baseball"
        "ICE HOCKEY", "HOCKEY" -> "Ice Hockey"
        "TENNIS" -> "Tennis"
        "GOLF" -> "Golf"
        "CRICKET" -> "Cricket"
        "CYCLING" -> "Cycling"
        "MOTORSPORT" -> "Motorsport"
        "FIGHTING", "BOXING", "MMA" -> "Fighting"
        "WRESTLING" -> "Wrestling"
        else -> raw?.lowercase(Locale.US)?.replaceFirstChar { it.uppercase(Locale.US) } ?: "Soccer"
    }

    /* ---------------------------------------------------------------- HTTP */

    private suspend fun fetchJson(path: String, ttlMs: Long): JsonObject? {
        val url = BASE_URL + path
        SportsCache.get(url, ttlMs)?.let { cached ->
            runCatching { sportsJson.parseToJsonElement(cached) as? JsonObject }.getOrNull()
                ?.let { return it }
        }
        val body = runCatching { fetch(url) }.getOrNull() ?: return null
        SportsCache.put(url, body)
        return runCatching { sportsJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
    }

    private suspend fun fetch(url: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val connection = (java.net.URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", "NuvioOneDesktop/0.1")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val input = BufferedInputStream(connection.getInputStream())
            val stream = if (connection.contentEncoding.equals("gzip", true)) GZIPInputStream(input) else input
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

internal data class SportsExtraLeague(
    val id: String,
    val sourceId: String,
    val name: String,
    val sport: String,
    val shortName: String,
    val badgeUrl: String?,
    val teams: List<SportsTeamInfo>,
    val words: List<String> = emptyList(),
)

internal data class SportsExtraLeagueData(
    val season: String?,
    val events: List<SportsEvent>,
    val table: List<SportsTableRow>,
)
