package com.nuvio.app.features.sports

/**
 * Curated catalog.
 *
 * The app keeps its own list of major leagues. Older ids are legacy public
 * identifiers; schedules, tables and teams come from ESPN or the shared
 * NuvioOne data files. No API key is used.
 */
internal object SportsCatalog {
    val sports: List<SportCategory> = listOf(
        SportCategory("Soccer", "Football", "\u26BD"),
        SportCategory("Basketball", "Basketball", "\uD83C\uDFC0"),
        SportCategory("American Football", "NFL", "\uD83C\uDFC8"),
        SportCategory("Baseball", "Baseball", "\u26BE"),
        SportCategory("Ice Hockey", "Hockey", "\uD83C\uDFD2"),
        SportCategory("Fighting", "Fighting", "\uD83E\uDD4A"),
        SportCategory("Wrestling", "Wrestling", "\uD83E\uDD3C"),
        SportCategory("Motorsport", "Motorsport", "\uD83C\uDFCE"),
        SportCategory("Tennis", "Tennis", "\uD83C\uDFBE"),
        SportCategory("Golf", "Golf", "\u26F3"),
        SportCategory("Cricket", "Cricket", "\uD83C\uDFCF"),
        SportCategory("Cycling", "Cycling", "\uD83D\uDEB4"),
    )

    val leagues: List<CuratedLeague> = listOf(
        // Football (soccer)
        CuratedLeague(
            id = "4328",
            name = "English Premier League",
            shortName = "Premier League",
            sport = "Soccer",
            country = "England",
            espnPath = "soccer/eng.1",
        ),
        CuratedLeague(
            id = "4335",
            name = "Spanish La Liga",
            shortName = "La Liga",
            sport = "Soccer",
            country = "Spain",
            espnPath = "soccer/esp.1",
        ),
        CuratedLeague(
            id = "4332",
            name = "Italian Serie A",
            shortName = "Serie A",
            sport = "Soccer",
            country = "Italy",
            espnPath = "soccer/ita.1",
        ),
        CuratedLeague(
            id = "4331",
            name = "German Bundesliga",
            shortName = "Bundesliga",
            sport = "Soccer",
            country = "Germany",
            espnPath = "soccer/ger.1",
        ),
        CuratedLeague(
            id = "4334",
            name = "French Ligue 1",
            shortName = "Ligue 1",
            sport = "Soccer",
            country = "France",
            espnPath = "soccer/fra.1",
        ),
        CuratedLeague(
            id = "4346",
            name = "American Major League Soccer",
            shortName = "MLS",
            sport = "Soccer",
            country = "United States",
            espnPath = "soccer/usa.1",
        ),
        CuratedLeague(
            id = "4480",
            name = "UEFA Champions League",
            shortName = "Champions League",
            sport = "Soccer",
            country = "Europe",
            espnPath = "soccer/uefa.champions",
        ),
        CuratedLeague(
            id = "4481",
            name = "UEFA Europa League",
            shortName = "Europa League",
            sport = "Soccer",
            country = "Europe",
            espnPath = "soccer/uefa.europa",
        ),
        CuratedLeague(
            id = "uefa.nations",
            name = "UEFA Nations League",
            shortName = "Nations League",
            sport = "Soccer",
            country = "Europe",
            espnPath = "soccer/uefa.nations",
        ),
        // North American sports
        CuratedLeague(
            id = "4387",
            name = "NBA",
            shortName = "NBA",
            sport = "Basketball",
            country = "United States",
            espnPath = "basketball/nba",
        ),
        CuratedLeague(
            id = "4391",
            name = "NFL",
            shortName = "NFL",
            sport = "American Football",
            country = "United States",
            espnPath = "football/nfl",
        ),
        CuratedLeague(
            id = "4424",
            name = "MLB",
            shortName = "MLB",
            sport = "Baseball",
            country = "United States",
            espnPath = "baseball/mlb",
        ),
        CuratedLeague(
            id = "4380",
            name = "NHL",
            shortName = "NHL",
            sport = "Ice Hockey",
            country = "United States",
            espnPath = "hockey/nhl",
        ),
        // Individual / event sports
        CuratedLeague(
            id = "4443",
            name = "UFC",
            shortName = "UFC",
            sport = "Fighting",
            country = "United States",
            espnPath = "mma/ufc",
        ),
        CuratedLeague(
            id = "4444",
            name = "WWE",
            shortName = "WWE",
            sport = "Wrestling",
            country = "United States",
        ),
        CuratedLeague(
            id = "guide/aew",
            name = "AEW",
            shortName = "AEW",
            sport = "Wrestling",
            country = "United States",
            searchTerms = listOf("AEW", "All Elite Wrestling", "Dynamite", "Collision", "Rampage"),
        ),
        CuratedLeague(
            id = "guide/tna",
            name = "TNA Wrestling",
            shortName = "TNA",
            sport = "Wrestling",
            country = "United States",
            searchTerms = listOf(
                "TNA",
                "TNA iMPACT",
                "TNA Wrestling",
                "Impact Wrestling",
                "Destination X",
                "Bound For Glory",
                "TNA+",
            ),
        ),
        CuratedLeague(
            id = "guide/boxing",
            name = "Boxing",
            shortName = "BOX",
            sport = "Fighting",
            country = "World",
        ),
        CuratedLeague(
            id = "4370",
            name = "Formula 1",
            shortName = "F1",
            sport = "Motorsport",
            country = "World",
            espnPath = "racing/f1",
        ),
        CuratedLeague(
            id = "4464",
            name = "ATP World Tour",
            shortName = "ATP",
            sport = "Tennis",
            country = "World",
            espnPath = "tennis/atp",
        ),
        CuratedLeague(
            id = "4425",
            name = "PGA Tour",
            shortName = "PGA",
            sport = "Golf",
            country = "United States",
            espnPath = "golf/pga",
        ),
        CuratedLeague(
            id = "4460",
            name = "Indian Premier League",
            shortName = "IPL",
            sport = "Cricket",
            country = "India",
            espnPath = "cricket/ipl",
        ),
        CuratedLeague(
            id = "4465",
            name = "UCI World Tour",
            shortName = "UCI",
            sport = "Cycling",
            country = "World",
        ),
    )

    fun leaguesForSport(sport: String): List<CuratedLeague> =
        leagues.filter { it.sport.equals(sport, ignoreCase = true) }

    fun league(id: String): CuratedLeague? =
        leagues.firstOrNull { it.id == id }

    fun sport(apiName: String): SportCategory? =
        sports.firstOrNull { it.apiName.equals(apiName, ignoreCase = true) }
}
