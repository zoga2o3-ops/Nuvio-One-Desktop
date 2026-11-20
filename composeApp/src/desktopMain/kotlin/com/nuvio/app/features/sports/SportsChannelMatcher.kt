package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.EpgProgram
import com.nuvio.app.features.iptv.LiveChannel
import java.text.Normalizer
import java.util.Locale

/**
 * Finds the channels in the user's Live TV playlist that are likely to carry a
 * game, by team name, by league/network name and by the EPG programme that is
 * on now. Used by the Sports side panel so "Watching on" reflects the same
 * playlist Live TV uses.
 */
internal fun matchSportsChannels(
    event: SportsEvent,
    channels: List<LiveChannel>,
    programs: Map<String, List<EpgProgram>> = emptyMap(),
    now: Long = System.currentTimeMillis(),
    limit: Int = 4,
): List<LiveChannel> {
    if (channels.isEmpty()) return emptyList()
    val teamTerms = listOfNotNull(event.homeTeam, event.awayTeam)
        .flatMap { teamTerms(it) }
        .distinct()
    val leagueTerms = leagueTerms(event.leagueName, event.sport)
    val preferredGroups = SportsSettings.preferredGroups().map { it.lowercase() }
    val regionTags = SportsChannelRegions.firstOrNull { it.first == SportsSettings.channelRegion() }?.second.orEmpty()
    val otherTags = SportsChannelRegions.flatMap { it.second }.toSet() - regionTags.toSet()

    return channels.mapNotNull { channel ->
        val channelHaystack = normalise("${channel.name} ${channel.group}")
        // The EPG only describes what is on now: only trust it for a live game.
        val epgHaystack = if (event.isLive) {
            normalise(
                programs[channel.key].orEmpty()
                    .filter { it.isLive(now) }
                    .joinToString(" ") { "${it.title} ${it.description.orEmpty()}" },
            )
        } else {
            ""
        }
        var score = 0
        teamTerms.forEach { term ->
            if (channelHaystack.contains(term)) score += 3
            if (epgHaystack.contains(term)) score += 5
        }
        leagueTerms.forEach { term ->
            if (channelHaystack.contains(term)) score += 1
            if (epgHaystack.contains(term)) score += 2
        }
        if (score == 0) return@mapNotNull null
        // Settings: preferred channel groups go to the top; the chosen region's
        // channels come before other regions'.
        if (preferredGroups.isNotEmpty() && preferredGroups.any { channel.group.lowercase().contains(it) }) score += 20
        if (regionTags.isNotEmpty()) {
            val tokens = normalise("${channel.name} ${channel.group}").split(' ').toSet()
            if (regionTags.any { it in tokens }) score += 6 else if (otherTags.any { it in tokens }) score -= 3
        }
        channel to score
    }
        .sortedWith(compareByDescending<Pair<LiveChannel, Int>> { it.second }.thenBy { it.first.number })
        .map { it.first }
        .take(limit)
}

private val teamStopWords = setOf(
    "the", "fc", "afc", "cf", "ac", "sc", "bk", "if", "ik", "sk", "club",
    "women", "basketball", "football", "soccer", "team",
    "united", "city", "real", "sporting", "athletic", "athletico", "atletico",
    "state", "states", "national", "county", "town", "rovers", "wanderers",
    "albion", "forest", "hotspur", "park", "new",
)

/** What "Watch" should search for: a matching Live TV channel, else the team/league. */
internal fun bestSportsWatchQuery(
    event: SportsEvent,
    channels: List<LiveChannel>,
    programs: Map<String, List<EpgProgram>> = emptyMap(),
): String {
    val matched = matchSportsChannels(event, channels, programs, System.currentTimeMillis(), 1)
    return matched.firstOrNull()?.name
        ?: event.homeTeam
        ?: event.awayTeam
        ?: event.leagueName
        ?: event.title
}

private fun teamTerms(name: String): List<String> {
    val norm = normalise(name)
    if (norm.isBlank()) return emptyList()
    val words = norm.split(' ').filter { it.length >= 3 && it !in teamStopWords }
    return buildList {
        if (norm.length >= 4) add(norm)
        words.forEach { add(it) }
        when (norm) {
            "united states" -> add("usa")
            "south korea" -> add("korea")
            "north korea" -> add("korea dpr")
            "ivory coast" -> add("cote d ivoire")
        }
    }
}

private val leagueNetworkTerms: Map<String, List<String>> = mapOf(
    "english premier league" to listOf("premier league", "epl", "sky sports", "tnt sport"),
    "spanish la liga" to listOf("la liga", "laliga"),
    "italian serie a" to listOf("serie a"),
    "german bundesliga" to listOf("bundesliga"),
    "french ligue 1" to listOf("ligue 1"),
    "uefa champions league" to listOf("champions league"),
    "uefa europa league" to listOf("europa league"),
    "uefa nations league" to listOf("nations league"),
    "major league soccer" to listOf("mls", "major league soccer"),
    "national basketball association" to listOf("nba"),
    "nba" to listOf("nba"),
    "national football league" to listOf("nfl", "sunday ticket"),
    "nfl" to listOf("nfl", "sunday ticket"),
    "major league baseball" to listOf("mlb"),
    "mlb" to listOf("mlb"),
    "national hockey league" to listOf("nhl"),
    "nhl" to listOf("nhl"),
    "wwe" to listOf("wwe"),
    "aew" to listOf("aew"),
    "tna wrestling" to listOf("tna", "impact"),
    "boxing" to listOf("boxing", "dazn"),
    "ufc" to listOf("ufc"),
)

private fun leagueTerms(leagueName: String?, sport: String?): List<String> {
    val key = leagueName?.let(::normalise).orEmpty()
    val mapped = leagueNetworkTerms[key].orEmpty()
    if (mapped.isNotEmpty()) return mapped
    val words = key.split(' ').filter { it.length >= 4 }
    val sportKey = sport?.let(::normalise).orEmpty()
    val generic = when (sportKey) {
        "basketball" -> listOf("nba", "basketball")
        "american football" -> listOf("nfl", "football")
        "baseball" -> listOf("mlb", "baseball")
        "ice hockey" -> listOf("nhl", "hockey")
        "fighting" -> listOf("ufc", "fight")
        "wrestling" -> listOf("wrestling", "wwe", "aew", "tna")
        "cricket" -> listOf("cricket")
        "tennis" -> listOf("tennis")
        "golf" -> listOf("golf")
        "motorsport" -> listOf("f1", "formula")
        else -> emptyList()
    }
    return (mapped + words + generic).distinct()
}

private fun normalise(value: String): String {
    var out = Normalizer.normalize(value.lowercase(Locale.US), Normalizer.Form.NFD)
    out = out.replace(Regex("\\p{M}+"), "")
    out = out.replace(Regex("[^a-z0-9]+"), " ")
    return out.trim()
}
