package com.nuvio.app.features.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nuvio.app.core.ui.CoverNativeVideo
import com.nuvio.app.features.autosync.AutoSyncMpvEngineController
import com.nuvio.app.features.player.desktop.NativePlayerController
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerResizeMode

/**
 * Several channels at once, in a grid of four, like the TV app's multiview. Each tile is its own player and
 * can be given any channel; only the tile with sound plays audio. Buttons sit under each tile, because the video
 * window covers its tile and can't take clicks itself.
 *
 * [tiles] has one slot per tile; an empty slot shows a "Choose channel" button.
 */
@Composable
internal fun LiveMultiview(
    tiles: List<PlaybackTarget?>,
    channels: List<LiveChannel>,
    fullscreen: Boolean,
    onChoose: (Int, PlaybackTarget?) -> Unit,
    onClose: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var soundIndex by remember { mutableStateOf(0) }
    var pickingFor by remember { mutableStateOf<Int?>(null) }
    // The video windows sit above everything else, so while the channel picker is open they shrink to nothing.
    CoverNativeVideo(covering = pickingFor != null)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Multiview", color = IptvMuted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleFullscreen) {
                Text(if (fullscreen) "Exit full screen" else "Full screen", color = IptvMuted, fontSize = 13.sp)
            }
            TextButton(onClick = onClose) { Text("Close multiview", color = IptvMuted, fontSize = 13.sp) }
        }
        tiles.indices.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().then(if (fullscreen) Modifier.weight(1f) else Modifier.height(300.dp)),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { index ->
                    MultiviewTile(
                        target = tiles[index],
                        hasSound = index == soundIndex && tiles[index] != null,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onSound = { soundIndex = index },
                        onChoose = { pickingFor = index },
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    pickingFor?.let { slot ->
        Dialog(onDismissRequest = { pickingFor = null }) {
            ChannelSearch(
                channels = channels,
                onPick = { channel ->
                    onChoose(slot, PlaybackTarget(channel.url, channel.headers, channel.name))
                    pickingFor = null
                },
                onCancel = { pickingFor = null },
            )
        }
    }
}

/**
 * Sound for one tile. The sound tile is set to full; the others are muted without changing the saved volume.
 * (setVolume alone saves the level app-wide, so muted tiles would leave the next player silent.)
 */
private fun PlayerEngineController.applySound(on: Boolean) {
    val raw = (this as? AutoSyncMpvEngineController)?.base ?: this
    val native = raw as? NativePlayerController
    if (native == null) {
        setVolume(if (on) 1f else 0f)
        return
    }
    if (on) setVolume(1f)
    native.setMutedPreview(!on)
}

/** A search box over the channel list: type to narrow it down, then click a channel. */
@Composable
private fun ChannelSearch(
    channels: List<LiveChannel>,
    onPick: (LiveChannel) -> Unit,
    onCancel: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query, channels) {
        val text = query.trim()
        channels.asSequence()
            .filter { text.isEmpty() || it.name.contains(text, ignoreCase = true) }
            .take(300)
            .toList()
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp).background(Color(0xFF1A1A22)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Choose a channel", color = IptvMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search channels") },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(matches, key = { it.key }) { channel ->
                Text(
                    channel.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(channel) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
internal fun MultiviewTile(
    target: PlaybackTarget?,
    hasSound: Boolean,
    modifier: Modifier,
    onSound: () -> Unit,
    onChoose: () -> Unit,
    chooseLabel: String = "Channel",
    emptyLabel: String = "Choose channel",
    showPause: Boolean = false,
) {
    if (target == null) {
        Box(
            modifier.background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(onClick = onChoose) { Text(emptyLabel, color = IptvMuted, fontSize = 13.sp) }
        }
        return
    }
    var controller by remember(target.url) { mutableStateOf<PlayerEngineController?>(null) }
    // The volume is re-applied on the player's status updates too: a volume set before playback has started can be lost.
    val latestSound by rememberUpdatedState(hasSound)
    var applied by remember(target.url) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(controller, hasSound) {
        controller?.applySound(hasSound)
        applied = hasSound
    }
    // Paused per video: a new video in this tile starts playing.
    var paused by remember(target.url) { mutableStateOf(false) }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            key(target.url) {
                PlatformPlayerSurface(
                    sourceUrl = target.url,
                    sourceAudioUrl = target.audioUrl,
                    sourceHeaders = target.headers,
                    modifier = Modifier.fillMaxSize(),
                    useNativeController = true,
                    playerControlsState = PlayerControlsState(hideControls = true, hideBackButton = true),
                    resizeMode = PlayerResizeMode.Fit,
                    onControllerReady = { controller = it },
                    onSnapshot = {
                        val want = latestSound
                        if (applied != want) {
                            controller?.applySound(want)
                            applied = want
                        }
                    },
                    onError = {},
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                target.title,
                color = IptvMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showPause) {
                TextButton(onClick = {
                    paused = !paused
                    if (paused) controller?.pause() else controller?.play()
                }) {
                    Text(if (paused) "Play" else "Pause", color = IptvMuted, fontSize = 12.sp)
                }
            }
            TextButton(onClick = onChoose) { Text(chooseLabel, color = IptvMuted, fontSize = 12.sp) }
            IconButton(onClick = onSound, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (hasSound) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (hasSound) "Sound on" else "Sound off",
                    tint = if (hasSound) Color.White else IptvMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
