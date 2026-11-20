package com.nuvio.app.features.iptv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Desktop Live TV options, mirroring the Android TV Live TV settings that apply to
 * this player: channel rows, guide behaviour and playback.
 */
internal data class IptvSettings(
    // Channel rows
    val showChannelNames: Boolean = true,
    val showChannelNumbers: Boolean = true,
    val showChannelLogos: Boolean = true,
    val showQualityBadges: Boolean = true,
    val outlinePlayingChannel: Boolean = true,
    val compactRows: Boolean = false,
    val showFavoritesGroup: Boolean = true,
    val showGroupCounts: Boolean = true,
    val groupSort: String = "provider",
    val channelSort: String = "provider",
    val customGroupOrder: List<String> = emptyList(),
    val customChannelOrder: Map<String, List<String>> = emptyMap(),
    // Guide
    val showProgramDetails: Boolean = true,
    val showProgramTimes: Boolean = true,
    val showNowLine: Boolean = true,
    val use24HourClock: Boolean = true,
    val epgOffsetMinutes: Int = 0,
    val epgPastHours: Int = 24,
    val epgFutureDays: Int = 1,
    // Playback
    val aspectMode: String = "fit",
    val autoReconnect: Boolean = true,
    /** Start full screen as soon as a channel is opened. */
    val openChannelsFullScreen: Boolean = false,
    val rememberLastChannel: Boolean = true,
    // Parental
    val hideAdultGroups: Boolean = false,
    // Recording
    val recordPreMinutes: Int = 0,
    val recordPostMinutes: Int = 0,
    // Last selection, restored on open
    val lastChannelKey: String = "",
    // Ported from the TV app's Live TV settings.
    /** A "Recent" group with the channels you watched last. */
    val showRecentGroup: Boolean = true,
    /** The "All" group listing every channel. */
    val showAllGroup: Boolean = true,
    /** Provider groups you chose to hide. */
    val hiddenGroups: List<String> = emptyList(),
    /** Words taken off the start or end of channel names (USA, 24/7, FHD…). */
    val nameRemovals: List<String> = emptyList(),
    /** Strip country/language prefixes like "US| " or "UK: " from group names. */
    val cleanGroupNames: Boolean = false,
    /** Number channels 1, 2, 3… in list order instead of the provider's numbers. */
    val sequentialNumbers: Boolean = false,
    /** Hours between automatic channel and guide updates (0 = only when you ask). */
    val playlistRefreshHours: Int = 2,
    /** Channels watched most recently, newest first. */
    val recentChannelKeys: List<String> = emptyList(),
)

internal object IptvSettingsStore {
    private val json = Json { ignoreUnknownKeys = true }

    private fun path(): Path {
        val override = System.getProperty("nuvio.iptv.storageDir")
        val dir = if (override != null) {
            Path.of(override)
        } else {
            val osName = System.getProperty("os.name").orEmpty().lowercase()
            val userHome = Path.of(System.getProperty("user.home").orEmpty())
            when {
                osName.contains("win") -> {
                    val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                    (appData?.let { Path.of(it) } ?: userHome.resolve("AppData/Roaming"))
                        .resolve("Nuvio IPTV")
                }
                osName.contains("mac") -> userHome.resolve("Library/Application Support/Nuvio IPTV")
                else -> {
                    val config = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                    (config?.let { Path.of(it) } ?: userHome.resolve(".config")).resolve("nuvio-iptv")
                }
            }
        }
        return dir.resolve("settings.json")
    }

    fun load(): IptvSettings = runCatching {
        val file = path()
        if (!Files.isRegularFile(file)) return IptvSettings()
        parse(json.parseToJsonElement(Files.readString(file)).jsonObject)
    }.getOrDefault(IptvSettings())

    fun parse(o: JsonObject): IptvSettings {
        val defaults = IptvSettings()
        return IptvSettings(
            showChannelNames = o.bool("show_channel_names") ?: defaults.showChannelNames,
            showChannelNumbers = o.bool("show_channel_numbers") ?: defaults.showChannelNumbers,
            showChannelLogos = o.bool("show_channel_logos") ?: defaults.showChannelLogos,
            showQualityBadges = o.bool("show_quality_badges") ?: defaults.showQualityBadges,
            outlinePlayingChannel = o.bool("outline_playing") ?: defaults.outlinePlayingChannel,
            compactRows = o.bool("compact_rows") ?: defaults.compactRows,
            showFavoritesGroup = o.bool("show_favorites_group") ?: defaults.showFavoritesGroup,
            showGroupCounts = o.bool("show_group_counts") ?: defaults.showGroupCounts,
            groupSort = o.str("group_sort") ?: defaults.groupSort,
            channelSort = o.str("channel_sort") ?: defaults.channelSort,
            customGroupOrder = o.stringList("group_order"),
            customChannelOrder = o.stringListMap("channel_order"),
            showProgramDetails = o.bool("show_program_details") ?: defaults.showProgramDetails,
            showProgramTimes = o.bool("show_program_times") ?: defaults.showProgramTimes,
            showNowLine = o.bool("show_now_line") ?: defaults.showNowLine,
            use24HourClock = o.bool("use_24h_clock") ?: defaults.use24HourClock,
            epgOffsetMinutes = o.int("epg_offset") ?: defaults.epgOffsetMinutes,
            epgPastHours = o.int("epg_past_hours") ?: defaults.epgPastHours,
            epgFutureDays = o.int("epg_future_days") ?: defaults.epgFutureDays,
            aspectMode = o.str("aspect_mode") ?: defaults.aspectMode,
            autoReconnect = o.bool("auto_reconnect") ?: defaults.autoReconnect,
            openChannelsFullScreen = o.bool("open_channels_fullscreen") ?: defaults.openChannelsFullScreen,
            rememberLastChannel = o.bool("remember_last_channel") ?: defaults.rememberLastChannel,
            hideAdultGroups = o.bool("hide_adult_groups") ?: defaults.hideAdultGroups,
            recordPreMinutes = o.int("record_pre") ?: defaults.recordPreMinutes,
            recordPostMinutes = o.int("record_post") ?: defaults.recordPostMinutes,
            lastChannelKey = o.str("last_channel") ?: defaults.lastChannelKey,
            showRecentGroup = o.bool("show_recent_group") ?: defaults.showRecentGroup,
            showAllGroup = o.bool("show_all_group") ?: defaults.showAllGroup,
            hiddenGroups = o.stringList("hidden_groups"),
            nameRemovals = o.stringList("name_removals"),
            cleanGroupNames = o.bool("clean_group_names") ?: defaults.cleanGroupNames,
            sequentialNumbers = o.bool("sequential_numbers") ?: defaults.sequentialNumbers,
            playlistRefreshHours = o.int("playlist_refresh_hours") ?: defaults.playlistRefreshHours,
            recentChannelKeys = o.stringList("recent_channels"),
        )
    }

    fun save(settings: IptvSettings) {
        runCatching {
            val file = path()
            Files.createDirectories(file.parent)
            val payload = toJson(settings)
            val temporary = Files.createTempFile(file.parent, "settings-", ".json")
            try {
                Files.writeString(temporary, payload.toString())
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        com.nuvio.app.features.iptv.sync.LiveTvSyncLocal.noteChanged()
    }

    fun toJson(settings: IptvSettings): JsonObject = buildJsonObject {
                put("show_channel_names", JsonPrimitive(settings.showChannelNames))
                put("show_channel_numbers", JsonPrimitive(settings.showChannelNumbers))
                put("show_channel_logos", JsonPrimitive(settings.showChannelLogos))
                put("show_quality_badges", JsonPrimitive(settings.showQualityBadges))
                put("outline_playing", JsonPrimitive(settings.outlinePlayingChannel))
                put("compact_rows", JsonPrimitive(settings.compactRows))
                put("show_favorites_group", JsonPrimitive(settings.showFavoritesGroup))
                put("show_group_counts", JsonPrimitive(settings.showGroupCounts))
                put("group_sort", JsonPrimitive(settings.groupSort))
                put("channel_sort", JsonPrimitive(settings.channelSort))
                put("group_order", buildJsonArray { settings.customGroupOrder.forEach { add(JsonPrimitive(it)) } })
                put("channel_order", buildJsonObject {
                    settings.customChannelOrder.forEach { (group, keys) ->
                        put(group, buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } })
                    }
                })
                put("show_program_details", JsonPrimitive(settings.showProgramDetails))
                put("show_program_times", JsonPrimitive(settings.showProgramTimes))
                put("show_now_line", JsonPrimitive(settings.showNowLine))
                put("use_24h_clock", JsonPrimitive(settings.use24HourClock))
                put("epg_offset", JsonPrimitive(settings.epgOffsetMinutes))
                put("epg_past_hours", JsonPrimitive(settings.epgPastHours))
                put("epg_future_days", JsonPrimitive(settings.epgFutureDays))
                put("aspect_mode", JsonPrimitive(settings.aspectMode))
                put("auto_reconnect", JsonPrimitive(settings.autoReconnect))
                put("open_channels_fullscreen", JsonPrimitive(settings.openChannelsFullScreen))
                put("remember_last_channel", JsonPrimitive(settings.rememberLastChannel))
                put("hide_adult_groups", JsonPrimitive(settings.hideAdultGroups))
                put("record_pre", JsonPrimitive(settings.recordPreMinutes))
                put("record_post", JsonPrimitive(settings.recordPostMinutes))
                put("last_channel", JsonPrimitive(settings.lastChannelKey))
                put("show_recent_group", JsonPrimitive(settings.showRecentGroup))
                put("show_all_group", JsonPrimitive(settings.showAllGroup))
                put("hidden_groups", buildJsonArray { settings.hiddenGroups.forEach { add(JsonPrimitive(it)) } })
                put("name_removals", buildJsonArray { settings.nameRemovals.forEach { add(JsonPrimitive(it)) } })
                put("clean_group_names", JsonPrimitive(settings.cleanGroupNames))
                put("sequential_numbers", JsonPrimitive(settings.sequentialNumbers))
                put("playlist_refresh_hours", JsonPrimitive(settings.playlistRefreshHours))
                put("recent_channels", buildJsonArray { settings.recentChannelKeys.forEach { add(JsonPrimitive(it)) } })
            }

    private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
    private fun JsonObject.stringList(key: String): List<String> =
        this[key]?.jsonArray?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()

    private fun JsonObject.stringListMap(key: String): Map<String, List<String>> =
        this[key]?.jsonObject?.entries?.associate { (group, value) ->
            group to value.jsonArray.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
        }.orEmpty()
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
