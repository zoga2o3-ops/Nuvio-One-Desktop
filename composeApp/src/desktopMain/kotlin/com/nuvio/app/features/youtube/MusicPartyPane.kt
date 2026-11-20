package com.nuvio.app.features.youtube

import com.nuvio.app.core.ui.SectionPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

private val PartyAccent = Color(0xFF9AA8BD)
private val PartyAccentFill = Color(0xFF64748B)
private val PartyPanel: Color get() = SectionPalette.surface
private val PartyPanelRaised: Color get() = SectionPalette.raised
private val PartyMuted: Color get() = SectionPalette.muted
private val PartyDim: Color get() = SectionPalette.text
private val PartyDanger = Color(0xFFFF8A9B)

@Composable
internal fun MusicPartyPane() {
    LaunchedEffect(Unit) { MusicLibraryStore.ensureLoaded() }
    val state = MusicParty.state
    val queue = MusicPlaybackQueue.items
    val current = MusicPlaybackQueue.current

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text("Party", fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (state.running) "Guests can add songs from their phones" else "Turn the queue into a shared session",
                        fontSize = 12.5.sp,
                        color = PartyDim,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.running) {
                    PartyPill("Live", PartyAccent)
                    PartyAction("Stop", PartyDanger) { MusicParty.stop() }
                } else {
                    PartyAction("Start party", PartyAccent, filled = true) { MusicParty.start() }
                }
            }
        }
        state.error?.let { item { Text(it, color = PartyDanger, fontSize = 13.sp) } }
        if (state.running && state.url != null) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PartyPanel).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MusicQrCode(state.url, Modifier.size(132.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Groups, contentDescription = null, tint = PartyAccent, modifier = Modifier.size(18.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Guest link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Scan the code or open the link on the same Wi-Fi.", fontSize = 11.5.sp, color = PartyDim)
                                }
                            }
                            Text(
                                state.url,
                                fontSize = 13.sp,
                                color = PartyAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { copyToClipboard(state.url) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = PartyMuted, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Copy", color = PartyMuted, fontSize = 12.5.sp)
                                }
                                Spacer(Modifier.weight(1f))
                                Text("${state.additions} added", fontSize = 11.5.sp, color = PartyDim)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PartyToggle(
                            icon = if (state.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            label = if (state.locked) "Party locked" else "Party open",
                            active = state.locked,
                        ) { MusicParty.setLocked(!state.locked) }
                        PartyToggle(
                            icon = Icons.Filled.Shield,
                            label = "Approve requests",
                            active = state.requireApproval,
                        ) { MusicParty.setRequireApproval(!state.requireApproval) }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (state.pending.isNotEmpty()) {
                item {
                    Text("Waiting for approval", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                items(state.pending, key = { it.id }) { request ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(PartyPanel).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PartyArt(request.item)
                        Column(Modifier.weight(1f)) {
                            Text(request.item.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${request.item.author ?: "Music"} · from ${request.guestName}",
                                fontSize = 11.5.sp,
                                color = PartyDim,
                                maxLines = 1,
                            )
                        }
                        IconButton(onClick = { MusicParty.approve(request.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.Check, contentDescription = "Approve", tint = PartyAccent, modifier = Modifier.size(17.dp))
                        }
                        IconButton(onClick = { MusicParty.reject(request.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Reject", tint = PartyDanger, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PartyPanel).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("How it works", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    PartyStep("1", "Start the party", "Nuvio shows a link that works on the same Wi-Fi network.")
                    PartyStep("2", "Guests open the link", "They search YouTube Music and tap + to add a song.")
                    PartyStep("3", "Keep control", "Lock the party or approve each request from this screen.")
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (current == null) "Queue" else "Up next · ${queue.size} tracks",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (queue.isNotEmpty()) {
                    TextButton(onClick = MusicPlaybackQueue::clear) {
                        Text("Clear queue", color = PartyDim, fontSize = 12.sp)
                    }
                }
            }
        }
        if (queue.isEmpty()) {
            item { Text("Nothing queued yet.", fontSize = 12.5.sp, color = PartyDim) }
        } else {
            itemsIndexed(queue, key = { index, item -> "$index:${item.videoId}" }) { index, item ->
                val guest = MusicParty.addedBy(item.videoId)
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { MusicPlaybackQueue.jumpTo(index) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        if (index == MusicPlaybackQueue.index) "▶" else "${index + 1}",
                        fontSize = 12.sp,
                        color = if (index == MusicPlaybackQueue.index) PartyAccent else PartyDim,
                        modifier = Modifier.width(18.dp),
                    )
                    PartyArt(item, 38.dp)
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (item.author ?: "Music") + if (guest != null) " · added by $guest" else "",
                            fontSize = 11.5.sp,
                            color = PartyDim,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { MusicPlaybackQueue.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove from queue", tint = PartyDim, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PartyStep(number: String, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(999.dp)).background(PartyPanelRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PartyAccent)
        }
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 11.5.sp, color = PartyDim)
        }
    }
}

@Composable
private fun PartyToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (active) PartyAccentFill else PartyPanelRaised)
            .border(1.dp, if (active) PartyAccentFill else Color(0xFF3A3A42), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = PartyMuted, modifier = Modifier.size(13.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PartyPill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFF1E2A24)).padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun PartyAction(text: String, color: Color, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (filled) PartyAccentFill else PartyPanel)
            .border(1.dp, if (filled) PartyAccentFill else Color(0xFF3A3A42), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.White else color)
    }
}

@Composable
private fun PartyArt(item: YouTubeVideoItem, size: androidx.compose.ui.unit.Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(PartyPanelRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (item.thumbnailUrl != null) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = PartyDim, modifier = Modifier.size(size / 2.5f))
        }
    }
}
