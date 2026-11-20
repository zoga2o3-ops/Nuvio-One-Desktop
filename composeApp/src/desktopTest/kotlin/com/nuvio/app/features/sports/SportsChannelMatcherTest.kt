package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.EpgProgram
import com.nuvio.app.features.iptv.LiveChannel
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportsChannelMatcherTest {
    private fun channel(name: String, group: String = "Sports", number: Int = 1) = LiveChannel(
        key = name,
        sourceId = "test",
        sourceName = "Test playlist",
        name = name,
        tvgId = null,
        tvgName = null,
        logo = null,
        group = group,
        number = number,
        url = "http://example.com/$number.m3u8",
        headers = emptyMap(),
        catchup = null,
        drm = null,
    )

    @Test
    fun matchesTeamNamesAndLeagueNetworks() {
        val event = SportsEvent(
            id = "nba-1",
            leagueName = "NBA",
            sport = "Basketball",
            title = "Los Angeles Lakers vs Golden State Warriors",
            homeTeam = "Los Angeles Lakers",
            awayTeam = "Golden State Warriors",
            timestamp = Instant.now().toString(),
            state = "in",
        )
        val channels = listOf(
            channel("Sky Sports Main Event", number = 1),
            channel("NBA TV", number = 2),
            channel("Lakers TV", number = 3),
            channel("ESPN", number = 4),
        )

        val matched = matchSportsChannels(event, channels)
        assertEquals(listOf("Lakers TV", "NBA TV"), matched.map { it.name })
    }

    @Test
    fun matchesAPremierLeagueNetwork() {
        val event = SportsEvent(
            id = "epl-1",
            leagueName = "English Premier League",
            sport = "Soccer",
            title = "Arsenal vs Chelsea",
            homeTeam = "Arsenal",
            awayTeam = "Chelsea",
            timestamp = Instant.now().toString(),
            state = "pre",
        )
        val channels = listOf(
            channel("Sky Sports Premier League", number = 1),
            channel("Sky Sports Football", number = 2),
            channel("TNT Sports 1", number = 3),
            channel("Cartoon Network", number = 4),
        )

        val matched = matchSportsChannels(event, channels)
        assertTrue(matched.any { it.name == "Sky Sports Premier League" })
        assertTrue(matched.none { it.name == "Cartoon Network" })
    }

    @Test
    fun genericWordsDoNotMatchClubChannels() {
        val event = SportsEvent(
            id = "swe-1",
            leagueName = "Sweden Damallsvenskan",
            sport = "Soccer",
            title = "Eskilstuna United Women vs Djurgården Women",
            homeTeam = "Eskilstuna United Women",
            awayTeam = "Djurgården Women",
            timestamp = Instant.now().toString(),
        )
        val channels = listOf(
            channel("24/7: Take Us Home Leeds United", number = 1),
            channel("24/7: United States of AI", number = 2),
            channel("A-LEAGUE: ADELAIDE UNITED", number = 3),
            channel("Djurgården TV", number = 4),
        )

        val matched = matchSportsChannels(event, channels)
        assertEquals(listOf("Djurgården TV"), matched.map { it.name })
    }

    @Test
    fun epgProgrammeLinksTheChannelToTheGame() {
        val now = System.currentTimeMillis()
        val event = SportsEvent(
            id = "nba-epg",
            leagueName = "NBA",
            sport = "Basketball",
            title = "Los Angeles Lakers vs Golden State Warriors",
            homeTeam = "Los Angeles Lakers",
            awayTeam = "Golden State Warriors",
            timestamp = Instant.ofEpochMilli(now - 30 * 60_000).toString(),
            state = "in",
        )
        val channels = listOf(
            channel("Sports 1", number = 1),
            channel("Sports 2", number = 2),
        )
        val programs = mapOf(
            "Sports 2" to listOf(
                EpgProgram(
                    startMs = now - 60 * 60_000,
                    stopMs = now + 60 * 60_000,
                    title = "NBA Basketball: Los Angeles Lakers at Golden State Warriors",
                ),
            ),
        )

        val matched = matchSportsChannels(event, channels, programs, now)
        assertEquals(listOf("Sports 2"), matched.map { it.name })

        val upcoming = event.copy(
            id = "nba-epg-future",
            timestamp = Instant.ofEpochMilli(now + 2 * 60 * 60_000).toString(),
            state = "pre",
        )
        assertTrue(
            matchSportsChannels(upcoming, channels, programs, now).isEmpty(),
            "an upcoming game matched the EPG programme that is on now",
        )
    }

    @Test
    fun emptyPlaylistMatchesNothing() {
        val event = SportsEvent(id = "x", title = "A vs B", homeTeam = "A", awayTeam = "B")
        assertTrue(matchSportsChannels(event, emptyList()).isEmpty())
    }

    @Test
    fun gamesWithoutAStateGoLiveFromTheStartTime() {
        val live = SportsEvent(
            id = "live",
            title = "A vs B",
            sport = "Basketball",
            timestamp = Instant.now().minusSeconds(30 * 60).toString(),
        )
        val future = SportsEvent(
            id = "future",
            title = "A vs B",
            sport = "Basketball",
            timestamp = Instant.now().plusSeconds(60 * 60).toString(),
        )
        val finished = live.copy(id = "done", state = "post")

        assertTrue(live.isLive)
        assertFalse(future.isLive)
        assertFalse(finished.isLive)
    }
}
