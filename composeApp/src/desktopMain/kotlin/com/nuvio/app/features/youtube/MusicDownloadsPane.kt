package com.nuvio.app.features.youtube

import com.nuvio.app.core.ui.SectionPalette

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
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
import java.awt.Desktop

private val MusicAccent = Color(0xFF9AA8BD)
private val MusicAccentFill = Color(0xFF64748B)
private val MusicPanel: Color get() = SectionPalette.surface
private val MusicPanelRaised: Color get() = SectionPalette.raised
private val MusicMuted: Color get() = SectionPalette.muted
private val MusicDim: Color get() = SectionPalette.text

@Composable
internal fun MusicDownloadsPane() {
    LaunchedEffect(Unit) { MusicDownloads.ensureLoaded() }
    val entries = MusicDownloads.entries
    val activeId = MusicDownloads.activeId
    val progress = MusicDownloads.progress
    val error = MusicDownloads.error
    val current = MusicPlaybackQueue.current

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text("Downloads", fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("${entries.size} saved tracks", fontSize = 12.5.sp, color = MusicDim)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { runCatching { Desktop.getDesktop().open(MusicDownloads.folder.toFile()) } }) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = MusicMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Open folder", color = MusicMuted, fontSize = 12.5.sp)
            }
            if (current != null && !MusicDownloads.isDownloaded(current.videoId)) {
                TextButton(onClick = { MusicDownloads.download(current) }, enabled = activeId == null) {
                    Text(if (activeId == current.videoId) "Downloading ${(progress * 100).toInt()}%" else "Download current", color = MusicAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (activeId != null) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)).background(MusicPanelRaised)) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(MusicAccentFill))
                }
                Text("Downloading… ${(progress * 100).toInt()}%", fontSize = 11.5.sp, color = MusicDim)
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8A9B), fontSize = 13.sp) }
        when {
            entries.isEmpty() && activeId == null -> Text(
                "No downloads yet. Play a track and choose Download current.",
                color = MusicDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 20.dp),
            )
            else -> {
                val items = entries.map(MusicDownloads::toVideoItem)
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    itemsIndexed(entries, key = { _, entry -> entry.videoId }) { index, entry ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { MusicPlaybackQueue.play(items, index) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DownloadArt(entry)
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${entry.author ?: "Music"} · ${formatDownloadDuration(entry.durationMs)}", fontSize = 12.sp, color = MusicDim, maxLines = 1)
                            }
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MusicMuted, modifier = Modifier.size(18.dp))
                            IconButton(onClick = { MusicDownloads.delete(entry) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete download", tint = MusicDim, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadArt(entry: MusicDownloadEntry) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MusicPanelRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (entry.thumbnailUrl != null) {
            AsyncImage(
                model = entry.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MusicDim, modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatDownloadDuration(ms: Long): String {
    if (ms <= 0) return ""
    val total = ms / 1000
    return String.format(java.util.Locale.US, "%d:%02d", total / 60, total % 60)
}
