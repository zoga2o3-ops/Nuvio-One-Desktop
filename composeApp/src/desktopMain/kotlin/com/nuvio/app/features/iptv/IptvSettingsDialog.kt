package com.nuvio.app.features.iptv

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val settingsTabs = listOf(
    "setup" to "Setup",
    "guide" to "Guide",
    "playback" to "Playback",
    "parental" to "Parental",
    "sync" to "Sync",
)

private val offsetChoices = (-8..8).map { it * 30 }
private val pastHourChoices = listOf(6, 12, 24, 48, 72, 168)
private val futureDayChoices = listOf(1, 2, 3, 5, 7, 10, 14)
private val padChoices = listOf(0, 1, 2, 5, 10)
private val aspectChoices = listOf(
    "fit" to "Fit",
    "fill" to "Fill",
    "zoom" to "Zoom",
    "stretch" to "Stretch",
)

@Composable
internal fun IptvSettingsDialog(
    settings: IptvSettings,
    groups: List<String>,
    channelsByGroup: Map<String, List<LiveChannel>>,
    onChange: (IptvSettings) -> Unit,
    onDismiss: () -> Unit,
    onManageSources: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = IptvPanel,
            contentColor = IptvText,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(780.dp).height(620.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Live TV settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Channel rows, guide and playback",
                            fontSize = 11.5.sp,
                            color = IptvDim,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = IptvMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                IptvSettingsContent(
                    settings = settings,
                    groups = groups,
                    channelsByGroup = channelsByGroup,
                    onChange = onChange,
                    onManageSources = onManageSources,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = IptvText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** The tabs and their options, shared by the Live TV settings dialog and Settings → Sections → Live TV. */
@Composable
internal fun IptvSettingsContent(
    settings: IptvSettings,
    groups: List<String>,
    channelsByGroup: Map<String, List<LiveChannel>>,
    onChange: (IptvSettings) -> Unit,
    onManageSources: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf("setup") }
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            settingsTabs.forEach { (id, label) ->
                SettingsTab(label, tab == id) { tab = id }
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when (tab) {
                "setup" -> SetupTab(settings, groups, channelsByGroup, onChange, onManageSources)
                "guide" -> GuideTab(settings, onChange)
                "playback" -> PlaybackTab(settings, onChange)
                "parental" -> ParentalTab(settings, onChange)
                "sync" -> SyncTab()
            }
        }
    }
}

/**
 * Live TV settings for Settings → Sections → Live TV: the same options as the gear menu in Live TV,
 * saved to [IptvSettingsStore]. The channel and group order editors use the channels Live TV has
 * loaded this session (none until Live TV has been opened). Sources are managed in Live TV itself.
 */
@Composable
internal fun IptvSettingsPage(modifier: Modifier = Modifier) {
    var settings by remember { mutableStateOf(IptvSettingsStore.load()) }
    val channels = IptvSession.guide.channels
    val groups = remember(channels) { channels.map { it.group }.filter { it.isNotBlank() }.distinct() }
    val byGroup = remember(channels) { channels.groupBy { it.group } }
    IptvSettingsContent(
        settings = settings,
        groups = groups,
        channelsByGroup = byGroup,
        onChange = {
            settings = it
            IptvSettingsStore.save(it)
        },
        onManageSources = null,
        modifier = modifier,
    )
}

@Composable
private fun SettingsTab(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (active) IptvAccent else IptvRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) Color.White else IptvMuted,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.3.sp,
        color = IptvDim,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = IptvDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IptvAccent,
                uncheckedThumbColor = Color(0xFF8A8A99),
                uncheckedTrackColor = Color(0xFF2A2A35),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    value: String,
    choices: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 11.sp, color = IptvDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(value, fontSize = 12.sp, color = IptvMuted)
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = IptvDim,
                modifier = Modifier.size(18.dp),
            )
        }
        if (open) {
            Column(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101016)),
            ) {
                choices.forEach { (id, label) ->
                    val selected = label == value
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                onSelect(id)
                                open = false
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            fontSize = 12.5.sp,
                            color = if (selected) IptvText else IptvMuted,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = IptvAccent,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupTab(
    settings: IptvSettings,
    groups: List<String>,
    channelsByGroup: Map<String, List<LiveChannel>>,
    onChange: (IptvSettings) -> Unit,
    onManageSources: (() -> Unit)?,
) {
    var editingChannels by remember { mutableStateOf(false) }
    var editingGroups by remember { mutableStateOf(false) }
    SectionTitle("Channel rows")
    ToggleRow(
        "Show channel names",
        "Off: just numbers and logos, which leaves more room for the list",
        settings.showChannelNames,
    ) { onChange(settings.copy(showChannelNames = it)) }
    ToggleRow(
        "Show channel numbers",
        "The number in front of each channel",
        settings.showChannelNumbers,
    ) { onChange(settings.copy(showChannelNumbers = it)) }
    ToggleRow(
        "Show channel logos",
        "The logo in front of each channel name",
        settings.showChannelLogos,
    ) { onChange(settings.copy(showChannelLogos = it)) }
    ToggleRow(
        "Quality badges",
        "4K, FHD, HD or SD next to channels that name it",
        settings.showQualityBadges,
    ) { onChange(settings.copy(showQualityBadges = it)) }
    ToggleRow(
        "Outline the playing channel",
        "A red outline around the channel you're watching",
        settings.outlinePlayingChannel,
    ) { onChange(settings.copy(outlinePlayingChannel = it)) }
    ToggleRow(
        "Compact rows",
        "Slimmer rows and smaller text, so more channels fit",
        settings.compactRows,
    ) { onChange(settings.copy(compactRows = it)) }
    ToggleRow(
        "Show Favourites",
        "A Favourites shortcut above the channels and group, with the channels you starred",
        settings.showFavoritesGroup,
    ) { onChange(settings.copy(showFavoritesGroup = it)) }
    ToggleRow(
        "Show group counts",
        "How many channels each group holds, beside its name",
        settings.showGroupCounts,
    ) { onChange(settings.copy(showGroupCounts = it)) }
    ToggleRow(
        "Show Recently watched",
        "A Recently watched shortcut above the channels and group, with the channels you watched last, newest first",
        settings.showRecentGroup,
    ) { onChange(settings.copy(showRecentGroup = it)) }
    if (settings.recentChannelKeys.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${settings.recentChannelKeys.size} channel${if (settings.recentChannelKeys.size == 1) "" else "s"} in Recently watched",
                fontSize = 11.5.sp,
                color = IptvDim,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onChange(settings.copy(recentChannelKeys = emptyList())) }) {
                Text("Clear Recently watched", color = Color(0xFFFF8A9B), fontSize = 12.sp)
            }
        }
    }
    ToggleRow(
        "Show the All group",
        "A group listing every channel",
        settings.showAllGroup,
    ) { onChange(settings.copy(showAllGroup = it)) }
    ToggleRow(
        "Sequential numbers",
        "Number channels 1, 2, 3… in list order instead of the provider's numbers",
        settings.sequentialNumbers,
    ) { onChange(settings.copy(sequentialNumbers = it)) }

    SectionTitle("Names & groups")
    ToggleRow(
        "Clean group names",
        "Take country tags like \"US|\" or \"[UK]\" off the front of group names",
        settings.cleanGroupNames,
    ) { onChange(settings.copy(cleanGroupNames = it)) }
    IptvWordListRow(
        title = "Channel name cleanup",
        subtitle = "Words to take off the start or end of channel names, like USA, 24/7 or FHD",
        values = settings.nameRemovals,
        onChange = { onChange(settings.copy(nameRemovals = it)) },
    )
    IptvHiddenGroupsRow(
        groups = groups.filter { it != "Favorites" && it != "All" && it != "Recent" } + settings.hiddenGroups,
        hidden = settings.hiddenGroups,
        onChange = { onChange(settings.copy(hiddenGroups = it)) },
    )

    SectionTitle("Updates")
    ChoiceRow(
        title = "Update channels and guide",
        subtitle = "How often playlists and listings refresh by themselves",
        value = when (settings.playlistRefreshHours) {
            0 -> "Only when I ask"
            1 -> "Every hour"
            else -> "Every ${settings.playlistRefreshHours} hours"
        },
        choices = listOf(
            "1" to "Every hour",
            "2" to "Every 2 hours",
            "6" to "Every 6 hours",
            "12" to "Every 12 hours",
            "24" to "Every 24 hours",
            "0" to "Only when I ask",
        ),
    ) { onChange(settings.copy(playlistRefreshHours = it.toIntOrNull() ?: 2)) }

    SectionTitle("Sorting")
    ChoiceRow(
        title = "Channel order",
        subtitle = "How the channel list is sorted, or set your own order",
        value = when (settings.channelSort) {
            "az" -> "Name"
            "provider" -> "Provider order"
            "custom" -> "Your order"
            else -> "Channel number"
        },
        choices = listOf(
            "number" to "Channel number",
            "az" to "Name",
            "provider" to "Provider order",
            "custom" to "Your order",
        ),
    ) { onChange(settings.copy(channelSort = it)) }
    if (settings.channelSort == "custom") {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { editingChannels = true }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Edit channel order",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text("Move channels up or down, per group", fontSize = 11.sp, color = IptvDim)
            Spacer(Modifier.width(10.dp))
            Text("Edit", fontSize = 12.sp, color = IptvMuted)
        }
    }
    ChoiceRow(
        title = "Group order",
        subtitle = "How the group list is sorted, or set your own order",
        value = when (settings.groupSort) {
            "az" -> "A-Z"
            "custom" -> "Your order"
            else -> "Provider order"
        },
        choices = listOf(
            "provider" to "Provider order",
            "az" to "A-Z",
            "custom" to "Your order",
        ),
    ) { onChange(settings.copy(groupSort = it)) }
    if (settings.groupSort == "custom") {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { editingGroups = true }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Edit group order",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text("Move groups up or down", fontSize = 11.sp, color = IptvDim)
            Spacer(Modifier.width(10.dp))
            Text("Edit", fontSize = 12.sp, color = IptvMuted)
        }
    }

    SectionTitle("Sources")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Playlists, guides and catch-up are managed per source.",
            fontSize = 11.sp,
            color = IptvDim,
            modifier = Modifier.weight(1f),
        )
        if (onManageSources != null) {
            TextButton(onClick = onManageSources) {
                Text("Manage sources", fontSize = 12.sp, color = IptvMuted)
            }
        } else {
            Text("Open Live TV to manage sources", fontSize = 11.sp, color = IptvDim)
        }
    }

    val importedSources = IptvSourceStore.load()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (importedSources.isEmpty()) {
            Text("No playlists imported yet.", fontSize = 11.sp, color = IptvDim)
        } else {
            Text(
                "Imported playlists (${importedSources.size})",
                fontSize = 10.5.sp,
                color = IptvDim,
            )
            importedSources.forEach { source ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(IptvPanel)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            sourceTypeLabel(source),
                            fontSize = 10.5.sp,
                            color = IptvDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (editingChannels) {
        ChannelOrderDialog(
            channelsByGroup = channelsByGroup,
            order = settings.customChannelOrder,
            onChange = { onChange(settings.copy(customChannelOrder = it)) },
            onDismiss = { editingChannels = false },
        )
    }
    if (editingGroups) {
        GroupOrderDialog(
            groups = groups,
            order = settings.customGroupOrder,
            onChange = { onChange(settings.copy(customGroupOrder = it)) },
            onDismiss = { editingGroups = false },
        )
    }
}

@Composable
private fun ChannelOrderDialog(
    channelsByGroup: Map<String, List<LiveChannel>>,
    order: Map<String, List<String>>,
    onChange: (Map<String, List<String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val groupNames = channelsByGroup.keys.filter { it.isNotBlank() }.sorted()
    var group by remember { mutableStateOf(groupNames.firstOrNull().orEmpty()) }
    val channels = channelsByGroup[group].orEmpty()
    val saved = order[group].orEmpty()
    val rank = remember(saved) { saved.withIndex().associate { it.value to it.index } }
    val ordered = remember(channels, rank) { channels.sortedBy { rank[it.key] ?: Int.MAX_VALUE } }
    val keys = ordered.map { it.key }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (index !in keys.indices || target !in keys.indices) return
        val next = keys.toMutableList()
        val temp = next[index]
        next[index] = next[target]
        next[target] = temp
        onChange(order + (group to next))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = IptvPanel,
            contentColor = IptvText,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(860.dp).height(620.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Channel order", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Pick a group on the left, then move channels up or down", fontSize = 11.5.sp, color = IptvDim)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = IptvMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.width(240.dp).fillMaxHeight()) {
                        Text("GROUPS", fontSize = 10.sp, letterSpacing = 1.2.sp, color = IptvDim)
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(groupNames, key = { it }) { name ->
                                val selected = name == group
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) IptvRaised else Color.Transparent)
                                        .clickable { group = name }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        name,
                                        fontSize = 12.5.sp,
                                        color = if (selected) IptvText else IptvMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${channelsByGroup[name].orEmpty().size}",
                                        fontSize = 10.5.sp,
                                        color = IptvDim,
                                    )
                                }
                            }
                        }
                    }

                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Text(
                            if (group.isBlank()) "CHANNELS" else "CHANNELS IN ${group.uppercase()}",
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            color = IptvDim,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (ordered.isEmpty()) {
                            Text("No channels in this group.", fontSize = 12.sp, color = IptvMuted)
                        } else {
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(ordered, key = { it.key }) { channel ->
                                    val index = keys.indexOf(channel.key)
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(IptvRaised)
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            fontSize = 10.5.sp,
                                            color = IptvDim,
                                            modifier = Modifier.width(28.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                channel.name,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text("${channel.number}", fontSize = 10.sp, color = IptvDim)
                                        }
                                        IconButton(
                                            onClick = { move(index, -1) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(30.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.KeyboardArrowUp,
                                                contentDescription = "Move up",
                                                tint = if (index > 0) IptvMuted else Color(0xFF3A3A45),
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { move(index, 1) },
                                            enabled = index < keys.lastIndex,
                                            modifier = Modifier.size(30.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.KeyboardArrowDown,
                                                contentDescription = "Move down",
                                                tint = if (index < keys.lastIndex) IptvMuted else Color(0xFF3A3A45),
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onChange(order - group) }) {
                        Text("Reset this group", fontSize = 12.sp, color = IptvMuted)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = IptvText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupOrderDialog(
    groups: List<String>,
    order: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val rank = remember(order) { order.withIndex().associate { it.value to it.index } }
    val ordered = remember(groups, rank) { groups.sortedBy { rank[it] ?: Int.MAX_VALUE } }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (index !in ordered.indices || target !in ordered.indices) return
        val next = ordered.toMutableList()
        val temp = next[index]
        next[index] = next[target]
        next[target] = temp
        onChange(next)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = IptvPanel,
            contentColor = IptvText,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(560.dp).height(560.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Group order", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Move groups up or down", fontSize = 11.5.sp, color = IptvDim)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = IptvMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(ordered, key = { it }) { name ->
                        val index = ordered.indexOf(name)
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(IptvRaised)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 10.5.sp,
                                color = IptvDim,
                                modifier = Modifier.width(26.dp),
                            )
                            Text(
                                name,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { move(index, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Move up",
                                    tint = if (index > 0) IptvMuted else Color(0xFF3A3A45),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            IconButton(
                                onClick = { move(index, 1) },
                                enabled = index < ordered.lastIndex,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                    tint = if (index < ordered.lastIndex) IptvMuted else Color(0xFF3A3A45),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onChange(emptyList()) }) {
                        Text("Reset", fontSize = 12.sp, color = IptvMuted)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = IptvText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideTab(settings: IptvSettings, onChange: (IptvSettings) -> Unit) {
    SectionTitle("Guide")
    ToggleRow(
        "Show programme details",
        "The description under each programme in the guide",
        settings.showProgramDetails,
    ) { onChange(settings.copy(showProgramDetails = it)) }
    ToggleRow(
        "Show times in the guide",
        "Each programme's start and end time under its title",
        settings.showProgramTimes,
    ) { onChange(settings.copy(showProgramTimes = it)) }
    ToggleRow(
        "Show the \"now\" line",
        "The progress line marking where you are in the current programme",
        settings.showNowLine,
    ) { onChange(settings.copy(showNowLine = it)) }
    ToggleRow(
        "24-hour clock",
        "Times like 20:30 instead of 8:30 PM",
        settings.use24HourClock,
    ) { onChange(settings.copy(use24HourClock = it)) }

    SectionTitle("Guide data")
    ChoiceRow(
        title = "EPG offset",
        subtitle = "Shift guide times when your provider's clock is off",
        value = if (settings.epgOffsetMinutes == 0) "Off" else "${settings.epgOffsetMinutes} min",
        choices = offsetChoices.map { it.toString() to (if (it == 0) "Off" else "$it min") },
    ) { onChange(settings.copy(epgOffsetMinutes = it.toIntOrNull() ?: 0)) }
    ChoiceRow(
        title = "Past hours",
        subtitle = "How far back the guide keeps programmes",
        value = "${settings.epgPastHours} h",
        choices = pastHourChoices.map { it.toString() to "$it hours" },
    ) { onChange(settings.copy(epgPastHours = it.toIntOrNull() ?: 24)) }
    ChoiceRow(
        title = "Future days",
        subtitle = "How far ahead the guide lists programmes",
        value = "${settings.epgFutureDays} d",
        choices = futureDayChoices.map { it.toString() to "$it days" },
    ) { onChange(settings.copy(epgFutureDays = it.toIntOrNull() ?: 1)) }
}

@Composable
private fun PlaybackTab(settings: IptvSettings, onChange: (IptvSettings) -> Unit) {
    SectionTitle("Picture")
    ChoiceRow(
        title = "Aspect",
        subtitle = "How the picture fills the player",
        value = aspectChoices.firstOrNull { it.first == settings.aspectMode }?.second ?: "Fit",
        choices = aspectChoices,
    ) { onChange(settings.copy(aspectMode = it)) }

    SectionTitle("Streaming")
    ToggleRow(
        "Auto reconnect",
        "Retry the stream once if it drops",
        settings.autoReconnect,
    ) { onChange(settings.copy(autoReconnect = it)) }
    ToggleRow(
        "Remember last channel",
        "Open Live TV on the channel you watched last",
        settings.rememberLastChannel,
    ) { onChange(settings.copy(rememberLastChannel = it)) }

    SectionTitle("Recording")
    ChoiceRow(
        title = "Start before",
        subtitle = "Padding added before a scheduled recording",
        value = padLabel(settings.recordPreMinutes),
        choices = padChoices.map { it.toString() to padLabel(it) },
    ) { onChange(settings.copy(recordPreMinutes = it.toIntOrNull() ?: 0)) }
    ChoiceRow(
        title = "Stop after",
        subtitle = "Padding added after a scheduled recording",
        value = padLabel(settings.recordPostMinutes),
        choices = padChoices.map { it.toString() to padLabel(it) },
    ) { onChange(settings.copy(recordPostMinutes = it.toIntOrNull() ?: 0)) }
}

@Composable
private fun ParentalTab(settings: IptvSettings, onChange: (IptvSettings) -> Unit) {
    SectionTitle("Parental controls")
    ToggleRow(
        "Hide adult groups",
        "Groups marked ADULT, XXX or 18+ are removed from the list, search and guide",
        settings.hideAdultGroups,
    ) { onChange(settings.copy(hideAdultGroups = it)) }
    Text(
        "Desktop keeps this simpler than the TV app: there is no PIN prompt, the groups are simply " +
            "hidden on this computer.",
        fontSize = 11.sp,
        color = IptvDim,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

private fun padLabel(minutes: Int): String = if (minutes <= 0) "Off" else "$minutes min"


/** Comma-separated words, edited in place. */
@Composable
private fun IptvWordListRow(
    title: String,
    subtitle: String,
    values: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var text by remember(values) { mutableStateOf(values.joinToString(", ")) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, fontSize = 11.sp, color = IptvDim)
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("USA, 24/7, FHD", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
        )
        val parsed = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parsed != values) {
            Text(
                "Apply",
                fontSize = 12.sp,
                color = IptvAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onChange(parsed) }.padding(6.dp),
            )
        }
    }
}

/** Hidden groups: a settings row that opens the full show/hide list. */
@Composable
private fun IptvHiddenGroupsRow(
    groups: List<String>,
    hidden: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val all = remember(groups, hidden) { (groups + hidden).filter { it.isNotBlank() }.distinct() }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { open = true }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Show & hide groups", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("Choose which groups appear in the list and guide", fontSize = 11.sp, color = IptvDim)
        }
        Text(
            if (hidden.isEmpty()) "All shown" else "${all.size - hidden.size} of ${all.size} shown",
            fontSize = 12.sp,
            color = IptvMuted,
        )
        Spacer(Modifier.width(6.dp))
        Text("Edit", fontSize = 12.sp, color = IptvMuted)
    }
    if (open) {
        HiddenGroupsDialog(groups = all, hidden = hidden, onChange = onChange, onDismiss = { open = false })
    }
}

/**
 * Every group in one scrollable list with a checkbox each (ticked = shown).
 * Show all / Hide all act on the groups currently listed, so a search such as
 * "adult" or "spain" can hide a whole family of groups in one go.
 */
@Composable
private fun HiddenGroupsDialog(
    groups: List<String>,
    hidden: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val hiddenSet = hidden.toSet()
    val listed = remember(groups, query) {
        if (query.isBlank()) groups else groups.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    val shownCount = groups.count { it !in hiddenSet }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = IptvPanel,
            contentColor = IptvText,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(560.dp).height(620.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show & hide groups", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("$shownCount of ${groups.size} groups shown", fontSize = 11.5.sp, color = IptvDim)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = IptvMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search groups", fontSize = 12.5.sp) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HiddenGroupsButton(if (query.isBlank()) "Show all" else "Show these ${listed.size}") {
                        onChange(hidden.filter { it !in listed })
                    }
                    HiddenGroupsButton(if (query.isBlank()) "Hide all" else "Hide these ${listed.size}") {
                        onChange((hidden + listed).distinct())
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${listed.count { it !in hiddenSet }} shown · ${listed.count { it in hiddenSet }} hidden",
                        fontSize = 11.5.sp,
                        color = IptvDim,
                    )
                }

                Spacer(Modifier.height(10.dp))
                if (listed.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (groups.isEmpty()) "No groups yet. Add a playlist first." else "No groups match \"$query\".",
                            fontSize = 12.5.sp,
                            color = IptvDim,
                        )
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(listed, key = { it }) { group ->
                            val shown = group !in hiddenSet
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (shown) IptvRaised else Color.Transparent)
                                    .clickable { onChange(if (shown) hidden + group else hidden - group) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = shown,
                                    onCheckedChange = { show -> onChange(if (show) hidden - group else hidden + group) },
                                    colors = androidx.compose.material3.CheckboxDefaults.colors(
                                        checkedColor = IptvAccent,
                                        uncheckedColor = IptvDim,
                                        checkmarkColor = Color.White,
                                    ),
                                )
                                Text(
                                    group,
                                    fontSize = 12.5.sp,
                                    color = if (shown) IptvText else IptvDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!shown) Text("Hidden", fontSize = 11.sp, color = IptvDim, modifier = Modifier.padding(end = 8.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row {
                    Spacer(Modifier.weight(1f))
                    HiddenGroupsButton("Done", primary = true, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun HiddenGroupsButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (primary) Color.White else IptvText,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (primary) IptvAccent else IptvRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}


// ---------------------------------------------------------------------- Sync

@Composable
private fun SyncTab() {
    val cloud by com.nuvio.app.features.iptv.sync.LiveTvCloudSync.state.collectAsState()
    val drive by com.nuvio.app.features.iptv.sync.LiveTvDriveSync.state.collectAsState()
    val cloudSync = com.nuvio.app.features.iptv.sync.LiveTvCloudSync
    val driveSync = com.nuvio.app.features.iptv.sync.LiveTvDriveSync

    Text(
        "Keep your playlists (with their logins), guides, hidden groups, group order and display " +
            "settings the same on this computer and your Nuvio TVs, along with Music's Liked Songs " +
            "and playlists. Channel favourites and channel order sync between computers; TVs keep their own.",
        fontSize = 11.5.sp,
        color = IptvDim,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )

    // The same playlist added on two devices arrives twice after combining (different ids).
    var duplicateCount by remember { mutableStateOf(IptvSourceStore.load().let { it.size - IptvLoader.distinctSources(it).size }) }
    if (duplicateCount > 0) {
        SectionTitle("Duplicates")
        SyncNote(
            "$duplicateCount playlist${if (duplicateCount == 1) " is" else "s are"} here more than once (same address and login), " +
                "usually from combining two devices' setups. Each loads only once either way. Removing the extra " +
                "entries also removes them from your other devices.",
        )
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            SyncButton("Remove duplicate playlists", primary = true) {
                val all = IptvSourceStore.load()
                IptvSourceStore.save(IptvLoader.distinctSources(all))
                duplicateCount = 0
            }
        }
    }

    SectionTitle("Nuvio account")
    when {
        !cloud.available -> SyncNote("Only available with the official Nuvio server.")
        !cloud.signedIn -> SyncNote("Sign in to your Nuvio account (Settings → Account) to sync through it.")
        else -> {
            ToggleRow(
                title = "Sync through your Nuvio account",
                subtitle = (cloud.email?.let { "Signed in as $it. " } ?: "") +
                    if (cloud.enabled) "Synced ${syncAgo(cloud.lastSyncMs)}." else "Off on this computer.",
                checked = cloud.enabled,
            ) { on -> if (on) cloudSync.turnOn() else cloudSync.turnOff() }
            SyncButtonRow {
                if (cloud.enabled) {
                    SyncButton("Sync now", primary = true, enabled = !cloud.busy) { cloudSync.syncNow() }
                    SyncButton("Restore earlier copy", enabled = !cloud.busy) { cloudSync.restorePrevious() }
                }
                SyncButton("Import from Google Drive", enabled = !cloud.busy && drive.available) { cloudSync.importFromDrive() }
                if (cloud.enabled) {
                    SyncButton("Delete cloud copy", danger = true, enabled = !cloud.busy) { cloudSync.deleteCloudCopy() }
                }
            }
            SyncStatus(cloud.busy, cloud.message)
        }
    }

    SectionTitle("Google Drive")
    when {
        !drive.available -> SyncNote("This build has no Google Drive sign-in set up.")
        drive.signIn != null -> {
            val prompt = drive.signIn!!
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IptvRaised)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("On your phone or in a browser, go to", fontSize = 12.sp, color = IptvMuted)
                Text(prompt.url, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("and enter this code:", fontSize = 12.sp, color = IptvMuted)
                Text(prompt.userCode, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncButton("Open in browser", primary = true) {
                        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(prompt.qrUrl)) }
                    }
                    SyncButton("Cancel") { driveSync.cancelSignIn() }
                }
            }
        }
        !drive.connected -> {
            SyncNote("Back up to your own Google Drive, as a file Nuvio creates (\"Nuvio Live TV backup.json\"); Nuvio can only see the files it made. It's the same backup your TVs use.")
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                SyncButton("Connect Google Drive", primary = true, enabled = !drive.busy) { driveSync.beginSignIn() }
            }
            SyncStatus(drive.busy, drive.message)
        }
        else -> {
            ToggleRow(
                title = "Sync automatically",
                subtitle = (drive.email?.let { "Connected as $it. " } ?: "Connected. ") +
                    "Last synced ${syncAgo(drive.lastSyncMs)}.",
                checked = drive.autoSync,
            ) { driveSync.setAutoSync(it) }
            SyncButtonRow {
                SyncButton("Back up now", primary = true, enabled = !drive.busy) { driveSync.backUpNow() }
                SyncButton("Restore from Drive", enabled = !drive.busy) { driveSync.restoreNow() }
                SyncButton("Disconnect", danger = true, enabled = !drive.busy) { driveSync.disconnect() }
            }
            SyncStatus(drive.busy, drive.message)
        }
    }
}

@Composable
private fun SyncNote(text: String) {
    Text(text, fontSize = 12.sp, color = IptvMuted, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
private fun SyncStatus(busy: Boolean, message: String?) {
    val text = when {
        busy -> "Working…"
        !message.isNullOrBlank() -> message
        else -> return
    }
    Text(text, fontSize = 11.5.sp, color = IptvMuted, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Sync actions wrap onto a second line instead of squeezing the last button. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SyncButtonRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SyncButton(
    label: String,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val danger = danger && !primary
    val red = Color(0xFFFF6B7D)
    Box(
        Modifier.height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    !enabled -> Color(0xFF1E1E26)
                    primary -> IptvAccent
                    danger -> red.copy(alpha = 0.08f)
                    else -> IptvRaised
                },
            )
            .border(
                1.dp,
                when {
                    !enabled || primary -> Color.Transparent
                    danger -> red.copy(alpha = 0.55f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                RoundedCornerShape(999.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            color = when {
                !enabled -> IptvDim
                primary -> Color.White
                danger -> red
                else -> IptvText
            },
        )
    }
}

private fun syncAgo(ms: Long): String {
    if (ms <= 0L) return "never"
    val minutes = (System.currentTimeMillis() - ms) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 48 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}

/** The one-time questions when a Nuvio account is first seen (shown over Live TV). */
@Composable
internal fun LiveTvCloudPromptHost() {
    val cloudSync = com.nuvio.app.features.iptv.sync.LiveTvCloudSync
    val state by cloudSync.state.collectAsState()
    val prompt = state.prompt ?: return
    Dialog(onDismissRequest = {}) {
        Surface(color = IptvPanel, contentColor = IptvText, shape = RoundedCornerShape(16.dp), modifier = Modifier.width(520.dp)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (prompt) {
                    com.nuvio.app.features.iptv.sync.CloudPrompt.BackUp -> {
                        Text("Save your Live TV setup to your Nuvio account?", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Your playlists (including their logins), guides and Live TV settings are saved to your Nuvio " +
                                "account, so your TVs and computers signed in to it have the same setup. You can turn it " +
                                "off any time in Live TV settings → Sync.",
                            fontSize = 12.5.sp, color = IptvMuted,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            SyncButton("Not now") { cloudSync.answerBackUp(false) }
                            SyncButton("Save and sync", primary = true) { cloudSync.answerBackUp(true) }
                        }
                    }
                    com.nuvio.app.features.iptv.sync.CloudPrompt.Choose -> {
                        Text("Your Nuvio account has a Live TV setup", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "This computer has its own setup too. Combine keeps the playlists and guides from both. " +
                                "Use the saved setup replaces what is on this computer.",
                            fontSize = 12.5.sp, color = IptvMuted,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            SyncButton("Later") { cloudSync.answerChoose(com.nuvio.app.features.iptv.sync.CloudChoice.Later) }
                            SyncButton("Use the saved setup") { cloudSync.answerChoose(com.nuvio.app.features.iptv.sync.CloudChoice.UseSaved) }
                            SyncButton("Combine", primary = true) { cloudSync.answerChoose(com.nuvio.app.features.iptv.sync.CloudChoice.Combine) }
                        }
                    }
                }
            }
        }
    }
}

private fun sourceTypeLabel(source: IptvSource): String {
    val kind = when (source.kind.lowercase()) {
        "xtream" -> "Xtream"
        "stalker" -> "Stalker"
        else -> "M3U"
    }
    val host = source.url.substringAfter("://").substringBefore('/').ifBlank { source.url }
    return "$kind · $host"
}
