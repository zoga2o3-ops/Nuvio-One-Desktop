package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.EpgProgram
import com.nuvio.app.features.iptv.LiveChannel
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Sports TV Guide is the user's own Live TV EPG, filtered to sports
 * channels and sports programmes. No external TV listing service is used.
 */
internal fun sportsTvListings(
    date: LocalDate,
    channels: List<LiveChannel>,
    programs: Map<String, List<EpgProgram>>,
    limit: Int = 400,
): List<SportsTvListing> {
    val zone = ZoneId.systemDefault()
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    return channels.flatMap { channel ->
        val sportsChannel = isSportsChannel(channel)
        programs[channel.key].orEmpty()
            .filter { it.startMs in dayStart until dayEnd }
            .filter { sportsChannel || isSportsProgram(it) }
            .map { program ->
                SportsTvListing(
                    event = program.title,
                    sport = sportForProgram(program.title),
                    channel = channel.name,
                    country = channel.group,
                    logoUrl = channel.logo,
                    date = date.toString(),
                    time = Instant.ofEpochMilli(program.startMs).atZone(zone).format(timeFormat),
                    timestamp = Instant.ofEpochMilli(program.startMs).toString(),
                )
            }
    }
        // Playlists often repeat a channel (backup streams with the same name) and
        // EPG feeds can repeat a programme; show each channel/start/programme once.
        .distinctBy { Triple(it.channel, it.timestamp, it.event) }
        .sortedBy { it.startEpochMs ?: Long.MAX_VALUE }
        .take(limit)
}

private val sportsChannelWords = listOf(
    "sport", "espn", "eurosport", "bein", "sky sport", "dazn", "nba", "nfl", "mlb", "nhl",
    "ufc", "wwe", "aew", "tna", "fight", "football", "soccer", "cricket", "tennis", "golf",
    "motorsport", "formula", "f1",
)

private val sportsProgramWords = listOf(
    "football", "soccer", "nba", "nfl", "mlb", "nhl", "ufc", "boxing", "wrestling",
    "wwe", "aew", "tna", "formula 1", "f1", "motogp", "tennis", "golf", "cricket",
    "rugby", "sport", "match", " vs ", " v ", " @ ",
)

private fun isSportsChannel(channel: LiveChannel): Boolean {
    val haystack = normalise("${channel.name} ${channel.group}")
    return sportsChannelWords.any { haystack.contains(it) }
}

private fun isSportsProgram(program: EpgProgram): Boolean {
    val haystack = normalise("${program.title} ${program.description.orEmpty()}")
    return sportsProgramWords.any { haystack.contains(it) }
}

private fun sportForProgram(title: String): String {
    val t = normalise(title)
    return when {
        "nba" in t || "basketball" in t -> "Basketball"
        "nfl" in t || "american football" in t -> "American Football"
        "mlb" in t || "baseball" in t -> "Baseball"
        "nhl" in t || "ice hockey" in t || "hockey" in t -> "Ice Hockey"
        "ufc" in t || "boxing" in t || "fight" in t -> "Fighting"
        "wwe" in t || "aew" in t || "tna" in t || "wrestling" in t -> "Wrestling"
        "f1" in t || "formula" in t || "motogp" in t || "racing" in t -> "Motorsport"
        "tennis" in t -> "Tennis"
        "golf" in t -> "Golf"
        "cricket" in t -> "Cricket"
        "football" in t || "soccer" in t -> "Soccer"
        else -> "Sport"
    }
}

private fun normalise(value: String): String {
    var out = Normalizer.normalize(value.lowercase(Locale.US), Normalizer.Form.NFD)
    out = out.replace(Regex("\\p{M}+"), "")
    out = out.replace(Regex("[^a-z0-9]+"), " ")
    return out.trim()
}
