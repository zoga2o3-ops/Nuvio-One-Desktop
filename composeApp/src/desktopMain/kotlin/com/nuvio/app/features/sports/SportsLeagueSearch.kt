package com.nuvio.app.features.sports

import java.util.Locale

/**
 * Filters the Tables picker by league name, short name or one of its FotMob
 * search words. Empty/blank queries keep the full list.
 */
internal fun filterSportsLeagues(
    leagues: List<CuratedLeague>,
    query: String,
): List<CuratedLeague> {
    val needle = query.trim().lowercase(Locale.US)
    if (needle.isEmpty()) return leagues
    return leagues.filter { league ->
        league.name.lowercase(Locale.US).contains(needle) ||
            league.shortName.lowercase(Locale.US).contains(needle) ||
            league.searchTerms.any { it.lowercase(Locale.US).contains(needle) }
    }
}
