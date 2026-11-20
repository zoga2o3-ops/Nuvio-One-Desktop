package com.nuvio.app.features.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.nuvio.app.core.ui.CoverNativeVideo
import kotlinx.coroutines.delay

private val Panel = Color(0xFF16171D)
private val Raised = Color(0xFF22232B)
private val Muted = Color(0xFFA0A0AC)

/** "Cast to": the Chromecasts and TVs on the network, searched again every few seconds while open. */
@Composable
internal fun CastPickerDialog(onDismiss: () -> Unit, onPick: (CastDevice) -> Unit) {
    CoverNativeVideo(true)
    val devices by CastDiscovery.devices.collectAsState()
    val scanning by CastDiscovery.scanning.collectAsState()
    var searchedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            CastDiscovery.scan()
            searchedOnce = true
            delay(3000)
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(min = 360.dp, max = 460.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Panel)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cast to", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Muted)
            }
            if (devices.isEmpty()) {
                Text(
                    if (searchedOnce) {
                        "No Chromecasts or smart TVs found yet. Make sure the TV is on and on the same network as this PC."
                    } else {
                        "Looking for Chromecasts and smart TVs…"
                    },
                    color = Muted,
                    fontSize = 14.sp,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(devices, key = { it.id }) { device ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Raised)
                                .clickable { onPick(device) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                if (device.kind == CastKind.CHROMECAST) Icons.Filled.Cast else Icons.Filled.Tv,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(device.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(
                                        if (device.kind == CastKind.CHROMECAST) "Chromecast" else "Smart TV (DLNA)",
                                        device.model,
                                    ).joinToString(" · "),
                                    color = Muted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

/** Shown in the player in place of the video while it plays on a TV. */
@Composable
internal fun CastingPanel(state: CastState, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Cast, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            Text("Casting to ${state.device.name}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(state.title, color = Muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when {
                state.error != null -> Text(state.error, color = Color(0xFFFF8A80), fontSize = 13.sp)
                state.connecting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Muted)
                    Text("Connecting…", color = Muted, fontSize = 13.sp)
                }
                else -> CastTransport(state, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = onStop) { Text("Stop casting") }
        }
    }
}

@Composable
private fun CastTransport(state: CastState, modifier: Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.durationMs > 0) {
            Slider(
                value = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { CastSession.seekTo((it * duration).toLong()) }
                    dragging = null
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = CastSession::togglePause) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.playing) "Pause" else "Play",
                    tint = Color.White,
                )
            }
            Text(
                if (state.durationMs > 0) "${clock(state.positionMs)} / ${clock(state.durationMs)}" else clock(state.positionMs),
                color = Muted,
                fontSize = 13.sp,
            )
        }
    }
}

/** Bottom-of-app bar for a cast whose player was closed, so it can still be paused or stopped. */
@Composable
internal fun CastMiniBar(modifier: Modifier = Modifier) {
    val state by CastSession.state.collectAsState()
    val current = state ?: return
    if (current.owner != null) return
    Row(
        modifier
            .widthIn(min = 320.dp, max = 560.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Cast, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(current.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                current.error ?: "Casting to ${current.device.name}" +
                    if (current.durationMs > 0) " · ${clock(current.positionMs)} / ${clock(current.durationMs)}" else "",
                color = if (current.error != null) Color(0xFFFF8A80) else Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (current.error == null && !current.connecting) {
            IconButton(onClick = CastSession::togglePause, modifier = Modifier.size(32.dp)) {
                Icon(if (current.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }
        TextButton(onClick = { CastSession.stop() }) { Text("Stop") }
        Spacer(Modifier.width(2.dp))
    }
}

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60) else "%d:%02d".format(total / 60, total % 60)
}
