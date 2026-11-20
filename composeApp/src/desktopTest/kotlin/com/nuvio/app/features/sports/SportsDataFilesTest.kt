package com.nuvio.app.features.sports

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SportsDataFilesTest {
    @Test
    fun searchFindsTheExtraLeagues() {
        fun league(id: String, name: String, short: String, words: List<String>) = CuratedLeague(
            id = id,
            name = name,
            shortName = short,
            sport = "Soccer",
            country = "",
            searchTerms = words,
        )

        val leagues = listOf(
            league("tsdb/196", "Ekstraklasa", "EKSTRA", listOf("Ekstraklasa")),
            league("tsdb/197", "Poland I Liga", "PIL", listOf("I Liga", "Poland I Liga")),
            league("tsdb/198", "Puchar Polski", "PUCHAR", listOf("Puchar Polski")),
            league("tsdb/9116", "K League 2", "KL2", listOf("K League 2")),
            league("tsdb/163", "Switzerland Challenge League", "SCL", listOf("Challenge League")),
            league("4328", "English Premier League", "EPL", emptyList()),
        )

        assertEquals(leagues, filterSportsLeagues(leagues, ""))
        assertEquals(leagues, filterSportsLeagues(leagues, "   "))
        assertEquals(listOf("Ekstraklasa"), filterSportsLeagues(leagues, "ekstra").map { it.name })
        assertEquals(listOf("Poland I Liga"), filterSportsLeagues(leagues, "I Liga").map { it.name })
        assertEquals(listOf("Puchar Polski"), filterSportsLeagues(leagues, "puchar").map { it.name })
        assertEquals(listOf("K League 2"), filterSportsLeagues(leagues, "K League 2").map { it.name })
        assertEquals(
            listOf("Switzerland Challenge League"),
            filterSportsLeagues(leagues, "Challenge League").map { it.name },
        )
        assertEquals(listOf("English Premier League"), filterSportsLeagues(leagues, "EPL").map { it.name })
        assertTrue(filterSportsLeagues(leagues, "Serie A").isEmpty())
    }

    @Test
    fun teamColoursComeFromTheFeedPaletteOrName() {
        assertEquals(Color(0xFFE20520), sportsTeamColor("Arsenal"))
        assertEquals(Color(0xFFC8102E), sportsTeamColor("Atlanta Hawks"))
        assertEquals(Color(0xFF003580), sportsTeamColor("Finland Basketball Women"))
        assertEquals(Color(0xFF195CCD), sportsTeamColor("Faroe Islands"))
        assertEquals(Color(0xFFFF0000), sportsTeamColor("Test", "FF0000", "000000"))
        assertEquals("ARS", sportsTeamAbbr("Arsenal"))
        assertEquals("FIN", sportsTeamAbbr("Finland"))
        val unknown = sportsTeamColor("Some Made Up Team")
        assertEquals(1f, unknown.alpha)
    }

    @Test
    fun espnIdsBecomeSafeFileNames() {
        assertEquals("soccer_eng_1", SportsDataClient.safeFileId("soccer/eng.1"))
        assertEquals("basketball_nba", SportsDataClient.safeFileId("basketball/nba"))
        assertEquals("football_nfl", SportsDataClient.safeFileId("football/nfl"))
    }

    @Test
    fun creditsNameEverySource() {
        val credits = SportsDataClient.credits.joinToString(", ")
        listOf("ESPN", "FotMob", "WWE.com", "TNA", "Box.live", "BoxingScene")
            .forEach { source -> assertTrue(credits.contains(source), "missing credit: $source") }
    }

    @Test(timeout = 240_000)
    fun teamSearchFindsEspnAndExtraLeagueTeams() = runBlocking {
        val lakers = SportsRepository.searchTeams("Lakers")
        println("sports-team-search lakers=" + lakers.map { it.team.name })
        assertTrue(lakers.any { it.team.name == "Los Angeles Lakers" && it.team.id.startsWith("espn/") })

        val arsenal = SportsRepository.searchTeams("Arsenal")
        assertTrue(arsenal.any { it.team.name == "Arsenal" })

        val extra = SportsRepository.searchTeams("Cracovia")
        assertTrue(extra.any { it.team.id.startsWith("tsdb/") })
    }

    @Test(timeout = 240_000)
    fun espnGameDetailsHaveStatsScoringAndQuarters() = runBlocking {
        val details = EspnClient.gameDetails("football/nfl", "401873053", "American Football")
        println(
            "sports-game-details stats=${details.stats.size} leaders=${details.leaders.size} " +
                "scoring=${details.scoring.size} quarters=${details.quarters.size}",
        )

        assertTrue(details.stats.isNotEmpty(), "no team stats")
        assertTrue(details.leaders.isNotEmpty(), "no top performers")
        assertTrue(details.scoring.isNotEmpty(), "no scoring plays")
        assertTrue(details.quarters.isNotEmpty(), "no quarter scores")
    }

    @Test(timeout = 240_000)
    fun nbaTeamsComeFromEspn() = runBlocking {
        val nba = requireNotNull(SportsCatalog.league("4387")) { "NBA is missing from the catalog" }
        val teams = SportsRepository.leagueTeams(nba)
        println("sports-nba-teams: ${teams.size}")

        assertEquals(30, teams.size)
        assertTrue(teams.all { it.id.startsWith("espn/basketball/nba/") })
        assertTrue(teams.all { it.color != null }, "an NBA team has no colour")
        assertTrue(teams.all { it.badgeUrl != null }, "an NBA team has no badge")
        assertTrue(teams.any { it.name == "Atlanta Hawks" && it.abbr == "ATL" })
        assertTrue(teams.any { it.name == "Golden State Warriors" })
    }

    @Test(timeout = 240_000)
    fun espnTeamPageHasGames() = runBlocking {
        val page = SportsRepository.teamPage("espn/basketball/nba/1")
        println(
            "sports-espn-team: ${page.team?.name} next=${page.next.size} last=${page.last.size}",
        )

        assertEquals("Atlanta Hawks", page.team?.name)
        assertTrue(page.next.isNotEmpty(), "ESPN team page has no upcoming games")
        assertTrue(page.last.isNotEmpty(), "ESPN team page has no results")
        assertTrue(page.last.all { it.isFinished })
    }

    @Test(timeout = 240_000)
    fun indexListsTheExtraLeagues() = runBlocking {
        val leagues = SportsDataClient.index()
        println("sports-index: extra leagues=${leagues.size}")

        assertTrue(leagues.size >= 90, "extra league index looks short: ${leagues.size}")
        assertEquals(leagues.size, leagues.map { it.id }.toSet().size, "duplicate extra league ids")
        assertEquals(
            leagues.size,
            leagues.map { it.sourceId }.toSet().size,
            "duplicate FotMob ids",
        )

        val wanted = listOf("Ekstraklasa", "Poland I Liga", "Puchar Polski", "K League 2", "Challenge League")
        wanted.forEach { name ->
            assertTrue(
                leagues.any { it.name.contains(name, ignoreCase = true) },
                "index is missing $name",
            )
        }

        val ekstraklasa = leagues.first { it.name.equals("Ekstraklasa", ignoreCase = true) }
        assertTrue(ekstraklasa.id.startsWith("tsdb/"), "extra league id not namespaced: ${ekstraklasa.id}")
        assertTrue(ekstraklasa.teams.isNotEmpty(), "Ekstraklasa has no teams in the index")
        assertTrue(
            ekstraklasa.teams.all { it.badgeUrl != null },
            "some Ekstraklasa teams have no badge",
        )
    }

    @Test(timeout = 240_000)
    fun extraLeagueFileHasMatchesAndTable() = runBlocking {
        val data = requireNotNull(SportsDataClient.league("197")) {
            "sportsdb/197.json did not load"
        }
        println(
            "sports-extra: events=${data.events.size} table=${data.table.size} season=${data.season}",
        )

        assertTrue(data.events.isNotEmpty(), "Poland I Liga has no events")
        assertTrue(data.table.isNotEmpty(), "Poland I Liga has no table")
        assertTrue(data.table.any { it.position == 1 }, "table has no first row")
        assertTrue(data.events.all { it.startEpochMs != null }, "an event has no start time")
        assertTrue(
            data.events.all { it.homeTeam != null || it.awayTeam != null },
            "an event has no teams",
        )
    }

    @Test(timeout = 240_000)
    fun wrestlingFileSeparatesThePromotions() = runBlocking {
        val events = SportsDataClient.wrestling()
        println("sports-wrestling: events=${events.size}")

        assertTrue(events.isNotEmpty(), "wrestling.json has no events")
        listOf("guide/wwe", "guide/aew", "guide/tna", "guide/boxing").forEach { id ->
            assertTrue(events.any { it.leagueId == id }, "no events for $id")
        }
        val tna = events.filter { it.leagueId == "guide/tna" }
        assertTrue(tna.all { it.leagueName == "TNA Wrestling" }, "TNA events have the wrong league")
        assertTrue(
            events.filter { it.leagueId == "guide/boxing" }.all { it.sport == "Fighting" },
            "boxing events are not classified as Fighting",
        )
    }
}
