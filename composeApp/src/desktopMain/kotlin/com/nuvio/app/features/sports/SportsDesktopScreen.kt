package com.nuvio.app.features.sports

import com.nuvio.app.core.ui.SectionPalette

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.LocalNuvioPlatformDensity
import com.nuvio.app.core.ui.CoverNativeVideo
import com.nuvio.app.core.ui.isFullscreenActionActive
import com.nuvio.app.features.iptv.EpgProgram
import com.nuvio.app.features.iptv.IptvSession
import com.nuvio.app.features.iptv.LiveMultiview
import com.nuvio.app.features.iptv.PlaybackTarget
import com.nuvio.app.core.ui.toggleFullscreenAction
import com.nuvio.app.features.iptv.IptvSourceStore
import com.nuvio.app.features.iptv.LiveChannel
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.desktop.NativePlayerController
import com.nuvio.app.features.player.desktop.NativePlayerHost
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SportsBackground: Color get() = SectionPalette.background
private val SportsPanel: Color get() = SectionPalette.surface
private val SportsPanelRaised: Color get() = SectionPalette.raised
private val SportsBorder: Color get() = SectionPalette.border
private val SportsAccent = Color(0xFF4EA1FF)
private val SportsLive = Color(0xFFFF3B4E)
private val SportsMuted: Color get() = SectionPalette.muted
private val SportsDim: Color get() = SectionPalette.muted

private enum class SportsMode { HUB, LEAGUE, TEAM, EVENT, LISTINGS }

@Composable
internal fun SportsDesktopScreen(
    onBack: () -> Unit,
    onWatch: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sport by remember { mutableStateOf(SportsCatalog.sports.first()) }
    var mode by remember { mutableStateOf(SportsMode.HUB) }
    var myTeamsOnly by remember { mutableStateOf(false) }
    var dayIndex by remember { mutableStateOf(0) }
    var chip by remember { mutableStateOf("all") }
    var videoWindow by remember { mutableStateOf(true) }
    var weekEvents by remember { mutableStateOf<List<SportsEvent>>(emptyList()) }

    val iptvSources = remember { IptvSourceStore.load() }
    LaunchedEffect(iptvSources.size) {
        IptvSession.ensureLoaded(iptvSources)
    }
    val iptvChannels = IptvSession.guide.channels
    val iptvPrograms = IptvSession.guide.programs
    var inlineChannel by remember { mutableStateOf<LiveChannel?>(null) }
    // Multiview: four tiles, one per slot; an empty slot is a tile still waiting for a channel.
    var multiview by remember { mutableStateOf<List<PlaybackTarget?>?>(null) }

    var dayEvents by remember { mutableStateOf<List<SportsEvent>>(emptyList()) }
    var hubLoading by remember { mutableStateOf(false) }
    var hubErrors by remember { mutableStateOf<List<String>>(emptyList()) }

    var league by remember { mutableStateOf<CuratedLeague?>(null) }
    var leaguePage by remember { mutableStateOf<LeaguePageData?>(null) }
    var leagueLoading by remember { mutableStateOf(false) }
    var leagueTab by remember { mutableStateOf("matches") }
    var leagueTeams by remember { mutableStateOf<List<SportsTeamInfo>>(emptyList()) }
    var teamsLoading by remember { mutableStateOf(false) }

    var teamId by remember { mutableStateOf<String?>(null) }
    var teamData by remember { mutableStateOf<TeamPageData?>(null) }
    var teamLoading by remember { mutableStateOf(false) }

    var teamQuery by remember { mutableStateOf("") }
    var teamResults by remember { mutableStateOf<List<SportsTeamSearchResult>>(emptyList()) }
    var teamSearchLoading by remember { mutableStateOf(false) }

    LaunchedEffect(teamQuery) {
        val query = teamQuery.trim()
        if (query.isBlank()) {
            teamResults = emptyList()
            teamSearchLoading = false
            return@LaunchedEffect
        }
        delay(200)
        teamSearchLoading = true
        runCatching { SportsRepository.warmTeamIndex() }
        teamResults = runCatching { SportsRepository.searchTeams(query) }
            .getOrDefault(emptyList())
        teamSearchLoading = false
    }

    var eventId by remember { mutableStateOf<String?>(null) }
    var eventData by remember { mutableStateOf<EventPageData?>(null) }
    var eventLoading by remember { mutableStateOf(false) }

    var pins by remember { mutableStateOf(SportsSettings.pinnedTeams()) }
    var hideScores by remember { mutableStateOf(SportsSettings.hideScores()) }
    var autoRefreshMinutes by remember { mutableStateOf(SportsSettings.autoRefreshMinutes()) }
    var showSettings by remember { mutableStateOf(false) }
    var listingsDate by remember { mutableStateOf(LocalDate.now()) }
    var listings by remember { mutableStateOf<List<SportsTvListing>>(emptyList()) }
    var listingsLoading by remember { mutableStateOf(false) }

    fun refreshHub() {
        scope.launch {
            hubLoading = true
            hubErrors = emptyList()
            val date = LocalDate.now().plusDays(dayIndex.toLong()).toString()
            runCatching { SportsRepository.eventsForDay(sport.apiName, date) }
                .onSuccess { dayEvents = it }
                .onFailure { hubErrors = listOf(it.message ?: "Could not load today's matches.") }
            hubLoading = false
        }
    }

    LaunchedEffect(sport, dayIndex) { refreshHub() }

    LaunchedEffect(sport) {
        weekEvents = emptyList()
        val collected = mutableListOf<SportsEvent>()
        for (offset in 1..3) {
            runCatching {
                SportsRepository.eventsForDay(
                    sport.apiName,
                    LocalDate.now().plusDays(offset.toLong()).toString(),
                )
            }.getOrNull()?.let { collected += it }
            weekEvents = collected.toList()
        }
    }

    LaunchedEffect(autoRefreshMinutes, sport) {
        if (autoRefreshMinutes <= 0) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(autoRefreshMinutes * 60_000L)
            refreshHub()
        }
    }

    LaunchedEffect(mode, listingsDate, iptvChannels, iptvPrograms) {
        if (mode != SportsMode.LISTINGS) return@LaunchedEffect
        listingsLoading = true
        listings = sportsTvListings(listingsDate, iptvChannels, iptvPrograms)
        listingsLoading = false
    }

    LaunchedEffect(mode, eventId) {
        if (mode != SportsMode.EVENT) return@LaunchedEffect
        while (true) {
            val current = eventData?.event ?: break
            if (!current.isLive) break
            delay(60_000)
            eventData = runCatching { SportsRepository.eventPage(current.id) }
                .getOrElse { EventPageData() }
        }
    }

    LaunchedEffect(eventData?.event?.id) {
        val current = eventData?.event ?: return@LaunchedEffect
        if (current.isLive) {
            inlineChannel = matchSportsChannels(
                event = current,
                channels = iptvChannels,
                programs = iptvPrograms,
                now = System.currentTimeMillis(),
                limit = 1,
            ).firstOrNull()
        }
    }

    fun openLeague(selected: CuratedLeague) {
        league = selected
        mode = SportsMode.LEAGUE
        leagueTab = "matches"
        leagueTeams = emptyList()
        scope.launch {
            leagueLoading = true
            leaguePage = runCatching { SportsRepository.leaguePage(selected) }
                .getOrElse { LeaguePageData(SportsLeague(selected.id, selected.name, selected.shortName, selected.sport, selected.country)) }
            leagueLoading = false
        }
    }

    fun openTeam(id: String) {
        teamId = id
        teamData = null
        mode = SportsMode.TEAM
        scope.launch {
            teamLoading = true
            teamData = runCatching { SportsRepository.teamPage(id) }.getOrElse { TeamPageData() }
            teamLoading = false
        }
    }

    fun openEvent(id: String) {
        eventId = id
        eventData = null
        mode = SportsMode.EVENT
        scope.launch {
            eventLoading = true
            eventData = runCatching { SportsRepository.eventPage(id) }.getOrElse { EventPageData() }
            eventLoading = false
        }
    }

    fun playGame(event: SportsEvent) {
        val channel = matchSportsChannels(
            event = event,
            channels = iptvChannels,
            programs = iptvPrograms,
            now = System.currentTimeMillis(),
            limit = 1,
        ).firstOrNull()
        if (channel != null) {
            inlineChannel = channel
        } else {
            onWatch(bestSportsWatchQuery(event, iptvChannels, iptvPrograms))
        }
    }

    fun togglePin(team: SportsTeamInfo) {
        pins = if (pins.any { it.id == team.id }) {
            pins.filterNot { it.id == team.id }
        } else {
            pins + PinnedTeam(team.id, team.name, team.badgeUrl)
        }
        SportsSettings.setPinnedTeams(pins)
    }

    // Four empty tiles: each one gets a channel from its own "Choose channel" button.
    fun openMultiview() {
        multiview = List(4) { null }
    }

    fun chooseMultiviewTile(index: Int, target: PlaybackTarget?) {
        multiview = multiview?.toMutableList()?.also { it[index] = target }
    }

    val appFullscreen = isFullscreenActionActive()
    val multiFull = multiview
    if (appFullscreen && multiFull != null) {
        Box(Modifier.fillMaxSize().background(Color.Black).padding(8.dp)) {
            LiveMultiview(
                tiles = multiFull,
                channels = iptvChannels,
                fullscreen = true,
                onChoose = ::chooseMultiviewTile,
                onClose = { multiview = null },
                onToggleFullscreen = { toggleFullscreenAction() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    val fullscreenChannel = inlineChannel
    if (appFullscreen && fullscreenChannel != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            SportsInlinePlayer(fullscreenChannel, Modifier.fillMaxSize())
            Row(
                Modifier.align(Alignment.BottomStart).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SportsBadge("Live", SportsLive)
                Text(
                    fullscreenChannel.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        return
    }

    val multi = multiview
    Surface(color = SportsBackground, contentColor = Color.White) {
        Column(Modifier.fillMaxSize()) {
            SportsTopBar(
                chip = if (mode == SportsMode.LISTINGS) "guide" else chip,
                liveCount = dayEvents.count { it.isLive },
                videoWindow = videoWindow,
                onChip = { selected ->
                    mode = SportsMode.HUB
                    chip = selected
                    myTeamsOnly = selected == "my"
                },
                onToggleVideo = { videoWindow = !videoWindow },
                onSettings = { showSettings = true },
                onWatchGuide = { mode = SportsMode.LISTINGS },
                onBack = onBack,
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { openMultiview() }) {
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = "Multiview",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            SportsSearchBar(
                query = teamQuery,
                onQuery = { teamQuery = it },
            )

            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (multi != null) {
                    LiveMultiview(
                        tiles = multi,
                        channels = iptvChannels,
                        fullscreen = false,
                        onChoose = ::chooseMultiviewTile,
                        onClose = { multiview = null },
                        onToggleFullscreen = { toggleFullscreenAction() },
                        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    )
                } else if (teamQuery.isNotBlank()) {
                    TeamSearchResults(
                        query = teamQuery,
                        results = teamResults,
                        loading = teamSearchLoading,
                        onOpen = { id ->
                            teamQuery = ""
                            teamResults = emptyList()
                            openTeam(id)
                        },
                    )
                } else {
                    when (mode) {
                    SportsMode.HUB -> HubPane(
                        sport = sport,
                        dayIndex = dayIndex,
                        onDay = { dayIndex = it },
                        onSport = { sport = it },
                        chip = chip,
                        events = dayEvents,
                        weekEvents = weekEvents,
                        loading = hubLoading,
                        errors = hubErrors,
                        pins = pins,
                        channels = iptvChannels,
                        programs = iptvPrograms,
                        videoWindow = videoWindow,
                        hideScores = hideScores,
                        onRefresh = ::refreshHub,
                        onChooseLeagues = { chip = "tables" },
                        onOpenLeague = ::openLeague,
                        onOpenTeam = ::openTeam,
                        onOpenEvent = ::openEvent,
                        onWatch = onWatch,
                        inlineChannel = inlineChannel,
                        hideVideo = showSettings,
                        onPlayGame = ::playGame,
                        onPlayChannel = { inlineChannel = it },
                    )

                    SportsMode.LEAGUE -> LeaguePane(
                        league = league,
                        page = leaguePage,
                        loading = leagueLoading,
                        tab = leagueTab,
                        teams = leagueTeams,
                        teamsLoading = teamsLoading,
                        hideScores = hideScores,
                        onTab = { tab ->
                            leagueTab = tab
                            if (tab == "teams" && leagueTeams.isEmpty() && !teamsLoading) {
                                league?.let { current ->
                                    scope.launch {
                                        teamsLoading = true
                                        leagueTeams = when {
                                            current.id.startsWith("tsdb/") -> SportsRepository.extraTeams(current)
                                            current.id.startsWith("guide/") -> emptyList()
                                            else -> SportsRepository.leagueTeams(current)
                                        }
                                        teamsLoading = false
                                    }
                                }
                            }
                        },
                        onBack = { mode = SportsMode.HUB },
                        onOpenEvent = ::openEvent,
                        onOpenTeam = ::openTeam,
                    )

                    SportsMode.TEAM -> TeamPane(
                        teamId = teamId,
                        data = teamData,
                        loading = teamLoading,
                        pinned = teamId != null && pins.any { it.id == teamId },
                        hideScores = hideScores,
                        onBack = {
                            mode = if (league != null) SportsMode.LEAGUE else SportsMode.HUB
                        },
                        onTogglePin = {
                            teamData?.team?.let { togglePin(it) }
                        },
                        onOpenEvent = ::openEvent,
                        onWatch = onWatch,
                    )

                    SportsMode.EVENT -> EventPane(
                        data = eventData,
                        loading = eventLoading,
                        hideScores = hideScores,
                        inlineChannel = inlineChannel,
                        hideVideo = showSettings,
                        onPlayGame = ::playGame,
                        onBack = {
                            mode = when {
                                teamId != null && teamData != null -> SportsMode.TEAM
                                league != null -> SportsMode.LEAGUE
                                else -> SportsMode.HUB
                            }
                        },
                        onOpenTeam = ::openTeam,
                    )

                    SportsMode.LISTINGS -> ListingsPane(
                        date = listingsDate,
                        onDate = { listingsDate = it },
                        listings = listings,
                        loading = listingsLoading,
                        onOpenEvent = ::openEvent,
                    )
                    }
                }
            }
        }
    }

    // The video windows sit above Compose, so they hide while the settings dialog is open over them.
    CoverNativeVideo(covering = showSettings)
    if (showSettings) {
        SportsSettingsDialog(
            hideScores = hideScores,
            onHideScores = {
                hideScores = it
                SportsSettings.setHideScores(it)
            },
            autoRefreshMinutes = autoRefreshMinutes,
            onAutoRefresh = {
                autoRefreshMinutes = it
                SportsSettings.setAutoRefreshMinutes(it)
            },
            onClearCache = {
                SportsRepository.clearCache()
                refreshHub()
            },
            pins = pins,
            onPins = { pins = it },
            channelGroups = remember(iptvChannels) { iptvChannels.map { it.group }.distinct().sorted() },
            onDismiss = {
                showSettings = false
                refreshHub()
            },
        )
    }
}

/* --------------------------------------------------------------------- rail */

@Composable
private fun SportsRail(
    selectedSport: SportCategory,
    myTeamsActive: Boolean,
    listingsActive: Boolean,
    pinnedCount: Int,
    onSport: (SportCategory) -> Unit,
    onMyTeams: () -> Unit,
    onListings: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.width(224.dp).fillMaxHeight().background(Color(0xFF0A0A0E))) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Nuvio",
                    tint = SportsMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text("Sports", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (myTeamsActive) SportsAccent.copy(alpha = 0.16f) else Color.Transparent)
                .clickable(onClick = onMyTeams)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "My Teams",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (myTeamsActive) Color.White else SportsMuted,
                modifier = Modifier.weight(1f),
            )
            Text("$pinnedCount", fontSize = 11.sp, color = if (myTeamsActive) SportsAccent else SportsDim)
        }

        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (listingsActive) SportsAccent.copy(alpha = 0.16f) else Color.Transparent)
                .clickable(onClick = onListings)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "TV Guide",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (listingsActive) Color.White else SportsMuted,
            )
        }

        HorizontalDivider(color = SportsBorder, modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(SportsCatalog.sports, key = { it.apiName }) { item ->
                val active = item.apiName == selectedSport.apiName && !myTeamsActive && !listingsActive
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) SportsAccent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onSport(item) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(item.icon, fontSize = 14.sp)
                    Text(
                        item.label,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) Color.White else SportsMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------- hub */

@Composable
private fun SportsTopBar(
    chip: String,
    liveCount: Int,
    videoWindow: Boolean,
    onChip: (String) -> Unit,
    onToggleVideo: () -> Unit,
    onSettings: () -> Unit,
    onWatchGuide: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).background(Color(0xFF050507))
            .padding(start = 12.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Nuvio",
                tint = SportsMuted,
                modifier = Modifier.size(17.dp),
            )
        }
        Text("Sports", fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp)
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                "my" to "\u2605  My Sports",
                "all" to "\u25A6  All Events" + if (liveCount > 0) " · $liveCount live" else "",
                "tables" to "\uD83C\uDFC6  Tables",
                "networks" to "\uD83D\uDCE1  League Networks",
            ).forEach { (id, label) ->
                SportsPill(label, chip == id) { onChip(id) }
            }
        }
        SportsPill("TV Guide", chip == "guide") { onWatchGuide() }
        SportsPill(if (videoWindow) "Video on" else "Video off", videoWindow) { onToggleVideo() }
        IconButton(onClick = onSettings, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Sports settings", tint = SportsMuted, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun SportsPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (active) SportsAccent else SportsPanel)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) Color(0xFF07101C) else SportsMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun HubPane(
    sport: SportCategory,
    dayIndex: Int,
    onDay: (Int) -> Unit,
    onSport: (SportCategory) -> Unit,
    chip: String,
    events: List<SportsEvent>,
    weekEvents: List<SportsEvent>,
    loading: Boolean,
    errors: List<String>,
    pins: List<PinnedTeam>,
    channels: List<LiveChannel>,
    programs: Map<String, List<EpgProgram>>,
    videoWindow: Boolean,
    hideScores: Boolean,
    onRefresh: () -> Unit,
    onChooseLeagues: () -> Unit,
    onOpenLeague: (CuratedLeague) -> Unit,
    onOpenTeam: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
    onWatch: (String) -> Unit,
    inlineChannel: LiveChannel?,
    hideVideo: Boolean,
    onPlayGame: (SportsEvent) -> Unit,
    onPlayChannel: (LiveChannel) -> Unit,
) {
    if (chip == "tables") {
        TablesPickerPane(sport = sport, onOpenLeague = onOpenLeague)
        return
    }
    if (chip == "networks") {
        NetworksPane()
        return
    }

    val pinNames = pins.map { it.name.lowercase(Locale.US) }.toSet()
    fun matchesPins(event: SportsEvent): Boolean {
        val home = event.homeTeam?.lowercase(Locale.US)
        val away = event.awayTeam?.lowercase(Locale.US)
        return (home != null && home in pinNames) || (away != null && away in pinNames)
    }
    val now = rememberNow()
    val scoped = if (chip == "my") {
        events.filter(::matchesPins)
    } else {
        events
    }
    val live = scoped.filter { it.isLive }
    val upcoming = scoped.filter { !it.isLive && !it.isFinished }
        .sortedBy { it.startEpochMs ?: Long.MAX_VALUE }
    val finished = scoped.filter { it.isFinished }
        .sortedByDescending { it.startEpochMs ?: 0L }
    val weekSource = if (chip == "my") weekEvents.filter(::matchesPins) else weekEvents
    val week = weekSource.filter { !it.isFinished }.sortedBy { it.startEpochMs ?: Long.MAX_VALUE }
    val spotlight = live.firstOrNull() ?: upcoming.firstOrNull() ?: finished.firstOrNull()
    val dayFormat = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())

    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (-1..6).forEach { index ->
                val date = LocalDate.now().plusDays(index.toLong())
                val active = dayIndex == index
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (active) SportsAccent.copy(alpha = 0.18f) else SportsPanel)
                        .then(
                            if (active) {
                                Modifier.border(1.dp, SportsAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onDay(index) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        when (index) {
                            -1 -> "Yesterday"
                            0 -> "Today"
                            1 -> "Tomorrow"
                            else -> date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) Color.White else SportsMuted,
                    )
                    Text(date.format(dayFormat), fontSize = 10.sp, color = SportsMuted)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SportsCatalog.sports.forEach { item ->
                SportsPill("${item.icon}  ${item.label}", item.apiName == sport.apiName) { onSport(item) }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (errors.isNotEmpty()) {
                    Text(
                        errors.first(),
                        fontSize = 11.sp,
                        color = Color(0xFFFF8A9B),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                if (loading && scoped.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SportsAccent, strokeWidth = 2.dp)
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (live.isNotEmpty()) {
                        item { SectionHeader("LIVE NOW", "${live.size}", live = true) }
                        item { EventGrid(live, hideScores, now,
                            onWatch = { ev -> onPlayGame(ev) },
                            onOpen = onOpenEvent,
                        ) }
                    }
                    if (upcoming.isNotEmpty()) {
                        item {
                            SectionHeader(
                                if (dayIndex == 0) {
                                    "COMING UP"
                                } else {
                                    "SCHEDULE · " + LocalDate.now().plusDays(dayIndex.toLong())
                                        .format(dayFormat)
                                        .uppercase(Locale.US)
                                },
                                "${upcoming.size}",
                            )
                        }
                        item {
                            UpcomingGrid(
                                upcoming,
                                hideScores,
                                onOpen = onOpenEvent,
                            )
                        }
                    }
                    if (dayIndex == 0 && week.isNotEmpty()) {
                        item { SectionHeader("THIS WEEK", "${week.size}") }
                        item { EventGrid(week, hideScores, now,
                            onWatch = { ev -> onPlayGame(ev) },
                            onOpen = onOpenEvent,
                        ) }
                    }
                    if (finished.isNotEmpty()) {
                        item { SectionHeader(if (dayIndex == 0) "FINISHED" else "RESULTS", "${finished.size}") }
                        item { EventGrid(finished, hideScores, now,
                            onWatch = { ev -> onPlayGame(ev) },
                            onOpen = onOpenEvent,
                        ) }
                    }
                    if (!loading && scoped.isEmpty()) {
                        item {
                            EmptyPanel(
                                if (chip == "my") {
                                    "No matches for your teams today."
                                } else {
                                    "No ${sport.label.lowercase(Locale.US)} games listed for this day."
                                },
                            )
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 14.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SportsPanel)
                                .clickable(onClick = onChooseLeagues)
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Choose Leagues", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text("›", fontSize = 13.sp, color = SportsDim)
                        }
                    }
                }
            }

            if (videoWindow) {
                Spacer(Modifier.width(18.dp))
                SportsSidePanel(
                    spotlight = spotlight,
                    live = live,
                    channels = channels,
                    programs = programs,
                    hideScores = hideScores,
                    inlineChannel = inlineChannel,
                    hideVideo = hideVideo,
                    onOpenEvent = onOpenEvent,
                    onWatch = onWatch,
                    onPlayChannel = onPlayChannel,
                )
            }
        }
    }
}

@Composable
private fun SportsSidePanel(
    spotlight: SportsEvent?,
    live: List<SportsEvent>,
    channels: List<LiveChannel>,
    programs: Map<String, List<EpgProgram>>,
    hideScores: Boolean,
    inlineChannel: LiveChannel?,
    hideVideo: Boolean,
    onOpenEvent: (String) -> Unit,
    onWatch: (String) -> Unit,
    onPlayChannel: (LiveChannel) -> Unit,
) {
    val now = rememberNow()
    val matched = remember(spotlight?.id, channels, programs, now / 30_000L) {
        spotlight?.let { matchSportsChannels(it, channels, programs, now) }.orEmpty()
    }
    Column(Modifier.width(360.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        if (spotlight != null) {
                            val home = sportsBandColor(
                                sportsTeamColor(
                                    spotlight.homeTeam.orEmpty(),
                                    spotlight.homeColor,
                                    spotlight.homeAltColor,
                                ),
                            )
                            val away = sportsBandColor(
                                sportsTeamColor(
                                    spotlight.awayTeam.orEmpty(),
                                    spotlight.awayColor,
                                    spotlight.awayAltColor,
                                ),
                            )
                            listOf(
                                home.copy(alpha = 0.80f),
                                away.copy(alpha = 0.42f),
                                Color(0xFF0A1520),
                            )
                        } else {
                            listOf(Color(0xFF12314F), Color(0xFF0A1520))
                        },
                    ),
                )
                .border(1.dp, SportsBorder, RoundedCornerShape(14.dp))
                .clickable(enabled = inlineChannel == null && spotlight != null) {
                    spotlight?.let { onOpenEvent(it.id) }
                },
            contentAlignment = Alignment.BottomStart,
        ) {
            if (inlineChannel != null) {
                SportsInlinePlayer(inlineChannel, if (hideVideo) Modifier.size(1.dp) else Modifier.fillMaxSize())
                Row(
                    Modifier.align(Alignment.BottomStart).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SportsBadge("Live", SportsLive)
                    Text(
                        inlineChannel.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                spotlight?.let { event ->
                    val homeColor = sportsTeamColor(event.homeTeam.orEmpty(), event.homeColor, event.homeAltColor)
                    val awayColor = sportsTeamColor(event.awayTeam.orEmpty(), event.awayColor, event.awayAltColor)
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (event.isLive) SportsBadge("Live", SportsLive)
                            Text(event.leagueName.orEmpty(), fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TeamMark(event.homeTeam, event.homeBadge, homeColor, 36.dp)
                            Text(
                                sportsTeamAbbr(event.homeTeam.orEmpty()),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (!hideScores && event.hasScore && (event.isLive || event.isFinished)) {
                                    "${event.homeScore ?: 0} - ${event.awayScore ?: 0}"
                                } else {
                                    "vs"
                                },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                sportsTeamAbbr(event.awayTeam.orEmpty()),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            TeamMark(event.awayTeam, event.awayBadge, awayColor, 36.dp)
                        }
                        Text(formatEventTime(event), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SportsPanel)
                .border(1.dp, SportsBorder, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "LATEST SCORING",
                fontSize = 10.5.sp,
                letterSpacing = 1.2.sp,
                color = SportsMuted,
                fontWeight = FontWeight.ExtraBold,
            )
            val scoring = live.take(4)
            if (scoring.isEmpty()) {
                Text("No live scores right now.", fontSize = 11.5.sp, color = SportsMuted)
            } else {
                scoring.forEach { event ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenEvent(event.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            (event.homeTeam?.take(3) ?: "?").uppercase(Locale.US),
                            fontSize = 10.5.sp,
                            color = SportsDim,
                            modifier = Modifier.width(38.dp),
                        )
                        Text(
                            if (!hideScores && event.hasScore && (event.isLive || event.isFinished)) {
                                "${event.homeScore ?: 0} - ${event.awayScore ?: 0}"
                            } else {
                                "vs"
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            (event.awayTeam?.take(3) ?: "?").uppercase(Locale.US),
                            fontSize = 10.5.sp,
                            color = SportsDim,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(event.status.orEmpty(), fontSize = 10.5.sp, color = SportsMuted, maxLines = 1)
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SportsPanel)
                .border(1.dp, SportsBorder, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "WATCHING ON",
                fontSize = 10.5.sp,
                letterSpacing = 1.2.sp,
                color = SportsMuted,
                fontWeight = FontWeight.ExtraBold,
            )
            if (matched.isEmpty()) {
                Text(
                    if (channels.isEmpty()) {
                        "Add a playlist in Live TV settings to watch from here."
                    } else {
                        "No matching channel in your Live TV playlists."
                    },
                    fontSize = 11.5.sp,
                    color = SportsMuted,
                )
            } else {
                matched.forEach { channel ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPlayChannel(channel) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SportsImage(channel.logo, channel.name, 26.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                channel.name,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                channel.group.ifBlank { "Live TV" },
                                fontSize = 9.sp,
                                color = SportsDim,
                                maxLines = 1,
                            )
                        }
                        if (spotlight?.isLive == true) SportsBadge("Live", SportsLive)
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsInlinePlayer(channel: LiveChannel, modifier: Modifier = Modifier) {
    val platformDensity = LocalNuvioPlatformDensity.current
    val playerSettings by PlayerSettingsRepository.uiState.collectAsState()
    val host = remember(channel.key) { NativePlayerHost() }
    val controller = remember(host) { NativePlayerController(host) }

    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    LaunchedEffect(controller, channel.url) {
        controller.attach(
            sourceUrl = channel.url,
            sourceHeaders = channel.headers,
            sourceAudioUrl = null,
            playWhenReady = true,
            initialPositionMs = 0L,
            decoderPriority = playerSettings.decoderPriority,
            nvidiaRtxSuperResolutionEnabled = playerSettings.nvidiaRtxSuperResolutionEnabled,
            onError = {},
        )
    }

    CompositionLocalProvider(LocalDensity provides platformDensity) {
        // Covered by a menu or dialog: keep the video out of the way (see NativeVideoCover).
        androidx.compose.foundation.layout.Box(modifier.background(Color.Black)) {
            SwingPanel(
                factory = { host },
                modifier = if (com.nuvio.app.core.ui.NativeVideoCover.active) {
                    Modifier.align(androidx.compose.ui.Alignment.BottomEnd).requiredSize(1.dp)
                } else {
                    Modifier.fillMaxSize()
                },
                background = Color.Black,
            )
        }
    }
}

@Composable
private fun SportsSearchBar(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF08080C)).padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.width(480.dp),
            singleLine = true,
            placeholder = {
                Text("Search teams — NBA, Premier League, Lakers…", fontSize = 12.sp, color = SportsDim)
            },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
            shape = RoundedCornerShape(10.dp),
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = SportsMuted, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear team search", tint = SportsMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
        )
        Text(
            "Find any team in the catalog and the extra NuvioOne leagues",
            fontSize = 10.5.sp,
            color = SportsDim,
        )
    }
}

@Composable
private fun TeamSearchResults(
    query: String,
    results: List<SportsTeamSearchResult>,
    loading: Boolean,
    onOpen: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TEAMS", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text("${results.size}", fontSize = 10.5.sp, color = SportsDim)
            Spacer(Modifier.weight(1f))
            Text("“$query”", fontSize = 10.5.sp, color = SportsDim)
        }

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SportsAccent, strokeWidth = 2.dp)
                Text("Searching teams…", fontSize = 11.5.sp, color = SportsMuted)
            }
        }

        if (!loading && results.isEmpty()) {
            Text("No teams match “$query”.", fontSize = 11.5.sp, color = SportsMuted)
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results, key = { it.team.id }) { result ->
                    val color = sportsTeamColor(result.team.name, result.team.color, result.team.altColor)
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SportsPanel)
                            .border(1.dp, SportsBorder, RoundedCornerShape(10.dp))
                            .clickable { onOpen(result.team.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TeamMark(result.team.name, result.team.badgeUrl, color, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                result.team.name,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(result.leagueName, result.sport).joinToString(" · "),
                                fontSize = 9.5.sp,
                                color = SportsDim,
                                maxLines = 1,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = SportsDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TablesPickerPane(sport: SportCategory, onOpenLeague: (CuratedLeague) -> Unit) {
    var items by remember(sport.apiName) { mutableStateOf<List<CuratedLeague>>(emptyList()) }
    var loading by remember(sport.apiName) { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(sport.apiName) {
        items = SportsRepository.allLeagues(sport.apiName)
        loading = false
    }
    val filtered = remember(items, query) { filterSportsLeagues(items, query) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Tables", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Pick a league to open its standings", fontSize = 11.5.sp, color = SportsMuted)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(0.55f),
            singleLine = true,
            placeholder = { Text("Search leagues", fontSize = 12.sp, color = SportsDim) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, color = Color.White),
            shape = RoundedCornerShape(10.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (loading && items.isEmpty()) {
            Text("Loading leagues…", fontSize = 11.5.sp, color = SportsMuted)
        }
        if (!loading && filtered.isEmpty()) {
            Text("No leagues match “$query”.", fontSize = 11.5.sp, color = SportsMuted)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(230.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filtered, key = { it.id }) { item ->
                LeagueCard(item) { onOpenLeague(item) }
            }
        }
    }
}

@Composable
private fun NetworksPane() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("League Networks", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Each league's own channel appears here once channel mapping is configured.",
            fontSize = 12.sp,
            color = SportsMuted,
        )
        EmptyPanel("No league networks yet. Add a playlist and map league channels in Live TV settings.")
    }
}

@Composable
private fun SectionHeader(title: String, count: String, live: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    ) {
        if (live) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(SportsLive))
        }
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.7.sp,
        )
        Text(count, fontSize = 11.sp, color = SportsMuted)
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Box(
        Modifier.fillMaxWidth().height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SportsPanel)
            .border(1.dp, SportsBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.sp, color = SportsMuted)
    }
}

@Composable
private fun EventGrid(
    events: List<SportsEvent>,
    hideScores: Boolean,
    now: Long,
    onWatch: (SportsEvent) -> Unit,
    onOpen: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 1300.dp) 2 else 3
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            events.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { event ->
                        GameCard(
                            event = event,
                            hideScores = hideScores,
                            now = now,
                            modifier = Modifier.weight(1f),
                            onWatch = { onWatch(event) },
                            onClick = { onOpen(event.id) },
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A game the way the TV app draws it: league mark, a colour band per team, score and status. */
@Composable
private fun GameCard(
    event: SportsEvent,
    hideScores: Boolean,
    now: Long,
    modifier: Modifier = Modifier,
    onWatch: () -> Unit,
    onClick: () -> Unit,
) {
    val homeColor = sportsTeamColor(event.homeTeam.orEmpty(), event.homeColor, event.homeAltColor)
    val awayColor = sportsTeamColor(event.awayTeam.orEmpty(), event.awayColor, event.awayAltColor)
    val showScore = !hideScores && (event.isLive || event.isFinished)
    Column(
        modifier.height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SportsPanel)
            .border(
                1.dp,
                if (event.isLive) SportsLive.copy(alpha = 0.55f) else SportsBorder,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().height(3.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(homeColor))
            Box(Modifier.weight(1f).fillMaxHeight().background(awayColor))
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            LeagueMark(event, Modifier.width(54.dp).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TeamBand(
                    name = event.homeTeam,
                    badge = event.homeBadge,
                    color = homeColor,
                    score = event.homeScore,
                    showScore = showScore,
                    modifier = Modifier.weight(1f),
                )
                TeamBand(
                    name = event.awayTeam,
                    badge = event.awayBadge,
                    color = awayColor,
                    score = event.awayScore,
                    showScore = showScore,
                    modifier = Modifier.weight(1f),
                )
            }
            GameStatus(event, hideScores, now, onWatch, Modifier.width(104.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun LeagueMark(event: SportsEvent, modifier: Modifier = Modifier) {
    val name = event.leagueName ?: event.sport.orEmpty()
    val color = sportsLeagueColor(name)
    Box(
        modifier.background(Color.White.copy(alpha = 0.035f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    leagueMark(name),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

private fun leagueMark(name: String): String {
    val words = name.split(' ', '/', '-', '_').filter { it.isNotBlank() }
    if (words.isEmpty()) return "?"
    val skip = setOf("of", "the", "and", "de", "la", "le")
    val useful = words.filterNot { it.lowercase(Locale.US) in skip }
    return when {
        useful.isEmpty() -> "?"
        useful.size == 1 -> useful[0].take(4).uppercase(Locale.US)
        else -> useful.take(3).joinToString("") { it.first().uppercase(Locale.US) }
    }
}

@Composable
private fun TeamBand(
    name: String?,
    badge: String?,
    color: Color,
    score: Int?,
    showScore: Boolean,
    modifier: Modifier = Modifier,
) {
    val band = sportsBandColor(color)
    Row(
        modifier.fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    0f to band.copy(alpha = 0.92f),
                    0.55f to band.copy(alpha = 0.34f),
                    1f to band.copy(alpha = 0.05f),
                ),
            )
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TeamMark(name, badge, color, 28.dp)
        Text(
            name ?: "TBD",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showScore && score != null) {
            Text(
                score.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.padding(end = 2.dp),
            )
        }
    }
}

@Composable
private fun TeamMark(name: String?, badge: String?, color: Color, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (badge.isNullOrBlank()) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(size * 0.24f)).background(color)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(size * 0.24f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    sportsTeamAbbr(name.orEmpty()),
                    fontSize = (size.value * 0.28f).sp,
                    fontWeight = FontWeight.Black,
                    color = if (color.luminance() > 0.62f) Color(0xFF07101C) else Color.White,
                    maxLines = 1,
                )
            }
        } else {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = badge,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(1.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun GameStatus(
    event: SportsEvent,
    hideScores: Boolean,
    now: Long,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 7.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        ) {
            when {
                event.isLive -> SportsBadge("\u25CF LIVE", SportsLive)
                event.isFinished -> SportsBadge("FINAL", Color(0x22FFFFFF), SportsMuted)
                else -> SportsBadge(eventClock(event), Color(0x22FFFFFF), Color.White)
            }
            if (event.isLive) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(SportsAccent).clickable(onClick = onWatch),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Watch live",
                        tint = Color(0xFF07101C),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Text(
            statusLine(event, hideScores),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val progress = eventProgress(event, now)
        if (progress > 0f) {
            Box(
                Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(SportsLive))
            }
        }
    }
}

private fun eventClock(event: SportsEvent): String {
    val millis = event.startEpochMs
    if (millis != null) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
    }
    event.time?.take(5)?.takeIf { it.isNotBlank() }?.let { return it }
    rawInstant(event.date)?.let { instant ->
        return instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
    }
    return "TBD"
}

private fun shortEventDate(event: SportsEvent): String {
    val millis = event.startEpochMs
    if (millis != null) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
    rawInstant(event.date)?.let { instant ->
        return instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
    val raw = event.date.orEmpty()
    return if ('T' in raw) raw.substringBefore('T') else raw
}

/** Feeds write `2026-11-16T15:00Z` (no seconds); Instant.parse rejects it. */
private fun rawInstant(value: String?): Instant? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
}

private fun statusLine(event: SportsEvent, hideScores: Boolean): String {
    val raw = event.status.orEmpty()
    return when {
        event.isLive && !hideScores && raw.isNotBlank() ->
            raw.removePrefix("LIVE").removePrefix("\u00B7").trim().ifBlank { "Live" }
        event.isFinished -> shortEventDate(event).ifBlank { "Full time" }
        else -> shortEventDate(event)
    }
}

private fun eventProgress(event: SportsEvent, now: Long): Float {
    if (!event.isLive) return 0f
    val start = event.startEpochMs ?: return 0.55f
    return ((now - start) / 7_200_000f).toFloat().coerceIn(0.04f, 0.96f)
}

@Composable
private fun UpcomingGrid(
    events: List<SportsEvent>,
    hideScores: Boolean,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        events.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { event ->
                    UpcomingRow(
                        event = event,
                        hideScores = hideScores,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen(event.id) },
                    )
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun UpcomingRow(
    event: SportsEvent,
    hideScores: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val homeColor = sportsTeamColor(event.homeTeam.orEmpty(), event.homeColor, event.homeAltColor)
    val awayColor = sportsTeamColor(event.awayTeam.orEmpty(), event.awayColor, event.awayAltColor)
    Row(
        modifier.height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SportsPanel)
            .background(
                Brush.horizontalGradient(
                    0f to sportsBandColor(homeColor).copy(alpha = 0.52f),
                    0.47f to sportsBandColor(homeColor).copy(alpha = 0.10f),
                    0.53f to sportsBandColor(awayColor).copy(alpha = 0.10f),
                    1f to sportsBandColor(awayColor).copy(alpha = 0.52f),
                ),
            )
            .border(1.dp, SportsBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (event.homeTeam == null && event.awayTeam == null) {
            Column(Modifier.width(50.dp)) {
                Text(eventClock(event), fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(
                    shortEventDate(event),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                )
            }
            Text(
                event.title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(50.dp)) {
                    Text(eventClock(event), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(
                        shortEventDate(event),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1,
                    )
                }
                UpcomingTeam(event.homeTeam, event.homeBadge, homeColor, Modifier.weight(1f))
            }
            Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) {
                Text(
                    "V",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                UpcomingTeam(event.awayTeam, event.awayBadge, awayColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UpcomingTeam(name: String?, badge: String?, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TeamMark(name, badge, color, 26.dp)
        Text(
            name ?: "TBD",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LeagueCard(league: CuratedLeague, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SportsPanel)
            .border(1.dp, SportsBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SportsImage(league.badgeUrl, league.shortName, 46.dp)
        Column(Modifier.weight(1f)) {
            Text(
                league.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(league.country, fontSize = 11.5.sp, color = SportsMuted)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SportsDim,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PinnedTeamCard(team: PinnedTeam, onClick: () -> Unit) {
    Column(
        Modifier.width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SportsPanel)
            .border(1.dp, SportsBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SportsImage(team.badgeUrl, team.name, 54.dp)
        Text(
            team.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ------------------------------------------------------------------- league */

@Composable
private fun LeaguePane(
    league: CuratedLeague?,
    page: LeaguePageData?,
    loading: Boolean,
    tab: String,
    teams: List<SportsTeamInfo>,
    teamsLoading: Boolean,
    hideScores: Boolean,
    onTab: (String) -> Unit,
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenTeam: (String) -> Unit,
) {
    if (league == null) return
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SportsMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            SportsImage(page?.league?.badgeUrl ?: league.badgeUrl, league.shortName, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    page?.league?.name ?: league.name,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(league.country, page?.league?.currentSeason).joinToString(" · "),
                    fontSize = 11.sp,
                    color = SportsDim,
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SportsAccent, strokeWidth = 2.dp)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SportsTab("Matches", tab == "matches") { onTab("matches") }
            SportsTab("Table", tab == "table") { onTab("table") }
            SportsTab("Teams", tab == "teams") { onTab("teams") }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = SportsBorder)

        when (tab) {
            "table" -> TablePane(page?.table.orEmpty(), loading)
            "teams" -> TeamsPane(teams, teamsLoading) { id ->
                val current = league
                if (current != null && current.id.startsWith("tsdb/")) {
                    onOpenTeam("tsdb/" + current.id.removePrefix("tsdb/") + "/" + id)
                } else {
                    onOpenTeam(id)
                }
            }
            else -> MatchesPane(page, loading, hideScores, onOpenEvent)
        }
    }
}

@Composable
private fun SportsTab(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (active) SportsAccent else SportsPanel)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) Color(0xFF07101C) else SportsMuted,
        )
    }
}

@Composable
private fun MatchesPane(
    page: LeaguePageData?,
    loading: Boolean,
    hideScores: Boolean,
    onOpenEvent: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (loading && page == null) {
            item {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SportsAccent, strokeWidth = 2.dp)
                }
            }
        }
        val upcoming = page?.upcoming.orEmpty()
        val results = page?.results.orEmpty()
        if (upcoming.isNotEmpty()) {
            item { Text("Upcoming", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            items(upcoming, key = { "up-${it.id}" }) { event ->
                MatchRow(event, hideScores) { onOpenEvent(event.id) }
            }
        }
        if (results.isNotEmpty()) {
            item {
                Text(
                    "Results",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            items(results, key = { "res-${it.id}" }) { event ->
                MatchRow(event, hideScores) { onOpenEvent(event.id) }
            }
        }
        if (!loading && upcoming.isEmpty() && results.isEmpty()) {
            item { EmptyPanel("No fixtures listed for this league right now.") }
        }
        page?.errors?.firstOrNull()?.let { message ->
            item { Text(message, fontSize = 11.sp, color = Color(0xFFFF8A9B)) }
        }
    }
}

@Composable
private fun MatchRow(event: SportsEvent, hideScores: Boolean, onClick: () -> Unit) {
    val homeColor = sportsTeamColor(event.homeTeam.orEmpty(), event.homeColor, event.homeAltColor)
    val awayColor = sportsTeamColor(event.awayTeam.orEmpty(), event.awayColor, event.awayAltColor)
    Row(
        Modifier.fillMaxWidth().height(60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SportsPanel)
            .border(1.dp, if (event.isLive) SportsLive.copy(alpha = 0.4f) else SportsBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(4.dp).fillMaxHeight()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(homeColor))
            Box(Modifier.fillMaxWidth().weight(1f).background(awayColor))
        }
        Column(Modifier.width(86.dp).padding(start = 10.dp)) {
            Text(
                if (event.isLive) "LIVE" else if (event.isFinished) "FT" else eventClock(event),
                fontSize = 10.5.sp,
                fontWeight = if (event.isLive) FontWeight.Bold else FontWeight.SemiBold,
                color = if (event.isLive) SportsLive else Color.White,
            )
            Text(
                shortEventDate(event),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
            )
        }
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            TeamMark(event.homeTeam, event.homeBadge, homeColor, 22.dp)
            Text(
                event.homeTeam ?: "TBD",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!hideScores && (event.isLive || event.isFinished)) {
                Text("${event.homeScore ?: 0}", fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
        }
        Text("v", fontSize = 10.5.sp, color = SportsMuted, modifier = Modifier.padding(horizontal = 8.dp))
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (!hideScores && (event.isLive || event.isFinished)) {
                Text("${event.awayScore ?: 0}", fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
            Text(
                event.awayTeam ?: "TBD",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TeamMark(event.awayTeam, event.awayBadge, awayColor, 22.dp)
        }
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun TablePane(rows: List<SportsTableRow>, loading: Boolean) {
    if (rows.isEmpty() && loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SportsAccent, strokeWidth = 2.dp)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { TableHeader() }
        items(rows, key = { "${it.position}-${it.team}" }) { row -> TableRow(row) }
        if (rows.isEmpty()) {
            item { EmptyPanel("Standings are not available for this league.") }
        }
    }
}

@Composable
private fun TeamsPane(teams: List<SportsTeamInfo>, loading: Boolean, onOpenTeam: (String) -> Unit) {
    if (loading && teams.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SportsAccent, strokeWidth = 2.dp)
        }
        return
    }
    if (teams.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(20.dp)) {
            EmptyPanel("Team list is not available for this league.")
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(teams, key = { it.id }) { team ->
            val color = sportsTeamColor(team.name, team.color, team.altColor)
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SportsPanel)
                    .background(
                        Brush.horizontalGradient(
                            0f to color.copy(alpha = 0.34f),
                            0.55f to color.copy(alpha = 0.08f),
                            1f to Color.Transparent,
                        ),
                    )
                    .border(1.dp, SportsBorder, RoundedCornerShape(10.dp))
                    .clickable { onOpenTeam(team.id) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TeamMark(team.name, team.badgeUrl, color, 40.dp)
                Text(
                    team.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("#", modifier = Modifier.width(26.dp), fontSize = 10.5.sp, color = SportsMuted)
        Text("Team", modifier = Modifier.weight(1f), fontSize = 10.5.sp, color = SportsMuted)
        Text("P", modifier = Modifier.width(28.dp), fontSize = 10.5.sp, color = SportsMuted)
        Text("W", modifier = Modifier.width(28.dp), fontSize = 10.5.sp, color = SportsMuted)
        Text("D", modifier = Modifier.width(28.dp), fontSize = 10.5.sp, color = SportsMuted)
        Text("L", modifier = Modifier.width(28.dp), fontSize = 10.5.sp, color = SportsMuted)
        Text("GD", modifier = Modifier.width(34.dp), fontSize = 10.5.sp, color = SportsMuted)
        Text("Pts", modifier = Modifier.width(34.dp), fontSize = 10.5.sp, color = SportsMuted)
    }
}

@Composable
private fun TableRow(row: SportsTableRow) {
    val color = sportsTeamColor(row.team, row.color, row.altColor)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SportsPanel)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text("${row.position}", modifier = Modifier.width(26.dp), fontSize = 11.sp, color = SportsDim)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TeamMark(row.team, row.badgeUrl, color, 24.dp)
            Text(row.team, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(row.played.valueOrDash(), modifier = Modifier.width(28.dp), fontSize = 11.sp)
        Text(row.wins.valueOrDash(), modifier = Modifier.width(28.dp), fontSize = 11.sp)
        Text(row.draws.valueOrDash(), modifier = Modifier.width(28.dp), fontSize = 11.sp)
        Text(row.losses.valueOrDash(), modifier = Modifier.width(28.dp), fontSize = 11.sp)
        Text(row.goalDifference.valueOrDash(), modifier = Modifier.width(34.dp), fontSize = 11.sp)
        Text(
            row.points.valueOrDash(),
            modifier = Modifier.width(34.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/* --------------------------------------------------------------------- team */

@Composable
private fun TeamPane(
    teamId: String?,
    data: TeamPageData?,
    loading: Boolean,
    pinned: Boolean,
    hideScores: Boolean,
    onBack: () -> Unit,
    onTogglePin: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onWatch: (String) -> Unit,
) {
    val teamColor = sportsTeamColor(
        data?.team?.name ?: teamId.orEmpty(),
        data?.team?.color,
        data?.team?.altColor,
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SportsPanel)
                .background(
                    Brush.horizontalGradient(
                        0f to teamColor.copy(alpha = 0.55f),
                        0.6f to teamColor.copy(alpha = 0.14f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SportsMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            TeamMark(data?.team?.name, data?.team?.badgeUrl, teamColor, 64.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    data?.team?.name ?: "Team",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        data?.team?.leagueName,
                        data?.team?.stadium,
                        data?.team?.country,
                    ).joinToString(" · "),
                    fontSize = 11.sp,
                    color = SportsDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SportsAccent, strokeWidth = 2.dp)
            }
            data?.team?.name?.let { name ->
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(SportsAccent)
                        .clickable { onWatch(name) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF07101C),
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        "Watch",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF07101C),
                    )
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (pinned) SportsAccent.copy(alpha = 0.18f) else SportsPanel)
                    .border(1.dp, if (pinned) SportsAccent.copy(alpha = 0.5f) else SportsBorder, RoundedCornerShape(999.dp))
                    .clickable(onClick = onTogglePin)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (pinned) SportsAccent else SportsMuted,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    if (pinned) "Following" else "Follow",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (pinned) Color.White else SportsMuted,
                )
            }
        }

        data?.team?.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 11.5.sp, color = SportsMuted, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }

        if (data != null) {
            if (data.next.isNotEmpty()) {
                SectionHeader("Next matches", "${data.next.size}")
                data.next.forEach { event -> MatchRow(event, hideScores) { onOpenEvent(event.id) } }
            }
            if (data.last.isNotEmpty()) {
                SectionHeader("Recent results", "${data.last.size}")
                data.last.forEach { event -> MatchRow(event, hideScores) { onOpenEvent(event.id) } }
            }
            if (!loading && data.next.isEmpty() && data.last.isEmpty()) {
                EmptyPanel("No fixtures listed for this team right now.")
            }
        } else if (!loading) {
            EmptyPanel("Team details are not available.")
        }
    }
}

/* -------------------------------------------------------------------- event */

@Composable
private fun EventPane(
    data: EventPageData?,
    loading: Boolean,
    hideScores: Boolean,
    inlineChannel: LiveChannel?,
    hideVideo: Boolean,
    onPlayGame: (SportsEvent) -> Unit,
    onBack: () -> Unit,
    onOpenTeam: (String) -> Unit,
) {
    val event = data?.event
    val homeColor = sportsTeamColor(event?.homeTeam.orEmpty(), event?.homeColor, event?.homeAltColor)
    val awayColor = sportsTeamColor(event?.awayTeam.orEmpty(), event?.awayColor, event?.awayAltColor)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 1050.dp
        if (wide) {
            // Big watch card on top, the vs/score card under it, then team stats
            // (and quarters) on the left beside scoring and top performers.
            val contentWidth = maxWidth - 48.dp
            val videoHeight = minOf(contentWidth * 9f / 16f, maxHeight * 0.58f)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EventTopRow(event, loading, onBack) { event?.let(onPlayGame) }
                EventVideoPanel(
                    event,
                    inlineChannel,
                    hideVideo,
                    onPlayGame,
                    Modifier.fillMaxWidth().height(videoHeight),
                )
                EventScoreCard(event, hideScores, homeColor, awayColor, onOpenTeam)
                val hasLeft = data != null && (data.stats.isNotEmpty() || data.quarters.isNotEmpty())
                val hasRight = data != null && (data.scoring.isNotEmpty() || data.leaders.isNotEmpty())
                if (data != null && (hasLeft || hasRight)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (hasLeft) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (data.stats.isNotEmpty()) {
                                    SectionHeader("TEAM STATS", "${data.stats.size}")
                                    TeamStatsPanel(event, data.stats)
                                }
                                if (data.quarters.isNotEmpty()) {
                                    SectionHeader("BY QUARTER", "${data.quarters.size}")
                                    QuarterTable(event, data.quarters)
                                }
                            }
                        }
                        if (hasRight) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (data.scoring.isNotEmpty()) {
                                    SectionHeader("LATEST SCORING", "${data.scoring.size}")
                                    ScoringPanel(data.scoring.takeLast(4).reversed())
                                }
                                if (data.leaders.isNotEmpty()) {
                                    SectionHeader("TOP PERFORMERS", "${data.leaders.size}")
                                    LeadersPanel(data.leaders.take(4))
                                }
                            }
                        } else {
                            // Keep stats in the left half; full-width rows put values
                            // too far from their labels to read.
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                event?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 11.5.sp, color = SportsMuted, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                data?.errors?.firstOrNull()?.let {
                    Text(it, fontSize = 11.sp, color = Color(0xFFFF8A9B))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EventTopRow(event, loading, onBack) { event?.let(onPlayGame) }
                EventVideoPanel(event, inlineChannel, hideVideo, onPlayGame)
                EventScoreCard(event, hideScores, homeColor, awayColor, onOpenTeam)
                if (data != null) {
                    if (data.quarters.isNotEmpty()) {
                        SectionHeader("BY QUARTER", "${data.quarters.size}")
                        QuarterTable(event, data.quarters)
                    }
                    if (data.stats.isNotEmpty()) {
                        SectionHeader("TEAM STATS", "${data.stats.size}")
                        TeamStatsPanel(event, data.stats)
                    }
                    if (data.scoring.isNotEmpty()) {
                        SectionHeader("LATEST SCORING", "${data.scoring.size}")
                        ScoringPanel(data.scoring.takeLast(4).reversed())
                    }
                    if (data.leaders.isNotEmpty()) {
                        SectionHeader("TOP PERFORMERS", "${data.leaders.size}")
                        LeadersPanel(data.leaders.take(4))
                    }
                }
                event?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 11.5.sp, color = SportsMuted, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                data?.errors?.firstOrNull()?.let {
                    Text(it, fontSize = 11.sp, color = Color(0xFFFF8A9B))
                }
            }
        }
    }
}

@Composable
private fun EventTopRow(
    event: SportsEvent?,
    loading: Boolean,
    onBack: () -> Unit,
    onWatch: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = SportsMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                event?.leagueName ?: "Match",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            event?.let {
                Text(formatEventTime(it), fontSize = 11.5.sp, color = SportsMuted)
            }
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SportsAccent, strokeWidth = 2.dp)
        }
        event?.let {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(SportsAccent)
                    .clickable(onClick = onWatch)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF07101C),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    if (it.isLive) "Watch live" else "Watch",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF07101C),
                )
            }
        }
    }
}

@Composable
private fun EventScoreCard(
    event: SportsEvent?,
    hideScores: Boolean,
    homeColor: Color = SportsMuted,
    awayColor: Color = SportsMuted,
    onOpenTeam: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SportsPanel)
            .background(
                Brush.horizontalGradient(
                    0f to sportsBandColor(homeColor).copy(alpha = 0.50f),
                    0.5f to Color.Transparent,
                    1f to sportsBandColor(awayColor).copy(alpha = 0.50f),
                ),
            )
            .border(1.dp, SportsBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (event?.isLive == true) SportsBadge("Live", SportsLive)
            else if (event?.isFinished == true) SportsBadge("Full time", Color(0x22FFFFFF), SportsMuted)
            event?.status?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 11.sp, color = SportsMuted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreSide(event?.homeTeam, event?.homeBadge, Modifier.weight(1f)) {
                event?.homeTeamId?.let(onOpenTeam)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!hideScores && (event?.hasScore == true || event?.isLive == true) &&
                    (event?.isLive == true || event?.isFinished == true)
                ) {
                    Text(
                        "${event?.homeScore ?: 0} - ${event?.awayScore ?: 0}",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                } else if (event?.isLive == true) {
                    Text("Live", fontSize = 20.sp, color = SportsMuted)
                } else {
                    Text("vs", fontSize = 20.sp, color = SportsMuted)
                }
                event?.venue?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 11.sp, color = SportsMuted)
                }
            }
            ScoreSide(event?.awayTeam, event?.awayBadge, Modifier.weight(1f)) {
                event?.awayTeamId?.let(onOpenTeam)
            }
        }
    }
}

@Composable
private fun EventVideoPanel(
    event: SportsEvent?,
    channel: LiveChannel?,
    hideVideo: Boolean,
    onPlayGame: (SportsEvent) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
) {
    val homeColor = sportsTeamColor(event?.homeTeam.orEmpty(), event?.homeColor, event?.homeAltColor)
    val awayColor = sportsTeamColor(event?.awayTeam.orEmpty(), event?.awayColor, event?.awayAltColor)
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .background(
                Brush.linearGradient(
                    listOf(
                        sportsBandColor(homeColor).copy(alpha = 0.75f),
                        sportsBandColor(awayColor).copy(alpha = 0.38f),
                        Color(0xFF0A1520),
                    ),
                ),
            )
            .border(1.dp, SportsBorder, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (channel != null) {
            SportsInlinePlayer(channel, if (hideVideo) Modifier.size(1.dp) else Modifier.fillMaxSize())
            Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SportsBadge("Live", SportsLive)
                Text(
                    channel.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                Modifier.align(Alignment.BottomStart).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (event?.isLive == true) SportsBadge("Live", SportsLive)
                Text(event?.leagueName.orEmpty(), fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.85f))
                Text(
                    listOfNotNull(event?.homeTeam, event?.awayTeam).joinToString("  v  ").ifBlank { event?.title.orEmpty() },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                event?.let {
                    Text(formatEventTime(it), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
                }
            }
            if (event?.isLive == true) {
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(SportsAccent)
                        .clickable { onPlayGame(event) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF07101C), modifier = Modifier.size(15.dp))
                    Text("Watch live", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF07101C))
                }
            }
        }
    }
}

@Composable
private fun TeamStatsPanel(event: SportsEvent?, stats: List<SportsTeamStat>) {
    val homeAbbr = sportsTeamAbbr(event?.homeTeam.orEmpty())
    val awayAbbr = sportsTeamAbbr(event?.awayTeam.orEmpty())
    val homeColor = sportsTeamColor(event?.homeTeam.orEmpty(), event?.homeColor, event?.homeAltColor)
    val awayColor = sportsTeamColor(event?.awayTeam.orEmpty(), event?.awayColor, event?.awayAltColor)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SportsPanel)
            .background(
                Brush.horizontalGradient(
                    0f to sportsBandColor(homeColor).copy(alpha = 0.34f),
                    0.5f to Color.Transparent,
                    1f to sportsBandColor(awayColor).copy(alpha = 0.34f),
                ),
            )
            .border(1.dp, SportsBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TeamAbbrChip(homeAbbr, homeColor) }
            Text("TEAM STATS", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = SportsDim)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { TeamAbbrChip(awayAbbr, awayColor) }
        }
        stats.forEach { stat ->
            val homeValue = stat.home.toDoubleOrNull()
            val awayValue = stat.away.toDoubleOrNull()
            val homeLead = homeValue != null && awayValue != null && homeValue > awayValue
            val awayLead = homeValue != null && awayValue != null && awayValue > homeValue
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stat.home,
                    fontSize = 12.sp,
                    fontWeight = if (homeLead) FontWeight.Bold else FontWeight.Normal,
                    color = if (homeLead) Color.White else SportsMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stat.label,
                    fontSize = 10.5.sp,
                    color = SportsDim,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(160.dp),
                )
                Text(
                    stat.away,
                    fontSize = 12.sp,
                    fontWeight = if (awayLead) FontWeight.Bold else FontWeight.Normal,
                    color = if (awayLead) Color.White else SportsMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LeadersPanel(leaders: List<SportsLeader>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        leaders.forEach { leader ->
            val color = sportsTeamColor(leader.team)
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SportsPanel)
                    .border(1.dp, SportsBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(leader.team.takeIf { it.isNotBlank() }, leader.category).joinToString(" · "),
                        fontSize = 9.5.sp,
                        color = SportsDim,
                        maxLines = 1,
                    )
                    Text(leader.player, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Text(
                    leader.statLine,
                    fontSize = 10.5.sp,
                    color = SportsMuted,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    modifier = Modifier.width(180.dp),
                )
            }
        }
    }
}

@Composable
private fun ScoringPanel(plays: List<SportsScoringPlay>) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SportsPanel)
            .border(1.dp, SportsBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        plays.forEach { play ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(play.period.takeIf { it.isNotBlank() }, play.clock.takeIf { it.isNotBlank() }).joinToString(" "),
                    fontSize = 9.5.sp,
                    color = SportsDim,
                    modifier = Modifier.width(58.dp),
                )
                Text(
                    play.team,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(38.dp),
                )
                Text(
                    play.description,
                    fontSize = 11.sp,
                    color = SportsMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    play.score,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun QuarterTable(event: SportsEvent?, quarters: List<SportsQuarterScore>) {
    val homeAbbr = sportsTeamAbbr(event?.homeTeam.orEmpty())
    val awayAbbr = sportsTeamAbbr(event?.awayTeam.orEmpty())
    val homeColor = sportsTeamColor(event?.homeTeam.orEmpty(), event?.homeColor, event?.homeAltColor)
    val awayColor = sportsTeamColor(event?.awayTeam.orEmpty(), event?.awayColor, event?.awayAltColor)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SportsPanel)
            .background(
                Brush.horizontalGradient(
                    0f to sportsBandColor(homeColor).copy(alpha = 0.34f),
                    0.5f to Color.Transparent,
                    1f to sportsBandColor(awayColor).copy(alpha = 0.34f),
                ),
            )
            .border(1.dp, SportsBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("", modifier = Modifier.width(70.dp))
            quarters.forEach { quarter ->
                Text(
                    quarter.label,
                    fontSize = 9.5.sp,
                    color = SportsDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamAbbrChip(homeAbbr, homeColor, Modifier.width(70.dp))
            quarters.forEach { quarter ->
                Text(
                    quarter.home,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamAbbrChip(awayAbbr, awayColor, Modifier.width(70.dp))
            quarters.forEach { quarter ->
                Text(
                    quarter.away,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Team abbreviation on a solid team-coloured chip. Plain coloured text (e.g. a
 * navy "HOU") disappears on the dark panels; the chip keeps the team colour and
 * picks dark or white text by contrast, so any team stays readable.
 */
@Composable
private fun TeamAbbrChip(abbr: String, color: Color, modifier: Modifier = Modifier) {
    val fill = if (color.luminance() < 0.035f) lerp(color, Color.White, 0.22f) else color
    Box(modifier) {
        Text(
            abbr,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.6.sp,
            color = if (fill.luminance() > 0.45f) Color(0xFF07101C) else Color.White,
            maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(fill)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ScoreSide(name: String?, badge: String?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(enabled = name != null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SportsImage(badge, name.orEmpty(), 54.dp)
        Text(
            name ?: "TBD",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ListingsPane(
    date: LocalDate,
    onDate: (LocalDate) -> Unit,
    listings: List<SportsTvListing>,
    loading: Boolean,
    onOpenEvent: (String) -> Unit,
) {
    val days = (0..6).map { LocalDate.now().plusDays(it.toLong()) }
    val dayFormat = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TV Guide", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                Text(
                    "${listings.size} listings · ${date.format(dayFormat)}",
                    fontSize = 11.5.sp,
                    color = SportsDim,
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SportsAccent, strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            days.forEach { day ->
                val active = day == date
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (active) SportsAccent else SportsPanel)
                        .clickable { onDate(day) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (day == LocalDate.now()) "Today" else day.format(dayFormat),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) Color(0xFF07101C) else SportsMuted,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // EPG listings have no unique id (eventId is usually null), so the
            // position keeps keys unique even when two rows look identical.
            itemsIndexed(
                listings,
                key = { index, it -> "$index-${it.channel}-${it.timestamp ?: it.time}" },
            ) { _, listing ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SportsPanel)
                        .border(1.dp, SportsBorder, RoundedCornerShape(10.dp))
                        .clickable(enabled = listing.eventId != null) {
                            listing.eventId?.let(onOpenEvent)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.width(76.dp)) {
                        Text(
                            listing.time?.take(5) ?: "--:--",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            listing.country.orEmpty(),
                            fontSize = 9.5.sp,
                            color = SportsDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            listing.event,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listing.sport.orEmpty(),
                            fontSize = 10.sp,
                            color = SportsDim,
                        )
                    }
                    SportsImage(listing.logoUrl, listing.channel, 26.dp)
                    Text(
                        listing.channel,
                        fontSize = 11.5.sp,
                        color = SportsMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(150.dp),
                    )
                }
            }
            if (!loading && listings.isEmpty()) {
                item { EmptyPanel("No TV listings for this day.") }
            }
        }
    }
}

@Composable
private fun SportsSettingsDialog(
    hideScores: Boolean,
    onHideScores: (Boolean) -> Unit,
    autoRefreshMinutes: Int,
    onAutoRefresh: (Int) -> Unit,
    onClearCache: () -> Unit,
    pins: List<PinnedTeam>,
    onPins: (List<PinnedTeam>) -> Unit,
    channelGroups: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sports settings") },
        text = {
            SportsSettingsBody(
                hideScores = hideScores,
                onHideScores = onHideScores,
                autoRefreshMinutes = autoRefreshMinutes,
                onAutoRefresh = onAutoRefresh,
                onClearCache = onClearCache,
                pins = pins,
                onPins = onPins,
                channelGroups = channelGroups,
                modifier = Modifier.width(470.dp).heightIn(max = 560.dp),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * Sports settings for Settings → Sections → Sports: the same options as the gear menu in Sports,
 * saved to [SportsSettings]. Channel groups come from the Live TV channels loaded this session.
 */
@Composable
internal fun SportsSettingsPage(modifier: Modifier = Modifier) {
    var hideScores by remember { mutableStateOf(SportsSettings.hideScores()) }
    var autoRefreshMinutes by remember { mutableStateOf(SportsSettings.autoRefreshMinutes()) }
    var pins by remember { mutableStateOf(SportsSettings.pinnedTeams()) }
    val channels = com.nuvio.app.features.iptv.IptvSession.guide.channels
    val groups = remember(channels) { channels.map { it.group }.distinct().sorted() }
    SportsSettingsBody(
        hideScores = hideScores,
        onHideScores = {
            hideScores = it
            SportsSettings.setHideScores(it)
        },
        autoRefreshMinutes = autoRefreshMinutes,
        onAutoRefresh = {
            autoRefreshMinutes = it
            SportsSettings.setAutoRefreshMinutes(it)
        },
        onClearCache = { SportsRepository.clearCache() },
        pins = pins,
        onPins = {
            pins = it
            SportsSettings.setPinnedTeams(it)
        },
        channelGroups = groups,
        modifier = modifier,
    )
}

/** The Sports options themselves, shared by the gear dialog and the Settings page. */
@Composable
private fun SportsSettingsBody(
    hideScores: Boolean,
    onHideScores: (Boolean) -> Unit,
    autoRefreshMinutes: Int,
    onAutoRefresh: (Int) -> Unit,
    onClearCache: () -> Unit,
    pins: List<PinnedTeam>,
    onPins: (List<PinnedTeam>) -> Unit,
    channelGroups: List<String>,
    modifier: Modifier,
) {
    var scoreDelay by remember { mutableStateOf(SportsSettings.scoreDelayMinutes()) }
    var region by remember { mutableStateOf(SportsSettings.channelRegion()) }
    var preferred by remember { mutableStateOf(SportsSettings.preferredGroups()) }
    var groupQuery by remember { mutableStateOf("") }
            Column(
                modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onHideScores(!hideScores) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Hide scores", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Spoiler-free browsing: scores only appear on the match page",
                            fontSize = 11.sp,
                            color = SportsDim,
                        )
                    }
                    Box(
                        Modifier.width(38.dp).height(22.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (hideScores) SportsAccent else SportsPanelRaised),
                        contentAlignment = if (hideScores) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(16.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (hideScores) Color(0xFF07101C) else SportsMuted),
                        )
                    }
                }

                Column {
                    Text("Auto refresh", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "How often the hub reloads today's matches",
                        fontSize = 11.sp,
                        color = SportsDim,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 2, 5, 10).forEach { minutes ->
                            val active = autoRefreshMinutes == minutes
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp))
                                    .background(if (active) SportsAccent else SportsPanel)
                                    .clickable { onAutoRefresh(minutes) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    if (minutes == 0) "Off" else "$minutes min",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (active) Color(0xFF07101C) else SportsMuted,
                                )
                            }
                        }
                    }
                }

                SportsSettingChips(
                    title = "Score delay",
                    description = "IPTV runs behind the live game. Scores, clocks and results wait this long so they don't show before you see them.",
                    options = listOf(0, 1, 2, 3, 5, 10).map { (if (it == 0) "Off" else "$it min") to it },
                    selected = scoreDelay,
                    onSelect = {
                        scoreDelay = it
                        SportsSettings.setScoreDelayMinutes(it)
                    },
                )

                SportsSettingChips(
                    title = "Channels from",
                    description = "Each game lists this region's (or language's) channels first.",
                    options = listOf("Any" to "") + SportsChannelRegions.map { it.first to it.first },
                    selected = region,
                    onSelect = {
                        region = it
                        SportsSettings.setChannelRegion(it)
                    },
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Preferred channel groups", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Channels in these Live TV groups go to the top of every game's list (your UK sports channels, say).",
                        fontSize = 11.sp,
                        color = SportsDim,
                    )
                    if (preferred.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            preferred.forEach { group ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(999.dp))
                                        .background(SportsAccent)
                                        .clickable {
                                            preferred = preferred - group
                                            SportsSettings.setPreferredGroups(preferred)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("$group  ✕", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF07101C))
                                }
                            }
                        }
                    }
                    if (channelGroups.isEmpty()) {
                        Text("Add a Live TV playlist to pick its groups here.", fontSize = 11.sp, color = SportsDim)
                    } else {
                        androidx.compose.material3.OutlinedTextField(
                            value = groupQuery,
                            onValueChange = { groupQuery = it },
                            placeholder = { Text("Find a group to add", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val matches = channelGroups
                            .filter { it !in preferred }
                            .filter { groupQuery.isBlank() || it.contains(groupQuery, ignoreCase = true) }
                            .sortedByDescending { it.contains("sport", ignoreCase = true) }
                            .take(8)
                        matches.forEach { group ->
                            Text(
                                "+ $group",
                                fontSize = 12.sp,
                                color = SportsMuted,
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        preferred = preferred + group
                                        SportsSettings.setPreferredGroups(preferred)
                                        groupQuery = ""
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                if (pins.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Arrange My Sports", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("The order of your teams' rows on My Sports", fontSize = 11.sp, color = SportsDim)
                        pins.forEachIndexed { index, team ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(team.name, fontSize = 12.5.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                TextButton(
                                    onClick = {
                                        val list = pins.toMutableList()
                                        java.util.Collections.swap(list, index, index - 1)
                                        onPins(list)
                                    },
                                    enabled = index > 0,
                                ) { Text("▲", fontSize = 11.sp) }
                                TextButton(
                                    onClick = {
                                        val list = pins.toMutableList()
                                        java.util.Collections.swap(list, index, index + 1)
                                        onPins(list)
                                    },
                                    enabled = index < pins.lastIndex,
                                ) { Text("▼", fontSize = 11.sp) }
                            }
                        }
                    }
                }

                TextButton(onClick = onClearCache) {
                    Text("Clear cached sports data", fontSize = 12.sp, color = SportsMuted)
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "DATA",
                        fontSize = 9.5.sp,
                        letterSpacing = 1.3.sp,
                        color = SportsDim,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "Schedules, scores, tables and results come from " +
                            SportsDataClient.credits.joinToString(", ") + ".",
                        fontSize = 10.5.sp,
                        color = SportsDim,
                    )
                    Text(
                        "Data is unofficial and may be delayed or incomplete.",
                        fontSize = 10.5.sp,
                        color = SportsDim,
                    )
                }
            }
}

/* ------------------------------------------------------------------ pieces */
@Composable
private fun SportsBadge(text: String, background: Color, foreground: Color = Color.White) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp)).background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text.uppercase(Locale.US),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = foreground,
        )
    }
}

@Composable
private fun SportsImage(url: String?, fallback: String, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(SportsPanelRaised),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            fallback.trim().take(2).uppercase(Locale.US).ifBlank { "?" },
            fontSize = (size.value * 0.3f).sp,
            fontWeight = FontWeight.Bold,
            color = SportsMuted,
        )
        url?.takeIf { it.isNotBlank() }?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private val eventTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())

private fun formatEventTime(event: SportsEvent): String {
    val millis = event.startEpochMs
    if (millis != null) {
        return eventTimeFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }
    return listOfNotNull(event.date, event.time).joinToString(" ").ifBlank { "Time TBD" }
}

/** Re-reads the clock so live badges and progress bars appear without a refresh. */
@Composable
private fun rememberNow(periodMs: Long = 30_000L): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

private fun Int?.valueOrDash(): String = this?.toString() ?: "-"


/** A labelled row of choice chips in Sports settings. */
@Composable
private fun <T> SportsSettingChips(
    title: String,
    description: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(description, fontSize = 11.sp, color = SportsDim)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (label, value) ->
                val active = value == selected
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (active) SportsAccent else SportsPanel)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) Color(0xFF07101C) else SportsMuted,
                    )
                }
            }
        }
    }
}
