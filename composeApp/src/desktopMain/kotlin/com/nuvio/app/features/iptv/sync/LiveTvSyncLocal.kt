package com.nuvio.app.features.iptv.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.features.iptv.IptvFavoritesStore
import com.nuvio.app.features.iptv.IptvGuideSource
import com.nuvio.app.features.iptv.IptvGuideSourceStore
import com.nuvio.app.features.iptv.IptvSettings
import com.nuvio.app.features.iptv.IptvSettingsStore
import com.nuvio.app.features.iptv.IptvSource
import com.nuvio.app.features.iptv.IptvSourceStore
import com.nuvio.app.features.iptv.cleanXtreamServer
import com.nuvio.app.features.iptv.sync.LiveTvMerge.str
import com.nuvio.app.features.iptv.xtreamCredentialsFromLink
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The desktop's Live TV setup in the TV app's sync format (LiveTvPreferences.exportForSync), so a
 * desktop and a TV share one saved copy.
 *
 * The TV's copy is a dump of all its Live TV settings, and a TV that applies a copy removes every
 * setting the copy doesn't have. So the desktop never sends only what it understands: it starts
 * from the last copy it saw (the "shadow") and replaces just the settings it maps:
 *  - playlists, in order, with their addresses and logins ("playlists");
 *  - guides ("epg_sources"): a playlist's own guide travels as "src-epg:<playlist id>", other
 *    guides are kept in the desktop's guide list;
 *  - the switches and numbers both apps have, hidden groups and group order (TV keys);
 *  - everything else desktop-only under "desktop_…" keys (whole settings file, favourites),
 *    which TVs carry along untouched.
 * Channel favourites and channel order use each app's own channel ids, so they sync between
 * desktops only.
 */
internal object LiveTvSyncLocal {
    private val json = Json { ignoreUnknownKeys = true }

    /** Bumped on every saved change to Live TV data (sync watches it). */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes

    /** Bumped after a synced copy was written here: Live TV reloads what it shows. */
    var appliedCount by mutableIntStateOf(0)
        private set

    @Volatile private var applying = false

    fun noteChanged() {
        if (!applying) _changes.value = _changes.value + 1
    }

    fun noteRemoved(ids: Set<String>) {
        if (applying || ids.isEmpty()) return
        synchronized(this) { writeRemoved(removedSourceIds() + ids) }
    }

    /** Playlists and guides removed here since the last sync (they stay removed everywhere). */
    fun removedSourceIds(): Set<String> = runCatching {
        val f = removedFile()
        if (!Files.isRegularFile(f)) emptySet()
        else stringsOf(json.parseToJsonElement(Files.readString(f))).toSet()
    }.getOrDefault(emptySet())

    fun forgetRemovedSources(ids: Set<String>) = synchronized(this) {
        if (ids.isEmpty()) return@synchronized
        writeRemoved(removedSourceIds() - ids)
    }

    private fun removedFile() = IptvSourceStore.dataDir().resolve("livetv-sync-removed.json")

    private fun writeRemoved(ids: Set<String>) {
        runCatching {
            Files.createDirectories(removedFile().parent)
            Files.writeString(removedFile(), JsonArray(ids.sorted().map(::JsonPrimitive)).toString())
        }
    }

    fun sourceGuideId(sourceId: String) = SOURCE_GUIDE_PREFIX + sourceId

    // ------------------------------------------------------------------ export

    fun exportForSync(): JsonObject {
        val shadow = readShadow()
        val out = LinkedHashMap<String, JsonElement>()
        // Everything the last shared copy had (TV-only settings), minus what we map below.
        shadow?.forEach { (k, v) -> if (k !in MAPPED_KEYS && !k.startsWith(DESKTOP_PREFIX)) out[k] = v }

        val sources = IptvSourceStore.load()
        val guides = IptvGuideSourceStore.load()
        val settings = IptvSettingsStore.load()
        val removed = removedSourceIds()

        // Playlists: each desktop source over the shared entry it came from (keeps TV-only fields),
        // then playlists switched off on a TV (the desktop doesn't load those, but keeps them).
        val shadowPlaylists = LiveTvMerge.readSources((shadow?.get(KEY_PLAYLISTS) as? JsonObject)?.str("v"))
        val playlists = ArrayList<JsonElement>()
        sources.forEach { s -> playlists += playlistEntry(s, shadowPlaylists[s.id]) }
        val desktopIds = sources.mapTo(HashSet()) { it.id }
        shadowPlaylists.forEach { (id, o) ->
            if (id !in desktopIds && id !in removed && !loadableHere(o)) playlists += o
        }
        out[KEY_PLAYLISTS] = keepIfSame(shadow, KEY_PLAYLISTS, strEntry(JsonArray(playlists).toString()))

        // Guides: each playlist's own guide, then the desktop's guide list.
        val shadowGuides = LiveTvMerge.readSources((shadow?.get(KEY_EPGS) as? JsonObject)?.str("v"))
        val epgs = ArrayList<JsonElement>()
        sources.filter { it.epgUrl.isNotBlank() }.forEach { s ->
            val id = sourceGuideId(s.id)
            epgs += guideEntry(id, "${s.name} guide", s.epgUrl, true, shadowGuides[id])
        }
        guides.forEach { g -> epgs += guideEntry(g.id, g.name, g.url, g.enabled, shadowGuides[g.id]) }
        out[KEY_EPGS] = keepIfSame(shadow, KEY_EPGS, strEntry(JsonArray(epgs).toString()))

        // Settings both apps have, under the TV's names.
        BOOL_KEYS.forEach { (key, get) -> out[key] = keepIfSame(shadow, key, typed("b", JsonPrimitive(get(settings)))) }
        INT_KEYS.forEach { (key, get) -> out[key] = keepIfSame(shadow, key, typed("i", JsonPrimitive(get(settings)))) }
        // An empty list here means "nothing arranged on this computer", not "clear it everywhere":
        // a first combine takes this device's order, and an empty one wiped the TVs' "Your order".
        if (settings.hiddenGroups.isNotEmpty()) {
            out[KEY_HIDDEN_GROUPS] = keepIfSame(
                shadow, KEY_HIDDEN_GROUPS,
                typed("s", JsonArray(tvGroupIds(settings.hiddenGroups, shadow?.get(KEY_HIDDEN_GROUPS), sources).map(::JsonPrimitive))),
            )
        } else {
            shadow?.get(KEY_HIDDEN_GROUPS)?.let { out[KEY_HIDDEN_GROUPS] = it }
        }
        if (settings.customGroupOrder.isNotEmpty()) {
            out[KEY_GROUP_ORDER] = keepIfSame(
                shadow, KEY_GROUP_ORDER,
                strEntry(JsonArray(tvGroupIds(settings.customGroupOrder, shadow?.get(KEY_GROUP_ORDER), sources).map(::JsonPrimitive)).toString()),
            )
        } else {
            shadow?.get(KEY_GROUP_ORDER)?.let { out[KEY_GROUP_ORDER] = it }
        }

        // Favourites in the TVs' form (the first profile's list). An empty list here passes the
        // shared one through, like the group order: it never clears the TVs' favourites.
        val tvFavorites = com.nuvio.app.features.iptv.IptvTvFavorites.load()
        if (tvFavorites.isNotEmpty()) {
            out[KEY_FAVORITES] = keepIfSame(shadow, KEY_FAVORITES, strEntry(JsonArray(tvFavorites.map(::JsonPrimitive)).toString()))
        } else {
            shadow?.get(KEY_FAVORITES)?.let { out[KEY_FAVORITES] = it }
        }

        // Liked Songs and Music playlists (shared with the TV app's Music library).
        out.putAll(LiveTvMusicSync.export(shadow))

        // Desktop-only: the whole settings file (minus what's per device) and favourites.
        val desktopSettings = JsonObject(IptvSettingsStore.toJson(settings) - PER_DEVICE_SETTINGS)
        out[KEY_DESKTOP_SETTINGS] = keepIfSame(shadow, KEY_DESKTOP_SETTINGS, strEntry(desktopSettings.toString()))
        out[KEY_DESKTOP_FAVORITES] = keepIfSame(
            shadow, KEY_DESKTOP_FAVORITES,
            strEntry(JsonArray(IptvFavoritesStore.load().sorted().map(::JsonPrimitive)).toString()),
        )
        return JsonObject(out)
    }

    /** Whether a shared copy has any playlist or guide in it (an empty copy never empties a device). */
    fun syncHasSources(data: JsonObject?): Boolean {
        if (data == null) return false
        fun count(key: String) = LiveTvMerge.readSources((data[key] as? JsonObject)?.str("v")).size
        return count(KEY_PLAYLISTS) > 0 || count(KEY_EPGS) > 0 || LiveTvMusicSync.hasMusic(data)
    }

    // ------------------------------------------------------------------ import

    /** Writes a shared copy here. Returns true when something on this desktop changed. */
    fun importFromSync(data: JsonObject): Boolean {
        var changed = false
        applying = true
        try {
            val playlists = LiveTvMerge.readSources((data[KEY_PLAYLISTS] as? JsonObject)?.str("v"))
            val epgs = LiveTvMerge.readSources((data[KEY_EPGS] as? JsonObject)?.str("v"))

            // Playlists (switched-off ones stay on the TVs only).
            val current = IptvSourceStore.load().associateBy { it.id }
            val sources = playlists.values.filter(::loadableHere).mapNotNull { o ->
                val id = o.str("id") ?: return@mapNotNull null
                val rawXtream = o.str("xtreamServer").orEmpty()
                val xtream = cleanXtreamServer(rawXtream)
                val xtreamCredentials = xtreamCredentialsFromLink(rawXtream)
                val stalkerPortal = o.str("stalkerPortal").orEmpty()
                val stalkerMac = o.str("stalkerMac").orEmpty()
                val guide = epgs[sourceGuideId(id)]?.takeIf { it.bool("enabled", true) }?.str("url").orEmpty()
                IptvSource(
                    id = id,
                    name = o.str("name").orEmpty().ifBlank { current[id]?.name ?: "Playlist" },
                    url = when {
                        xtream.isNotBlank() -> xtream
                        stalkerPortal.isNotBlank() -> stalkerPortal
                        else -> o.str("url").orEmpty()
                    },
                    epgUrl = if (stalkerPortal.isNotBlank()) "" else guide,
                    kind = when {
                        xtream.isNotBlank() -> "xtream"
                        stalkerPortal.isNotBlank() -> "stalker"
                        else -> "m3u"
                    },
                    username = when {
                        xtream.isNotBlank() -> o.str("xtreamUsername").orEmpty()
                            .ifBlank { xtreamCredentials?.first.orEmpty() }
                        stalkerPortal.isNotBlank() -> stalkerMac
                        else -> ""
                    },
                    password = if (xtream.isNotBlank()) {
                        o.str("xtreamPassword").orEmpty().ifBlank { xtreamCredentials?.second.orEmpty() }
                    } else {
                        ""
                    },
                )
            }.filter { it.url.isNotBlank() }
            if (sources != IptvSourceStore.load()) {
                IptvSourceStore.save(sources)
                changed = true
            }

            // Guides that aren't a playlist's own.
            val guides = epgs.values.filter { !(it.str("id").orEmpty().startsWith(SOURCE_GUIDE_PREFIX)) }.mapNotNull { o ->
                val id = o.str("id") ?: return@mapNotNull null
                val url = o.str("url").orEmpty().ifBlank { return@mapNotNull null }
                IptvGuideSource(id, o.str("name").orEmpty().ifBlank { "Guide" }, url, o.bool("enabled", true))
            }
            if (guides != IptvGuideSourceStore.load()) {
                IptvGuideSourceStore.save(guides)
                changed = true
            }

            // Settings: the desktop's own copy, then whatever the TVs set under their names.
            val local = IptvSettingsStore.load()
            var settings = (data[KEY_DESKTOP_SETTINGS] as? JsonObject)?.str("v")?.let(LiveTvMerge::parse)
                ?.let { IptvSettingsStore.parse(it) }
                ?.copy(lastChannelKey = local.lastChannelKey, recentChannelKeys = local.recentChannelKeys)
                ?: local
            BOOL_KEYS.forEach { (key, _) ->
                (data[key] as? JsonObject)?.get("v")?.jsonPrimitive?.booleanOrNull?.let { settings = setBool(settings, key, it) }
            }
            INT_KEYS.forEach { (key, _) ->
                (data[key] as? JsonObject)?.get("v")?.jsonPrimitive?.intOrNull?.let { settings = setInt(settings, key, it) }
            }
            (data[KEY_HIDDEN_GROUPS] as? JsonObject)?.get("v")?.let { v ->
                groupTitles(stringsOf(v)).takeIf { it.isNotEmpty() }?.let { settings = settings.copy(hiddenGroups = it) }
            }
            (data[KEY_GROUP_ORDER] as? JsonObject)?.str("v")?.let { raw ->
                runCatching { json.parseToJsonElement(raw) }.getOrNull()
                    ?.let { groupTitles(stringsOf(it)) }?.takeIf { it.isNotEmpty() }
                    ?.let { settings = settings.copy(customGroupOrder = it) }
            }
            if (settings != local) {
                IptvSettingsStore.save(settings)
                changed = true
            }

            (data[KEY_FAVORITES] as? JsonObject)?.str("v")?.let { raw ->
                val list = runCatching { stringsOf(json.parseToJsonElement(raw)) }.getOrNull()
                if (list != null && list.isNotEmpty() && list != com.nuvio.app.features.iptv.IptvTvFavorites.load()) {
                    com.nuvio.app.features.iptv.IptvTvFavorites.save(list)
                    changed = true
                }
            }
            (data[KEY_DESKTOP_FAVORITES] as? JsonObject)?.str("v")?.let { raw ->
                val favorites = runCatching { stringsOf(json.parseToJsonElement(raw)).toSet() }.getOrNull()
                if (favorites != null && favorites != IptvFavoritesStore.load()) {
                    IptvFavoritesStore.save(favorites)
                    changed = true
                }
            }

            if (LiveTvMusicSync.import(data)) changed = true

            writeShadow(data)
            if (changed) appliedCount++
        } finally {
            applying = false
        }
        // Outside "applying": the extra copies are removed as a change made here, so the next save
        // takes them off the other devices too.
        if (collapseDuplicates()) {
            appliedCount++
            changed = true
        }
        return changed
    }

    /**
     * The same playlist (same address and login) kept twice under different ids, which happens
     * when two devices each added it and their setups were combined. Keeps one: the copy the
     * shared setup's other settings refer to (the TVs' favourites, groups, channel names), and
     * points this computer's favourites, channel order and recent channels at it. Returns true
     * when anything was removed.
     */
    fun collapseDuplicates(): Boolean = synchronized(this) {
        val sources = IptvSourceStore.load()
        val groups = sources.groupBy(com.nuvio.app.features.iptv.IptvLoader::sameSourceKey).values.filter { it.size > 1 }
        if (groups.isEmpty()) return@synchronized false
        val shadowText = readShadow()?.filterKeys { it !in setOf(KEY_PLAYLISTS, KEY_EPGS) && !it.startsWith(DESKTOP_PREFIX) }?.toString().orEmpty()
        fun references(id: String) = Regex(Regex.escape(id)).findAll(shadowText).count()
        val remap = HashMap<String, String>()
        val replacement = HashMap<String, IptvSource>()
        groups.forEach { same ->
            val keep = same.withIndex().maxWith(compareBy<IndexedValue<IptvSource>>({ references(it.value.id) }, { -it.index })).value
            val guide = keep.epgUrl.ifBlank { same.firstOrNull { it.epgUrl.isNotBlank() }?.epgUrl.orEmpty() }
            replacement[keep.id] = keep.copy(epgUrl = guide)
            same.filter { it.id != keep.id }.forEach { remap[it.id] = keep.id }
        }
        // Keep each playlist where its first copy was.
        val seen = HashSet<String>()
        val next = sources.mapNotNull { s ->
            val id = remap[s.id] ?: s.id
            if (!seen.add(id)) null else replacement[id] ?: sources.first { it.id == id }
        }
        fun moved(key: String): String {
            val colon = key.indexOf(':')
            if (colon <= 0) return key
            val target = remap[key.substring(0, colon)] ?: return key
            return target + key.substring(colon)
        }
        IptvSourceStore.save(next)
        IptvFavoritesStore.save(IptvFavoritesStore.load().map(::moved).toSet())
        val settings = IptvSettingsStore.load()
        IptvSettingsStore.save(
            settings.copy(
                customChannelOrder = settings.customChannelOrder.mapValues { (_, keys) -> keys.map(::moved).distinct() },
                recentChannelKeys = settings.recentChannelKeys.map(::moved).distinct(),
                lastChannelKey = moved(settings.lastChannelKey),
            ),
        )
        // Guides added twice (same address).
        val guides = IptvGuideSourceStore.load()
        val distinctGuides = guides.distinctBy { it.url.trim().trimEnd('/').lowercase() }
        if (distinctGuides.size != guides.size) IptvGuideSourceStore.save(distinctGuides)
        true
    }

    /** The favourites in the last shared copy (the TVs' first profile). */
    fun sharedFavorites(): List<String> =
        (readShadow()?.get(KEY_FAVORITES) as? JsonObject)?.str("v")
            ?.let { raw -> runCatching { stringsOf(json.parseToJsonElement(raw)) }.getOrNull() }
            .orEmpty()

    /** Remembers the shared copy this desktop now matches (TV-only settings are kept from it). */
    fun rememberShared(data: JsonObject) = writeShadow(data)

    // ------------------------------------------------------------------ helpers

    private fun playlistEntry(s: IptvSource, shared: JsonObject?): JsonObject {
        val out = LinkedHashMap<String, JsonElement>(shared ?: DEFAULT_PLAYLIST)
        val xtream = s.kind.equals("xtream", true)
        val stalker = s.kind.equals("stalker", true)
        out["id"] = JsonPrimitive(s.id)
        out["name"] = JsonPrimitive(s.name)
        if (xtream) {
            out["sourceType"] = JsonPrimitive("xtream")
            out["xtreamServer"] = JsonPrimitive(s.url)
            out["xtreamUsername"] = JsonPrimitive(s.username)
            out["xtreamPassword"] = JsonPrimitive(s.password)
            out["stalkerPortal"] = JsonPrimitive("")
            out["stalkerMac"] = JsonPrimitive("")
            if (shared == null) out["url"] = JsonPrimitive("")
        } else if (stalker) {
            out["sourceType"] = JsonPrimitive("stalker")
            out["stalkerPortal"] = JsonPrimitive(s.url)
            out["stalkerMac"] = JsonPrimitive(s.username)
            out["url"] = JsonPrimitive(s.url)
            out["xtreamServer"] = JsonPrimitive("")
            out["xtreamUsername"] = JsonPrimitive("")
            out["xtreamPassword"] = JsonPrimitive("")
        } else {
            out["sourceType"] = JsonPrimitive("m3u")
            out["url"] = JsonPrimitive(s.url)
            out["xtreamServer"] = JsonPrimitive("")
            out["xtreamUsername"] = JsonPrimitive("")
            out["xtreamPassword"] = JsonPrimitive("")
            out["stalkerPortal"] = JsonPrimitive("")
            out["stalkerMac"] = JsonPrimitive("")
        }
        out["enabled"] = JsonPrimitive(true)
        return JsonObject(out)
    }

    private fun guideEntry(id: String, name: String, url: String, enabled: Boolean, shared: JsonObject?): JsonObject {
        val out = LinkedHashMap<String, JsonElement>(shared ?: DEFAULT_GUIDE)
        out["id"] = JsonPrimitive(id)
        out["name"] = JsonPrimitive(shared?.str("name")?.takeIf { it.isNotBlank() } ?: name)
        out["url"] = JsonPrimitive(url)
        out["enabled"] = JsonPrimitive(enabled)
        return JsonObject(out)
    }

    /**
     * Desktop group titles as the TV's group ids: the TV writes "playlist::Title" when it has
     * more than one playlist. Ids the shared copy already had for a title are kept as they were.
     */
    private fun tvGroupIds(titles: List<String>, shared: JsonElement?, sources: List<IptvSource>): List<String> {
        val sharedIds = shared?.let { e ->
            val v = (e as? JsonObject)?.get("v") ?: return@let emptyList()
            when (v) {
                is JsonArray -> stringsOf(v)
                is JsonPrimitive -> v.contentOrNull?.let { runCatching { stringsOf(json.parseToJsonElement(it)) }.getOrNull() }.orEmpty()
                else -> emptyList()
            }
        }.orEmpty()
        val byTitle = sharedIds.groupBy(::titleOf)
        return titles.flatMap { title ->
            byTitle[title] ?: if (sources.size > 1) sources.map { "${it.id}::$title" } else listOf(title)
        }.distinct()
    }

    private fun groupTitles(ids: List<String>) = ids.map(::titleOf).filter { it.isNotBlank() }.distinct()

    private fun titleOf(id: String) = id.substringAfter("::", id)

    private fun stringsOf(e: JsonElement): List<String> =
        runCatching { e.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrDefault(emptyList())

    private fun typed(t: String, v: JsonElement) = JsonObject(linkedMapOf("t" to JsonPrimitive(t), "v" to v))
    private fun strEntry(v: String) = typed("str", JsonPrimitive(v))

    /**
     * The shared copy's own value when it means the same: the TV writes JSON slightly differently
     * (escaping, field order), and a re-written but equal value would look like a change.
     */
    private fun keepIfSame(shadow: JsonObject?, key: String, fresh: JsonObject): JsonElement {
        val old = shadow?.get(key) as? JsonObject ?: return fresh
        if (old.toString() == fresh.toString()) return old
        if (old.str("t") != fresh.str("t")) return fresh
        val a = old["v"]
        val b = fresh["v"]
        val same = when {
            a is JsonPrimitive && b is JsonPrimitive && a.isString && b.isString ->
                a.content == b.content || runCatching { json.parseToJsonElement(a.content) == json.parseToJsonElement(b.content) }.getOrDefault(false)
            a is JsonArray && b is JsonArray -> stringsOf(a).toSet() == stringsOf(b).toSet()
            else -> a?.toString() == b?.toString()
        }
        return if (same) old else fresh
    }

    /** A TV playlist this computer can load: switched on, an M3U address, an Xtream login or a Stalker portal. */
    private fun loadableHere(o: JsonObject): Boolean {
        if (!o.bool("enabled", true)) return false
        val type = o.str("sourceType").orEmpty()
        if (type.isNotBlank() && !type.equals("m3u", true) && !type.equals("xtream", true) &&
            !type.equals("stalker", true)
        ) {
            return false
        }
        return o.str("url").orEmpty().isNotBlank() ||
            o.str("xtreamServer").orEmpty().isNotBlank() ||
            o.str("stalkerPortal").orEmpty().isNotBlank()
    }

    private fun JsonObject.bool(key: String, default: Boolean): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

    private fun shadowFile() = IptvSourceStore.dataDir().resolve("livetv-sync-shared.json")

    private fun readShadow(): JsonObject? = runCatching {
        val f = shadowFile()
        if (!Files.isRegularFile(f)) null else LiveTvMerge.parse(Files.readString(f))
    }.getOrNull()

    private fun writeShadow(data: JsonObject) {
        runCatching {
            val f = shadowFile()
            Files.createDirectories(f.parent)
            Files.writeString(f, data.toString())
            IptvSourceStore.setOwnerOnly(f)
        }
    }

    private fun setBool(s: IptvSettings, key: String, v: Boolean): IptvSettings = when (key) {
        "show_channel_names" -> s.copy(showChannelNames = v)
        "show_channel_numbers" -> s.copy(showChannelNumbers = v)
        "show_channel_logos" -> s.copy(showChannelLogos = v)
        "show_quality_badges" -> s.copy(showQualityBadges = v)
        "mark_playing_channel" -> s.copy(outlinePlayingChannel = v)
        "compact_rows" -> s.copy(compactRows = v)
        "show_favorites_group" -> s.copy(showFavoritesGroup = v)
        "show_group_counts" -> s.copy(showGroupCounts = v)
        "show_program_details" -> s.copy(showProgramDetails = v)
        "show_program_times" -> s.copy(showProgramTimes = v)
        "show_now_line" -> s.copy(showNowLine = v)
        "clock_24h" -> s.copy(use24HourClock = v)
        "auto_reconnect" -> s.copy(autoReconnect = v)
        "show_recent_group" -> s.copy(showRecentGroup = v)
        "show_all_group" -> s.copy(showAllGroup = v)
        "sequential_numbers" -> s.copy(sequentialNumbers = v)
        "clean_group_names" -> s.copy(cleanGroupNames = v)
        else -> s
    }

    private fun setInt(s: IptvSettings, key: String, v: Int): IptvSettings = when (key) {
        "epg_offset_minutes" -> s.copy(epgOffsetMinutes = v)
        "epg_past_hours" -> s.copy(epgPastHours = v)
        "epg_future_days" -> s.copy(epgFutureDays = v)
        "playlist_refresh_hours" -> s.copy(playlistRefreshHours = v)
        "record_pre_minutes" -> s.copy(recordPreMinutes = v)
        "record_post_minutes" -> s.copy(recordPostMinutes = v)
        else -> s
    }

    private val BOOL_KEYS: List<Pair<String, (IptvSettings) -> Boolean>> = listOf(
        "show_channel_names" to { it.showChannelNames },
        "show_channel_numbers" to { it.showChannelNumbers },
        "show_channel_logos" to { it.showChannelLogos },
        "show_quality_badges" to { it.showQualityBadges },
        "mark_playing_channel" to { it.outlinePlayingChannel },
        "compact_rows" to { it.compactRows },
        "show_favorites_group" to { it.showFavoritesGroup },
        "show_group_counts" to { it.showGroupCounts },
        "show_program_details" to { it.showProgramDetails },
        "show_program_times" to { it.showProgramTimes },
        "show_now_line" to { it.showNowLine },
        "clock_24h" to { it.use24HourClock },
        "auto_reconnect" to { it.autoReconnect },
        "show_recent_group" to { it.showRecentGroup },
        "show_all_group" to { it.showAllGroup },
        "sequential_numbers" to { it.sequentialNumbers },
        "clean_group_names" to { it.cleanGroupNames },
    )

    private val INT_KEYS: List<Pair<String, (IptvSettings) -> Int>> = listOf(
        "epg_offset_minutes" to { it.epgOffsetMinutes },
        "epg_past_hours" to { it.epgPastHours },
        "epg_future_days" to { it.epgFutureDays },
        "playlist_refresh_hours" to { it.playlistRefreshHours },
        "record_pre_minutes" to { it.recordPreMinutes },
        "record_post_minutes" to { it.recordPostMinutes },
    )

    private const val KEY_PLAYLISTS = "playlists"
    private const val KEY_EPGS = "epg_sources"
    private const val KEY_HIDDEN_GROUPS = "hidden_groups"
    private const val KEY_GROUP_ORDER = "group_order"
    private const val KEY_FAVORITES = "favorites"
    private const val DESKTOP_PREFIX = "desktop_"
    private const val KEY_DESKTOP_SETTINGS = "desktop_settings"
    private const val KEY_DESKTOP_FAVORITES = "desktop_favorites"
    private const val SOURCE_GUIDE_PREFIX = "src-epg:"
    private val PER_DEVICE_SETTINGS = setOf("last_channel", "recent_channels")

    private val MAPPED_KEYS: Set<String> =
        setOf(KEY_PLAYLISTS, KEY_EPGS, KEY_HIDDEN_GROUPS, KEY_GROUP_ORDER, KEY_FAVORITES) + BOOL_KEYS.map { it.first } + INT_KEYS.map { it.first } +
            LiveTvMusicSync.KEYS

    private val DEFAULT_PLAYLIST: Map<String, JsonElement> = linkedMapOf(
        "id" to JsonPrimitive(""), "name" to JsonPrimitive(""), "url" to JsonPrimitive(""),
        "enabled" to JsonPrimitive(true), "userAgent" to JsonPrimitive(""), "useEmbeddedEpg" to JsonPrimitive(true),
        "xtreamServer" to JsonPrimitive(""), "xtreamUsername" to JsonPrimitive(""), "xtreamPassword" to JsonPrimitive(""),
        "lastUpdatedMs" to JsonPrimitive(0), "lastError" to JsonPrimitive(""), "channelCount" to JsonPrimitive(0),
        "importVod" to JsonPrimitive(false), "importLive" to JsonPrimitive(true), "streamFormat" to JsonPrimitive("auto"),
        "serverTimezone" to JsonPrimitive(""), "catchupNoHlsSince" to JsonPrimitive(0), "logoSource" to JsonPrimitive("playlist"),
    )

    private val DEFAULT_GUIDE: Map<String, JsonElement> = linkedMapOf(
        "id" to JsonPrimitive(""), "name" to JsonPrimitive(""), "url" to JsonPrimitive(""),
        "enabled" to JsonPrimitive(true), "lastUpdatedMs" to JsonPrimitive(0), "lastError" to JsonPrimitive(""),
        "programCount" to JsonPrimitive(0),
    )
}
