package com.nuvio.app.features.sports

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SportsCatalogTest {
    @Test
    fun curatedCatalogIsConsistent() {
        assertTrue(SportsCatalog.sports.isNotEmpty())
        assertTrue(SportsCatalog.leagues.isNotEmpty())

        val ids = SportsCatalog.leagues.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate league ids")

        SportsCatalog.leagues.forEach { league ->
            assertTrue(league.id.isNotBlank(), "blank id for ${league.name}")
            assertTrue(league.name.isNotBlank(), "blank name for ${league.id}")
            assertTrue(league.sport.isNotBlank(), "blank sport for ${league.id}")
            assertTrue(
                SportsCatalog.sport(league.sport) != null,
                "sport '${league.sport}' for ${league.name} is not in the sport rail",
            )
            assertTrue(
                SportsCatalog.leaguesForSport(league.sport).any { it.id == league.id },
                "${league.name} is not returned by its sport filter",
            )
        }
    }
}

class SportsRepositorySmokeTest {
    @Test(timeout = 240_000)
    fun premierLeaguePageLoads() = runBlocking {
        val league = requireNotNull(SportsCatalog.league("4328")) {
            "Premier League is missing from the curated catalog"
        }

        val page = SportsRepository.leaguePage(league)
        println(
            "sports-smoke: league=${page.league.name} upcoming=${page.upcoming.size} " +
                "results=${page.results.size} table=${page.table.size} errors=${page.errors}",
        )

        assertTrue(page.league.name.contains("Premier League", ignoreCase = true))
        assertTrue(
            page.upcoming.isNotEmpty() || page.results.isNotEmpty() || page.table.isNotEmpty(),
            "ESPN returned no league data: ${page.errors}",
        )
    }
}
