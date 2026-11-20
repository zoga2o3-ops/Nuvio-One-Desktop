package com.nuvio.app.features.youtube

import com.nuvio.app.features.autosync.AutoSyncMpvEngineController
import com.nuvio.app.features.player.desktop.NativePlayerController
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.border
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.PlayerControlsAction
import com.nuvio.app.features.player.formatPlaybackSpeedLabel
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.composed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.aspectRatio
import com.nuvio.app.core.ui.toggleFullscreenAction
import com.nuvio.app.core.ui.isFullscreenActionActive
import com.nuvio.app.core.ui.CoverNativeVideo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.desktop.DesktopPlayerVolumeStorage
import com.nuvio.app.features.player.PlatformPlayerSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val MusicBg = Color(0xFF202024)
private val MusicPanel = Color(0xFF29292F)
private val MusicPanelRaised = Color(0xFF35353D)
private val MusicBar = Color(0xFF24242A)
private val MusicAccent = Color(0xFF9AA8BD)
private val MusicAccentFill = Color(0xFF64748B)
private val MusicMuted = Color(0xFFFFFFFF)
private val MusicDim = Color(0xFFF0F0F4)

/** Whether the Music section is on screen. Elsewhere, the bottom bar becomes a small pill. */
internal object MusicShellState {
    var inMusic by mutableStateOf(false)

    /** Where the pill was dragged to (window pixels); null = bottom centre. */
    var pillOffset by mutableStateOf<Offset?>(null)

    /** A video in another section is playing; only one player can run at a time. */
    var otherVideoActive by mutableStateOf(false)
}

/** The small pill shown while another section is open. It starts at the bottom centre and can be dragged. */
@Composable
private fun MusicPillBar(item: YouTubeVideoItem, playing: Boolean, onToggle: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val windowW = with(density) { maxWidth.toPx() }
        val windowH = with(density) { maxHeight.toPx() }
        val margin = with(density) { 8.dp.toPx() }
        var pillSize by remember { mutableStateOf(IntSize.Zero) }
        var dragging by remember { mutableStateOf(false) }

        fun clamp(o: Offset): Offset = Offset(
            x = o.x.coerceIn(0f, (windowW - pillSize.width).coerceAtLeast(0f)),
            y = o.y.coerceIn(0f, (windowH - pillSize.height).coerceAtLeast(0f)),
        )
        fun current(): Offset = MusicShellState.pillOffset ?: clamp(
            Offset(
                x = (windowW - pillSize.width) / 2f,
                y = windowH - pillSize.height - margin,
            ),
        )

        val pos = current()
        Row(
            Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .onSizeChanged { pillSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, drag ->
                        change.consume()
                        MusicShellState.pillOffset = clamp(current() + drag)
                    }
                }
                .graphicsLayer {
                    val scale = if (dragging) 1.04f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, if (dragging) MusicAccent else Color.Transparent, RoundedCornerShape(999.dp))
                .background(MusicPanel)
                .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Grip: a vertical line that shows the pill can be moved.
            Box(
                Modifier.width(3.dp).height(26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (dragging) MusicAccent else MusicDim.copy(alpha = 0.55f)),
            )
            MusicArtwork(item, 36.dp, rounded = 18.dp)
            Column(Modifier.widthIn(max = 220.dp).padding(horizontal = 4.dp)) {
                Text(
                    item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.author.orEmpty(),
                    fontSize = 11.sp,
                    color = MusicDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = MusicPlaybackQueue::previous, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = MusicPlaybackQueue::next, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next track", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            // Closing the pill stops the music and clears the queue, as Stop does on the Now Playing page.
            IconButton(onClick = { MusicPlaybackQueue.clear() }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close music player", tint = MusicDim, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Volume shared by the music bar, Now Playing and the desktop player's own shortcuts. */
// The surface hands back a wrapper (AutoSync) around the native player; the swap needs the native one itself.
private fun nativePlayerOf(controller: PlayerEngineController?): NativePlayerController? {
    val unwrapped = (controller as? AutoSyncMpvEngineController)?.base ?: controller
    return unwrapped as? NativePlayerController
}

private object MusicVolume {
    private val stored = DesktopPlayerVolumeStorage.loadVolumeLevel()
    var level by mutableStateOf((stored ?: 1f).coerceIn(0f, 1f))
        private set
    private var lastNonZero = if (level > 0f) level else 1f

    fun set(value: Float) {
        level = value.coerceIn(0f, 1f)
        if (level > 0f) lastNonZero = level
    }

    fun toggleMute() {
        level = if (level > 0f) {
            lastNonZero = level
            0f
        } else {
            lastNonZero
        }
    }
}

@Composable
internal fun MusicPlayerHost(modifier: Modifier = Modifier) {
    val item = MusicPlaybackQueue.current
    LaunchedEffect(item?.videoId) {
        if (item == null) MusicPlaybackSession.reset()
    }
    if (item == null) return
    var playback by remember(item.videoId) { mutableStateOf<YouTubePlayback?>(null) }
    var error by remember(item.videoId) { mutableStateOf<String?>(null) }
    var radioBusy by remember(item.videoId) { mutableStateOf(false) }
    var controller by remember { mutableStateOf<PlayerEngineController?>(null) }
    // Where the artwork frame and the whole window are on screen: the one player window is laid over them.
    var artworkBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // Changes on every seek of the music video (a position jump), to start the seek log.
    var seekStamp by remember { mutableStateOf(0L) }
    var rootBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var videoLoaded by remember(item.videoId) { mutableStateOf(false) }
    var snapshot by remember(item.videoId) { mutableStateOf(PlayerPlaybackSnapshot()) }
    var showLyrics by remember { mutableStateOf(true) }
    var lyrics by remember(item.videoId) { mutableStateOf<List<MusicLyricLine>>(emptyList()) }
    var lyricsLoading by remember(item.videoId) { mutableStateOf(false) }
    var lyricsError by remember(item.videoId) { mutableStateOf<String?>(null) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var accountChoices by remember { mutableStateOf<List<YouTubePlaylistChoice>>(emptyList()) }
    var accountChoicesLoading by remember { mutableStateOf(false) }
    var showVideo by remember { mutableStateOf(false) }
    // "Watch video" swaps the audio-only stream for the video stream at the same position, and back.
    var videoPlayback by remember(item.videoId) { mutableStateOf<YouTubePlayback?>(null) }
    var videoPositionMs by remember(item.videoId) { mutableStateOf(0L) }
    var audioResumeMs by remember(item.videoId) { mutableStateOf(0L) }
    var videoLoading by remember(item.videoId) { mutableStateOf(false) }
    val videoAvailable = item.localUrl == null &&
        item.videoId.length == 11 &&
        item.videoId.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistTracks by remember { mutableStateOf<List<YouTubeVideoItem>>(emptyList()) }
    var countedListen by remember(item.videoId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Radio keeps this track playing and replaces "Up next" with its radio. It falls back to
    // related tracks, and says so when neither answers.
    fun startRadio() {
        if (radioBusy) return
        radioBusy = true
        error = null
        scope.launch {
            val radio = runCatching { MusicBitChordRepository.radio(item.videoId) }.getOrDefault(emptyList())
            val tracks = radio.ifEmpty {
                runCatching { MusicAutoplay.related(item) }.getOrDefault(emptyList())
            }
            radioBusy = false
            if (tracks.isEmpty()) {
                error = "Radio isn't available right now. Try again in a moment."
            } else {
                MusicPlaybackQueue.replaceUpNext(item.videoId, tracks)
            }
        }
    }

    LaunchedEffect(controller) { MusicPlaybackSession.controller = controller }
    LaunchedEffect(
        controller,
        MusicEqualizer.preset,
        MusicEqualizer.gains,
        MusicPlaybackSettings.skipSilence,
        MusicPlaybackSettings.fadeSeconds,
    ) {
        MusicAudioEffects.apply(controller)
    }
    LaunchedEffect(controller, MusicPlaybackSettings.playbackSpeed) {
        controller?.setPlaybackSpeed(MusicPlaybackSettings.playbackSpeed)
    }
    LaunchedEffect(controller) { controller?.setVolume(MusicVolume.level) }
    LaunchedEffect(item.videoId) {
        OneTubeDebugLog.line("music track start ${item.videoId}")
    }
    // The queue runs short (shuffle, a short list): add related music, as a station does, so playback doesn't end after a few songs.
    var stationSeed by remember { mutableStateOf("") }
    LaunchedEffect(MusicPlaybackQueue.index, MusicPlaybackQueue.items.size) {
        val seed = MusicPlaybackQueue.current ?: return@LaunchedEffect
        val left = MusicPlaybackQueue.items.size - MusicPlaybackQueue.index - 1
        if (left > 3 || stationSeed == seed.videoId) return@LaunchedEffect
        stationSeed = seed.videoId
        val candidates = runCatching { MusicBitChordRepository.radio(seed.videoId) }.getOrNull().orEmpty()
            .filter { MusicTrackFilter.isMusic(it) }
        val more = MusicStation.extend(MusicPlaybackQueue.items, candidates, 25)
        if (more.isNotEmpty()) MusicPlaybackQueue.append(more)
    }
    // After each seek of the music video, log the player's counters every 300 ms for 3 s (diagnostic).
    LaunchedEffect(seekStamp) {
        if (seekStamp == 0L) return@LaunchedEffect
        repeat(10) { step ->
            delay(300L)
            OneTubeDebugLog.line("music seek +${(step + 1) * 300}ms ${nativePlayerOf(controller)?.debugStats()}")
        }
    }
    LaunchedEffect(showVideo) {
        if (showVideo) {
            delay(3_000L)
            OneTubeDebugLog.line("music video +3s ${nativePlayerOf(controller)?.debugStats()}")
        }
    }
    LaunchedEffect(snapshot) { MusicPlaybackSession.snapshot = snapshot }
    LaunchedEffect(Unit) { MusicLibraryStore.ensureLoaded() }
    LaunchedEffect(Unit) {
        MusicEqualizer.ensureLoaded()
        MusicPlaybackSettings.ensureLoaded()
        MusicListeningStore.ensureLoaded()
        MusicScrobbler.ensureLoaded()
    }
    LaunchedEffect(item.videoId, snapshot.positionMs, snapshot.isPlaying) {
        if (countedListen || !snapshot.isPlaying) return@LaunchedEffect
        val duration = snapshot.durationMs.takeIf { it > 0 } ?: item.durationMs
        val threshold = if (duration > 0) minOf(30_000L, duration / 2) else 30_000L
        if (snapshot.positionMs >= threshold) {
            countedListen = true
            MusicListeningStore.record(item)
        }
    }
    LaunchedEffect(snapshot.isEnded, item.videoId) {
        if (snapshot.isEnded) countedListen = false
    }
    LaunchedEffect(item.videoId, snapshot.isPlaying, snapshot.positionMs) {
        MusicScrobbler.observe(item, snapshot)
    }

    LaunchedEffect(item.videoId) {
        playback = null
        error = null
        snapshot = PlayerPlaybackSnapshot()
        if (item.localUrl != null) {
            playback = YouTubePlayback(
                videoUrl = item.localUrl,
                audioUrl = null,
                title = item.title,
                author = item.author,
                headers = item.playbackHeaders,
            )
        } else {
            val addonPlayback = runCatching { MusicAddonSource.resolve(item) }.getOrNull()
            if (addonPlayback != null) {
                playback = addonPlayback
            } else {
                val resolveStart = System.currentTimeMillis()
                OneTubeDebugLog.line("music resolve start ${item.videoId}")
                runCatching {
                    MusicStreamResolver.resolve(item, MusicPlaybackSettings.audioQuality.maxKbps)
                }
                    .onSuccess {
                        OneTubeDebugLog.line("music resolve done ${item.videoId} in ${System.currentTimeMillis() - resolveStart}ms")
                        playback = it
                    }
                    .onFailure { error = it.message ?: "Could not play this track." }
            }
        }
    }

    // The player couldn't open the stream (expired or refused): look once more,
    // this time for the YouTube Music song version, before giving up.
    var retriedStream by remember(item.videoId) { mutableStateOf(false) }
    LaunchedEffect(error, item.videoId) {
        if (error == null || item.localUrl != null) return@LaunchedEffect
        if (playback != null && !retriedStream) {
            retriedStream = true
            val failed = playback
            MusicStreamResolver.invalidate(item.videoId)
            val retry = runCatching {
                MusicStreamResolver.resolve(item, MusicPlaybackSettings.audioQuality.maxKbps, skipDirect = true)
            }.getOrNull()
            if (retry != null && retry.videoUrl != failed?.videoUrl) {
                error = null
                playback = retry
                return@LaunchedEffect
            }
        }
        // Unplayable: say so briefly, then carry on with the queue.
        delay(2_500L)
        if (MusicPlaybackQueue.current?.videoId == item.videoId &&
            MusicPlaybackQueue.index < MusicPlaybackQueue.items.lastIndex
        ) {
            MusicPlaybackQueue.next()
        }
    }

    // Once this track is playing, look up the next one so skipping is instant.
    LaunchedEffect(item.videoId, snapshot.isPlaying) {
        if (!snapshot.isPlaying) return@LaunchedEffect
        delay(1_500L)
        // Two ahead: some first lookups take ~20 s on YouTube's side.
        for (offset in 1..2) {
            MusicPlaybackQueue.items.getOrNull(MusicPlaybackQueue.index + offset)?.let {
                MusicStreamResolver.prefetch(it, MusicPlaybackSettings.audioQuality.maxKbps)
            }
        }
    }
    LaunchedEffect(Unit) { MusicStreamResolver.warmUp() }

    LaunchedEffect(showLyrics, item.videoId) {
        if (!showLyrics || lyrics.isNotEmpty()) return@LaunchedEffect
        lyricsLoading = true
        lyricsError = null
        lyrics = MusicLyricsRepository.lyrics(item).orEmpty()
        if (lyrics.isEmpty()) lyricsError = "No lyrics found for this track."
        lyricsLoading = false
    }
    // Timed lyrics follow the album recording: line them up with a longer music video.
    LaunchedEffect(item.videoId, lyrics.isNotEmpty(), snapshot.durationMs > 0) {
        if (lyrics.none { it.timeMs > 0 }) return@LaunchedEffect
        MusicLyricsTiming.align(item, snapshot.durationMs)
    }

    LaunchedEffect(snapshot.isEnded) {
        if (!snapshot.isEnded) return@LaunchedEffect
        if (MusicPlaybackQueue.repeatMode == MusicRepeatMode.ONE) {
            controller?.trySeekTo(0L)
            controller?.play()
            return@LaunchedEffect
        }
        val canAdvance = MusicPlaybackQueue.index < MusicPlaybackQueue.items.lastIndex ||
            MusicPlaybackQueue.repeatMode == MusicRepeatMode.ALL
        if (canAdvance) {
            MusicPlaybackQueue.next()
            return@LaunchedEffect
        }
        if (!MusicPlaybackSettings.autoplay || !videoAvailable) return@LaunchedEffect
        val related = MusicAutoplay.related(item)
        if (related.isEmpty()) return@LaunchedEffect
        MusicPlaybackQueue.append(related)
        MusicPlaybackQueue.next()
    }

    val likedTracks = MusicLibraryStore.liked
    val playlists = MusicLibraryStore.playlists
    val isLiked = likedTracks.any { it.videoId == item.videoId }

    val appFullscreen = isFullscreenActionActive()
    fun exitVideo() {
        audioResumeMs = videoPositionMs
        videoPlayback = null
        controller = null
        showVideo = false
    }
    // Leaves the video first: the artist page opens behind Now Playing otherwise, and Now Playing stays up while a video is on.
    fun openArtistFromPlayer() {
        val artist = musicArtistsFor(item).firstOrNull()
        if (appFullscreen) toggleFullscreenAction()
        exitVideo()
        openMusicArtist(artist?.name ?: item.author.orEmpty(), artist?.id)
    }
    // The video's own player. It lives in the artwork frame, or fills the window in full screen.
    val videoSurface: @Composable (Modifier) -> Unit = { surfaceModifier ->
        // Through the stream proxy, as OneTube's streams are: YouTube throttles a single long download,
        // which made the picture stop and start.
        val video = videoPlayback?.let(YouTubeStreamProxy::wrapPlayback)
        // The start point is taken once per video. The player reloads its source whenever this value changes,
        // so passing the live position made the picture stop and restart on every tick.
        val startMs = remember(video?.videoUrl, item.videoId) { videoPositionMs }
        if (video != null) {
            key(item.videoId) {
                PlatformPlayerSurface(
                    sourceUrl = video.videoUrl,
                    sourceAudioUrl = video.audioUrl,
                    sourceHeaders = video.headers,
                    modifier = surfaceModifier,
                    initialPositionMs = startMs.takeIf { it > 0L },
                    initialPositionRequestKey = "music-video-${item.videoId}",
                    useNativeController = true,
                    playerControlsState = PlayerControlsState(
                        hideControls = !appFullscreen,
                        hideBackButton = true,
                        showMusicControls = true,
                    ),
                    // Music buttons in the native bar. Queue, playlist and artist use Compose panels that the
                    // video window covers, so in full screen they first leave full screen.
                    onPlayerControlsAction = { action ->
                        when (action) {
                            PlayerControlsAction.MusicShuffle -> {
                                MusicPlaybackQueue.setShuffleEnabled(!MusicPlaybackQueue.shuffle)
                                true
                            }
                            PlayerControlsAction.MusicRepeat -> {
                                MusicPlaybackQueue.cycleRepeat()
                                true
                            }
                            PlayerControlsAction.MusicQueue -> {
                                if (appFullscreen) toggleFullscreenAction()
                                true
                            }
                            PlayerControlsAction.MusicPlaylist -> {
                                if (appFullscreen) toggleFullscreenAction()
                                showPlaylistPicker = true
                                true
                            }
                            PlayerControlsAction.MusicArtist -> {
                                openArtistFromPlayer()
                                true
                            }
                            else -> false
                        }
                    },
                    onControllerReady = {
                        controller = it
                        OneTubeDebugLog.line("music video controller ready")
                    },
                    onSnapshot = {
                        snapshot = it
                        if (kotlin.math.abs(it.positionMs - videoPositionMs) > 3_000L && videoPositionMs > 0L) {
                            seekStamp = System.currentTimeMillis()
                        }
                        videoPositionMs = it.positionMs
                        // Keep the sound going while the picture refills after a seek (see the controller).
                        nativePlayerOf(controller)?.setKeepPlayingWhileBuffering(true)
                    },
                    onError = { error = it },
                )
            }
        }
    }
    // Shown in the Now Playing header while the video is on.
    val videoControlsContent: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Taller touch areas: the labels are small, and taps just above or beside them were missing.
            TextButton(onClick = { toggleFullscreenAction() }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 6.dp)) { Text("Full screen", color = MusicMuted, fontSize = 12.5.sp) }
            TextButton(onClick = { exitVideo() }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 6.dp)) { Text("Back to music", color = MusicMuted, fontSize = 12.5.sp) }
        }
    }
    // Takes the album-art place in Now Playing. The one player window is laid over this frame, so here it
    // is only the black backdrop, the loading text, and the place the window measures itself against.
    val videoArtworkContent: @Composable () -> Unit = {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val viewportHeight = with(density) {
                LocalWindowInfo.current.containerSize.height.toDp()
            }
            val maxVideoHeight = (viewportHeight - 550.dp).coerceAtLeast(180.dp)
            val naturalHeight = maxWidth * 9f / 16f
            val videoHeight = minOf(naturalHeight, maxVideoHeight)
            val videoWidth = minOf(maxWidth, videoHeight * 16f / 9f)
            Box(
                Modifier.size(width = videoWidth, height = videoHeight)
                    .onGloballyPositioned { artworkBounds = it.boundsInWindow() }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (videoPlayback == null) {
                    Text("Loading video…", color = MusicMuted, fontSize = 12.5.sp)
                } else if (appFullscreen) {
                    Text("Playing full screen", color = MusicMuted, fontSize = 12.5.sp)
                }
            }
        }
    }

    // Video mode carries over to the next queue item: it loads that item's video from the start, or
    // falls back to audio when there isn't one.
    LaunchedEffect(item.videoId) {
        if (!showVideo) return@LaunchedEffect
        videoPlayback = null
        videoPositionMs = 0L
        val video = if (videoAvailable) {
            runCatching { YouTubeEngine.resolvePlayback(item, false, null) }.getOrNull()
        } else {
            null
        }
        if (video != null) videoPlayback = MusicAudioCache.localAudio(video) else exitVideo()
    }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        CoverNativeVideo(showVideo && (showPlaylistPicker || showNewPlaylist))
        Box(modifier.fillMaxSize().onGloballyPositioned { rootBounds = it.boundsInWindow() }) {
        // Audio: a 3 dp player window in the corner. It is only in the composition while the video is off;
        // the video has its own player (in the Now Playing artwork frame, or full screen), so the audio stops
        // here and the video starts there.
        playback?.takeIf { !showVideo && !MusicShellState.otherVideoActive }?.let { raw ->
            // Audio goes through the stream proxy too (see the video above).
            val active = YouTubeStreamProxy.wrapPlayback(raw)
            key(item.videoId) {
                PlatformPlayerSurface(
                    sourceUrl = active.videoUrl,
                    sourceAudioUrl = active.audioUrl,
                    sourceHeaders = active.headers,
                    modifier = Modifier.align(Alignment.BottomStart).size(3.dp),
                    initialPositionMs = audioResumeMs.takeIf { it > 0L },
                    initialPositionRequestKey = "music-audio-${item.videoId}",
                    useNativeController = true,
                    playerControlsState = PlayerControlsState(),
                    onControllerReady = {
                        controller = it
                        OneTubeDebugLog.line("music controller ready")
                    },
                    onSnapshot = { snapshot = it },
                    onError = { error = it },
                )
            }
        }

        // The video player sits here, at a fixed place in the tree, whether it is in the artwork frame or full
        // screen. Only its size and position change, so turning full screen on or off doesn't rebuild the player
        // (a rebuild cut the audio for a few seconds).
        if (showVideo) {
            val density = LocalDensity.current
            val root = rootBounds
            val target: androidx.compose.ui.geometry.Rect? = if (appFullscreen) root else artworkBounds
            if (target != null && root != null) {
                val offsetX = with(density) { (target.left - root.left).toDp() }
                val offsetY = with(density) { (target.top - root.top).toDp() }
                videoSurface(
                    Modifier
                        .offset(x = offsetX, y = offsetY)
                        .size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() }),
                )
            }
        }

        when {
            appFullscreen && showVideo -> Box(Modifier.fillMaxSize().background(Color.Black))
            MusicPlaybackQueue.expanded || showVideo -> NowPlayingScreen(
                item = item,
                snapshot = snapshot,
                error = error,
                playing = snapshot.isPlaying,
                isLiked = isLiked,
                shuffle = MusicPlaybackQueue.shuffle,
                repeatMode = MusicPlaybackQueue.repeatMode,
                showLyrics = showLyrics,
                lyrics = lyrics,
                lyricsLoading = lyricsLoading,
                lyricsError = lyricsError,
                onToggle = { if (snapshot.isPlaying) controller?.pause() else controller?.play() },
                onSeek = { controller?.trySeekTo(it) },
                onNext = MusicPlaybackQueue::next,
                onPrevious = MusicPlaybackQueue::previous,
                onToggleShuffle = { MusicPlaybackQueue.setShuffleEnabled(!MusicPlaybackQueue.shuffle) },
                onCycleRepeat = MusicPlaybackQueue::cycleRepeat,
                onToggleLike = { MusicLibraryStore.toggleLike(item) },
                onToggleLyrics = { showLyrics = !showLyrics },
                onOpenArtist = { openArtistFromPlayer() },
                onAddToPlaylist = { showPlaylistPicker = true },
                onSaveQueue = {
                    newPlaylistName = ""
                    newPlaylistTracks = MusicPlaybackQueue.items
                    showNewPlaylist = true
                },
                onRemoveQueue = MusicPlaybackQueue::removeAt,
                onMoveQueue = MusicPlaybackQueue::move,
                onClose = { MusicPlaybackQueue.clear() },
                onCollapse = { exitVideo(); MusicPlaybackQueue.expanded = false },
                videoAvailable = videoAvailable,
                videoLoading = videoLoading,
                radioBusy = radioBusy,
                onStartRadio = { startRadio() },
                volume = MusicVolume.level,
                onVolumeChange = {
                    MusicVolume.set(it)
                    controller?.setVolume(MusicVolume.level)
                },
                onToggleMute = {
                    MusicVolume.toggleMute()
                    controller?.setVolume(MusicVolume.level)
                },
                videoControls = if (showVideo) videoControlsContent else null,
                videoArtwork = if (showVideo) videoArtworkContent else null,
                videoActive = showVideo,
                onWatchVideo = {
                    if (showVideo) {
                        exitVideo()
                    } else if (!videoLoading) scope.launch {
                        videoLoading = true
                        val startMs = snapshot.positionMs
                        val video = runCatching { YouTubeEngine.resolvePlayback(item, false, null) }.getOrNull()
                        videoLoading = false
                        if (video != null) {
                            // The sound is read from a local copy, so seeks in the picture can't cut it off.
                            videoPlayback = MusicAudioCache.localAudio(video)
                            videoPositionMs = startMs
                            showVideo = true
                        } else {
                            error = "No video is available for this track right now."
                        }
                    }
                },
            )
            else -> {
            if (!MusicShellState.inMusic) MusicPillBar(
                item = item,
                playing = snapshot.isPlaying,
                onToggle = { if (snapshot.isPlaying) controller?.pause() else controller?.play() },
            ) else Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                MiniPlayerBar(
                    item = item,
                    snapshot = snapshot,
                    error = error,
                    volume = MusicVolume.level,
                    onVolumeChange = {
                        MusicVolume.set(it)
                        controller?.setVolume(MusicVolume.level)
                    },
                    onToggleMute = {
                        MusicVolume.toggleMute()
                        controller?.setVolume(MusicVolume.level)
                    },
                    onToggle = { if (snapshot.isPlaying) controller?.pause() else controller?.play() },
                    onSeek = { controller?.trySeekTo(it) },
                    onPrevious = MusicPlaybackQueue::previous,
                    onNext = MusicPlaybackQueue::next,
                    onExpand = { MusicPlaybackQueue.expanded = true },
                    onClose = { MusicPlaybackQueue.clear() },
                )
            }
            }
        }
    }
    }

    LaunchedEffect(showPlaylistPicker, item.videoId) {
        if (!showPlaylistPicker) return@LaunchedEffect
        accountChoicesLoading = true
        accountChoices = runCatching { YouTubeEngine.playlistChoices(item.videoId) }.getOrDefault(emptyList())
        accountChoicesLoading = false
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            containerColor = MusicPanel,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Add to playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (playlists.isEmpty() && accountChoices.isEmpty() && !accountChoicesLoading) {
                        Text("No playlists yet.", color = MusicMuted, fontSize = 12.sp)
                    }
                    playlists.forEach { playlist ->
                        TextButton(
                            onClick = {
                                MusicLibraryStore.addToPlaylist(playlist.id, item)
                                showPlaylistPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color.White),
                        ) {
                            Text(playlist.name, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${playlist.tracks.size}", color = MusicDim, fontSize = 12.5.sp)
                        }
                    }
                    // The signed-in YouTube account's playlists (the same ones the sidebar lists).
                    if (accountChoicesLoading) {
                        Text("Loading your YouTube playlists…", color = MusicMuted, fontSize = 12.sp)
                    }
                    if (accountChoices.isNotEmpty()) {
                        Text(
                            "YouTube account",
                            color = MusicDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    accountChoices.forEach { choice ->
                        TextButton(
                            onClick = {
                                showPlaylistPicker = false
                                scope.launch {
                                    runCatching { YouTubeEngine.addToPlaylist(choice.id, item.videoId) }
                                        .onFailure { error = it.message ?: "Could not add to ${choice.title}." }
                                }
                            },
                            enabled = !choice.containsVideo,
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = MusicDim,
                            ),
                        ) {
                            Text(choice.title, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(
                                if (choice.containsVideo) "Added" else "${choice.size}",
                                color = MusicDim,
                                fontSize = 12.5.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPlaylistPicker = false
                        newPlaylistName = ""
                        newPlaylistTracks = listOf(item)
                        showNewPlaylist = true
                    },
                ) { Text("New playlist", color = MusicAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Cancel", color = MusicMuted) }
            },
        )
    }

    if (showNewPlaylist) {
        AlertDialog(
            onDismissRequest = { showNewPlaylist = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistTracks.isEmpty()) {
                            MusicLibraryStore.createPlaylist(newPlaylistName, item)
                        } else {
                            MusicLibraryStore.createPlaylist(newPlaylistName, newPlaylistTracks)
                        }
                        newPlaylistTracks = emptyList()
                        showNewPlaylist = false
                    },
                    enabled = newPlaylistName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylist = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MiniPlayerBar(
    item: YouTubeVideoItem,
    snapshot: PlayerPlaybackSnapshot,
    error: String?,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    val duration = snapshot.durationMs.coerceAtLeast(item.durationMs)
    Row(
        Modifier.fillMaxWidth()
            .height(120.dp)
            .background(MusicBar)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.width(340.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MusicArtwork(item, 64.dp)
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                MusicArtistLinks(item, fontSize = 12.5.sp, color = MusicMuted)
                error?.let { Text(it, color = Color(0xFFFF8A9B), fontSize = 13.sp, maxLines = 1) }
            }
            val liked = MusicLibraryStore.liked.any { it.videoId == item.videoId }
            IconButton(onClick = { MusicLibraryStore.toggleLike(item) }, modifier = Modifier.size(30.dp).likeBurst(liked)) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (liked) "Unlike" else "Like",
                    tint = if (liked) MusicLikeRed else MusicDim,
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconButton(onClick = { MusicPlaybackQueue.setShuffleEnabled(!MusicPlaybackQueue.shuffle) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = if (MusicPlaybackQueue.shuffle) "Shuffle on" else "Shuffle off",
                        tint = if (MusicPlaybackQueue.shuffle) MusicActive else MusicMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(999.dp)).background(Color.White).clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (snapshot.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (snapshot.isPlaying) "Pause" else "Play",
                        tint = Color(0xFF121216),
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next track", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = MusicPlaybackQueue::cycleRepeat, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (MusicPlaybackQueue.repeatMode == MusicRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (MusicPlaybackQueue.repeatMode != MusicRepeatMode.OFF) MusicActive else MusicMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().widthIn(max = 620.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(formatMs(snapshot.positionMs), fontSize = 12.5.sp, color = MusicDim)
                Slider(
                    value = snapshot.positionMs.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.weight(1f).height(22.dp),
                    colors = musicSliderColors(),
                )
                Text(formatMs(duration), fontSize = 12.5.sp, color = MusicDim)
            }
        }

        Row(
            Modifier.width(300.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleMute, modifier = Modifier.size(30.dp)) {
                Icon(
                    if (volume <= 0.001f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (volume <= 0.001f) "Unmute" else "Mute",
                    tint = if (volume <= 0.001f) MusicDim else MusicMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.width(100.dp).height(22.dp),
                colors = musicSliderColors(),
            )
            Spacer(Modifier.width(8.dp))
            val downloaded = MusicDownloads.entries.any { it.videoId == item.videoId }
            IconButton(onClick = { MusicDownloads.download(item) }, enabled = !downloaded && MusicDownloads.activeId == null, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.FileDownload, contentDescription = if (downloaded) "Downloaded" else "Download", tint = if (downloaded) MusicActive else MusicMuted, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onExpand, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Open now playing", tint = MusicMuted, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Stop music", tint = MusicMuted, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun NowPlayingScreen(
    item: YouTubeVideoItem,
    snapshot: PlayerPlaybackSnapshot,
    error: String?,
    playing: Boolean,
    isLiked: Boolean,
    shuffle: Boolean,
    repeatMode: MusicRepeatMode,
    showLyrics: Boolean,
    lyrics: List<MusicLyricLine>,
    lyricsLoading: Boolean,
    lyricsError: String?,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenArtist: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSaveQueue: () -> Unit,
    onRemoveQueue: (Int) -> Unit,
    onMoveQueue: (Int, Int) -> Unit,
    onClose: () -> Unit,
    onCollapse: () -> Unit,
    videoAvailable: Boolean,
    onWatchVideo: () -> Unit,
    videoLoading: Boolean = false,
    radioBusy: Boolean = false,
    onStartRadio: () -> Unit = {},
    volume: Float = 1f,
    onVolumeChange: (Float) -> Unit = {},
    onToggleMute: () -> Unit = {},
    videoControls: (@Composable () -> Unit)? = null,
    videoArtwork: (@Composable () -> Unit)? = null,
    videoActive: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize()
            // Taps on empty space stay on this screen. A no-op clickable: the buttons inside still get their own
            // taps, and nothing reaches the list behind. (A pointer loop that consumed every press made the
            // buttons react only some of the time.)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color(0xFF24212A), MusicBg, MusicBg),
            ),
        ).padding(horizontal = 28.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Back to music", tint = MusicMuted)
            }
            Text("NOW PLAYING", fontSize = 12.5.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.ExtraBold, color = MusicMuted)
            Spacer(Modifier.weight(1f))
            videoControls?.invoke()
        }

        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val artwork = videoArtwork
                // The playback controls and their text, shared by both layouts.
                val controlsContent: @Composable () -> Unit = {
                Text(
                    item.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MusicArtistLinks(item, fontSize = 15.5.sp, color = MusicMuted)

                val duration = snapshot.durationMs.coerceAtLeast(item.durationMs)
                Slider(
                    value = snapshot.positionMs.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = musicSliderColors(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(snapshot.positionMs), fontSize = 12.5.sp, color = MusicDim)
                    Text(formatMs(duration), fontSize = 12.5.sp, color = MusicDim)
                }
                // Volume: the same setting as the bottom bar (and the video, which plays through the same controller).
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(onClick = onToggleMute, modifier = Modifier.size(30.dp)) {
                        Icon(
                            if (volume <= 0.001f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (volume <= 0.001f) "Unmute" else "Mute",
                            tint = if (volume <= 0.001f) MusicDim else MusicMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.width(240.dp),
                        colors = musicSliderColors(),
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    NowPlayingIcon(
                        icon = Icons.Filled.Shuffle,
                        description = if (shuffle) "Shuffle on" else "Shuffle off",
                        active = shuffle,
                        onClick = onToggleShuffle,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(999.dp)).background(MusicAccentFill)
                            .clickable(onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next track", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    NowPlayingIcon(
                        icon = if (repeatMode == MusicRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        description = when (repeatMode) {
                            MusicRepeatMode.OFF -> "Repeat off"
                            MusicRepeatMode.ALL -> "Repeat queue"
                            MusicRepeatMode.ONE -> "Repeat one"
                        },
                        active = repeatMode != MusicRepeatMode.OFF,
                        onClick = onCycleRepeat,
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LikeHeartButton(liked = isLiked, onToggle = onToggleLike)
                    Spacer(Modifier.width(10.dp))
                    NowPlayingIcon(
                        icon = Icons.Filled.Subtitles,
                        description = "Lyrics",
                        active = showLyrics,
                        onClick = onToggleLyrics,
                    )
                    Spacer(Modifier.width(10.dp))
                    NowPlayingIcon(
                        icon = Icons.Filled.Person,
                        description = "Open artist",
                        active = false,
                        enabled = item.channelId != null,
                        onClick = onOpenArtist,
                    )
                    Spacer(Modifier.width(10.dp))
                    NowPlayingIcon(
                        icon = Icons.Filled.PlaylistAdd,
                        description = "Add to playlist",
                        active = false,
                        onClick = onAddToPlaylist,
                    )
                    if (videoAvailable) {
                        Spacer(Modifier.width(10.dp))
                        NowPlayingIcon(
                            icon = Icons.Filled.Movie,
                            description = when {
                                videoLoading -> "Loading video…"
                                videoActive -> "Music video on: turn off"
                                else -> "Watch video"
                            },
                            active = videoLoading || videoActive,
                            enabled = !videoLoading,
                            onClick = onWatchVideo,
                        )
                        Spacer(Modifier.width(10.dp))
                        NowPlayingIcon(
                            icon = Icons.Filled.Radio,
                            description = if (radioBusy) "Starting radio…" else "Start radio",
                            active = radioBusy,
                            enabled = !radioBusy,
                            onClick = onStartRadio,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    val downloaded = MusicDownloads.entries.any { it.videoId == item.videoId }
                    NowPlayingIcon(
                        icon = Icons.Filled.FileDownload,
                        description = if (downloaded) "Downloaded" else "Download",
                        active = downloaded,
                        enabled = !downloaded && MusicDownloads.activeId == null,
                        onClick = { MusicDownloads.download(item) },
                    )
                }
                error?.let { Text(it, color = Color(0xFFFF8A9B), fontSize = 12.5.sp, maxLines = 2) }

                }
                if (artwork != null) {
                    // Video: full width on top; controls on the left, lyrics on the right, side by side.
                    artwork()
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            controlsContent()
                        }
                        if (showLyrics) {
                            Column(
                                Modifier.weight(1f).fillMaxHeight()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MusicPanel.copy(alpha = 0.55f))
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                            ) {
                                LyricsPanel(
                                    lyrics = lyrics,
                                    loading = lyricsLoading,
                                    error = lyricsError,
                                    positionMs = snapshot.positionMs - MusicLyricsTiming.offset(item.videoId),
                                    modifier = Modifier.weight(1f),
                                )
                                if (lyrics.isNotEmpty()) {
                                    LyricsTimingBar(item, timed = lyrics.any { it.timeMs > 0 })
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(330.dp), contentAlignment = Alignment.Center) {
                        MusicArtwork(item, 310.dp, rounded = 24.dp)
                    }
                    controlsContent()
                // Lyrics fill the space under the controls (the lyrics button above shows or hides them).
                if (showLyrics) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MusicPanel.copy(alpha = 0.55f))
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        LyricsPanel(
                            lyrics = lyrics,
                            loading = lyricsLoading,
                            error = lyricsError,
                            positionMs = snapshot.positionMs - MusicLyricsTiming.offset(item.videoId),
                            modifier = Modifier.weight(1f),
                        )
                        if (lyrics.isNotEmpty()) {
                            LyricsTimingBar(item, timed = lyrics.any { it.timeMs > 0 })
                        }
                    }
                }
                }
            }

            Column(
                Modifier.width(460.dp).fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MusicPanel)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Queue", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 6.dp))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSaveQueue, enabled = MusicPlaybackQueue.items.isNotEmpty()) {
                        Text("Save queue", color = MusicMuted, fontSize = 12.5.sp)
                    }
                    Text("${MusicPlaybackQueue.items.size} tracks", fontSize = 12.sp, color = MusicDim)
                }
                Spacer(Modifier.height(10.dp))
                QueuePanel(
                    items = MusicPlaybackQueue.items,
                    activeIndex = MusicPlaybackQueue.index,
                    onPlay = MusicPlaybackQueue::jumpTo,
                    onRemove = onRemoveQueue,
                    onMove = onMoveQueue,
                )
            }
        }
    }
}

internal val MusicLikeRed = Color(0xFFE53935)
private val LikeRed = MusicLikeRed
/** Active toggles (shuffle, repeat, lyrics, video, like state): brighter than the panel accents. */
internal val MusicActive = Color(0xFF7DD3FC)

/**
 * Any like button in Music: when the track becomes liked, a red heart swells out of it and fades.
 * Attach it to the like button's modifier; it reacts to the liked state wherever the toggle came from.
 */
internal fun Modifier.likeBurst(liked: Boolean): Modifier = composed {
    val progress = remember { Animatable(1f) }
    val painter: Painter = rememberVectorPainter(Icons.Filled.Favorite)
    var previous by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked && !previous) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(750))
        }
        previous = liked
    }
    this.drawWithContent {
        drawContent()
        val t = progress.value
        if (t < 1f) {
            val side = 22.dp.toPx() * (1f + 2.4f * t)
            translate(left = (size.width - side) / 2f, top = (size.height - side) / 2f) {
                with(painter) {
                    draw(
                        size = Size(side, side),
                        alpha = (1f - t) * 0.9f,
                        colorFilter = ColorFilter.tint(LikeRed),
                    )
                }
            }
        }
    }
}


/** The like button; liking a track fires a red heart that swells and fades out over it. */
@Composable
private fun LikeHeartButton(liked: Boolean, onToggle: () -> Unit) {
    var bursts by remember { mutableStateOf(0) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(bursts) {
        if (bursts > 0) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(750))
        }
    }
    Box(contentAlignment = Alignment.Center) {
        NowPlayingIcon(
            icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            description = if (liked) "Remove from liked songs" else "Like this track",
            active = liked,
            tintWhenActive = MusicLikeRed,
            onClick = {
                if (!liked) bursts++
                onToggle()
            },
        )
        val t = burst.value
        if (bursts > 0 && t < 1f) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = LikeRed,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        val swell = 1f + 2.4f * t
                        scaleX = swell
                        scaleY = swell
                        alpha = (1f - t) * 0.9f
                    },
            )
        }
    }
}

@Composable
private fun NowPlayingIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    enabled: Boolean = true,
    tintWhenActive: Color = MusicActive,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                !enabled -> MusicDim.copy(alpha = 0.4f)
                active -> tintWhenActive
                else -> Color.White
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun RightPanelTab(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (active) MusicAccentFill else MusicPanelRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) Color.White else MusicMuted,
        )
    }
}

@Composable
private fun QueuePanel(
    items: List<YouTubeVideoItem>,
    activeIndex: Int,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(items, key = { index, track -> "$index:${track.videoId}" }) { index, track ->
            val active = index == activeIndex
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) MusicAccent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onPlay(index) }
                    .padding(start = 8.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MusicArtwork(track, 44.dp, rounded = 8.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        fontSize = 15.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (active) Color.White else MusicMuted,
                    )
                    MusicArtistLinks(track, fontSize = 12.5.sp, color = MusicDim)
                }
                Text(formatMs(track.durationMs), fontSize = 12.sp, color = MusicDim)
                val liked = MusicLibraryStore.liked.any { it.videoId == track.videoId }
                IconButton(onClick = { MusicLibraryStore.toggleLike(track) }, modifier = Modifier.size(28.dp).likeBurst(liked)) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (liked) "Remove from liked songs" else "Like this track",
                        tint = if (liked) LikeRed else MusicDim,
                        modifier = Modifier.size(17.dp),
                    )
                }
                IconButton(
                    onClick = { onMove(index, index - 1) },
                    enabled = index > 0,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", tint = MusicDim, modifier = Modifier.size(17.dp))
                }
                IconButton(
                    onClick = { onMove(index, index + 1) },
                    enabled = index < items.lastIndex,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", tint = MusicDim, modifier = Modifier.size(17.dp))
                }
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove from queue", tint = MusicDim, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Source, any automatic shift, and − / + to nudge the timing for this track. */
@Composable
private fun LyricsTimingBar(item: YouTubeVideoItem, timed: Boolean) {
    val offset = MusicLyricsTiming.offset(item.videoId)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            MusicLyricsRepository.source(item.videoId)?.let {
                Text("Lyrics · $it", fontSize = 11.sp, color = MusicDim)
            }
            MusicLyricsTiming.notes[item.videoId]?.let {
                Text(it, fontSize = 11.sp, color = MusicDim)
            }
        }
        if (timed) {
            Text(
                when {
                    offset == 0L -> "In time"
                    offset > 0 -> "Later by ${MusicLyricsTiming.formatSeconds(offset)}"
                    else -> "Earlier by ${MusicLyricsTiming.formatSeconds(-offset)}"
                },
                fontSize = 11.5.sp,
                color = MusicMuted,
                modifier = Modifier.padding(end = 6.dp),
            )
            TimingButton("−0.5s", "Show lyrics earlier") { MusicLyricsTiming.nudge(item.videoId, -500) }
            TimingButton("+0.5s", "Show lyrics later") { MusicLyricsTiming.nudge(item.videoId, 500) }
            if (offset != 0L) TimingButton("Reset", "Reset lyrics timing") { MusicLyricsTiming.reset(item.videoId) }
        }
    }
}

@Composable
private fun TimingButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MusicPanelRaised)
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun LyricsPanel(
    lyrics: List<MusicLyricLine>,
    loading: Boolean,
    error: String?,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    when {
        loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = MusicAccent)
        }
        error != null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error, color = MusicMuted, fontSize = 13.sp)
        }
        lyrics.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No lyrics found.", color = MusicMuted, fontSize = 14.sp)
        }
        else -> {
            val timed = lyrics.any { it.timeMs > 0 }
            val current = if (timed) lyrics.indexOfLast { it.timeMs > 0 && it.timeMs <= positionMs } else -1
            val listState = rememberLazyListState()
            LaunchedEffect(current) {
                if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
            }
            LazyColumn(state = listState, modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(lyrics) { index, line ->
                    val sung = index == current
                    Text(
                        line.text.ifBlank { "♪" },
                        fontSize = if (sung) 22.sp else 18.sp,
                        lineHeight = if (sung) 28.sp else 24.sp,
                        fontWeight = if (sung) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = when {
                            sung -> Color.White
                            !timed -> MusicMuted
                            index < current -> MusicDim
                            else -> MusicMuted
                        },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicArtwork(item: YouTubeVideoItem, size: androidx.compose.ui.unit.Dp, rounded: androidx.compose.ui.unit.Dp = 10.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(rounded)).background(MusicPanelRaised),
        contentAlignment = Alignment.Center,
    ) {
        val url = DeArrowDesktop.thumbnailUrl(item)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("♪", fontSize = (size.value * 0.4f).sp, color = MusicMuted)
        }
    }
}

@Composable
private fun musicSliderColors() = SliderDefaults.colors(
    thumbColor = MusicAccent,
    activeTrackColor = MusicAccent,
    inactiveTrackColor = MusicPanelRaised,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
