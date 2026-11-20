package com.nuvio.app.features.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Free, keyless ESPN scoreboard fallback.
 *
 * ESPN's endpoints are undocumented and can change, so every failure is
 * treated as "no extra events" and the shared NuvioOne data still renders.
 */
internal object EspnClient {
    private const val BASE_URL = "https://site.api.espn.com/apis/site/v2/sports"
    private const val CACHE_TTL_MS = 3 * 60_000L
    private const val TEAMS_TTL_MS = 6 * 60 * 60_000L
    private const val MONTH_TTL_MS = 30 * 60_000L
    private const val GAME_TTL_MS = 60_000L

    suspend fun scoreboard(league: CuratedLeague): List<SportsEvent> {
        val path = league.espnPath ?: return emptyList()
        val url = "$BASE_URL/$path/scoreboard"

        SportsCache.get(url, CACHE_TTL_MS)?.let { cached ->
            return runCatching { parse(cached, league) }.getOrDefault(emptyList())
        }

        val body = withContext(Dispatchers.IO) { fetch(url) }
        SportsCache.put(url, body)
        return parse(body, league)
    }

    /** One league's scoreboard for a specific day. */
    suspend fun scoreboardForDate(league: CuratedLeague, date: LocalDate): List<SportsEvent> {
        val path = league.espnPath ?: return emptyList()
        val day = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val url = "$BASE_URL/$path/scoreboard?dates=$day"
        val body = SportsCache.get(url, CACHE_TTL_MS)
            ?: withContext(Dispatchers.IO) { fetch(url) }.also { SportsCache.put(url, it) }
        return parse(body, league)
    }

    /** A whole month of a league's games (ESPN accepts `yyyyMM`, not day ranges). */
    suspend fun monthScoreboard(league: CuratedLeague, month: YearMonth): List<SportsEvent> {
        val path = league.espnPath ?: return emptyList()
        val key = month.format(DateTimeFormatter.ofPattern("yyyyMM"))
        val url = "$BASE_URL/$path/scoreboard?dates=$key"
        val body = SportsCache.get(url, MONTH_TTL_MS)
            ?: withContext(Dispatchers.IO) { fetch(url) }.also { SportsCache.put(url, it) }
        return parse(body, league)
    }

    /** Table for an ESPN league. */
    suspend fun standings(league: CuratedLeague): List<SportsTableRow> {
        val path = league.espnPath ?: return emptyList()
        val url = "https://site.api.espn.com/apis/v2/sports/$path/standings"
        val body = SportsCache.get(url, TEAMS_TTL_MS)
            ?: withContext(Dispatchers.IO) { fetch(url) }.also { SportsCache.put(url, it) }
        val root = runCatching { sportsJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return emptyList()
        val entries = root.arr("children")
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.obj("standings")
            ?.arr("entries")
            ?: return emptyList()
        return entries.mapIndexedNotNull { index, element ->
            val entry = element as? JsonObject ?: return@mapIndexedNotNull null
            val team = entry.obj("team") ?: return@mapIndexedNotNull null
            val name = team.str("displayName") ?: team.str("name") ?: return@mapIndexedNotNull null
            val stats = entry.arr("stats").orEmpty()
                .mapNotNull { stat -> (stat as? JsonObject)?.let { it.str("name") to it.number("value") } }
                .toMap()
            SportsTableRow(
                position = stats["rank"]?.toInt() ?: index + 1,
                team = name,
                badgeUrl = team.str("logo")
                    ?: team.arr("logos")?.firstOrNull()?.let { (it as? JsonObject)?.str("href") },
                color = team.str("color"),
                altColor = team.str("alternateColor"),
                played = stats["gamesPlayed"]?.toInt(),
                wins = stats["wins"]?.toInt(),
                draws = stats["ties"]?.toInt(),
                losses = stats["losses"]?.toInt(),
                goalDifference = stats["pointDifferential"]?.toInt(),
                points = stats["points"]?.toInt(),
            )
        }
    }

    private fun JsonObject.number(key: String): Double? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.toDoubleOrNull()

    /** Live/finished game detail panels (stats, leaders, scoring, by quarter). */
    suspend fun gameDetails(espnPath: String, eventId: String, sport: String): SportsGameDetails {
        val url = "$BASE_URL/$espnPath/summary?event=$eventId"
        val body = SportsCache.get(url, GAME_TTL_MS)
            ?: withContext(Dispatchers.IO) { fetch(url) }.also { SportsCache.put(url, it) }
        val root = runCatching { sportsJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return SportsGameDetails()

        val competition = root.obj("header")
            ?.arr("competitions")
            ?.firstOrNull()
            ?.let { it as? JsonObject }
        val competitors = competition?.arr("competitors").orEmpty()
        val home = competitors.firstOrNull { (it as? JsonObject)?.str("homeAway") == "home" }
            ?.let { it as? JsonObject }
        val away = competitors.firstOrNull { (it as? JsonObject)?.str("homeAway") == "away" }
            ?.let { it as? JsonObject }
        val homeId = home?.obj("team")?.str("id")
        val awayId = away?.obj("team")?.str("id")

        return SportsGameDetails(
            stats = teamStats(root, homeId, awayId),
            leaders = leaders(root),
            scoring = scoringPlays(root, sport),
            quarters = quarterScores(home, away, sport),
        )
    }

    private fun quarterScores(
        home: JsonObject?,
        away: JsonObject?,
        sport: String,
    ): List<SportsQuarterScore> {
        val homeLines = home?.arr("linescores").orEmpty()
        val awayLines = away?.arr("linescores").orEmpty()
        val count = maxOf(homeLines.size, awayLines.size)
        if (count == 0) return emptyList()
        return (0 until count).map { index ->
            SportsQuarterScore(
                label = periodLabel(index, sport),
                home = (homeLines.getOrNull(index) as? JsonObject)?.str("displayValue").orEmpty(),
                away = (awayLines.getOrNull(index) as? JsonObject)?.str("displayValue").orEmpty(),
            )
        }
    }

    private fun periodLabel(index: Int, sport: String): String = when (sport) {
        "Soccer" -> if (index < 2) "${index + 1}H" else "ET${index - 1}"
        "Ice Hockey" -> "P${index + 1}"
        "Baseball" -> "${index + 1}"
        else -> if (index < 4) "Q${index + 1}" else "OT${index - 3}"
    }

    private fun teamStats(root: JsonObject, homeId: String?, awayId: String?): List<SportsTeamStat> {
        val teams = root.obj("boxscore")?.arr("teams").orEmpty()
            .mapNotNull { it as? JsonObject }
        val home = teams.firstOrNull { it.obj("team")?.str("id") == homeId }
        val away = teams.firstOrNull { it.obj("team")?.str("id") == awayId }
        val homeStats = home?.arr("statistics").orEmpty()
            .mapNotNull { it as? JsonObject }
            .associateBy { it.str("name") ?: it.str("label") ?: "" }
        val awayStats = away?.arr("statistics").orEmpty()
            .mapNotNull { it as? JsonObject }
            .associateBy { it.str("name") ?: it.str("label") ?: "" }
        return homeStats.keys
            .filter { it.isNotBlank() }
            .mapNotNull { key ->
                val h = homeStats[key]
                val a = awayStats[key]
                val label = h?.str("label") ?: h?.str("name") ?: key
                val homeValue = h?.str("displayValue") ?: "-"
                val awayValue = a?.str("displayValue") ?: "-"
                if (homeValue == "-" && awayValue == "-") null
                else SportsTeamStat(label = label, home = homeValue, away = awayValue)
            }
            .take(12)
    }

    private fun leaders(root: JsonObject): List<SportsLeader> {
        val out = mutableListOf<SportsLeader>()
        root.obj("boxscore")?.arr("players").orEmpty()
            .mapNotNull { it as? JsonObject }
            .forEach { teamNode ->
                val team = teamNode.obj("team")?.str("displayName").orEmpty()
                teamNode.arr("statistics").orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .take(3)
                    .forEach { group ->
                        val category = group.str("text") ?: group.str("name") ?: return@forEach
                        val labels = group.arr("labels").orEmpty()
                            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                        val athlete = group.arr("athletes").orEmpty()
                            .firstOrNull() as? JsonObject ?: return@forEach
                        val name = athlete.obj("athlete")?.str("displayName") ?: return@forEach
                        val values = athlete.arr("stats").orEmpty()
                            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                        val line = labels.zip(values)
                            .take(4)
                            .joinToString("  ") { "${it.first} ${it.second}" }
                        out += SportsLeader(
                            team = team,
                            category = category,
                            player = name,
                            statLine = line,
                        )
                    }
            }
        return out.take(6)
    }

    private fun scoringPlays(root: JsonObject, sport: String): List<SportsScoringPlay> =
        root.arr("scoringPlays").orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { play ->
                val text = play.str("text") ?: return@mapNotNull null
                val period = play.obj("period")?.int("number") ?: 0
                val clock = play.obj("clock")?.str("displayValue").orEmpty()
                val team = play.obj("team")?.str("abbreviation")
                    ?: play.obj("team")?.str("displayName").orEmpty()
                val awayScore = play.int("awayScore") ?: 0
                val homeScore = play.int("homeScore") ?: 0
                SportsScoringPlay(
                    period = if (sport == "Soccer") "${period}H" else "Q$period",
                    clock = clock,
                    team = team,
                    description = text,
                    score = "$awayScore - $homeScore",
                )
            }
            .take(16)

    suspend fun teams(league: CuratedLeague): List<SportsTeamInfo> {
        val path = league.espnPath ?: return emptyList()
        val url = "$BASE_URL/$path/teams"
        val body = SportsCache.get(url, TEAMS_TTL_MS)
            ?: withContext(Dispatchers.IO) { fetch(url) }.also { SportsCache.put(url, it) }
        val root = sportsJson.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        val sports = root.arr("sports") ?: return emptyList()
        val teams = sports.firstOrNull()
            ?.let { it as? JsonObject }
            ?.arr("leagues")
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.arr("teams")
            ?: return emptyList()
        return teams.mapNotNull { entry ->
            (entry as? JsonObject)?.obj("team")?.let { parseTeam(it, path, league.name) }
        }
    }

    /** One team plus its next and recent games, addressed as espn/<sport>/<league>/<teamId>. */
    suspend fun teamPage(espnPath: String, teamId: String): TeamPageData {
        val root = sportsJson.parseToJsonElement(
            withContext(Dispatchers.IO) { fetch("$BASE_URL/$espnPath/teams/$teamId") },
        ) as? JsonObject
        val sport = sportForPath(espnPath)
        val league = CuratedLeague(
            id = "espn/$espnPath/$teamId",
            name = sport,
            shortName = sport.take(3),
            sport = sport,
            country = "",
        )
        val info = root?.obj("team")?.let { parseTeam(it, espnPath, null) }

        val scheduleBody = runCatching {
            withContext(Dispatchers.IO) { fetch("$BASE_URL/$espnPath/teams/$teamId/schedule") }
        }.getOrNull()
        val scheduleRoot = scheduleBody
            ?.let { runCatching { sportsJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val events = scheduleRoot?.let { parseScoreboard(it, league) }.orEmpty()
        val next = events.filter { it.state != "post" }
            .sortedBy { it.startEpochMs ?: Long.MAX_VALUE }
            .take(8)
        val last = events.filter { it.state == "post" }
            .sortedByDescending { it.startEpochMs ?: 0L }
            .take(8)
        return TeamPageData(team = info, next = next, last = last)
    }

    private fun parseTeam(node: JsonObject, espnPath: String, leagueName: String?): SportsTeamInfo? {
        val id = node.str("id") ?: return null
        return SportsTeamInfo(
            id = "espn/$espnPath/$id",
            name = node.str("displayName") ?: node.str("name") ?: "Team",
            badgeUrl = node.str("logo")
                ?: node.arr("logos")?.firstOrNull()?.let { (it as? JsonObject)?.str("href") },
            leagueName = leagueName ?: espnLeagueName(espnPath),
            sport = sportForPath(espnPath),
            color = node.str("color"),
            altColor = node.str("alternateColor"),
            abbr = node.str("abbreviation"),
        )
    }

    private fun sportForPath(path: String): String = when (path.substringBefore('/')) {
        "basketball" -> "Basketball"
        "football" -> "American Football"
        "baseball" -> "Baseball"
        "hockey" -> "Ice Hockey"
        "soccer" -> "Soccer"
        "mma" -> "Fighting"
        "racing" -> "Motorsport"
        "tennis" -> "Tennis"
        "golf" -> "Golf"
        "cricket" -> "Cricket"
        else -> "Sports"
    }

    private fun espnLeagueName(path: String): String? = when (path) {
        "basketball/nba" -> "NBA"
        "football/nfl" -> "NFL"
        "baseball/mlb" -> "MLB"
        "hockey/nhl" -> "NHL"
        "soccer/eng.1" -> "English Premier League"
        "soccer/esp.1" -> "Spanish La Liga"
        "soccer/ita.1" -> "Italian Serie A"
        "soccer/ger.1" -> "German Bundesliga"
        "soccer/fra.1" -> "French Ligue 1"
        "soccer/usa.1" -> "Major League Soccer"
        "soccer/uefa.champions" -> "UEFA Champions League"
        "soccer/uefa.europa" -> "UEFA Europa League"
        else -> null
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NuvioOneDesktop/0.1")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw SportsApiException("ESPN HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String, league: CuratedLeague): List<SportsEvent> {
        val root = sportsJson.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        return parseScoreboard(root, league)
    }

    /** Shared with the NuvioOne sports data cache, whose files store the raw scoreboard. */
    internal fun parseScoreboard(root: JsonObject, league: CuratedLeague): List<SportsEvent> {
        val events = root.arr("events") ?: return emptyList()
        return events.mapNotNull { element ->
            val event = element as? JsonObject ?: return@mapNotNull null
            val competition = event.arr("competitions")?.firstJsonObject() ?: return@mapNotNull null
            val competitors = competition.arr("competitors") ?: return@mapNotNull null
            val home = competitors.firstOrNull { it.homeAway() == "home" }
            val away = competitors.firstOrNull { it.homeAway() == "away" }
            val statusNode = competition.obj("status")?.obj("type")
            val state = statusNode?.str("state")
            val shortDetail = statusNode?.str("shortDetail")
            val detail = statusNode?.str("detail")
            val title = event.str("name")
                ?: event.str("shortName")
                ?: listOfNotNull(away?.teamName(), home?.teamName()).joinToString(" vs ")
            val date = event.str("date")

            SportsEvent(
                id = "espn-${event.str("id") ?: title.hashCode()}",
                leagueId = league.id,
                leagueName = league.name,
                sport = league.sport,
                title = title,
                homeTeam = home?.teamName(),
                awayTeam = away?.teamName(),
                homeBadge = home?.teamLogo(),
                awayBadge = away?.teamLogo(),
                homeColor = home?.teamColor(),
                awayColor = away?.teamColor(),
                homeAltColor = home?.teamAltColor(),
                awayAltColor = away?.teamAltColor(),
                homeScore = home?.score(),
                awayScore = away?.score(),
                date = date,
                timestamp = date,
                status = when (state) {
                    "in" -> listOfNotNull("LIVE", shortDetail ?: detail).joinToString(" \u00B7 ")
                    "post" -> "Final"
                    else -> shortDetail ?: detail
                },
                venue = competition.obj("venue")?.str("fullName"),
                state = state,
            )
        }
    }

    private fun JsonElement.homeAway(): String? =
        (this as? JsonObject)?.str("homeAway")

    private fun JsonElement.teamName(): String? {
        val team = (this as? JsonObject)?.obj("team") ?: return null
        return team.str("displayName") ?: team.str("shortDisplayName") ?: team.str("name")
    }

    private fun JsonElement.teamLogo(): String? {
        val team = (this as? JsonObject)?.obj("team") ?: return null
        return team.str("logo")
            ?: team.arr("logos")?.firstOrNull()
                ?.let { (it as? JsonObject)?.str("href") }
    }

    private fun JsonElement.teamColor(): String? =
        ((this as? JsonObject)?.obj("team"))?.str("color")

    private fun JsonElement.teamAltColor(): String? =
        ((this as? JsonObject)?.obj("team"))?.str("alternateColor")

    private fun JsonElement.score(): Int? =
        (this as? JsonObject)?.str("score")?.toIntOrNull()

    private fun JsonArray.firstJsonObject(): JsonObject? = firstOrNull() as? JsonObject
}
