package com.nuvio.app.features.iptv

import com.nuvio.app.features.iptv.sync.LiveTvSyncLocal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files

/**
 * Favourites in the TV app's form, so a channel starred on a TV is starred here and the other
 * way round (they travel as the shared setup's "favorites").
 *
 * The TV names a channel "<playlist id>|<tvg-id>|<name>" (plus "#2"… when a playlist has the same
 * channel twice); the desktop's own keys are "<playlist id>:<hash>". Both know the playlist, the
 * tvg-id and the name, which is what [matches] compares.
 */
internal object IptvTvFavorites {
    private val json = Json { ignoreUnknownKeys = true }
    private fun path() = IptvSourceStore.dataDir().resolve("tv-favorites.json")

    /** In the TV's order (its Favourites group keeps the order they were added). */
    fun load(): List<String> = runCatching {
        val f = path()
        // First run with this feature: start from the favourites already in the synced setup.
        if (!Files.isRegularFile(f)) LiveTvSyncLocal.sharedFavorites()
        else json.parseToJsonElement(Files.readString(f)).jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }.getOrDefault(emptyList())

    fun save(keys: List<String>) {
        runCatching {
            val f = path()
            Files.createDirectories(f.parent)
            Files.writeString(f, JsonArray(keys.distinct().map(::JsonPrimitive)).toString())
            IptvSourceStore.setOwnerOnly(f)
        }
        LiveTvSyncLocal.noteChanged()
    }

    /** This channel as the TV would name it. */
    fun keyFor(channel: LiveChannel): String = "${channel.sourceId}|${channel.tvgId.orEmpty()}|${channel.name}"

    private data class Parts(val playlist: String, val tvgId: String, val name: String)

    private fun parts(key: String): Parts? {
        val p = key.split('|', limit = 3)
        if (p.size < 3) return null
        return Parts(p[0], p[1], p[2])
    }

    /** The desktop keys of [channels] that [tvKeys] name. */
    fun matching(channels: List<LiveChannel>, tvKeys: List<String>): Set<String> =
        orderedMatches(channels, tvKeys).toSet()

    /**
     * The desktop keys of [channels] that [tvKeys] name, in the TVs' order (the order of [tvKeys]).
     * A channel named by several entries appears at its first one.
     */
    fun orderedMatches(channels: List<LiveChannel>, tvKeys: List<String>): List<String> {
        if (tvKeys.isEmpty() || channels.isEmpty()) return emptyList()
        val byExact = HashMap<String, MutableList<String>>()
        val byName = HashMap<String, MutableList<String>>()
        for (c in channels) {
            byExact.getOrPut("${c.sourceId}|${c.tvgId.orEmpty()}|${c.name}") { mutableListOf() } += c.key
            byName.getOrPut("${c.sourceId}|${c.name}") { mutableListOf() } += c.key
        }
        val result = LinkedHashSet<String>()
        for (key in tvKeys) {
            // "Name#2": the TV's second copy of the same channel; also try without it.
            for (k in listOf(key, key.replace(Regex("#\\d+$"), ""))) {
                val p = parts(k) ?: continue
                byExact["${p.playlist}|${p.tvgId}|${p.name}"]?.let(result::addAll)
                byName["${p.playlist}|${p.name}"]?.let(result::addAll)
            }
        }
        return result.toList()
    }

    /** [tvKeys] without the entries that name [channel]. */
    fun without(tvKeys: List<String>, channel: LiveChannel): List<String> =
        tvKeys.filterNot { key -> matching(listOf(channel), listOf(key)).isNotEmpty() }
}
