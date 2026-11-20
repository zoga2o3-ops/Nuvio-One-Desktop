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

/** Recordings page: saved files with play, delete and folder actions. */
@Composable
internal fun IptvRecordingsPane(
    recordings: List<Path>,
    onPlay: (Path) -> Unit,
    onDelete: (Path) -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: () -> Unit,
    onChangeFolder: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Recordings", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                Text(
                    "${recordings.size} saved · ${IptvRecordingStore.folder()}",
                    fontSize = 11.5.sp,
                    color = IptvDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = IptvMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh", fontSize = 12.sp, color = IptvMuted)
            }
            TextButton(onClick = onChangeFolder) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = IptvMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Change folder", fontSize = 12.sp, color = IptvMuted)
            }
            TextButton(onClick = onOpenFolder) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = IptvMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open folder", fontSize = 12.sp, color = IptvMuted)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (recordings.isEmpty()) {
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
                    "Use Record while watching a channel to save it here.",
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
                items(recordings, key = { it.toString() }) { path ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(IptvPanel)
                            .border(1.dp, IptvBorderSoft, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF241621)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = IptvAccent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                path.fileName.toString(),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${runCatching { path.toFile().length() / 1024 / 1024 }.getOrDefault(0L)} MB · ${path.parent?.fileName}",
                                fontSize = 11.sp,
                                color = IptvDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onPlay(path) }) { Text("Play", fontSize = 12.sp) }
                        TextButton(onClick = { onDelete(path) }) { Text("Delete", fontSize = 12.sp, color = IptvMuted) }
                    }
                }
            }
        }
    }
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
