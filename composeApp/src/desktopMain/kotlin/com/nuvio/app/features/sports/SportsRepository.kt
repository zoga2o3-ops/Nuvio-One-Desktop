package com.nuvio.app.features.sports

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth

internal object SportsRepository {
    fun sports(): List<SportCategory> = SportsCatalog.sports

    fun leagues(sport: String): List<CuratedLeague> = SportsCatalog.leaguesForSport(sport)

    @Volatile
    private var extraLeagues: List<SportsExtraLeague> = emptyList()
    @Volatile
    private var extraLeaguesLoadedAt: Long = 0L

    /** Events seen while browsing leagues/teams, for their detail page. */
    @Volatile
    private var cachedEvents: Map<String, SportsEvent> = emptyMap()

    private fun cacheEvents(events: List<SportsEvent>) {
        if (events.isEmpty()) return
        val merged = cachedEvents.toMutableMap()
        events.forEach { merged[it.id] = it }
        cachedEvents = if (merged.size > 500) {
            merged.entries.toList().takeLast(500).associate { it.key to it.value }
        } else {
            merged
        }
    }

    /** Curated leagues plus the extra FotMob leagues from the NuvioOne data files. */
    suspend fun allLeagues(sport: String): List<CuratedLeague> {
        val now = System.currentTimeMillis()
        if (extraLeagues.isEmpty() || now - extraLeaguesLoadedAt > SportsDataClient.INDEX_TTL_MS) {
            runCatching { SportsDataClient.index() }
                .onSuccess {
                    extraLeagues = it
                    extraLeaguesLoadedAt = now
                }
        }
        val extras = extraLeagues
            .filter { it.sport.equals(sport, ignoreCase = true) }
            .map {
                CuratedLeague(
                    id = it.id,
                    name = it.name,
                    shortName = it.shortName,
                    sport = it.sport,
                    country = "",
                    badgeUrl = it.badgeUrl,
                    searchTerms = it.words,
                )
            }
        return leagues(sport) + extras
    }

    @Volatile
    private var teamIndex: List<SportsTeamSearchResult> = emptyList()
    @Volatile
    private var teamIndexLoadedAt: Long = 0L
    private val teamIndexMutex = Mutex()

    private val teamSearchLeagues: List<CuratedLeague> by lazy {
        SportsCatalog.leagues.filter { it.espnPath != null && it.sport in TEAM_SPORTS } + listOf(
            CuratedLeague(
                id = "search/national",
                name = "National Teams",
                shortName = "NAT",
                sport = "Soccer",
                country = "Europe",
                espnPath = "soccer/uefa.nations",
            ),
            CuratedLeague(
                id = "search/world",
                name = "World Cup",
                shortName = "WC",
                sport = "Soccer",
                country = "World",
                espnPath = "soccer/fifa.world",
            ),
        )
    }

    /** Fills the team search index (extra FotMob leagues + ESPN team lists). */
    suspend fun warmTeamIndex() {
        if (teamIndex.isNotEmpty() && System.currentTimeMillis() - teamIndexLoadedAt < SportsDataClient.INDEX_TTL_MS) {
            return
        }
        teamIndexMutex.withLock {
            val now = System.currentTimeMillis()
            if (teamIndex.isNotEmpty() && now - teamIndexLoadedAt < SportsDataClient.INDEX_TTL_MS) {
                return@withLock
            }

            if (extraLeagues.isEmpty()) {
                runCatching { SportsDataClient.index() }
                    .onSuccess {
                        extraLeagues = it
                        extraLeaguesLoadedAt = now
                    }
            }

            val results = mutableListOf<SportsTeamSearchResult>()
            extraLeagues.forEach { league ->
                league.teams.forEach { team ->
                    results += SportsTeamSearchResult(
                        team = team.copy(
                            id = "tsdb/${league.sourceId}/${team.id}",
                            leagueName = league.name,
                            sport = league.sport,
                        ),
                        leagueName = league.name,
                        sport = league.sport,
                    )
                }
            }

            coroutineScope {
                teamSearchLeagues
                    .map { league ->
                        async {
                            runCatching { EspnClient.teams(league) }
                                .getOrDefault(emptyList())
                                .map { team ->
                                    SportsTeamSearchResult(
                                        team = team,
                                        leagueName = league.name,
                                        sport = league.sport,
                                    )
                                }
                        }
                    }
                    .awaitAll()
                    .forEach { results += it }
            }

            teamIndex = results.distinctBy { sportsTeamKey(it.team.name) }
            teamIndexLoadedAt = now
        }
    }

    /** Team search across the extra leagues and the ESPN-backed catalog. */
    suspend fun searchTeams(query: String, limit: Int = 60): List<SportsTeamSearchResult> {
        warmTeamIndex()
        val needle = sportsTeamKey(query)
        if (needle.isBlank()) return emptyList()
        return teamIndex.asSequence()
            .map { it to sportsTeamKey(it.team.name) }
            .filter { (_, key) ->
                key.contains(needle) ||
                    key.replace(" ", "").contains(needle.replace(" ", ""))
            }
            .sortedWith(
                compareBy(
                    { (_, key) -> if (key.startsWith(needle)) 0 else 1 },
                    { (_, key) -> key.length },
                ),
            )
            .map { it.first }
            .take(limit)
            .toList()
    }

    // Public reads go through the score delay (Sports settings); the *Raw
    // functions hold the actual loading.
    suspend fun leaguePage(league: CuratedLeague): LeaguePageData = leaguePageRaw(league).let { page ->
        page.copy(upcoming = SportsScoreDelay.apply(page.upcoming), results = SportsScoreDelay.apply(page.results))
    }

    private suspend fun leaguePageRaw(league: CuratedLeague): LeaguePageData {
        if (league.id.startsWith("tsdb/")) {
            return tsdbLeaguePage(league)
        }
        if (league.id.startsWith("guide/")) {
            return guideLeaguePage(league)
        }
        if (league.id == "4444") {
            // WWE's catalog id is legacy; its schedule lives in the shared wrestling feed.
            return guideLeaguePage(league, "guide/wwe")
        }
        if (league.espnPath != null) {
            return espnLeaguePage(league)
        }
        return LeaguePageData(league = catalogLeague(league))
    }

    private fun catalogLeague(league: CuratedLeague) = SportsLeague(
        id = league.id,
        name = league.name,
        shortName = league.shortName,
        sport = league.sport,
        country = league.country,
        badgeUrl = league.badgeUrl,
    )

    private suspend fun espnLeaguePage(league: CuratedLeague): LeaguePageData = coroutineScope {
        val month = YearMonth.now()
        val months = listOf(month.minusMonths(1), month, month.plusMonths(1))
        val eventsDeferred = async {
            months.map { m ->
                async {
                    runCatching { EspnClient.monthScoreboard(league, m) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinctBy { it.id }
        }
        val tableDeferred = async {
            runCatching { EspnClient.standings(league) }.getOrDefault(emptyList())
        }

        val events = eventsDeferred.await()
        cacheEvents(events)
        val now = System.currentTimeMillis()
        val upcoming = events
            .filter { it.state != "post" }
            .filter { event ->
                val start = event.startEpochMs
                start == null || start >= now - 3 * 60 * 60 * 1000L
            }
            .sortedBy { it.startEpochMs ?: Long.MAX_VALUE }
        val results = events
            .filter { it.state == "post" }
            .sortedByDescending { it.startEpochMs ?: 0L }

        LeaguePageData(
            league = catalogLeague(league).copy(currentSeason = month.year.toString()),
            upcoming = upcoming,
            results = results,
            table = tableDeferred.await(),
        )
    }

    private suspend fun tsdbLeaguePage(league: CuratedLeague): LeaguePageData {
        val tsdbId = league.id.removePrefix("tsdb/")
        val data = SportsDataClient.league(tsdbId)
            ?: return LeaguePageData(
                league = catalogLeague(league),
                errors = listOf("Could not load ${league.name}."),
            )
        val events = data.events.map { it.copy(leagueName = league.name, sport = league.sport) }
        cacheEvents(events)
        return LeaguePageData(
            league = catalogLeague(league).copy(currentSeason = data.season),
            upcoming = events.filter { it.state != "post" }
                .sortedBy { it.startEpochMs ?: Long.MAX_VALUE },
            results = events.filter { it.state == "post" }
                .sortedByDescending { it.startEpochMs ?: 0L },
            table = data.table,
        )
    }

    private suspend fun guideLeaguePage(
        league: CuratedLeague,
        guideId: String = league.id,
    ): LeaguePageData {
        val events = SportsDataClient.wrestling()
            .filter { it.leagueId == guideId }
        cacheEvents(events)
        return LeaguePageData(
            league = catalogLeague(league),
            upcoming = events.filter { it.state != "post" }
                .sortedBy { it.startEpochMs ?: Long.MAX_VALUE },
            results = events.filter { it.state == "post" }
                .sortedByDescending { it.startEpochMs ?: 0L },
        )
    }

    suspend fun eventPage(eventId: String): EventPageData = eventPageRaw(eventId).let { page ->
        page.copy(event = SportsScoreDelay.applyOrNull(page.event))
    }

    private suspend fun eventPageRaw(eventId: String): EventPageData {
        cachedEvents[eventId]?.let { cached ->
            val details = if (cached.id.startsWith("espn-")) {
                val path = cached.leagueId?.let { SportsCatalog.league(it)?.espnPath }
                val espnId = cached.id.removePrefix("espn-")
                if (path != null && espnId.isNotBlank()) {
                    runCatching {
                        EspnClient.gameDetails(path, espnId, cached.sport.orEmpty())
                    }.getOrNull()
                } else {
                    null
                }
            } else {
                null
            }
            return EventPageData(
                event = cached,
                stats = details?.stats.orEmpty(),
                leaders = details?.leaders.orEmpty(),
                scoring = details?.scoring.orEmpty(),
                quarters = details?.quarters.orEmpty(),
            )
        }
        if (eventId.any { !it.isDigit() }) {
            val event = runCatching { SportsDataClient.wrestling() }
                .getOrDefault(emptyList())
                .firstOrNull { it.id == eventId }
            return EventPageData(event = event)
        }
        return EventPageData()
    }

    /** Every event on one date for a sport, used by the hub's live/upcoming rows. */
    suspend fun eventsForDay(sport: String, date: String): List<SportsEvent> =
        SportsScoreDelay.apply(eventsForDayRaw(sport, date))

    private suspend fun eventsForDayRaw(sport: String, date: String): List<SportsEvent> {
        val day = LocalDate.parse(date)
        val wrestling = SportsDataClient.wrestling().filter { it.date == date }
        if (sport == "Wrestling") {
            return wrestling.filter { it.sport == "Wrestling" }
                .sortedBy { it.startEpochMs ?: Long.MAX_VALUE }
                .also(::cacheEvents)
        }
        if (sport == "Fighting") {
            val boxing = wrestling.filter { it.sport == "Fighting" }
            val mma = espnEventsForDay("Fighting", day)
            return (boxing + mma)
                .distinctBy { it.id }
                .sortedBy { it.startEpochMs ?: Long.MAX_VALUE }
        }
        return espnEventsForDay(sport, day)
    }

    private suspend fun espnEventsForDay(sport: String, day: LocalDate): List<SportsEvent> = coroutineScope {
        val leagues = SportsCatalog.leaguesForSport(sport).filter { it.espnPath != null }
        leagues.map { league ->
            async {
                val shared = runCatching {
                    SportsDataClient.espnScoreboard(league.espnPath!!, day)
                }.getOrNull()
                if (shared != null) {
                    EspnClient.parseScoreboard(shared, league)
                } else {
                    runCatching { EspnClient.scoreboardForDate(league, day) }
                        .getOrDefault(emptyList())
                }
            }
        }.awaitAll().flatten().distinctBy { it.id }.also(::cacheEvents)
    }

    /** Teams for an ESPN-backed league. */
    suspend fun leagueTeams(league: CuratedLeague): List<SportsTeamInfo> =
        if (league.espnPath != null) {
            runCatching { EspnClient.teams(league) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

    /** Team details plus their next matches and recent results. */
    suspend fun teamPage(teamId: String): TeamPageData = teamPageRaw(teamId).let { page ->
        page.copy(next = SportsScoreDelay.apply(page.next), last = SportsScoreDelay.apply(page.last))
    }

    private suspend fun teamPageRaw(teamId: String): TeamPageData {
        if (teamId.startsWith("espn/")) {
            val rest = teamId.removePrefix("espn/")
            val espnPath = rest.substringBeforeLast('/')
            val id = rest.substringAfterLast('/')
            if (espnPath.isNotBlank() && id.isNotBlank()) {
                return EspnClient.teamPage(espnPath, id)
            }
        }
        if (teamId.startsWith("tsdb/")) {
            return tsdbTeamPage(teamId)
        }
        return TeamPageData()
    }

    private suspend fun tsdbTeamPage(teamId: String): TeamPageData {
        val parts = teamId.split('/')
        val leagueId = parts.getOrNull(1) ?: return TeamPageData()
        val tid = parts.getOrNull(2) ?: return TeamPageData()
        if (extraLeagues.isEmpty()) {
            runCatching { SportsDataClient.index() }.onSuccess { extraLeagues = it }
        }
        val league = extraLeagues.firstOrNull { it.id == "tsdb/$leagueId" }
        val info = league?.teams?.firstOrNull { it.id == tid }
        val events = SportsDataClient.league(leagueId)?.events.orEmpty()
            .filter { it.homeTeamId == tid || it.awayTeamId == tid }
            .map { it.copy(leagueName = league?.name, sport = league?.sport ?: it.sport) }
        cacheEvents(events)
        val fallbackName = events.firstOrNull { it.homeTeamId == tid }?.homeTeam
            ?: events.firstOrNull { it.awayTeamId == tid }?.awayTeam
            ?: info?.name
            ?: "Team"
        return TeamPageData(
            team = info ?: SportsTeamInfo(id = tid, name = fallbackName, leagueName = league?.name),
            next = events.filter { it.state != "post" }
                .sortedBy { it.startEpochMs ?: Long.MAX_VALUE },
            last = events.filter { it.state == "post" }
                .sortedByDescending { it.startEpochMs ?: 0L },
        )
    }

    /** Teams for an extra FotMob league, from the sports data index. */
    suspend fun extraTeams(league: CuratedLeague): List<SportsTeamInfo> {
        if (!league.id.startsWith("tsdb/")) return emptyList()
        val sourceId = league.id.removePrefix("tsdb/")
        if (extraLeagues.isEmpty()) {
            runCatching { SportsDataClient.index() }.onSuccess { extraLeagues = it }
        }
        return extraLeagues.firstOrNull { it.sourceId == sourceId }?.teams.orEmpty()
    }

    fun clearCache() {
        SportsCache.clear()
        cachedEvents = emptyMap()
        teamIndex = emptyList()
        teamIndexLoadedAt = 0L
    }

    private val TEAM_SPORTS = setOf(
        "Soccer",
        "Basketball",
        "American Football",
        "Baseball",
        "Ice Hockey",
    )
}
