package com.nuvio.app.features.iptv

import androidx.compose.material.icons.filled.History
import nuvio.composeapp.generated.resources.nuvio_one_logo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import com.nuvio.app.core.ui.CoverNativeVideo
import com.nuvio.app.features.youtube.OneTubeDebugLog
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.isFullscreenActionActive
import com.nuvio.app.core.ui.toggleFullscreenAction
import com.nuvio.app.features.player.desktop.isDesktopAppFullscreen
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerResizeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.swing.JFileChooser

private val channelTileColors = listOf(
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF26A69A),
    Color(0xFF8E24AA),
    Color(0xFF43A047),
    Color(0xFF00ACC1),
    Color(0xFF7CB342),
    Color(0xFFF4511E),
    Color(0xFF5E35B1),
    Color(0xFFFB8C00),
    Color(0xFFD81B60),
    Color(0xFF3949AB),
)

internal data class PlaybackTarget(
    val url: String,
    val headers: Map<String, String>,
    val title: String,
    /** A separate audio stream (YouTube's adaptive audio); null when the video already carries its sound. */
    val audioUrl: String? = null,
    /** This is a Live TV recording (possibly from the share), not a channel. */
    val isRecording: Boolean = false,
)

/** Live TV and on-demand playback for the desktop app. */
@Composable
internal fun IptvDesktopScreen(
    onBack: () -> Unit,
    initialSearch: String? = null,
) {
    val sources = remember { mutableStateListOf<IptvSource>().apply { addAll(IptvSourceStore.load()) } }
    val scope = rememberCoroutineScope()
    val mainScroll = rememberScrollState()
    val guide = IptvSession.guide
    val loading = IptvSession.loading
    var onDemand by remember { mutableStateOf(false) }
    var seriesOnly by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("live") }
    var selectedGroup by remember { mutableStateOf("All") }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var selectedMovieKey by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf(initialSearch.orEmpty()) }
    var showAddSource by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<IptvSource?>(null) }
    var sourceSaveError by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<PlaybackTarget?>(null) }
    var playerController by remember { mutableStateOf<PlayerEngineController?>(null) }
    // Multiview: up to four channels at once, shown in place of the single player.
    // Four slots, one per tile; an empty slot is a tile with no channel yet.
    var multiview by remember { mutableStateOf<List<PlaybackTarget?>?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var recordings by remember { mutableStateOf(IptvRecorder.recordings()) }
    var smbConfig by remember { mutableStateOf(IptvSmbStore.load()) }
    var sharedRecordings by remember { mutableStateOf<List<SharedRecording>>(emptyList()) }
    var scanningShared by remember { mutableStateOf(false) }
    var recordingsMessage by remember { mutableStateOf<String?>(null) }
    var showRecordingSettings by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf<IptvRecordingExport.Progress?>(null) }
    var exportTitle by remember { mutableStateOf<String?>(null) }

    // A recording made from the player's own connection must hand over to the downloader when
    // playback changes or the screen closes, so it is never cut off by the player stopping.
    LaunchedEffect(playing?.url) {
        IptvRecorder.onPlaybackChanged(playing?.url)
    }
    LaunchedEffect(playerController) {
        IptvRecorder.onPlayerControllerChanged(playerController)
    }
    DisposableEffect(Unit) {
        onDispose { IptvRecorder.onScreenClosed() }
    }

    /** Reads the share's recording folders (the TV's and the phone's layouts). */
    fun scanShare() {
        val config = smbConfig
        if (config == null) {
            sharedRecordings = emptyList()
            return
        }
        if (scanningShared) return
        scanningShared = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { IptvSharedRecordings.scan(config) } }
            result.onSuccess {
                sharedRecordings = it
            }.onFailure {
                recordingsMessage = "Couldn't read the share: ${it.message ?: it.javaClass.simpleName}"
            }
            scanningShared = false
        }
    }

    fun refreshRecordings() {
        recordings = IptvRecorder.recordings()
        scanShare()
    }

    fun runRecordingJob(title: String, block: suspend ((IptvRecordingExport.Progress) -> Unit) -> Result<IptvRecordingExport.Info>) {
        if (exportProgress != null) return
        exportTitle = title
        scope.launch {
            exportProgress = IptvRecordingExport.Progress("Starting", 0L, 0L)
            val result = block { exportProgress = it }
            exportProgress = null
            exportTitle = null
            result.onSuccess {
                recordingsMessage = "Saved to ${it.destination}"
                recordings = IptvRecorder.recordings()
                if (smbConfig != null) scanShare()
            }.onFailure {
                recordingsMessage = it.message ?: it.javaClass.simpleName
            }
        }
    }
    // This computer's favourites, and the TVs' (synced, in their form; matched to channels below).
    var ownFavorites by remember { mutableStateOf(IptvFavoritesStore.load()) }
    var tvFavorites by remember { mutableStateOf(IptvTvFavorites.load()) }
    var settings by remember { mutableStateOf(IptvSettingsStore.load()) }
    var showSettings by remember { mutableStateOf(false) }
    var playRetry by remember { mutableStateOf(0) }
    var reconnecting by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(activeTab, smbConfig) {
        if (activeTab == "recordings") scanShare()
    }

    // The TVs' order first (their Favourites group), then this computer's stars in the order starred.
    val favoriteOrder = remember(guide.channels, ownFavorites, tvFavorites) {
        (IptvTvFavorites.orderedMatches(guide.channels, tvFavorites) + ownFavorites).distinct()
    }
    val favorites = remember(favoriteOrder) { favoriteOrder.toSet() }

    /** Star or unstar: kept in both forms, so it reaches the TVs too. */
    fun persistFavorites(next: Set<String>) {
        val added = next - favorites
        val removed = favorites - next
        val byKey = guide.channels.associateBy { it.key }
        var own = ownFavorites
        var tv = tvFavorites
        added.forEach { key ->
            own = own + key
            byKey[key]?.let { tv = tv + IptvTvFavorites.keyFor(it) }
        }
        removed.forEach { key ->
            own = own - key
            byKey[key]?.let { tv = IptvTvFavorites.without(tv, it) }
        }
        if (own != ownFavorites) {
            ownFavorites = own
            IptvFavoritesStore.save(own)
        }
        if (tv != tvFavorites) {
            tvFavorites = tv
            IptvTvFavorites.save(tv)
        }
    }

    fun refresh() {
        IptvSession.refresh(sources.toList())
    }

    // A synced copy from another device was written here: show it.
    var syncedGuides by remember { mutableStateOf(IptvGuideSourceStore.load()) }
    LaunchedEffect(com.nuvio.app.features.iptv.sync.LiveTvSyncLocal.appliedCount) {
        if (com.nuvio.app.features.iptv.sync.LiveTvSyncLocal.appliedCount == 0) return@LaunchedEffect
        ownFavorites = IptvFavoritesStore.load()
        tvFavorites = IptvTvFavorites.load()
        settings = IptvSettingsStore.load()
        val freshSources = IptvSourceStore.load()
        val freshGuides = IptvGuideSourceStore.load()
        if (freshSources != sources.toList() || freshGuides != syncedGuides) {
            sources.clear()
            sources.addAll(freshSources)
            syncedGuides = freshGuides
            refresh()
        }
    }
    LiveTvCloudPromptHost()

    fun changeRecordingFolder() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose recordings folder"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            currentDirectory = runCatching { IptvRecorder.directory.toFile() }.getOrNull()
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            IptvRecordingStore.setFolder(chooser.selectedFile.toPath())
            recordings = IptvRecorder.recordings()
        }
    }

    // Hidden groups must not be selectable or visible in the guide, list or
    // search. Compute these before the selection effect so it cannot keep a
    // hidden channel/movie selected.
    val visibleChannels = remember(
        guide.channels,
        settings.hideAdultGroups,
        settings.hiddenGroups,
        settings.nameRemovals,
        settings.cleanGroupNames,
    ) {
        val hidden = settings.hiddenGroups.toSet()
        guide.channels.asSequence()
            .filter { !settings.hideAdultGroups || !isAdultGroup(it.group) }
            .map { channel ->
                val group = if (settings.cleanGroupNames) cleanIptvGroupName(channel.group) else channel.group
                val name = cleanIptvChannelName(channel.name, settings.nameRemovals)
                if (group == channel.group && name == channel.name) channel else channel.copy(group = group, name = name)
            }
            .filter { it.group !in hidden }
            .toList()
    }
    val visibleMovies = remember(guide.movies, settings.hideAdultGroups) {
        if (!settings.hideAdultGroups) guide.movies
        else guide.movies.filter { !isAdultGroup(it.group) }
    }

    // Cached across section changes: only loads on first entry or when stale.
    LaunchedEffect(sources.size) {
        // Settings → Update channels and guide: how old the cached lists may get.
        val hours = settings.playlistRefreshHours
        IptvSession.ensureLoaded(sources.toList(), if (hours <= 0) Long.MAX_VALUE else hours * 3_600_000L)
    }

    LaunchedEffect(IptvSession.loadCount) {
        if (visibleChannels.isEmpty() && visibleMovies.isEmpty()) return@LaunchedEffect
        if (selectedKey !in visibleChannels.map { it.key }) {
            val saved = settings.lastChannelKey
            selectedKey = if (
                settings.rememberLastChannel &&
                visibleChannels.any { it.key == saved }
            ) {
                saved
            } else {
                visibleChannels.firstOrNull()?.key
            }
        }
        if (selectedMovieKey !in visibleMovies.map { it.key }) {
            selectedMovieKey = visibleMovies.firstOrNull()?.key
        }
        recordings = IptvRecorder.recordings()
    }
    LaunchedEffect(Unit) {
        var wasRecording = IptvRecorder.recording
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
            val isRecording = IptvRecorder.recording
            if (wasRecording && !isRecording) recordings = IptvRecorder.recordings()
            wasRecording = isRecording
        }
    }

    val offsetMs = settings.epgOffsetMinutes * 60_000L
    val shiftedPrograms = remember(guide.programs, offsetMs) {
        if (offsetMs == 0L) {
            guide.programs
        } else {
            guide.programs.mapValues { (_, list) ->
                list.map {
                    it.copy(startMs = it.startMs - offsetMs, stopMs = it.stopMs - offsetMs)
                }
            }
        }
    }
    val timeFormat = remember(settings.use24HourClock) {
        val pattern = if (settings.use24HourClock) "HH:mm" else "h:mm a"
        { timestamp: Long -> SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp)) }
    }

    val selected = visibleChannels.firstOrNull { it.key == selectedKey }
    val selectedMovie = visibleMovies.firstOrNull { it.key == selectedMovieKey }
    val programs = selected?.let { shiftedPrograms[it.key] }.orEmpty()
    val currentProgram = programs.firstOrNull { it.isLive(now) }
    val nextProgram = programs.filter { it.startMs > now }.minByOrNull { it.startMs }
    val recording = IptvRecorder.recording
    val recordedSeconds = if (recording) {
        ((now - IptvRecorder.startedAtMs) / 1000L).coerceAtLeast(0L)
    } else {
        0L
    }

    val sortedChannels = remember(visibleChannels, settings.channelSort, settings.customGroupOrder, settings.customChannelOrder, settings.sequentialNumbers) {
        when (settings.channelSort) {
            "az" -> visibleChannels.sortedBy { it.name.lowercase(Locale.getDefault()) }
            "custom" -> if (
                settings.customGroupOrder.isEmpty() &&
                settings.customChannelOrder.isEmpty()
            ) {
                visibleChannels
            } else {
                orderChannelsByCustom(visibleChannels, settings)
            }
            "provider" -> visibleChannels
            else -> visibleChannels.sortedBy { it.number }
        }.let { ordered ->
            // Settings → Sequential numbers: 1, 2, 3… in list order.
            if (settings.sequentialNumbers) ordered.mapIndexed { index, channel -> channel.copy(number = index + 1) } else ordered
        }
    }

    val channelGroups = remember(visibleChannels, settings.showFavoritesGroup, settings.showRecentGroup, settings.showAllGroup, settings.groupSort, settings.customGroupOrder) {
        val names = LinkedHashSet<String>(minOf(visibleChannels.size, 512))
        visibleChannels.forEach { channel ->
            if (channel.group.isNotBlank()) names.add(channel.group)
        }
        val ordered = when (settings.groupSort) {
            "az" -> names.sortedBy { it.lowercase(Locale.getDefault()) }
            "custom" -> {
                val rank = settings.customGroupOrder.withIndex().associate { it.value to it.index }
                names.sortedWith(compareBy { rank[it] ?: Int.MAX_VALUE })
            }
            else -> names.toList()
        }
        buildList {
            if (settings.showFavoritesGroup) add("Favorites")
            if (settings.showRecentGroup) add("Recent")
            if (settings.showAllGroup) add("All")
            addAll(ordered)
        }
    }
    val movieGroups = remember(visibleMovies) {
        listOf("All") + visibleMovies.map { it.group }.filter { it.isNotBlank() }.distinct()
    }
    val groups = if (onDemand) movieGroups else channelGroups

    LaunchedEffect(groups) {
        if (selectedGroup !in groups) selectedGroup = if ("All" in groups) "All" else groups.firstOrNull() ?: "All"
    }

    val filteredChannels = remember(sortedChannels, shiftedPrograms, selectedGroup, search, favorites, favoriteOrder, settings.recentChannelKeys) {
        val query = search.trim()
        val queryBlank = query.isEmpty()
        val nowMs = System.currentTimeMillis()
        val recentRank = settings.recentChannelKeys.withIndex().associate { it.value to it.index }
        val favoriteRank = favoriteOrder.withIndex().associate { it.value to it.index }
        val source = when (selectedGroup) {
            "Recent" -> sortedChannels.filter { it.key in recentRank }.sortedBy { recentRank[it.key] }
            // Favourites keep their own order (TV order, then starred order), whatever the channel sort.
            "Favorites" -> sortedChannels.filter { it.key in favoriteRank }.sortedBy { favoriteRank[it.key] }
            else -> sortedChannels
        }
        source.filter { channel ->
            val groupMatch = when (selectedGroup) {
                "All", "Recent" -> true
                "Favorites" -> channel.key in favorites
                else -> channel.group == selectedGroup
            }
            if (!groupMatch) return@filter false
            if (queryBlank) return@filter true
            channel.name.contains(query, ignoreCase = true) ||
                shiftedPrograms[channel.key]?.any { it.isLive(nowMs) && it.title.contains(query, ignoreCase = true) } == true
        }
    }
    val filteredMovies = remember(visibleMovies, selectedGroup, search, seriesOnly) {
        visibleMovies.filter { movie ->
            (selectedGroup == "All" || movie.group == selectedGroup) &&
                (!seriesOnly || movie.group.contains("serie", true) || movie.group.contains("show", true)) &&
                (search.isBlank() || movie.name.contains(search, ignoreCase = true))
        }
    }
    val moviesForPage = remember(visibleMovies, search) {
        visibleMovies.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
    }
    val seriesMovies = remember(visibleMovies, search) {
        visibleMovies.filter { movie ->
            (movie.group.contains("serie", true) || movie.group.contains("show", true)) &&
                (search.isBlank() || movie.name.contains(search, ignoreCase = true))
        }
    }

    val groupCounts = remember(groups, visibleChannels, favorites, visibleMovies, onDemand, settings.recentChannelKeys) {
        if (onDemand) {
            val movieCounts = visibleMovies.groupingBy { it.group }.eachCount()
            groups.associateWith { group ->
                if (group == "All") visibleMovies.size else movieCounts[group] ?: 0
            }
        } else {
            val channelCounts = visibleChannels.groupingBy { it.group }.eachCount()
            val favoriteCount = if ("Favorites" in groups) {
                visibleChannels.count { it.key in favorites }
            } else {
                0
            }
            val recentKeys = settings.recentChannelKeys.toSet()
            groups.associateWith { group ->
                when (group) {
                    "All" -> visibleChannels.size
                    "Favorites" -> favoriteCount
                    "Recent" -> visibleChannels.count { it.key in recentKeys }
                    else -> channelCounts[group] ?: 0
                }
            }
        }
    }

    // Four empty tiles: each one gets a channel from its own "Choose channel" button.
    fun openMultiview() {
        multiview = List(4) { null }
    }

    fun chooseTile(index: Int, target: PlaybackTarget?) {
        if (target != null && StalkerLinks.isStalker(target.url)) {
            scope.launch {
                val resolved = StalkerLinks.resolveOnIo(target.url)
                if (!StalkerLinks.isStalker(resolved)) {
                    multiview = multiview?.toMutableList()?.also { it[index] = target.copy(url = resolved) }
                }
            }
            return
        }
        multiview = multiview?.toMutableList()?.also { it[index] = target }
    }

    fun openPlayback(target: PlaybackTarget) {
        if (!StalkerLinks.isStalker(target.url)) {
            playing = target
            playbackError = null
            return
        }
        scope.launch {
            val resolved = StalkerLinks.resolveOnIo(target.url)
            if (StalkerLinks.isStalker(resolved)) {
                playbackError = "Couldn't get a stream link from the portal."
            } else {
                playing = target.copy(url = resolved)
                playbackError = null
            }
        }
    }

    fun play(channel: LiveChannel) {
        openPlayback(PlaybackTarget(channel.url, channel.headers, channel.name))
        // The "Open channels full screen" switch: go full screen as the channel starts.
        if (settings.openChannelsFullScreen && !isDesktopAppFullscreen()) toggleFullscreenAction()
        // Settings → Recent group: newest first, last 30 channels.
        val recent = (listOf(channel.key) + settings.recentChannelKeys.filter { it != channel.key }).take(30)
        if (recent != settings.recentChannelKeys) {
            settings = settings.copy(recentChannelKeys = recent)
            IptvSettingsStore.save(settings)
        }
    }

    fun startRecording(channel: LiveChannel, url: String) {
        // Recording a channel that is already playing uses the player's own connection. This
        // matters on accounts that allow one connection: a second download would be closed by
        // the provider after a couple of seconds.
        val fromPlayer = playing?.url == url &&
            IptvRecorder.startFromPlayer(
                channel.name,
                url,
                channel.headers,
                playerController,
            )
        if (!fromPlayer) {
            IptvRecorder.start(channel.name, url, channel.headers)
        }
    }

    fun toggleRecording() {
        if (IptvRecorder.recording) {
            IptvRecorder.stop()
            recordings = IptvRecorder.recordings()
            return
        }
        val channel = selected ?: return
        if (StalkerLinks.isStalker(channel.url)) {
            scope.launch {
                val url = StalkerLinks.resolveOnIo(channel.url)
                if (!StalkerLinks.isStalker(url)) startRecording(channel, url)
            }
        } else {
            startRecording(channel, channel.url)
        }
    }

    fun playMovie(movie: VodItem) {
        selectedMovieKey = movie.key
        onDemand = true
        playing = PlaybackTarget(movie.url, movie.headers, movie.name)
        playbackError = null
        activeTab = "live"
    }

    fun playFromStart(channel: LiveChannel, program: EpgProgram) {
        val catchup = channel.catchup ?: return
        val url = CatchupUrlBuilder.build(channel.url, catchup, program.startMs, program.stopMs)
            ?: channel.url
        openPlayback(PlaybackTarget(url, channel.headers, "${channel.name} (from start)"))
    }

    fun goTab(tab: String) {
        activeTab = tab
        if (tab == "guide" || tab == "live") onDemand = false
        selectedGroup = "All"
        scope.launch { mainScroll.animateScrollTo(0) }
    }

    val appFullscreen = isFullscreenActionActive()
    val fullscreenTarget = playing
    val multiFull = multiview
    if (appFullscreen && multiFull != null) {
        Box(Modifier.fillMaxSize().background(Color.Black).padding(8.dp)) {
            LiveMultiview(
                tiles = multiFull,
                channels = visibleChannels,
                fullscreen = true,
                onChoose = ::chooseTile,
                onClose = { multiview = null },
                onToggleFullscreen = { toggleFullscreenAction() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    if (appFullscreen && fullscreenTarget != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            key(fullscreenTarget.url, playRetry) {
                PlatformPlayerSurface(
                    sourceUrl = fullscreenTarget.url,
                    sourceHeaders = fullscreenTarget.headers,
                    modifier = Modifier.fillMaxSize(),
                    useNativeController = true,
                    playerControlsState = PlayerControlsState(hideBackButton = true),
                    resizeMode = when (settings.aspectMode) {
                        "fill" -> PlayerResizeMode.Fill
                        "zoom" -> PlayerResizeMode.Zoom
                        "stretch" -> PlayerResizeMode.Stretch
                        else -> PlayerResizeMode.Fit
                    },
                    onControllerReady = { playerController = it },
                    onSnapshot = {},
                    onError = { message ->
                        IptvRecorder.onPlaybackChanged(null)
                        if (settings.autoReconnect && !reconnecting) {
                            reconnecting = true
                            scope.launch {
                                delay(2_000)
                                playRetry += 1
                                reconnecting = false
                            }
                        } else {
                            playbackError = message
                        }
                    },
                )
            }
        }
        return
    }

    Surface(color = IptvBackground, contentColor = IptvText) {
        Column(Modifier.fillMaxSize()) {
            LiveTvHeader(
                activeTab = activeTab,
                search = search,
                clockText = timeFormat(now),
                dayText = dayLabel(now),
                onTab = ::goTab,
                onSearch = { search = it },
                onAddSource = {
                    editingSource = null
                    showAddSource = true
                },
                onSettings = { showSettings = true },
            )
            HorizontalDivider(color = IptvBorderSoft)

            Box(Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                "guide" -> IptvGuidePane(
                    modifier = Modifier.fillMaxSize(),
                    channels = filteredChannels,
                    programs = shiftedPrograms,
                    groups = channelGroups,
                    groupCounts = groupCounts,
                    selectedGroup = selectedGroup,
                    onGroupSelect = { selectedGroup = it },
                    selectedChannel = selected,
                    favorites = favorites,
                    now = now,
                    settings = settings,
                    timeFormat = timeFormat,
                    onSelect = { channel -> selectedKey = channel.key },
                    onWatch = { channel ->
                        selectedKey = channel.key
                        play(channel)
                        activeTab = "live"
                    },
                    onToggleFavorite = { channel ->
                        persistFavorites(
                            if (channel.key in favorites) favorites - channel.key else favorites + channel.key,
                        )
                    },
                    recording = recording,
                    onRecord = { toggleRecording() },
                )

                "movies" -> IptvVodGridPane(
                    title = "Movies",
                    subtitle = "On-demand titles from your playlists",
                    movies = moviesForPage,
                    emptyMessage = "No movies found in your playlists.",
                    onPlay = ::playMovie,
                )

                "series" -> IptvVodGridPane(
                    title = "Series",
                    subtitle = "Groups named SERIES or SHOWS",
                    movies = seriesMovies,
                    emptyMessage = "No series groups found. Most providers list them under SERIES or SHOWS.",
                    onPlay = ::playMovie,
                )

                "recordings" -> IptvRecordingsPane(
                    recordings = recordings,
                    shared = sharedRecordings,
                    smbConfigured = smbConfig != null,
                    smbLabel = smbConfig?.label,
                    scanning = scanningShared,
                    scanError = recordingsMessage,
                    busy = exportProgress,
                    busyTitle = exportTitle,
                    onPlay = { path ->
                        playing = PlaybackTarget(
                            path.toUri().toString(), emptyMap(), path.fileName.toString(), isRecording = true,
                        )
                        playbackError = null
                        onDemand = false
                        activeTab = "live"
                    },
                    onDelete = {
                        IptvRecorder.delete(it)
                        recordings = IptvRecorder.recordings()
                    },
                    onRefresh = ::refreshRecordings,
                    onOpenFolder = {
                        runCatching { Desktop.getDesktop().open(IptvRecorder.directory.toFile()) }
                    },
                    onChangeFolder = ::changeRecordingFolder,
                    onExportLocal = { path ->
                        recordingsMessage = null
                        runRecordingJob(path.fileName.toString()) { progress ->
                            IptvRecordingExport.exportLocal(path, progress)
                        }
                    },
                    onPlayShared = { rec ->
                        val config = smbConfig
                        if (config == null) {
                            recordingsMessage = "Set up the SMB share first."
                        } else {
                            playing = PlaybackTarget(
                                IptvRecordingServer.urlFor(rec.id, config, rec.shareDir),
                                emptyMap(),
                                rec.title,
                                isRecording = true,
                            )
                            playbackError = null
                            onDemand = false
                            activeTab = "live"
                        }
                    },
                    onCombineShared = { rec, toShare ->
                        val config = smbConfig
                        if (config == null) {
                            recordingsMessage = "Set up the SMB share first."
                        } else {
                            recordingsMessage = null
                            runRecordingJob(rec.title) { progress ->
                                IptvRecordingExport.combineShared(config, rec, toShare, progress)
                            }
                        }
                    },
                    onExportShared = { rec, toShare ->
                        val config = smbConfig
                        if (config == null) {
                            recordingsMessage = "Set up the SMB share first."
                        } else {
                            recordingsMessage = null
                            runRecordingJob(rec.title) { progress ->
                                IptvRecordingExport.exportShared(config, rec, toShare, progress)
                            }
                        }
                    },
                    onDeleteShared = { rec ->
                        val config = smbConfig
                        if (config == null) {
                            recordingsMessage = "Set up the SMB share first."
                        } else {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    IptvSmbClient.deleteRecursively(config, rec.shareDir)
                                }
                                if (ok) {
                                    recordingsMessage = "Deleted \"${rec.title}\" from the share"
                                    scanShare()
                                } else {
                                    recordingsMessage = "Couldn't delete \"${rec.title}\" from the share"
                                }
                            }
                        }
                    },
                    onRecordingSettings = { showRecordingSettings = true },
                )

                else -> Row(Modifier.fillMaxSize()) {
                    // ---------------------------------------------------- sidebar
                Column(Modifier.width(384.dp).fillMaxHeight().background(IptvSidebar)) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Nuvio",
                                    tint = IptvMuted,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            Text(
                                if (onDemand) "Titles" else "Channels",
                                modifier = Modifier.weight(1f).padding(start = 6.dp),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                            )
                            Text(
                                if (onDemand) "${filteredMovies.size} of ${visibleMovies.size}"
                                else "${filteredChannels.size} of ${visibleChannels.size}",
                                fontSize = 11.sp,
                                color = IptvDim,
                            )
                            IconButton(onClick = { refresh() }, enabled = !loading) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Refresh",
                                    tint = if (loading) IptvDim else IptvMuted,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }

                        // Favourites and Recently watched one click away, like the TV app's pinned groups.
                        if (!onDemand) {
                            QuickGroupChips(
                                groups = groups,
                                selected = selectedGroup,
                                counts = groupCounts,
                                onSelect = { selectedGroup = it },
                            )
                        }
                        GroupSelector(
                            groups = groups,
                            selected = selectedGroup,
                            counts = if (!settings.showGroupCounts) emptyMap() else groupCounts,
                            onSelect = { selectedGroup = it },
                        )

                        if (guide.errors.isNotEmpty()) {
                            Text(
                                guide.errors.first(),
                                fontSize = 11.sp,
                                color = Color(0xFFFF8A9B),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        if (onDemand) {
                            items(filteredMovies, key = { it.key }) { movie ->
                                MovieRow(
                                    movie = movie,
                                    selected = selectedMovieKey == movie.key,
                                    onClick = {
                                        selectedMovieKey = movie.key
                                        playing = PlaybackTarget(movie.url, movie.headers, movie.name)
                                        playbackError = null
                                    },
                                )
                            }
                        } else {
                            items(filteredChannels, key = { it.key }) { channel ->
                                ChannelRow(
                                    channel = channel,
                                    program = shiftedPrograms[channel.key]?.firstOrNull { it.isLive(now) },
                                    selected = selectedKey == channel.key,
                                    favorite = channel.key in favorites,
                                    now = now,
                                    showName = settings.showChannelNames,
                                    showNumber = settings.showChannelNumbers,
                                    showLogo = settings.showChannelLogos,
                                    showQuality = settings.showQualityBadges,
                                    compact = settings.compactRows,
                                    outlineSelected = settings.outlinePlayingChannel,
                                    onClick = {
                                        selectedKey = channel.key
                                        if (settings.lastChannelKey != channel.key) {
                                            settings = settings.copy(lastChannelKey = channel.key)
                                            IptvSettingsStore.save(settings)
                                        }
                                        play(channel)
                                    },
                                    onToggleFavorite = {
                                        persistFavorites(
                                            if (channel.key in favorites) favorites - channel.key
                                            else favorites + channel.key,
                                        )
                                    },
                                )
                            }
                        }
                        if (filteredChannels.isEmpty() && filteredMovies.isEmpty()) {
                            item {
                                Text(
                                    if (sources.isEmpty()) {
                                        "Add an M3U playlist, Xtream account or Stalker portal to see your channels and guide."
                                    } else {
                                        "Nothing matches this filter."
                                    },
                                    modifier = Modifier.padding(18.dp),
                                    color = IptvDim,
                                    fontSize = 12.5.sp,
                                )
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${sources.size} source${if (sources.size == 1) "" else "s"}",
                            fontSize = 11.sp,
                            color = IptvDim,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showSources = true }) {
                            Text("Manage", fontSize = 12.sp, color = IptvMuted)
                        }
                    }
                }

                Spacer(Modifier.width(1.dp).fillMaxHeight().background(IptvBorderSoft))

                // ------------------------------------------------------------ main
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(mainScroll)
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val windowHeight = with(LocalDensity.current) {
                        LocalWindowInfo.current.containerSize.height.toDp()
                    }
                    val playerHeight = (windowHeight * 0.50f).coerceIn(240.dp, 520.dp)

                    val multi = multiview
                    if (multi != null) {
                        LiveMultiview(
                            tiles = multi,
                            channels = visibleChannels,
                            fullscreen = false,
                            onChoose = ::chooseTile,
                            onClose = { multiview = null },
                            onToggleFullscreen = { toggleFullscreenAction() },
                        )
                    } else PlayerShell(
                        height = playerHeight,
                        live = !onDemand,
                        channel = selected,
                        movie = selectedMovie,
                        progress = currentProgram?.let { programProgress(it, now) },
                        programStart = currentProgram?.startMs,
                        programStop = currentProgram?.stopMs,
                        canWatchFromStart = !onDemand && selected != null && currentProgram != null &&
                            CatchupUrlBuilder.isAvailable(selected.catchup, currentProgram.startMs, now),
                        showNowLine = settings.showNowLine,
                        timeFormat = timeFormat,
                        onWatchFromStart = {
                            if (selected != null && currentProgram != null) playFromStart(selected, currentProgram)
                        },
                    ) { targetModifier ->
                        val target = playing
                        if (target != null) {
                            key(target.url, playRetry) {
                                PlatformPlayerSurface(
                                    sourceUrl = target.url,
                                    sourceHeaders = target.headers,
                                    modifier = if (showSettings || showAddSource || showSources) {
                                        Modifier.size(1.dp)
                                    } else {
                                        targetModifier
                                    },
                                    useNativeController = true,
                                    playerControlsState = PlayerControlsState(hideBackButton = true),
                                    resizeMode = when (settings.aspectMode) {
                                        "fill" -> PlayerResizeMode.Fill
                                        "zoom" -> PlayerResizeMode.Zoom
                                        "stretch" -> PlayerResizeMode.Stretch
                                        else -> PlayerResizeMode.Fit
                                    },
                                    onControllerReady = { playerController = it },
                                    onSnapshot = {},
                                    onError = { message ->
                                        IptvRecorder.onPlaybackChanged(null)
                                        if (settings.autoReconnect && !reconnecting) {
                                            reconnecting = true
                                            scope.launch {
                                                delay(2_000)
                                                playRetry += 1
                                                reconnecting = false
                                            }
                                        } else {
                                            playbackError = message
                                        }
                                    },
                                )
                            }
                        } else {
                            Column(
                                modifier = targetModifier
                                    .clickable(enabled = selected != null) { selected?.let { play(it) } },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    Modifier.size(66.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0x99101014))
                                        .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(999.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    selected?.name
                                        ?: if (onDemand) "Choose a title to play" else "Choose a channel to play",
                                    color = IptvMuted,
                                    fontSize = 13.sp,
                                )
                                currentProgram?.let {
                                    Text(
                                        it.title,
                                        color = IptvDim,
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Open channels full screen", color = IptvMuted, fontSize = 13.sp)
                        androidx.compose.material3.Switch(
                            checked = settings.openChannelsFullScreen,
                            onCheckedChange = {
                                settings = settings.copy(openChannelsFullScreen = it)
                                IptvSettingsStore.save(settings)
                            },
                        )
                        IconButton(onClick = { openMultiview() }) {
                            Icon(
                                imageVector = Icons.Filled.GridView,
                                contentDescription = "Multiview",
                                tint = IptvMuted,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    TitleRow(
                        title = when {
                            playing?.isRecording == true || playing?.url?.startsWith("file:") == true -> playing?.title ?: "Recording"
                            onDemand -> selectedMovie?.name ?: "On demand"
                            currentProgram != null -> currentProgram.title
                            selected != null -> selected.name
                            else -> "Select a channel"
                        },
                        channelName = when {
                            onDemand -> selectedMovie?.let { "${it.group} · ${it.sourceName}" }
                            selected != null -> selected.name
                            else -> null
                        },
                        channelNumber = if (!onDemand) selected?.number else null,
                        timeRange = currentProgram?.let { "${timeFormat(it.startMs)} - ${timeFormat(it.stopMs)}" },
                        live = !onDemand && currentProgram != null,
                        watchLabel = if (onDemand) "Play" else "Watch live",
                        recordLabel = if (recording) "Stop" else "Record",
                        recording = recording,
                        favorite = !onDemand && selected != null && selected.key in favorites,
                        showLiveActions = !onDemand,
                        onWatch = {
                            when {
                                onDemand -> selectedMovie?.let {
                                    playing = PlaybackTarget(it.url, it.headers, it.name)
                                    playbackError = null
                                }
                                selected != null -> play(selected)
                            }
                        },
                        onRecord = { toggleRecording() },
                        onFavorite = {
                            if (selected != null) {
                                persistFavorites(
                                    if (selected.key in favorites) favorites - selected.key
                                    else favorites + selected.key,
                                )
                            }
                        },
                        onGuide = { scope.launch { mainScroll.animateScrollTo(mainScroll.maxValue) } },
                    )

                    playbackError?.let {
                        Text(it, color = Color(0xFFFF8A9B), fontSize = 12.sp)
                    }
                    IptvRecorder.notice?.let {
                        Text(it, color = Color(0xFFFFC66D), fontSize = 12.sp)
                    }
                    IptvRecorder.error?.let {
                        Text(it, color = Color(0xFFFF8A9B), fontSize = 12.sp)
                    }

                    if (onDemand) {
                        InfoCard(
                            label = "On demand",
                            title = selectedMovie?.name ?: "Nothing selected",
                            body = selectedMovie?.let { movie ->
                                buildString {
                                    append(movie.group)
                                    movie.durationSeconds?.takeIf { it > 0 }?.let {
                                        append(" · ").append(formatDuration(it.toLong()))
                                    }
                                    append(" · ").append(movie.sourceName)
                                }
                            },
                            meta = null,
                            progress = null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            InfoCard(
                                label = "Now playing",
                                title = currentProgram?.title ?: "No guide data",
                                body = currentProgram?.description,
                                meta = currentProgram?.let {
                                    "${timeFormat(it.startMs)} – ${timeFormat(it.stopMs)} · ${programMinutes(it)} min"
                                },
                                progress = currentProgram?.let { programProgress(it, now) },
                                modifier = Modifier.weight(1.35f),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                InfoCard(
                                    label = "Up next",
                                    title = nextProgram?.title ?: "Schedule ends soon",
                                    body = nextProgram?.description,
                                    meta = nextProgram?.let { "${timeFormat(it.startMs)} – ${timeFormat(it.stopMs)}" },
                                    progress = null,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                RecordStrip(
                                    recording = recording,
                                    seconds = recordedSeconds,
                                    channelName = selected?.name,
                                    onStop = {
                                        IptvRecorder.stop()
                                        recordings = IptvRecorder.recordings()
                                    },
                                )
                            }
                        }
                    }

                    RecordingsCard(
                        recordings = recordings,
                        onRefresh = { recordings = IptvRecorder.recordings() },
                        onOpenFolder = { runCatching { Desktop.getDesktop().open(IptvRecorder.directory.toFile()) } },
                        onChangeFolder = ::changeRecordingFolder,
                        onPlay = { path ->
                            playing = PlaybackTarget(path.toUri().toString(), emptyMap(), path.fileName.toString())
                            playbackError = null
                        },
                        onDelete = {
                            IptvRecorder.delete(it)
                            recordings = IptvRecorder.recordings()
                        },
                    )

                    if (!onDemand && programs.isNotEmpty()) {
                        GuideCard(
                            programs = programs.filter {
                                it.stopMs > now - settings.epgPastHours * 3_600_000L &&
                                    it.startMs < now + settings.epgFutureDays * 86_400_000L
                            },
                            now = now,
                            catchup = selected?.catchup,
                            channelUrl = selected?.url,
                            showTimes = settings.showProgramTimes,
                            showDetails = settings.showProgramDetails,
                            showNowLine = settings.showNowLine,
                            timeFormat = timeFormat,
                            onPlayFromArchive = { program ->
                                selected?.let { channel ->
                                    val archive = channel.catchup
                                        ?.takeIf { CatchupUrlBuilder.isAvailable(it, program.startMs, now) }
                                        ?.let {
                                            CatchupUrlBuilder.build(channel.url, it, program.startMs, program.stopMs)
                                        }
                                    if (archive != null) {
                                        playing = PlaybackTarget(
                                            archive,
                                            channel.headers,
                                            "${channel.name}: ${program.title}",
                                        )
                                        playbackError = null
                                    }
                                }
                            },
                        )
                    }
                }
            }
            }
        }
    }
    }

    // The video windows sit above Compose, so they hide while a dialog is open over them.
    CoverNativeVideo(covering = showSettings || showAddSource || showSources || showRecordingSettings)
    if (showSettings) {
        val orderGroups = remember(visibleChannels) {
            visibleChannels.map { it.group }.filter { it.isNotBlank() }.distinct()
        }
        val orderChannels = remember(visibleChannels) { visibleChannels.groupBy { it.group } }
        IptvSettingsDialog(
            settings = settings,
            groups = orderGroups,
            channelsByGroup = orderChannels,
            onChange = {
                settings = it
                IptvSettingsStore.save(it)
            },
            onDismiss = { showSettings = false },
            onManageSources = {
                showSettings = false
                showSources = true
            },
        )
    }

    if (showRecordingSettings) {
        IptvRecordingSettingsDialog(
            onDismiss = { showRecordingSettings = false },
            onSaved = { config ->
                smbConfig = config
                if (config == null) sharedRecordings = emptyList()
                recordingsMessage = null
            },
        )
    }

    if (showAddSource) {
        AddSourceDialog(
            existing = editingSource,
            saveError = sourceSaveError,
            onDismiss = {
                showAddSource = false
                editingSource = null
                sourceSaveError = null
            },
            onAdd = { source ->
                sourceSaveError = null
                val target = editingSource
                val next = sources.toMutableList()
                if (target != null) {
                    val index = next.indexOfFirst { it.id == target.id }
                    if (index >= 0) next[index] = source else next += source
                } else {
                    next += source
                }
                val error = runCatching { IptvSourceStore.save(next) }.exceptionOrNull()
                if (error != null) {
                    sourceSaveError = "Could not save the playlist: ${error.message ?: error.javaClass.simpleName}"
                    OneTubeDebugLog.line("iptv-source-save-failed ${error.javaClass.simpleName}")
                } else {
                    sources.clear()
                    sources.addAll(next)
                    sourceSaveError = null
                    showAddSource = false
                    editingSource = null
                    refresh()
                }
            },
        )
    }

    if (showSources) {
        SourcesDialog(
            sources = sources.toList(),
            onDismiss = { showSources = false },
            onEdit = { source ->
                showSources = false
                editingSource = source
                showAddSource = true
            },
            onRemove = { source ->
                sources.remove(source)
                IptvSourceStore.save(sources)
                refresh()
            },
            onAdd = {
                showSources = false
                editingSource = null
                showAddSource = true
            },
        )
    }
}

/* ------------------------------------------------------------------ header */

@Composable
private fun LiveTvHeader(
    activeTab: String,
    search: String,
    clockText: String,
    dayText: String,
    onTab: (String) -> Unit,
    onSearch: (String) -> Unit,
    onAddSource: () -> Unit,
    onSettings: () -> Unit,
) {
    val tabs = listOf(
        "live" to "Live TV",
        "guide" to "Guide",
        "movies" to "Movies",
        "series" to "Series",
        "recordings" to "Recordings",
    )
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color(0xFF050507))) {
        // On a narrow window the centred tabs ran under the search box. Put the tabs on their
        // own row instead so Recordings stays clickable at 1280x720 and below.
        val availableWidth = maxWidth
        val compact = availableWidth < 1500.dp
        if (compact) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HeaderBrand()
                    Spacer(Modifier.weight(1f))
                    HeaderActions(
                        search = search,
                        clockText = clockText,
                        dayText = dayText,
                        onSearch = onSearch,
                        onAddSource = onAddSource,
                        onSettings = onSettings,
                        searchWidth = if (availableWidth < 1120.dp) 190.dp else 250.dp,
                        showClock = availableWidth >= 1120.dp,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderTabs(tabs, activeTab, onTab)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) {
                    HeaderBrand()
                }
                Box(Modifier.align(Alignment.Center)) {
                    HeaderTabs(tabs, activeTab, onTab)
                }
                Box(Modifier.align(Alignment.CenterEnd)) {
                    HeaderActions(
                        search = search,
                        clockText = clockText,
                        dayText = dayText,
                        onSearch = onSearch,
                        onAddSource = onAddSource,
                        onSettings = onSettings,
                        searchWidth = 272.dp,
                        showClock = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderBrand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.Image(
            painter = org.jetbrains.compose.resources.painterResource(
                nuvio.composeapp.generated.resources.Res.drawable.nuvio_one_logo,
            ),
            contentDescription = "Nuvio One",
            modifier = Modifier.size(34.dp),
        )
        Column {
            Text("Live TV", fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
            Text("NUVIO DESKTOP", fontSize = 9.sp, letterSpacing = 1.5.sp, color = IptvDim)
        }
    }
}

@Composable
private fun HeaderTabs(
    tabs: List<Pair<String, String>>,
    activeTab: String,
    onTab: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (id, label) ->
            HeaderTab(label, activeTab == id) { onTab(id) }
        }
    }
}

@Composable
private fun HeaderActions(
    search: String,
    clockText: String,
    dayText: String,
    onSearch: (String) -> Unit,
    onAddSource: () -> Unit,
    onSettings: () -> Unit,
    searchWidth: androidx.compose.ui.unit.Dp,
    showClock: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(searchWidth)) {
            SearchBox(
                value = search,
                placeholder = "Search channels, programmes",
                onValueChange = onSearch,
            )
        }
        if (showClock) {
            Spacer(Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(clockText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(dayText, fontSize = 9.5.sp, color = IptvDim)
            }
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onSettings, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Live TV settings",
                tint = IptvMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier.clip(RoundedCornerShape(999.dp))
                .background(IptvRaised)
                .border(1.dp, IptvBorder, RoundedCornerShape(999.dp))
                .clickable(onClick = onAddSource)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = IptvText, modifier = Modifier.size(15.dp))
            Text("Add source", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HeaderTab(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (active) IptvAccent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) Color.White else IptvMuted,
        )
    }
}

/* ------------------------------------------------------------------ pieces */

@Composable
private fun IptvChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else IptvPanel)
            .border(1.dp, if (selected) Color.White else IptvBorder, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color(0xFF0A0A0E) else IptvMuted,
        )
    }
}

@Composable
private fun SearchBox(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(IptvPanel)
            .border(1.dp, IptvBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = IptvDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = IptvDim, fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = IptvText, fontSize = 13.sp),
                cursorBrush = SolidColor(IptvAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** How a built-in group is shown ("Recent" is kept as the id: it is what's saved). */
internal fun groupLabel(group: String): String = when (group) {
    "Recent" -> "Recently watched"
    "Favorites" -> "Favourites"
    else -> group
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuickGroupChips(
    groups: List<String>,
    selected: String,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit,
) {
    // All is in the Group dropdown already.
    val pinned = listOf("Favorites", "Recent").filter { it in groups }
    if (pinned.isEmpty()) return
    // Wraps to a second line rather than squeezing a chip when the pane is narrow.
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        pinned.forEach { group ->
            val active = group == selected
            Row(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (active) IptvAccent else IptvRaised)
                    .clickable { onSelect(group) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                when (group) {
                    "Favorites" -> Icon(Icons.Filled.Star, contentDescription = null, tint = if (active) Color.White else Color(0xFFFFC857), modifier = Modifier.size(14.dp))
                    "Recent" -> Icon(Icons.Filled.History, contentDescription = null, tint = if (active) Color.White else IptvMuted, modifier = Modifier.size(14.dp))
                    else -> Unit
                }
                Text(
                    groupLabel(group),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) Color.White else IptvText,
                    maxLines = 1,
                    softWrap = false,
                )
                counts[group]?.let {
                    Text(
                        compactCount(it),
                        fontSize = 11.sp,
                        color = if (active) Color.White.copy(alpha = 0.8f) else IptvDim,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** 62395 -> "62.4k", 1200000 -> "1.2M": short enough for a chip. */
private fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
    n >= 10_000 -> String.format(Locale.US, "%.1fk", n / 1_000.0).replace(".0k", "k")
    else -> n.toString()
}

@Composable
private fun GroupSelector(
    groups: List<String>,
    selected: String,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = LocalDensity.current
    val scrollbarHeight = with(density) { listState.layoutInfo.viewportSize.height.toDp() }
    LaunchedEffect(open) {
        if (open) {
            val index = groups.indexOf(selected)
            if (index > 0) listState.scrollToItem(index)
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(IptvPanel)
                .border(1.dp, if (open) Color(0xFF3A3A48) else IptvBorder, RoundedCornerShape(9.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("Group", fontSize = 11.sp, color = IptvDim)
            Text(
                groupLabel(selected),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = IptvText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            counts[selected]?.let {
                Text("$it", fontSize = 11.sp, color = IptvDim)
            }
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Choose group",
                tint = IptvMuted,
                modifier = Modifier.size(19.dp),
            )
        }

        if (open) {
        androidx.compose.ui.window.Popup(
            alignment = Alignment.TopStart,
            offset = androidx.compose.ui.unit.IntOffset(0, 46),
            onDismissRequest = { open = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        ) {
            Column(
                Modifier.width(340.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IptvPanel)
                    .border(1.dp, IptvBorder, RoundedCornerShape(10.dp)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "GROUPS",
                        fontSize = 9.5.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = IptvDim,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        enabled = listState.firstVisibleItemIndex > 0,
                    ) {
                        Text("Top", fontSize = 11.sp, color = IptvMuted)
                    }
                }
                HorizontalDivider(color = IptvBorderSoft)
                Box(Modifier.fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(end = 10.dp),
                    ) {
                        items(groups, key = { it }) { group ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        onSelect(group)
                                        open = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (group == selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = IptvAccent,
                                        modifier = Modifier.size(17.dp),
                                    )
                                } else {
                                    Spacer(Modifier.size(17.dp))
                                }
                                Text(
                                    groupLabel(group),
                                    fontSize = 13.sp,
                                    fontWeight = if (group == selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (group == selected) IptvText else IptvMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                counts[group]?.let {
                                    Text("$it", fontSize = 11.sp, color = IptvDim)
                                }
                            }
                        }
                    }
                    androidx.compose.foundation.VerticalScrollbar(
                        adapter = androidx.compose.foundation.rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .height(scrollbarHeight)
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun StatusBadge(text: String, background: Color, foreground: Color = Color.White) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp)).background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text.uppercase(Locale.US),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.7.sp,
            color = foreground,
        )
    }
}

@Composable
private fun ChannelLogo(name: String, logo: String?, size: Dp) {
    val color = channelTileColors[(name.hashCode() and 0x7fffffff) % channelTileColors.size]
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(name),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.31f).sp,
            letterSpacing = (-0.4).sp,
        )
        logo?.takeIf { it.isNotBlank() }?.let { url ->
            NuvioAsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = IptvAccent) {
    Box(
        modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp))
            .background(Color(0x26FFFFFF)),
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(color),
        )
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    program: EpgProgram?,
    selected: Boolean,
    favorite: Boolean,
    now: Long,
    showName: Boolean,
    showNumber: Boolean,
    showLogo: Boolean,
    showQuality: Boolean,
    compact: Boolean,
    outlineSelected: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val quality = qualityOf(channel.name)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF17171F) else Color.Transparent)
            .then(
                if (selected && outlineSelected) {
                    Modifier.border(1.dp, Color(0xFF33333F), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (showLogo) {
            ChannelLogo(channel.name, channel.logo, if (compact) 38.dp else 46.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (showName) {
                    Text(
                        channel.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else if (showNumber) {
                    Text(
                        "${channel.number}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IptvText,
                    )
                }
                if (showQuality) {
                    quality?.let { StatusBadge(it, Color(0x1FFFFFFF), Color(0xFFDCDCE6)) }
                }
            }
            if (!compact) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showNumber && showName) {
                        Text("${channel.number}", fontSize = 10.sp, color = IptvDim)
                        Text("·", fontSize = 10.sp, color = IptvDim)
                    }
                    Text(
                        program?.title ?: channel.group,
                        fontSize = 11.5.sp,
                        color = IptvMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            program?.let {
                ProgressLine(programProgress(it, now), Modifier.fillMaxWidth().padding(top = 2.dp))
            }
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Filled.Star,
                contentDescription = if (favorite) "Remove favourite" else "Add favourite",
                tint = if (favorite) IptvStar else IptvDim,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun MovieRow(movie: VodItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF17171F) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ChannelLogo(movie.name, movie.logo, 46.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                movie.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(movie.group)
                    movie.durationSeconds?.takeIf { it > 0 }?.let {
                        append(" · ").append(formatDuration(it.toLong()))
                    }
                },
                fontSize = 11.5.sp,
                color = IptvMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PlayerShell(
    height: Dp,
    live: Boolean,
    channel: LiveChannel?,
    movie: VodItem?,
    progress: Float?,
    programStart: Long?,
    programStop: Long?,
    canWatchFromStart: Boolean,
    showNowLine: Boolean,
    timeFormat: (Long) -> String,
    onWatchFromStart: () -> Unit,
    player: @Composable (Modifier) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B0B0F))
            .border(1.dp, IptvBorderSoft, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val name = channel?.name ?: movie?.name
            if (name != null) {
                ChannelLogo(name, channel?.logo ?: movie?.logo, 30.dp)
                Text(
                    name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (live) StatusBadge("Live", IptvLive)
            val quality = (channel?.name ?: movie?.name)?.let(::qualityOf)
            quality?.let { StatusBadge(it, Color(0x1FFFFFFF), Color(0xFFDCDCE6)) }
            Spacer(Modifier.weight(1f))
            channel?.group?.takeIf { it.isNotBlank() }?.let {
                StatusBadge(it, Color(0x14FFFFFF), Color(0xFFB9B9C6))
            }
            if (canWatchFromStart) {
                TextButton(onClick = onWatchFromStart) {
                    Text("Watch from start", fontSize = 12.sp, color = IptvMuted)
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().height(height).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            player(Modifier.fillMaxSize())
        }

        if (live && showNowLine && progress != null && programStart != null && programStop != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(timeFormat(programStart), fontSize = 11.sp, color = IptvDim)
                ProgressLine(progress, Modifier.weight(1f), IptvAccent)
                Text(timeFormat(programStop), fontSize = 11.sp, color = IptvDim)
            }
        }
    }
}

@Composable
private fun TitleRow(
    title: String,
    channelName: String?,
    channelNumber: Int?,
    timeRange: String?,
    live: Boolean,
    watchLabel: String,
    recordLabel: String,
    recording: Boolean,
    favorite: Boolean,
    showLiveActions: Boolean,
    onWatch: () -> Unit,
    onRecord: () -> Unit,
    onFavorite: () -> Unit,
    onGuide: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // LIVE sits above the title, on one line, instead of squeezed into the channel row.
            if (live) StatusBadge("Live", IptvLive)
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                channelName?.let { Text(it, fontSize = 12.5.sp, color = IptvMuted) }
                channelNumber?.let { StatusBadge("$it", Color(0x1FFFFFFF), Color(0xFFDCDCE6)) }
                timeRange?.let { Text(it, fontSize = 12.sp, color = IptvDim) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionPill(watchLabel, Icons.Filled.PlayArrow, primary = true, onClick = onWatch)
            if (showLiveActions) {
                ActionPill(
                    recordLabel,
                    Icons.Filled.RadioButtonChecked,
                    primary = false,
                    tint = if (recording) IptvLive else IptvText,
                    onClick = onRecord,
                )
                ActionPill(
                    if (favorite) "Favourited" else "Favourite",
                    Icons.Filled.Star,
                    primary = false,
                    tint = if (favorite) IptvStar else IptvText,
                    onClick = onFavorite,
                )
                ActionPill("Guide", Icons.Filled.List, primary = false, onClick = onGuide)
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    tint: Color = IptvText,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (primary) IptvAccent else IptvRaised)
            .border(1.dp, if (primary) Color.Transparent else IptvBorder, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (primary) Color.White else tint,
            modifier = Modifier.size(15.dp),
        )
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) Color.White else tint,
        )
    }
}

@Composable
private fun InfoCard(
    label: String,
    title: String,
    body: String?,
    meta: String?,
    progress: Float?,
    modifier: Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(IptvPanel)
            .border(1.dp, IptvBorderSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            label.uppercase(Locale.US),
            fontSize = 10.sp,
            letterSpacing = 1.3.sp,
            color = IptvDim,
        )
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        body?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 12.sp, color = IptvMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        meta?.let { Text(it, fontSize = 11.5.sp, color = IptvDim) }
        progress?.let { ProgressLine(it, Modifier.fillMaxWidth().padding(top = 3.dp)) }
    }
}

@Composable
private fun RecordStrip(recording: Boolean, seconds: Long, channelName: String?, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101016))
            .border(1.dp, IptvBorderSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(if (recording) IptvLive else IptvDim))
        Column(Modifier.weight(1f)) {
            Text(
                if (recording) "Recording ${formatDuration(seconds)}" else "Ready to record",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (recording) Color(0xFFFF8A9B) else IptvMuted,
            )
            channelName?.let {
                Text(it, fontSize = 11.sp, color = IptvDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (recording) {
            TextButton(onClick = onStop) { Text("Stop", fontSize = 12.sp, color = IptvMuted) }
        }
    }
}

@Composable
private fun RecordingsCard(
    recordings: List<Path>,
    onRefresh: () -> Unit,
    onOpenFolder: () -> Unit,
    onChangeFolder: () -> Unit,
    onPlay: (Path) -> Unit,
    onDelete: (Path) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IptvPanel)
            .border(1.dp, IptvBorderSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recordings", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text("Refresh", fontSize = 12.sp, color = IptvMuted) }
            TextButton(onClick = onChangeFolder) { Text("Change folder", fontSize = 12.sp, color = IptvMuted) }
            TextButton(onClick = onOpenFolder) { Text("Open folder", fontSize = 12.sp, color = IptvMuted) }
        }
        if (recordings.isEmpty()) {
            Text(
                "No recordings yet.",
                fontSize = 12.sp,
                color = IptvDim,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            recordings.forEach { path ->
                RecordingRow(path = path, onPlay = { onPlay(path) }, onDelete = { onDelete(path) })
            }
        }
    }
}

@Composable
private fun GuideCard(
    programs: List<EpgProgram>,
    now: Long,
    catchup: CatchupInfo?,
    channelUrl: String?,
    showTimes: Boolean,
    showDetails: Boolean,
    showNowLine: Boolean,
    timeFormat: (Long) -> String,
    onPlayFromArchive: (EpgProgram) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IptvPanel)
            .border(1.dp, IptvBorderSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Guide", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${programs.size} programmes", fontSize = 11.sp, color = IptvDim)
        }
        Spacer(Modifier.height(6.dp))
        programs.take(40).forEach { program ->
            val archiveUrl = channelUrl?.takeIf { it.isNotBlank() }?.let { url ->
                catchup
                    ?.takeIf { CatchupUrlBuilder.isAvailable(it, program.startMs, now) }
                    ?.let { CatchupUrlBuilder.build(url, it, program.startMs, program.stopMs) }
            }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = archiveUrl != null) { onPlayFromArchive(program) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showTimes) {
                    Text(
                        timeFormat(program.startMs),
                        fontSize = 11.5.sp,
                        color = IptvDim,
                        modifier = Modifier.width(46.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        program.title,
                        fontSize = 12.5.sp,
                        fontWeight = if (program.isLive(now)) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showDetails) {
                        program.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 11.sp, color = IptvDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (showNowLine && program.isLive(now)) StatusBadge("Live", IptvLive)
                if (archiveUrl != null) Text("Replay", fontSize = 10.5.sp, color = IptvMuted)
            }
            HorizontalDivider(color = IptvBorderSoft)
        }
    }
}

@Composable
private fun RecordingRow(path: Path, onPlay: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(path.fileName.toString(), maxLines = 1, fontSize = 12.sp)
            Text(
                "${runCatching { path.toFile().length() / 1024 / 1024 }.getOrDefault(0L)} MB",
                color = IptvDim,
                fontSize = 10.5.sp,
            )
        }
        TextButton(onClick = onPlay) { Text("Play", fontSize = 12.sp) }
        TextButton(onClick = onDelete) { Text("Delete", fontSize = 12.sp, color = IptvMuted) }
    }
}

@Composable
private fun SourcesDialog(
    sources: List<IptvSource>,
    onDismiss: () -> Unit,
    onEdit: (IptvSource) -> Unit,
    onRemove: (IptvSource) -> Unit,
    onAdd: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sources") },
        text = {
            Column(Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sources.isEmpty()) {
                    Text("No sources yet.", color = IptvMuted, fontSize = 12.5.sp)
                } else {
                    sources.forEach { source ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(source.name, fontSize = 13.sp, maxLines = 1)
                                Text(
                                    when (source.kind) {
                                        "xtream" -> "Xtream · ${source.url}"
                                        "stalker" -> "Stalker · ${source.url}"
                                        else -> source.url
                                    },
                                    fontSize = 10.5.sp,
                                    color = IptvDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { onEdit(source) }) { Text("Edit", fontSize = 12.sp) }
                            TextButton(onClick = { onRemove(source) }) { Text("Remove", fontSize = 12.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onAdd) { Text("Add source") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (IptvSource) -> Unit,
    existing: IptvSource? = null,
    saveError: String? = null,
) {
    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: "m3u") }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember(existing?.id) { mutableStateOf(existing?.url.orEmpty()) }
    var epgUrl by remember(existing?.id) { mutableStateOf(existing?.epgUrl.orEmpty()) }
    var username by remember(existing?.id) { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember(existing?.id) { mutableStateOf(existing?.password.orEmpty()) }
    var attempted by remember { mutableStateOf(false) }
    val isM3u = kind == "m3u"
    val isXtream = kind.equals("xtream", true)
    val isStalker = kind.equals("stalker", true)
    val macValid = StalkerClient.isValidMac(username)
    val xtreamLinkCredentials = if (isXtream) xtreamCredentialsFromLink(url) else null
    val xtreamUser = username.ifBlank { xtreamLinkCredentials?.first.orEmpty() }
    val xtreamPass = password.ifBlank { xtreamLinkCredentials?.second.orEmpty() }
    val xtreamServer = cleanXtreamServer(url)
    val playlistUrl = normalizePlaylistUrl(url)
    val valid = when {
        isXtream -> xtreamServer.startsWith("http") &&
            xtreamUser.isNotBlank() && xtreamPass.isNotBlank()
        isStalker -> xtreamServer.isNotBlank() && macValid
        else -> playlistUrl.isNotBlank()
    }
    val validationMessage = when {
        url.isBlank() -> "Enter a playlist URL, server or portal address."
        isXtream && !xtreamServer.startsWith("http") ->
            "Enter the server as host:port or http://host:port."
        isXtream && xtreamUser.isBlank() -> "Enter the Xtream username."
        isXtream && xtreamPass.isBlank() -> "Enter the Xtream password."
        isStalker && !xtreamServer.startsWith("http") ->
            "Enter the portal as host:port or http://host:port."
        isStalker && username.isBlank() -> "Enter the MAC address the provider gave you."
        isStalker && !macValid -> "Enter a MAC address like 00:1A:79:AB:CD:EF."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit IPTV source" else "Add IPTV source") },
        text = {
            Column(
                Modifier.width(470.dp).height(400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IptvChip("M3U playlist", kind == "m3u") { kind = "m3u" }
                    IptvChip("Xtream", kind == "xtream") { kind = "xtream" }
                    IptvChip("Stalker", kind == "stalker") { kind = "stalker" }
                }
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    url,
                    { value ->
                        url = value
                        // A pasted Xtream link is recognised even if the M3U tab is selected.
                        xtreamCredentialsFromLink(value)?.let { (linkUser, linkPass) ->
                            if (isM3u) kind = "xtream"
                            if (username.isBlank()) username = linkUser
                            if (password.isBlank()) password = linkPass
                        }
                    },
                    label = {
                        Text(
                            when {
                                kind == "xtream" -> "Server URL"
                                kind == "stalker" -> "Portal URL"
                                else -> "Playlist URL"
                            },
                        )
                    },
                    placeholder = {
                        Text(
                            when {
                                kind == "xtream" -> "host:port or http://host:port"
                                kind == "stalker" -> "http://portal:port/c"
                                else -> "https://example.com/playlist.m3u or C:\\playlist.m3u"
                            },
                        )
                    },
                    isError = attempted && !valid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (attempted && validationMessage != null) {
                    Text(
                        validationMessage,
                        fontSize = 11.sp,
                        color = Color(0xFFFF8A9B),
                    )
                }
                if (kind == "xtream") {
                    OutlinedTextField(
                        username,
                        { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kind == "stalker") {
                    OutlinedTextField(
                        username,
                        { username = it },
                        label = { Text("MAC address") },
                        placeholder = { Text("00:1A:79:AB:CD:EF") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kind != "stalker") {
                    OutlinedTextField(
                        epgUrl,
                        { epgUrl = it },
                        label = { Text("XMLTV guide URL (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kind == "stalker") {
                    Text(
                        "The portal's own guide is used. The MAC address must be the one the provider gave you.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (kind == "xtream") {
                    Text(
                        "Enter the server as host:port or http://host:port. A pasted get.php?username=…&password=… link fills the login automatically.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (kind == "m3u") {
                    Text(
                        "Paste an http(s) playlist link, a file:// URL, or a local .m3u path such as C:\\playlist.m3u.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (saveError != null) {
                    Text(
                        saveError,
                        fontSize = 11.sp,
                        color = Color(0xFFFF8A9B),
                    )
                }
                Text(
                    "Sources are saved privately on this computer. Add only streams you can access.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!valid) {
                        attempted = true
                        OneTubeDebugLog.line(
                            "iptv-add-blocked kind=$kind url=${playlistUrlShape(url)} " +
                                "user=${username.isNotBlank()} pass=${password.isNotBlank()} " +
                                "reason=${validationMessage ?: "incomplete"}",
                        )
                        return@Button
                    }
                    val sourceUrl = when {
                        isXtream -> xtreamServer
                        isStalker -> xtreamServer
                        else -> playlistUrl
                    }
                    OneTubeDebugLog.line(
                        "iptv-add kind=$kind url=${playlistUrlShape(url)} " +
                            "normalized=${playlistUrlShape(sourceUrl)} " +
                            "user=${username.isNotBlank()} pass=${password.isNotBlank()}",
                    )
                    onAdd(
                        IptvSource(
                            existing?.id ?: UUID.randomUUID().toString(),
                            name.ifBlank { if (isStalker) "Stalker portal" else if (isXtream) "Xtream" else "Playlist" },
                            sourceUrl,
                            if (isStalker) "" else epgUrl.trim(),
                            kind,
                            when {
                                isXtream -> xtreamUser.trim()
                                isStalker -> StalkerClient.normalizeMac(username)
                                else -> username.trim()
                            },
                            if (isStalker) "" else if (isXtream) xtreamPass else password,
                        ),
                    )
                },
                enabled = true,
            ) { Text(if (existing != null) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/* ------------------------------------------------------------------ helpers */

private fun qualityOf(name: String): String? {
    val match = QualityPattern.find(name) ?: return null
    return match.value.uppercase(Locale.US)
}

private val AdultGroupPattern = Regex(
    """\b(?:adult|xxx+|porn|18\s*\+)""",
    RegexOption.IGNORE_CASE,
)
private val QualityPattern = Regex("\\b(4K|UHD|FHD|HD|SD)\\b", RegexOption.IGNORE_CASE)

internal fun isAdultGroup(group: String): Boolean = AdultGroupPattern.containsMatchIn(group)

internal fun orderChannelsByCustom(
    channels: List<LiveChannel>,
    settings: IptvSettings,
): List<LiveChannel> {
    val groupRank = settings.customGroupOrder.withIndex().associate { it.value to it.index }
    val channelRank = settings.customChannelOrder.mapValues { (_, keys) ->
        keys.withIndex().associate { it.value to it.index }
    }
    return channels.sortedWith(
        compareBy(
            { groupRank[it.group] ?: Int.MAX_VALUE },
            { channelRank[it.group]?.get(it.key) ?: Int.MAX_VALUE },
        ),
    )
}

private fun initialsOf(name: String): String {
    val words = name.split(' ', '-', '|', '/').filter { it.isNotBlank() }
    val letters = if (words.size >= 2) {
        listOf(words[0].first(), words[1].first())
    } else {
        name.trim().take(2).toList()
    }
    return letters.joinToString("").uppercase(Locale.US)
}

private fun programProgress(program: EpgProgram, now: Long): Float {
    val total = program.stopMs - program.startMs
    if (total <= 0L) return 0f
    return ((now - program.startMs).toFloat() / total).coerceIn(0f, 1f)
}

private fun programMinutes(program: EpgProgram): Int =
    ((program.stopMs - program.startMs) / 60_000L).toInt().coerceAtLeast(1)

private fun clock(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun dayLabel(timestamp: Long): String =
    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(timestamp))

private fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}


private val iptvGroupPrefix = Regex("""^\s*[|\[(]?\s*[A-Za-z]{2,3}\s*[|\]):\-–]+\s*""")

/** "US| Sports" -> "Sports", "[UK] News" -> "News", "|EN| Movies" -> "Movies". */
internal fun cleanIptvGroupName(group: String): String {
    val cleaned = iptvGroupPrefix.replace(group, "").trim()
    return cleaned.ifBlank { group }
}

/** Takes the user's cleanup words off the start or end of a channel name. */
internal fun cleanIptvChannelName(name: String, removals: List<String>): String {
    if (removals.isEmpty()) return name
    var result = name.trim()
    var changed = true
    while (changed) {
        changed = false
        for (word in removals) {
            val w = word.trim()
            if (w.isEmpty()) continue
            val start = Regex("""^\s*[|:\-–]?\s*${Regex.escape(w)}\s*[|:\-–]?\s+""", RegexOption.IGNORE_CASE)
            val end = Regex("""\s+[|:\-–(\[]?\s*${Regex.escape(w)}\s*[)\]]?\s*$""", RegexOption.IGNORE_CASE)
            val next = result.replace(start, "").replace(end, "").trim()
            if (next != result && next.isNotBlank()) {
                result = next
                changed = true
            }
        }
    }
    return result
}
