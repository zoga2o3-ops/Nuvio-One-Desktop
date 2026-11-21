package com.nuvio.app.features.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import java.nio.file.Path
import java.util.Locale

/** Movies and Series pages: a poster grid over the playlist's on-demand entries. */
@Composable
internal fun IptvVodGridPane(
    title: String,
    subtitle: String,
    movies: List<VodItem>,
    emptyMessage: String,
    onPlay: (VodItem) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                Text(subtitle, fontSize = 11.5.sp, color = IptvDim)
            }
            Text("${movies.size}", fontSize = 12.sp, color = IptvDim)
        }
        Spacer(Modifier.height(14.dp))

        if (movies.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().height(260.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(IptvPanel)
                    .border(1.dp, IptvBorderSoft, RoundedCornerShape(14.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = IptvDim,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(emptyMessage, fontSize = 12.5.sp, color = IptvMuted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(movies, key = { it.key }) { movie ->
                    VodCard(movie = movie, onClick = { onPlay(movie) })
                }
            }
        }
    }
}

@Composable
private fun VodCard(movie: VodItem, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(vodTileColor(movie.name)),
        ) {
            Text(
                vodInitials(movie.name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            movie.logo?.takeIf { it.isNotBlank() }?.let { url ->
                NuvioAsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                    .background(Color(0xCC0B0B0F)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    movie.group,
                    fontSize = 9.5.sp,
                    color = IptvMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            movie.durationSeconds?.takeIf { it > 0 }?.let { duration ->
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        formatVodDuration(duration.toLong()),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
        Text(
            movie.name,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

/** Recordings page: local files plus what the TV and phone saved on the SMB share. */
@Composable
internal fun IptvRecordingsPane(
    recordings: List<Path>,
    shared: List<SharedRecording>,
    smbConfigured: Boolean,
    smbLabel: String?,
    scanning: Boolean,
    scanError: String?,
    busy: IptvRecordingExport.Progress?,
    busyTitle: String?,
    onPlay: (Path) -> Unit,
    onDelete: (Path) -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: () -> Unit,
    onChangeFolder: () -> Unit,
    onExportLocal: (Path) -> Unit,
    onPlayShared: (SharedRecording) -> Unit,
    onCombineShared: (SharedRecording, Boolean) -> Unit,
    onExportShared: (SharedRecording, Boolean) -> Unit,
    onDeleteShared: (SharedRecording) -> Unit,
    onRecordingSettings: () -> Unit,
) {
    var action by remember { mutableStateOf<Pair<SharedRecording, String>?>(null) }
    var deleteShared by remember { mutableStateOf<SharedRecording?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Recordings", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                Text(
                    buildString {
                        append("${recordings.size} on this computer")
                        if (smbConfigured) append(" \u00B7 ${shared.size} on the share")
                        append(" \u00B7 ${IptvRecordingStore.folder()}")
                    },
                    fontSize = 11.5.sp,
                    color = IptvDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = IptvMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (scanning) "Scanning..." else "Refresh", fontSize = 12.sp, color = IptvMuted)
            }
            TextButton(onClick = onRecordingSettings) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = IptvMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share & export", fontSize = 12.sp, color = IptvMuted)
            }
            TextButton(onClick = onChangeFolder) { Text("Change folder", fontSize = 12.sp, color = IptvMuted) }
            TextButton(onClick = onOpenFolder) { Text("Open folder", fontSize = 12.sp, color = IptvMuted) }
        }
        Spacer(Modifier.height(14.dp))

        busy?.let { progress ->
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(IptvPanel)
                    .border(1.dp, IptvBorderSoft, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    "${progress.stage} ${busyTitle.orEmpty()}".trim(),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                androidx.compose.material3.LinearProgressIndicator(
                    progress = {
                        if (progress.totalBytes > 0) {
                            (progress.doneBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${progress.doneBytes / 1024 / 1024} MB of ${progress.totalBytes / 1024 / 1024} MB",
                    fontSize = 10.5.sp,
                    color = IptvDim,
                )
            }
        }

        scanError?.let {
            Text(it, fontSize = 11.5.sp, color = Color(0xFFFF8A9B), modifier = Modifier.padding(bottom = 8.dp))
        }

        if (recordings.isEmpty() && shared.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().height(240.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(IptvPanel)
                    .border(1.dp, IptvBorderSoft, RoundedCornerShape(14.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = IptvDim,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("No recordings yet.", fontSize = 12.5.sp, color = IptvMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Use Record while watching a channel, or connect the TV's SMB share in Share & export.",
                    fontSize = 11.sp,
                    color = IptvDim,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (recordings.isNotEmpty()) {
                    item { SectionHeader("On this computer") }
                }
                items(recordings, key = { it.toString() }) { path ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(IptvPanel)
                            .border(1.dp, IptvBorderSoft, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF241621)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = IptvAccent, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(path.fileName.toString(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${runCatching { path.toFile().length() / 1024 / 1024 }.getOrDefault(0L)} MB \u00B7 ${path.parent?.fileName}",
                                fontSize = 11.sp,
                                color = IptvDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onPlay(path) }) { Text("Play", fontSize = 12.sp) }
                        TextButton(onClick = { onExportLocal(path) }) { Text("Export MP4", fontSize = 12.sp) }
                        TextButton(onClick = { onDelete(path) }) { Text("Delete", fontSize = 12.sp, color = IptvMuted) }
                    }
                }

                if (smbConfigured) {
                    item {
                        SectionHeader(
                            if (shared.isEmpty()) "On the SMB share - scanning $smbLabel"
                            else "On the SMB share - ${shared.size} recording${if (shared.size == 1) "" else "s"}",
                        )
                    }
                    if (shared.isEmpty()) {
                        item {
                            Text(
                                if (scanning) "Reading the share..." else "No other recordings on the share yet.",
                                fontSize = 11.5.sp,
                                color = IptvDim,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(shared, key = { "shared:${it.id}" }) { rec ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(IptvPanel)
                                .border(1.dp, IptvBorderSoft, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1C2433)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = IptvAccent, modifier = Modifier.size(20.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (rec.isRecording) "\u25CF " else "") + rec.title,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildString {
                                        if (rec.channelName.isNotBlank()) append(rec.channelName).append(" \u00B7 ")
                                        append(rec.durationSec.toLong().let(::formatVodDuration))
                                        append(" \u00B7 ").append(rec.sizeBytes / 1024 / 1024).append(" MB")
                                        if (rec.isRecording) append(" \u00B7 recording now")
                                    },
                                    fontSize = 11.sp,
                                    color = IptvDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { onPlayShared(rec) }) { Text("Play", fontSize = 12.sp) }
                            if (!rec.isRecording) {
                                TextButton(onClick = { action = rec to "combine" }) { Text("Combine", fontSize = 12.sp) }
                                TextButton(onClick = { action = rec to "export" }) { Text("Export MP4", fontSize = 12.sp) }
                                TextButton(onClick = { deleteShared = rec }) { Text("Delete", fontSize = 12.sp, color = IptvMuted) }
                            }
                        }
                    }
                }
            }
        }
    }

    action?.let { (rec, kind) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { action = null },
            title = { Text(if (kind == "combine") "Combine recording" else "Export recording") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(rec.title, fontSize = 12.5.sp)
                    Text(
                        if (kind == "combine") "One MPEG-TS file, joined without re-encoding."
                        else "Remux to MP4 with ffmpeg. No re-encoding, so the same quality.",
                        fontSize = 11.5.sp,
                        color = IptvDim,
                    )
                    TextButton(onClick = {
                        action = null
                        if (kind == "combine") onCombineShared(rec, true) else onExportShared(rec, true)
                    }) { Text("Save to SMB share (Exports)") }
                    TextButton(onClick = {
                        action = null
                        if (kind == "combine") onCombineShared(rec, false) else onExportShared(rec, false)
                    }) { Text("Save on this computer") }
                }
            },
            confirmButton = { TextButton(onClick = { action = null }) { Text("Cancel") } },
        )
    }
    deleteShared?.let { rec ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteShared = null },
            title = { Text("Delete this recording?") },
            text = { Text("\"${rec.title}\" will be removed from the share, for every device.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteShared = null
                    onDeleteShared(rec)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteShared = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = IptvMuted,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

private fun formatVodDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun vodInitials(name: String): String {
    val words = name.split(' ', '-', ':', '|', '/').filter { it.isNotBlank() }
    return if (words.size >= 2) {
        "${words[0].first()}${words[1].first()}".uppercase(Locale.US)
    } else {
        name.trim().take(2).uppercase(Locale.US)
    }
}

private fun vodTileColor(name: String): Color {
    val palette = listOf(
        Color(0xFF4A1F2A), Color(0xFF1F2A4A), Color(0xFF1F4A3D), Color(0xFF3D1F4A),
        Color(0xFF4A3A1F), Color(0xFF1F3D4A), Color(0xFF2F4A1F), Color(0xFF4A281F),
    )
    return palette[(name.hashCode() and 0x7fffffff) % palette.size]
}
