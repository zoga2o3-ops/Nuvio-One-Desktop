package com.nuvio.app.features.iptv

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val guideChannelColumn = 232.dp
private val guideRowHeight = 84.dp
private val guideMinuteWidth = 4.dp
private const val HOURS_BACK = 6
private const val HOURS_FORWARD = 18
private const val HALF_HOUR_MS = 30 * 60 * 1000L

/**
 * TiviMate-style EPG: group rail, scrollable 24-hour timeline (drag, wheel or the
 * arrow controls), channel rows with programme blocks, a now-line and a floating
 * preview that plays the selected channel.
 */
@Composable
internal fun IptvGuidePane(
    modifier: Modifier,
    channels: List<LiveChannel>,
    programs: Map<String, List<EpgProgram>>,
    groups: List<String>,
    groupCounts: Map<String, Int>,
    selectedGroup: String,
    onGroupSelect: (String) -> Unit,
    selectedChannel: LiveChannel?,
    favorites: Set<String>,
    now: Long,
    settings: IptvSettings,
    timeFormat: (Long) -> String,
    onSelect: (LiveChannel) -> Unit,
    onWatch: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    recording: Boolean,
    onRecord: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pxPerMinute = with(density) { guideMinuteWidth.toPx() }
    val rangeStart = floorToHalfHour(now) - HOURS_BACK * 60 * 60 * 1000L
    val rangeEnd = floorToHalfHour(now) + HOURS_FORWARD * 60 * 60 * 1000L
    val totalMinutes = (rangeEnd - rangeStart) / 60_000f
    val totalWidthPx = totalMinutes * pxPerMinute
    val totalWidth = with(density) { totalWidthPx.toDp() }
    val nowPx = ((now - rangeStart) / 60_000f) * pxPerMinute

    var offsetPx by remember { mutableStateOf(0f) }
    var viewportPx by remember { mutableStateOf(0f) }
    var didInit by remember { mutableStateOf(false) }

    fun clamp(value: Float): Float = value.coerceIn(0f, (totalWidthPx - viewportPx).coerceAtLeast(0f))

    suspend fun glideTo(target: Float) {
        val from = offsetPx
        val to = clamp(target)
        if (from == to) return
        animate(from, to, animationSpec = tween(220)) { value, _ -> offsetPx = value }
    }

    val listState = rememberLazyListState()
    var didInitialChannelScroll by remember { mutableStateOf(false) }
    LaunchedEffect(selectedChannel?.key, channels.size) {
        if (didInitialChannelScroll || channels.isEmpty()) return@LaunchedEffect
        val index = channels.indexOfFirst { it.key == selectedChannel?.key }
        if (index >= 0) {
            listState.scrollToItem(index)
            didInitialChannelScroll = true
        }
    }

    Row(modifier) {
        // ------------------------------------------------------------- groups
        Column(
            Modifier.width(224.dp).fillMaxHeight().background(IptvSidebar)
                .padding(vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("GROUPS", fontSize = 10.sp, letterSpacing = 1.4.sp, color = IptvDim)
                Spacer(Modifier.weight(1f))
                if (settings.showGroupCounts) {
                    Text("${channels.size}", fontSize = 10.sp, color = IptvDim)
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(groups, key = { it }) { group ->
                    val active = group == selectedGroup
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) IptvAccent.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onGroupSelect(group) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            group,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) IptvText else IptvMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (settings.showGroupCounts) {
                            groupCounts[group]?.let {
                                Text("$it", fontSize = 11.sp, color = if (active) Color(0xFFFFB3C2) else IptvDim)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(1.dp).fillMaxHeight().background(IptvBorderSoft))

        // ---------------------------------------------------------------- grid
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val viewportWidth = maxWidth
            LaunchedEffect(viewportWidth, nowPx) {
                viewportPx = with(density) { viewportWidth.toPx() }
                if (!didInit) {
                    offsetPx = clamp(nowPx - viewportPx * 0.3f)
                    didInit = true
                } else {
                    offsetPx = clamp(offsetPx)
                }
            }

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(46.dp).background(Color(0xFF0B0B0F))) {
                    Row(
                        Modifier.width(guideChannelColumn).fillMaxHeight().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        IconButton(
                            onClick = { scope.launch { glideTo(offsetPx - 60 * pxPerMinute) } },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.ChevronLeft,
                                contentDescription = "Earlier",
                                tint = IptvMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        TextButton(
                            onClick = { scope.launch { glideTo(nowPx - viewportPx * 0.3f) } },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Now", fontSize = 11.5.sp, color = IptvMuted)
                        }
                        IconButton(
                            onClick = { scope.launch { glideTo(offsetPx + 60 * pxPerMinute) } },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = "Later",
                                tint = IptvMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Box(
                            Modifier.fillMaxSize().clipToBounds()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        change.consume()
                                        offsetPx = clamp(offsetPx - dragAmount)
                                    }
                                },
                        ) {
                            Box(
                                Modifier.offset { IntOffset(-offsetPx.roundToInt(), 0) }
                                    .width(totalWidth).fillMaxHeight(),
                            ) {
                                var tick = rangeStart
                                while (tick < rangeEnd) {
                                    val minute = (tick - rangeStart) / 60_000f
                                    Box(
                                        Modifier.offset(x = with(density) { (minute * pxPerMinute).toDp() })
                                            .fillMaxHeight(),
                                    ) {
                                        Box(Modifier.fillMaxHeight().width(1.dp).background(IptvBorderSoft))
                                        Text(
                                            timeFormat(tick),
                                            fontSize = 11.sp,
                                            color = IptvDim,
                                            modifier = Modifier.padding(start = 8.dp, top = 14.dp),
                                        )
                                    }
                                    tick += HALF_HOUR_MS
                                }
                                Box(
                                    Modifier.offset(x = with(density) { nowPx.toDp() })
                                        .width(2.dp).fillMaxHeight().background(IptvAccent),
                                )
                            }
                        }
                    }
                }

                androidx.compose.material3.HorizontalDivider(color = IptvBorderSoft)

                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(channels, key = { it.key }) { channel ->
                        GuideRow(
                            channel = channel,
                            programs = programs[channel.key].orEmpty(),
                            rangeStart = rangeStart,
                            rangeEnd = rangeEnd,
                            pxPerMinute = pxPerMinute,
                            totalWidth = totalWidth,
                            nowPx = nowPx,
                            now = now,
                            offsetPx = offsetPx,
                            selected = selectedChannel?.key == channel.key,
                            showNowLine = settings.showNowLine,
                            showTimes = settings.showProgramTimes,
                            showQuality = settings.showQualityBadges,
                            timeFormat = timeFormat,
                            onSelect = { onSelect(channel) },
                            onDrag = { delta -> offsetPx = clamp(offsetPx - delta) },
                        )
                    }
                    if (channels.isEmpty()) {
                        item {
                            Text(
                                "No channels match this filter.",
                                color = IptvDim,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
            }

            // ----------------------------------------------------------- preview
            selectedChannel?.let { channel ->
                val current = programs[channel.key]
                    ?.firstOrNull { it.isLive(now) }
                    ?: programs[channel.key]?.firstOrNull { it.startMs > now }
                GuidePreview(
                    channel = channel,
                    program = current,
                    now = now,
                    favorite = channel.key in favorites,
                    recording = recording,
                    timeFormat = timeFormat,
                    onWatch = { onWatch(channel) },
                    onRecord = onRecord,
                    onFavorite = { onToggleFavorite(channel) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: LiveChannel,
    programs: List<EpgProgram>,
    rangeStart: Long,
    rangeEnd: Long,
    pxPerMinute: Float,
    totalWidth: androidx.compose.ui.unit.Dp,
    nowPx: Float,
    now: Long,
    offsetPx: Float,
    selected: Boolean,
    showNowLine: Boolean,
    showTimes: Boolean,
    showQuality: Boolean,
    timeFormat: (Long) -> String,
    onSelect: () -> Unit,
    onDrag: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val visible = programs.filter { it.stopMs > rangeStart && it.startMs < rangeEnd }

    Row(
        Modifier.fillMaxWidth().height(guideRowHeight)
            .background(if (selected) Color(0xFF121218) else Color.Transparent),
    ) {
        Row(
            Modifier.width(guideChannelColumn).fillMaxHeight()
                .background(if (selected) Color(0xFF15151D) else Color(0xFF0B0B0F))
                .clickable(onClick = onSelect)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GuideLogo(channel, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    "${channel.number} · ${channel.group}",
                    fontSize = 10.sp,
                    color = IptvDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    channel.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IptvText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            Modifier.weight(1f).fillMaxHeight().clipToBounds()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                },
        ) {
            Box(
                Modifier.offset { IntOffset(-offsetPx.roundToInt(), 0) }
                    .width(totalWidth).fillMaxHeight(),
            ) {
                visible.forEach { program ->
                    val from = maxOf(program.startMs, rangeStart)
                    val to = minOf(program.stopMs, rangeEnd)
                    val left = with(density) { (((from - rangeStart) / 60_000f) * pxPerMinute).toDp() }
                    val blockWidth = with(density) {
                        ((((to - from) / 60_000f) * pxPerMinute).toDp()) - 4.dp
                    }
                    val live = program.isLive(now)
                    val progress = if (live && program.stopMs > program.startMs) {
                        ((now - program.startMs).toFloat() / (program.stopMs - program.startMs)).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Column(
                        Modifier.offset(x = left + 2.dp)
                            .width(blockWidth.coerceAtLeast(8.dp))
                            .fillMaxHeight()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (live) {
                                    Brush.linearGradient(
                                        listOf(IptvAccent.copy(alpha = 0.18f), IptvAccent.copy(alpha = 0.05f)),
                                    )
                                } else {
                                    Brush.linearGradient(listOf(IptvPanel, IptvPanel))
                                },
                            )
                            .then(
                                if (live) {
                                    Modifier.border(1.dp, IptvAccent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(onClick = onSelect)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            program.title,
                            fontSize = 12.sp,
                            fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal,
                            color = IptvText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showTimes) {
                            Text(
                                "${timeFormat(program.startMs)} – ${timeFormat(program.stopMs)}" +
                                    if (live) " · Live now" else "",
                                fontSize = 10.5.sp,
                                color = IptvMuted,
                                maxLines = 1,
                            )
                        }
                        if (live && showNowLine) {
                            Box(
                                Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp))
                                    .background(Color(0x26FFFFFF)),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(progress).fillMaxHeight()
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(IptvAccent),
                                )
                            }
                        }
                    }
                }

                if (showNowLine) {
                    Box(
                        Modifier.offset(x = with(density) { nowPx.toDp() })
                            .width(2.dp).fillMaxHeight()
                            .background(Color(0xD9FFFFFF)),
                    )
                }

                androidx.compose.material3.HorizontalDivider(
                    color = IptvBorderSoft,
                    modifier = Modifier.align(Alignment.BottomStart).width(totalWidth),
                )
            }
        }
    }
}

@Composable
private fun GuidePreview(
    channel: LiveChannel,
    program: EpgProgram?,
    now: Long,
    favorite: Boolean,
    recording: Boolean,
    timeFormat: (Long) -> String,
    onWatch: () -> Unit,
    onRecord: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (program != null && program.stopMs > program.startMs) {
        ((now - program.startMs).toFloat() / (program.stopMs - program.startMs)).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier.width(340.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101016))
            .border(1.dp, IptvBorder, RoundedCornerShape(14.dp)),
    ) {
        Box(
            Modifier.fillMaxWidth().height(191.dp).background(Color.Black),
        ) {
            GuideLivePreview(channel = channel, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            program?.let {
                Text(
                    it.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${timeFormat(it.startMs)} – ${timeFormat(it.stopMs)}",
                    fontSize = 10.5.sp,
                    color = IptvDim,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideLogo(channel, 26.dp)
                Text(channel.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                GuideBadge("${channel.number}", Color(0x1FFFFFFF), Color(0xFFDCDCE6))
                GuideBadge("Live", IptvLive)
                Spacer(Modifier.weight(1f))
                Text(
                    if (program?.isLive(now) == true) "Now" else "Next",
                    fontSize = 10.5.sp,
                    color = IptvDim,
                )
            }
            program?.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 11.5.sp, color = IptvMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (program != null && program.isLive(now)) {
                Box(
                    Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp))
                        .background(Color(0x26FFFFFF)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress).fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(IptvAccent),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideActionPill("Watch", Icons.Filled.PlayArrow, primary = true, onClick = onWatch)
                GuideActionPill(
                    if (recording) "Stop" else "Record",
                    Icons.Filled.RadioButtonChecked,
                    primary = false,
                    tint = if (recording) IptvLive else IptvText,
                    onClick = onRecord,
                )
                GuideActionPill(
                    if (favorite) "Favourited" else "Favourite",
                    Icons.Filled.Star,
                    primary = false,
                    tint = if (favorite) IptvStar else IptvText,
                    onClick = onFavorite,
                )
            }
        }
    }
}

@Composable
private fun GuideLivePreview(channel: LiveChannel, modifier: Modifier) {
    val platformDensity = com.nuvio.app.core.ui.LocalNuvioPlatformDensity.current
    val playerSettings by com.nuvio.app.features.player.PlayerSettingsRepository.uiState.collectAsState()
    val host = remember(channel.key) { com.nuvio.app.features.player.desktop.NativePlayerHost() }
    val controller = remember(host) { com.nuvio.app.features.player.desktop.NativePlayerController(host) }

    androidx.compose.runtime.DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    LaunchedEffect(controller, channel.url) {
        val sourceUrl = if (StalkerLinks.isStalker(channel.url)) {
            StalkerLinks.resolveOnIo(channel.url)
        } else {
            channel.url
        }
        controller.attach(
            sourceUrl = sourceUrl,
            sourceHeaders = channel.headers,
            sourceAudioUrl = null,
            playWhenReady = true,
            initialPositionMs = 0L,
            decoderPriority = playerSettings.decoderPriority,
            nvidiaRtxSuperResolutionEnabled = playerSettings.nvidiaRtxSuperResolutionEnabled,
            onError = {},
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides platformDensity,
    ) {
        // Covered by a menu or dialog: keep the video out of the way (see NativeVideoCover).
        androidx.compose.foundation.layout.Box(modifier.background(Color.Black)) {
            androidx.compose.ui.awt.SwingPanel(
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
private fun GuideBadge(text: String, background: Color, foreground: Color = Color.White) {
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
private fun GuideLogo(channel: LiveChannel, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.22f))
            .background(channelTileColor(channel.name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            guideInitials(channel.name),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.3f).sp,
        )
        channel.logo?.takeIf { it.isNotBlank() }?.let { url ->
            com.nuvio.app.core.ui.NuvioAsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun GuideActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    tint: Color = IptvText,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (primary) IptvAccent else IptvRaised)
            .border(1.dp, if (primary) Color.Transparent else IptvBorder, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (primary) Color.White else tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) Color.White else tint,
        )
    }
}

private fun floorToHalfHour(timestamp: Long): Long =
    (timestamp / HALF_HOUR_MS) * HALF_HOUR_MS

private fun channelTileColor(name: String): Color {
    val palette = listOf(
        Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF26A69A), Color(0xFF8E24AA),
        Color(0xFF43A047), Color(0xFF00ACC1), Color(0xFF7CB342), Color(0xFFF4511E),
        Color(0xFF5E35B1), Color(0xFFFB8C00), Color(0xFFD81B60), Color(0xFF3949AB),
    )
    return palette[(name.hashCode() and 0x7fffffff) % palette.size]
}

private fun guideInitials(name: String): String {
    val words = name.split(' ', '-', '|', '/').filter { it.isNotBlank() }
    return if (words.size >= 2) {
        "${words[0].first()}${words[1].first()}".uppercase(Locale.US)
    } else {
        name.trim().take(2).uppercase(Locale.US)
    }
}
