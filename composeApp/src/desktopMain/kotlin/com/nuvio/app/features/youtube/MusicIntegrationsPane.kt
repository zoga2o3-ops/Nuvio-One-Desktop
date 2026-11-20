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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private val MusicAccent = Color(0xFF9AA8BD)
private val MusicAccentFill = Color(0xFF64748B)
private val MusicPanel: Color get() = SectionPalette.surface
private val MusicPanelRaised: Color get() = SectionPalette.raised
private val MusicMuted: Color get() = SectionPalette.muted
private val MusicDim: Color get() = SectionPalette.text

@Composable
internal fun MusicIntegrationsPane() {
    LaunchedEffect(Unit) { MusicRemoteStore.load() }
    var source by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * 1.0f),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("Integrations", fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("Connect your own music servers", fontSize = 13.sp, color = MusicDim)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntegrationPill("WebDAV", source == 0) { source = 0 }
                IntegrationPill("Navidrome", source == 1) { source = 1 }
                IntegrationPill("Addons", source == 2) { source = 2 }
            }
            if (source == 0) WebDavSection() else if (source == 1) NavidromeSection() else AddonsSection()
        }
    }
}

@Composable
private fun IntegrationPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (active) MusicAccentFill else MusicPanel)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun WebDavSection() {
    val scope = rememberCoroutineScope()
    val config = MusicRemoteStore.webDav
    var url by remember(config.url) { mutableStateOf(config.url) }
    var user by remember(config.username) { mutableStateOf(config.username) }
    var password by remember(config.password) { mutableStateOf(config.password) }
    var editing by remember { mutableStateOf(config.url.isBlank()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var browsing by remember { mutableStateOf(false) }
    var path by remember(config.url) { mutableStateOf(config.url) }
    var stack by remember { mutableStateOf<List<String>>(emptyList()) }
    var entries by remember { mutableStateOf<List<WebDavEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(browsing, path, config.url, refresh) {
        if (!browsing || config.url.isBlank()) return@LaunchedEffect
        loading = true
        MusicWebDavClient.list(config, path.ifBlank { config.url })
            .onSuccess { entries = it; error = null }
            .onFailure { error = it.message ?: "Could not read this WebDAV folder." }
        loading = false
    }

    fun connect() {
        val normalized = url.trim().let { if (it.endsWith('/')) it else "$it/" }
        if (normalized.isBlank()) return
        scope.launch {
            busy = true
            error = null
            val candidate = WebDavConfig(normalized, user.trim(), password)
            MusicWebDavClient.list(candidate, normalized)
                .onSuccess {
                    MusicRemoteStore.saveWebDav(candidate)
                    editing = false
                    browsing = true
                    path = normalized
                    stack = emptyList()
                }
                .onFailure { error = it.message ?: "Could not connect." }
            busy = false
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionCard(
                title = "WebDAV",
                subtitle = if (config.url.isBlank()) "Browse folders on a WebDAV server." else config.url,
                icon = Icons.Filled.Cloud,
            ) {
                if (editing) {
                    CredentialsFields(url, user, password, busy, onUrl = { url = it }, onUser = { user = it }, onPassword = { password = it })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MusicPrimaryButton(
                            label = when {
                                busy -> "Connecting…"
                                config.url.isBlank() -> "Connect WebDAV"
                                else -> "Save & connect"
                            },
                            enabled = !busy && url.isNotBlank(),
                            onClick = ::connect,
                        )
                        if (config.url.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    url = config.url
                                    user = config.username
                                    password = config.password
                                    editing = false
                                },
                            ) {
                                Text("Cancel", color = MusicMuted, fontSize = 13.5.sp)
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Connected", fontSize = 13.5.sp, color = MusicAccent, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { browsing = !browsing }) {
                            Text(if (browsing) "Hide browser" else "Browse", color = MusicMuted, fontSize = 13.5.sp)
                        }
                        TextButton(onClick = { editing = true; browsing = false }) {
                            Text("Edit", color = MusicMuted, fontSize = 13.5.sp)
                        }
                        TextButton(onClick = { MusicRemoteStore.clearWebDav(); editing = true; browsing = false }) {
                            Text("Disconnect", color = Color(0xFFFF8A9B), fontSize = 13.5.sp)
                        }
                    }
                }
            }
        }
        error?.let { item { Text(it, color = Color(0xFFFF8A9B), fontSize = 13.sp) } }
        if (browsing) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            if (stack.isEmpty()) {
                                browsing = false
                            } else {
                                path = stack.last()
                                stack = stack.dropLast(1)
                            }
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MusicMuted, modifier = Modifier.size(16.dp))
                    }
                    Text(path.substringAfterLast('/').ifBlank { "WebDAV" }, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MusicAccent, strokeWidth = 2.dp)
                    IconButton(onClick = { refresh += 1 }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MusicMuted, modifier = Modifier.size(15.dp))
                    }
                }
            }
            val audioEntries = entries.filter { !it.isDirectory && MusicWebDavClient.isAudio(it.name) }
            val audioItems = audioEntries.map { MusicNavidromeClient.toVideoItem(config, it) }
            items(entries, key = { it.url }) { entry ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable {
                        if (entry.isDirectory) {
                            stack = stack + path
                            path = entry.url
                        } else if (MusicWebDavClient.isAudio(entry.name)) {
                            val index = audioEntries.indexOf(entry).coerceAtLeast(0)
                            MusicPlaybackQueue.play(audioItems, index)
                        }
                    }.padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MusicMuted,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(entry.name, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (!entry.isDirectory && MusicWebDavClient.isAudio(entry.name)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MusicAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (!loading && entries.isEmpty()) {
                item { Text("This folder is empty.", color = MusicDim, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun NavidromeSection() {
    val scope = rememberCoroutineScope()
    val config = MusicRemoteStore.navidrome
    var url by remember(config.url) { mutableStateOf(config.url) }
    var user by remember(config.username) { mutableStateOf(config.username) }
    var password by remember(config.password) { mutableStateOf(config.password) }
    var editing by remember { mutableStateOf(config.url.isBlank()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var albums by remember { mutableStateOf<List<NavidromeAlbum>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<NavidromePlaylist>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<NavidromeAlbum?>(null) }
    var selectedPlaylist by remember { mutableStateOf<NavidromePlaylist?>(null) }
    var tracks by remember { mutableStateOf<List<RemoteTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(config.url, selectedAlbum?.id, selectedPlaylist?.id) {
        if (config.url.isBlank() || editing) return@LaunchedEffect
        loading = true
        if (selectedAlbum != null) {
            MusicNavidromeClient.albumTracks(config, selectedAlbum!!.id)
                .onSuccess { tracks = it; error = null }
                .onFailure { error = it.message ?: "Could not load the album." }
        } else if (selectedPlaylist != null) {
            MusicNavidromeClient.playlistTracks(config, selectedPlaylist!!.id)
                .onSuccess { tracks = it; error = null }
                .onFailure { error = it.message ?: "Could not load the playlist." }
        } else {
            MusicNavidromeClient.albums(config).onSuccess { albums = it }
            MusicNavidromeClient.playlists(config).onSuccess { playlists = it }
        }
        loading = false
    }

    fun connect() {
        val normalized = url.trim().let { if (it.endsWith('/')) it.trimEnd('/') else it }
        if (normalized.isBlank()) return
        scope.launch {
            busy = true
            error = null
            val candidate = NavidromeConfig(normalized, user.trim(), password)
            MusicNavidromeClient.ping(candidate)
                .onSuccess {
                    MusicRemoteStore.saveNavidrome(candidate)
                    editing = false
                    selectedAlbum = null
                    selectedPlaylist = null
                }
                .onFailure { error = it.message ?: "Could not connect." }
            busy = false
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionCard(
                title = "Navidrome",
                subtitle = if (config.url.isBlank()) "Connect a Subsonic-compatible server." else config.url,
                icon = Icons.Filled.LibraryMusic,
            ) {
                if (editing) {
                    CredentialsFields(url, user, password, busy, onUrl = { url = it }, onUser = { user = it }, onPassword = { password = it })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MusicPrimaryButton(
                            label = when {
                                busy -> "Connecting…"
                                config.url.isBlank() -> "Connect Navidrome"
                                else -> "Save & connect"
                            },
                            enabled = !busy && url.isNotBlank(),
                            onClick = ::connect,
                        )
                        if (config.url.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    url = config.url
                                    user = config.username
                                    password = config.password
                                    editing = false
                                },
                            ) {
                                Text("Cancel", color = MusicMuted, fontSize = 13.5.sp)
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Connected", fontSize = 13.5.sp, color = MusicAccent, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { editing = true }) { Text("Edit", color = MusicMuted, fontSize = 13.5.sp) }
                        TextButton(onClick = { MusicRemoteStore.clearNavidrome(); editing = true; albums = emptyList(); playlists = emptyList() }) {
                            Text("Disconnect", color = Color(0xFFFF8A9B), fontSize = 13.5.sp)
                        }
                    }
                }
            }
        }
        error?.let { item { Text(it, color = Color(0xFFFF8A9B), fontSize = 13.sp) } }
        if (!editing && (selectedAlbum != null || selectedPlaylist != null)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedAlbum = null; selectedPlaylist = null; tracks = emptyList() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MusicMuted, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        selectedAlbum?.name ?: selectedPlaylist?.name ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MusicAccent, strokeWidth = 2.dp)
                }
            }
            val items = tracks.map { MusicNavidromeClient.toVideoItem(config, it) }
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(track.title, track.artist, track.durationMs) {
                    MusicPlaybackQueue.play(items, index)
                }
            }
        } else if (!editing) {
            if (loading) item { CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MusicAccent, strokeWidth = 2.dp) }
            item { Text("Albums", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            items(albums, key = { it.id }) { album ->
                val cover = MusicNavidromeClient.coverUrl(config, album.coverArt, 120)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable { selectedAlbum = album }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RemoteArt(cover, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(album.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(album.artist, fontSize = 11.5.sp, color = MusicDim, maxLines = 1)
                    }
                    Text("${album.songCount} tracks", fontSize = 11.sp, color = MusicDim)
                }
            }
            if (playlists.isNotEmpty()) {
                item { Text("Playlists", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                items(playlists, key = { "p:${it.id}" }) { playlist ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable { selectedPlaylist = playlist }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = MusicMuted, modifier = Modifier.size(20.dp))
                        Text(playlist.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${playlist.songCount} tracks", fontSize = 11.sp, color = MusicDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddonsSection() {
    LaunchedEffect(Unit) { MusicAddonRegistry.load() }
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<MusicAddonTrack>>(emptyList()) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard("Music addons", "HTTP music addons: manifest.json + search + stream", Icons.Filled.MusicNote) {
                MusicFormField(
                    label = "Addon URL",
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://example.com/manifest.json",
                    enabled = !adding,
                )
                MusicPrimaryButton(
                    label = if (adding) "Adding…" else "Add addon",
                    enabled = url.isNotBlank() && !adding,
                    onClick = {
                        scope.launch {
                            adding = true
                            error = null
                            MusicAddonRegistry.add(url)
                                .onSuccess { url = "" }
                                .onFailure { error = it.message ?: "Could not read that addon." }
                            adding = false
                        }
                    },
                )
                error?.let { Text(it, color = Color(0xFFFF8A9B), fontSize = 12.5.sp) }
                MusicAddonRegistry.configs.forEach { config ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MusicMuted, modifier = Modifier.size(15.dp))
                        Column(Modifier.weight(1f)) {
                            Text(config.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(config.url, fontSize = 11.sp, color = MusicDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { MusicAddonRegistry.setEnabled(config, !config.enabled) }) {
                            Text(if (config.enabled) "On" else "Off", color = if (config.enabled) MusicAccent else MusicDim, fontSize = 12.sp)
                        }
                        IconButton(onClick = { MusicAddonRegistry.remove(config) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove addon", tint = MusicDim, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
        item {
            SectionCard("Addon search", "Search the enabled addons directly", Icons.Filled.Search) {
                MusicFormField(
                    label = "Search addons",
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Song or artist",
                    enabled = !searching,
                )
                MusicPrimaryButton(
                    label = if (searching) "Searching…" else "Search",
                    enabled = query.isNotBlank() && !searching && MusicAddonRegistry.configs.any { it.enabled },
                    onClick = {
                        scope.launch {
                            searching = true
                            error = null
                            results = MusicAddonRegistry.search(query)
                            searching = false
                        }
                    },
                )
                if (searching) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MusicAccent, strokeWidth = 2.dp)
                if (!searching && query.isNotBlank() && results.isEmpty()) {
                    Text("No addon results.", fontSize = 12.5.sp, color = MusicDim)
                }
            }
        }
        items(results, key = { "${it.configId}:${it.id}" }) { track ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable {
                    scope.launch {
                        error = null
                        MusicAddonRegistry.resolve(track)
                            .onSuccess { MusicPlaybackQueue.play(listOf(it), 0) }
                            .onFailure { error = it.message ?: "Could not resolve this addon track." }
                    }
                }.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MusicMuted, modifier = Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${track.artist} · ${track.configName}", fontSize = 11.5.sp, color = MusicDim, maxLines = 1)
                }
                Text(formatRemoteDuration((track.durationSeconds ?: 0) * 1000L), fontSize = 11.sp, color = MusicDim)
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MusicAccent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MusicPanel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, contentDescription = null, tint = MusicAccent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 11.5.sp, color = MusicDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        content()
    }
}

@Composable
private fun CredentialsFields(
    url: String,
    user: String,
    password: String,
    busy: Boolean,
    onUrl: (String) -> Unit,
    onUser: (String) -> Unit,
    onPassword: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MusicFormField(
            label = "Server URL",
            value = url,
            onValueChange = onUrl,
            placeholder = "https://example.com/dav/",
            enabled = !busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicFormField(
                label = "Username",
                value = user,
                onValueChange = onUser,
                modifier = Modifier.weight(1f),
                enabled = !busy,
            )
            MusicFormField(
                label = "Password",
                value = password,
                onValueChange = onPassword,
                modifier = Modifier.weight(1f),
                password = true,
                enabled = !busy,
            )
        }
    }
}

@Composable
private fun TrackRow(title: String, artist: String, durationMs: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MusicMuted, modifier = Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, fontSize = 11.5.sp, color = MusicDim, maxLines = 1)
        }
        Text(formatRemoteDuration(durationMs), fontSize = 11.sp, color = MusicDim)
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MusicAccent, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun RemoteArt(url: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(7.dp)).background(MusicPanelRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MusicDim, modifier = Modifier.size(16.dp))
        }
    }
}

private fun formatRemoteDuration(ms: Long): String {
    if (ms <= 0) return ""
    val total = ms / 1000
    return String.format(java.util.Locale.US, "%d:%02d", total / 60, total % 60)
}
