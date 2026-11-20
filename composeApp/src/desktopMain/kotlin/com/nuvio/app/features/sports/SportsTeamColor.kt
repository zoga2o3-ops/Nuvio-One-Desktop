package com.nuvio.app.features.sports

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.text.Normalizer
import java.util.Locale

/**
 * Team colours for cards and tiles.
 *
 * Order: the colour on the feed (ESPN sends one for every team), then the generated
 * ESPN/league palette, then a stable colour derived from the name so every team has one.
 */
internal fun sportsTeamColor(
    name: String,
    primary: String? = null,
    alternate: String? = null,
): Color {
    val feedMain = sportsHexColor(primary)
    val feedAlt = sportsHexColor(alternate)
    val picked = pickReadable(feedMain, feedAlt)
    if (picked != null) return picked

    val key = sportsTeamKey(name)
    val paletteMain = paletteColor(key, SportsTeamPalette.colors)?.let { Color(it) }
    val paletteAlt = paletteColor(key, SportsTeamPalette.alternates)?.let { Color(it) }
    val fromPalette = pickReadable(paletteMain, paletteAlt)
    if (fromPalette != null) return fromPalette

    val manual = manualTeamColors[key] ?: manualTeamColors[key.removeSuffix(" women")]
    if (manual != null) return manual

    val hash = name.lowercase(Locale.US).hashCode()
    val hue = ((hash % 360) + 360) % 360
    return Color.hsv(hue.toFloat(), 0.55f, 0.62f)
}

/** Darkens a bright band enough for white text, the way the TV app does. */
internal fun sportsBandColor(color: Color): Color {
    val lum = color.luminance()
    val mix = when {
        lum > 0.5f -> 0.5f
        lum > 0.25f -> 0.25f
        else -> 0.08f
    }
    return lerp(color, Color(0xFF0B0C10), mix)
}

private fun <T> paletteColor(key: String, map: Map<String, T>): T? {
    map[key]?.let { return it }
    val base = key
        .removeSuffix(" basketball women")
        .removeSuffix(" football women")
        .removeSuffix(" women")
        .removeSuffix(" basketball")
        .removeSuffix(" football")
        .removeSuffix(" fc")
        .removeSuffix(" sc")
        .trim()
    return if (base != key) map[base] else null
}

/** The short code shown on a tile when there is no badge. */
internal fun sportsTeamAbbr(name: String, fallback: String? = null): String {
    val cleaned = fallback?.trim()?.takeIf { it.isNotEmpty() }
    if (cleaned != null) return cleaned.uppercase(Locale.US).take(4)
    val key = sportsTeamKey(name)
    SportsTeamPalette.abbreviations[key]?.let { return it.uppercase(Locale.US).take(4) }
    val skip = setOf("fc", "afc", "cf", "ac", "sc", "bk", "if", "ik", "sk", "club", "the")
    val words = name.split(' ', '-', '_')
        .map { it.trim() }
        .filter { it.isNotBlank() && it.lowercase(Locale.US) !in skip }
    if (words.isEmpty()) return "?"
    return words[0].take(3).uppercase(Locale.US)
}

internal fun sportsTeamKey(name: String): String {
    var key = Normalizer.normalize(name.lowercase(Locale.US).trim(), Normalizer.Form.NFD)
    key = key.replace(Regex("\\p{M}+"), "")
    key = key.replace("&", " and ")
    key = key.replace(Regex("[.'\u2019]"), "")
    key = key.replace(Regex("[^a-z0-9]+"), " ")
    key = key.trim()
    return teamAliases[key] ?: key
}

internal fun sportsHexColor(value: String?): Color? {
    val raw = value?.trim()?.removePrefix("#") ?: return null
    if (raw.length != 6 && raw.length != 8) return null
    val parsed = raw.toLongOrNull(16) ?: return null
    return if (raw.length == 6) Color(0xFF000000L or parsed) else Color(parsed)
}

private fun pickReadable(main: Color?, alt: Color?): Color? {
    if (main == null) return alt
    return if (main.luminance() > 0.62f && alt != null && alt.luminance() < 0.62f) alt else main
}

internal fun sportsLeagueColor(name: String?): Color {
    val key = name?.let { sportsTeamKey(it) }.orEmpty()
    leagueColors[key]?.let { return it }
    val hash = (name ?: "league").lowercase(Locale.US).hashCode()
    val hue = ((hash % 360) + 360) % 360
    return Color.hsv(hue.toFloat(), 0.35f, 0.45f)
}

private val leagueColors: Map<String, Color> = mapOf(
    "english premier league" to Color(0xFF3D195B),
    "premier league" to Color(0xFF3D195B),
    "spanish la liga" to Color(0xFFEE8707),
    "la liga" to Color(0xFFEE8707),
    "italian serie a" to Color(0xFF0068A8),
    "serie a" to Color(0xFF0068A8),
    "german bundesliga" to Color(0xFFD20515),
    "bundesliga" to Color(0xFFD20515),
    "french ligue 1" to Color(0xFF091C3E),
    "ligue 1" to Color(0xFF091C3E),
    "american major league soccer" to Color(0xFF1B2B5B),
    "major league soccer" to Color(0xFF1B2B5B),
    "uefa champions league" to Color(0xFF0E1E5B),
    "uefa europa league" to Color(0xFFF5821F),
    "national basketball association" to Color(0xFF1D428A),
    "nba" to Color(0xFF1D428A),
    "national football league" to Color(0xFF013369),
    "nfl" to Color(0xFF013369),
    "major league baseball" to Color(0xFF041E42),
    "mlb" to Color(0xFF041E42),
    "national hockey league" to Color(0xFF111111),
    "nhl" to Color(0xFF111111),
    "wwe" to Color(0xFF2B2B2B),
    "aew" to Color(0xFF96732B),
    "tna wrestling" to Color(0xFF1E5AC8),
    "boxing" to Color(0xFF7A1F1F),
)

/** Teams the big-league feeds don't carry (Swedish women, Australian NBL). */
private val manualTeamColors: Map<String, Color> = mapOf(
    "eskilstuna united" to Color(0xFF005CA9),
    "djurgarden" to Color(0xFF003B7A),
    "bk hacken" to Color(0xFFFFD200),
    "pitea" to Color(0xFFC8102E),
    "hammarby" to Color(0xFF007A3D),
    "kristianstad" to Color(0xFFE30613),
    "rosengard" to Color(0xFFE30613),
    "linkoping" to Color(0xFF0066B3),
    "vittsjo" to Color(0xFF111111),
    "orebro" to Color(0xFFC8102E),
    "alingsas" to Color(0xFF005CA9),
    "sydney kings" to Color(0xFF5B2C83),
    "new zealand breakers" to Color(0xFF002B5C),
    "melbourne united" to Color(0xFF00274C),
    "perth wildcats" to Color(0xFFC8102E),
    "adelaide 36ers" to Color(0xFF002B5C),
    "brisbane bullets" to Color(0xFF003DA5),
    "cairns taipans" to Color(0xFFF26522),
    "illawarra hawks" to Color(0xFFC8102E),
    "south east melbourne phoenix" to Color(0xFF00A9E0),
    "tasmania jackjumpers" to Color(0xFF006747),
)

/**
 * Names that differ between feeds (FotMob vs ESPN). Keys and values are
 * already normalised by [sportsTeamKey].
 */
private val teamAliases: Map<String, String> = mapOf(
    "la clippers" to "la clippers",
    "los angeles clippers" to "la clippers",
    "inter" to "internazionale",
    "inter milan" to "internazionale",
    "internazionale" to "internazionale",
    "inter miami" to "inter miami cf",
    "paris sg" to "paris saint germain",
    "paris st germain" to "paris saint germain",
    "bayern munchen" to "bayern munich",
    "wolves" to "wolverhampton wanderers",
    "wolverhampton" to "wolverhampton wanderers",
    "wolverhampton wanderers" to "wolverhampton wanderers",
    "tottenham" to "tottenham hotspur",
    "brighton" to "brighton and hove albion",
    "newcastle" to "newcastle united",
    "nottm forest" to "nottingham forest",
    "west ham" to "west ham united",
    "man utd" to "manchester united",
    "man city" to "manchester city",
    "psg" to "paris saint germain",
    "juve" to "juventus",
    "barca" to "barcelona",
    "atleti" to "atletico madrid",
    "dortmund" to "borussia dortmund",
    "gladbach" to "borussia monchengladbach",
    "moenchengladbach" to "borussia monchengladbach",
    "monchengladbach" to "borussia monchengladbach",
    "spurs" to "tottenham hotspur",
    "the hawks" to "atlanta hawks",
    "sixers" to "philadelphia 76ers",
    "philadelphia sixers" to "philadelphia 76ers",
    "portland trail blazers" to "portland trail blazers",
    "golden state" to "golden state warriors",
    "las vegas golden knights" to "vegas golden knights",
    "washington football team" to "washington commanders",
    "oakland athletics" to "athletics",
    "sacramento kings" to "sacramento kings",
    "czech republic" to "czechia",
    "korea republic" to "south korea",
    "usa" to "united states",
    "turkey" to "turkiye",
    "uae" to "united arab emirates",
    "bosnia herzegovina" to "bosnia and herzegovina",
)
