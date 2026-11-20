package com.nuvio.app.features.youtube

import com.nuvio.app.core.ui.SectionPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.Locale
import kotlinx.coroutines.launch

private val SettingsAccent = Color(0xFF9AA8BD)
private val SettingsAccentFill = Color(0xFF64748B)
private val SettingsPanel: Color get() = SectionPalette.surface
private val SettingsPanelRaised: Color get() = SectionPalette.raised
private val SettingsMuted: Color get() = SectionPalette.muted
private val SettingsDim: Color get() = SectionPalette.text
private val SettingsDanger = Color(0xFFFF8A9B)

@Composable
internal fun MusicSettingsPane(onOpenParty: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        MusicEqualizer.ensureLoaded()
        MusicPlaybackSettings.ensureLoaded()
        MusicListeningStore.ensureLoaded()
        MusicScrobbler.ensureLoaded()
        MusicDriveUploader.ensureLoaded()
        MusicAccountScope.refreshAccounts()
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * 1.0f),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("Music settings", fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text("Playback, account and sharing options", fontSize = 13.sp, color = SettingsDim)
                }
            }
            item { AccountCard(scope) }
            item { PlaybackCard() }
            item { RecommendationsCard() }
            item { EqualizerCard() }
            item { SoundSourcesCard() }
            item { LyricsSourcesCard() }
            item { DownloadQualityCard() }
            item { ShareLocalCard() }
            item { DriveCard() }
            item { LastFmCard(scope) }
            item { SpotifyTransferCard() }
            item { PhoneLinkCard(scope) }
            // The party lives in Music; from Settings → Sections there is nowhere to open it.
            if (onOpenParty != null) item { PartyCard(onOpenParty) }
        }
    }
}

@Composable
internal fun SettingsCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SettingsPanel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, contentDescription = null, tint = SettingsAccent, modifier = Modifier.size(19.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 11.5.sp, color = SettingsDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        content()
    }
}

@Composable
private fun PlaybackCard() {
    val audio = MusicPlaybackSettings.audioQuality
    val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val speedLabels = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x")
    val speedIndex = speedOptions.indexOfFirst { kotlin.math.abs(it - MusicPlaybackSettings.playbackSpeed) < 0.01f }
        .coerceAtLeast(0)
    SettingsCard("Playback", "Streaming quality, speed and queue behaviour", Icons.Filled.Tune) {
        SettingsChoice(
            title = "Audio quality",
            detail = audio.detail,
            options = MusicAudioQuality.entries.map { it.label },
            selected = MusicAudioQuality.entries.indexOf(audio),
        ) { index -> MusicPlaybackSettings.chooseAudioQuality(MusicAudioQuality.entries[index]) }
        SettingsChoice(
            title = "Autoplay",
            detail = "Add related tracks when the queue reaches the end.",
            options = listOf("On", "Off"),
            selected = if (MusicPlaybackSettings.autoplay) 0 else 1,
        ) { index -> MusicPlaybackSettings.enableAutoplay(index == 0) }
        SettingsChoice(
            title = "Playback speed",
            detail = "Applies to the current and next tracks.",
            options = speedLabels,
            selected = speedIndex,
        ) { index -> MusicPlaybackSettings.choosePlaybackSpeed(speedOptions[index]) }
        SettingsChoice(
            title = "Fade in",
            detail = "Fades each new track in instead of starting abruptly.",
            options = listOf("Off", "1s", "2s", "3s", "4s"),
            selected = MusicPlaybackSettings.fadeSeconds,
        ) { index -> MusicPlaybackSettings.chooseFadeSeconds(index) }
    }
}

@Composable
private fun RecommendationsCard() {
    val enabled = MusicPlaybackSettings.personalizedRecommendations
    val plays = MusicListeningStore.totalPlays
    val artists = MusicListeningStore.artistCount
    SettingsCard(
        "Recommendations",
        "Build Made for you from what you actually listen to",
        Icons.Filled.AutoAwesome,
    ) {
        SettingsChoice(
            title = "Personalized Made for you",
            detail = if (enabled) {
                "Uses local play counts, likes and recent listens."
            } else {
                "Using YouTube Music's editorial feed instead."
            },
            options = listOf("On", "Off"),
            selected = if (enabled) 0 else 1,
        ) { index ->
            MusicPlaybackSettings.enablePersonalizedRecommendations(index == 0)
            MusicRecommendations.invalidate()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$plays plays · $artists artists", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "A track counts after 30 seconds. History stays on this device.",
                    fontSize = 11.5.sp,
                    color = SettingsDim,
                )
            }
            TextButton(
                onClick = {
                    MusicListeningStore.clear()
                    MusicRecommendations.invalidate()
                },
                enabled = plays > 0,
            ) {
                Text("Clear history", color = SettingsDanger, fontSize = 12.5.sp)
            }
        }
        Text(
            "Recommendations refresh when you return to Home, or with the refresh button.",
            fontSize = 11.sp,
            color = SettingsDim,
        )
    }
}

@Composable
private fun SoundSourcesCard() {
    SettingsCard("Sound & sources", "Audio processing and stream preference", Icons.Filled.Settings) {
        SettingsChoice(
            title = "Skip silence",
            detail = "Trims long silent gaps while a track plays.",
            options = listOf("On", "Off"),
            selected = if (MusicPlaybackSettings.skipSilence) 0 else 1,
        ) { index -> MusicPlaybackSettings.enableSkipSilence(index == 0) }
        SettingsChoice(
            title = "Prefer addon sources",
            detail = "Try an enabled music addon before YouTube for matching tracks.",
            options = listOf("On", "Off"),
            selected = if (MusicPlaybackSettings.preferAddonSources) 0 else 1,
        ) { index -> MusicPlaybackSettings.enablePreferAddonSources(index == 0) }
    }
}

@Composable
private fun DownloadQualityCard() {
    val quality = MusicPlaybackSettings.downloadQuality
    SettingsCard("Downloads", "Quality used for new offline downloads", Icons.Filled.FileDownload) {
        SettingsChoice(
            title = "Download quality",
            detail = quality.detail,
            options = MusicAudioQuality.entries.map { it.label },
            selected = MusicAudioQuality.entries.indexOf(quality),
        ) { index -> MusicPlaybackSettings.chooseDownloadQuality(MusicAudioQuality.entries[index]) }
        Text("Changing this does not re-download existing files.", fontSize = 11.sp, color = SettingsDim)
    }
}

@Composable
private fun SettingsChoice(
    title: String,
    detail: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 11.sp, color = SettingsDim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { index, label ->
                SettingsPill(label, index == selected) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun EqualizerCard() {
    val preset = MusicEqualizer.preset
    val gains = MusicEqualizer.gains
    SettingsCard("Equalizer", "Applied to music through the desktop player", Icons.Filled.Equalizer) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MusicEqualizerPreset.entries.forEach { option ->
                SettingsPill(option.label, option == preset) { MusicEqualizer.selectPreset(option) }
            }
        }
        MusicEqualizer.bandsHz.forEachIndexed { index, frequency ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    formatFrequency(frequency),
                    fontSize = 12.sp,
                    color = SettingsDim,
                    modifier = Modifier.width(48.dp),
                )
                Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                    // Centre tick marks 0 dB; the fill stays flat so a centred thumb reads as
                    // "no boost", not "half volume".
                    Box(Modifier.width(1.dp).height(11.dp).background(Color(0xFF4A4A55)))
                    Slider(
                        value = gains.getOrElse(index) { 0f },
                        onValueChange = { MusicEqualizer.setBandGain(index, it) },
                        valueRange = MusicEqualizer.MinGainDb..MusicEqualizer.MaxGainDb,
                        modifier = Modifier.fillMaxWidth().height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = SettingsAccent,
                            activeTrackColor = SettingsPanelRaised,
                            inactiveTrackColor = SettingsPanelRaised,
                        ),
                    )
                }
                Text(
                    String.format(Locale.US, "%+.1f dB", gains.getOrElse(index) { 0f }),
                    fontSize = 11.5.sp,
                    color = SettingsMuted,
                    modifier = Modifier.width(56.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (preset == MusicEqualizerPreset.OFF) "Equalizer is off." else "${preset.label} curve active.",
                fontSize = 11.5.sp,
                color = SettingsDim,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = MusicEqualizer::reset) {
                Text("Reset", color = SettingsMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ShareLocalCard() {
    val state = MusicShareServer.state
    val tracks = MusicLocalRepository.tracks
    SettingsCard("Share local music", "Browse and download your local library from another device", Icons.Filled.Share) {
        if (state.running && state.url != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MusicQrCode(state.url, Modifier.size(112.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.url, fontSize = 12.sp, color = SettingsAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${tracks.size} local tracks · ${tracks.sumOf { it.durationMs } / 60_000} minutes", fontSize = 11.sp, color = SettingsDim)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { copyToClipboard(state.url) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = SettingsMuted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Copy", color = SettingsMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = MusicShareServer::stop) {
                            Text("Stop", color = SettingsDanger, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (tracks.isEmpty()) {
                        "Scan local folders first — this shares the Local tab library."
                    } else {
                        "Open the link on a phone to stream or download your local tracks."
                    },
                    fontSize = 11.5.sp,
                    color = SettingsDim,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = MusicShareServer::start, enabled = tracks.isNotEmpty()) {
                    Text("Start", color = SettingsAccent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }
        state.error?.let { Text(it, fontSize = 12.sp, color = SettingsDanger) }
    }
}

@Composable
private fun DriveCard() {
    val state = MusicDriveUploader.state
    SettingsCard("Google Drive backup", "Copy your local music library into your own Drive", Icons.Filled.CloudUpload) {
        if (!state.configured) {
            Text("This build has no Google Drive credentials.", fontSize = 12.sp, color = SettingsDim)
            return@SettingsCard
        }
        if (!state.signedIn) {
            val prompt = state.signIn
            if (prompt != null) {
                Text("Open the Google page and enter this code:", fontSize = 12.sp, color = SettingsDim)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        prompt.userCode,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = SettingsAccent,
                    )
                    TextButton(onClick = { copyToClipboard(prompt.userCode) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = SettingsMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Copy", color = SettingsMuted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { openExternal(prompt.verificationUrl) }) {
                        Text("Open Google page", color = SettingsAccent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(13.dp), color = SettingsAccent, strokeWidth = 2.dp)
                    Text("Waiting for approval…", fontSize = 11.5.sp, color = SettingsDim)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sign in once, then upload the tracks scanned in Local.", fontSize = 11.5.sp, color = SettingsDim, modifier = Modifier.weight(1f))
                    TextButton(onClick = MusicDriveUploader::signIn, enabled = !state.busy) {
                        Text("Connect Google Drive", color = SettingsAccent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Connected to Google Drive", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SettingsAccent)
                    state.folderUrl?.let { url ->
                        TextButton(onClick = { openExternal(url) }, contentPadding = PaddingValues(0.dp)) {
                            Text("Open Nuvio Music folder", fontSize = 11.5.sp, color = SettingsMuted)
                        }
                    }
                }
                TextButton(onClick = MusicDriveUploader::signOut) {
                    Text("Sign out", color = SettingsDanger, fontSize = 12.sp)
                }
                TextButton(onClick = MusicDriveUploader::uploadLocalLibrary, enabled = !state.busy) {
                    Text(
                        if (state.busy) "Uploading…" else "Upload local library",
                        color = SettingsAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                    )
                }
            }
            state.progress?.let { progress ->
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)).background(SettingsPanelRaised)) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(SettingsAccentFill))
                }
                state.progressLabel?.let { Text(it, fontSize = 11.sp, color = SettingsDim) }
            }
        }
        state.message?.let { Text(it, fontSize = 12.sp, color = SettingsAccent) }
        state.error?.let { Text(it, fontSize = 12.sp, color = SettingsDanger) }
        Text("Only uploads your scanned local files; the drive.file scope limits access to files Nuvio creates.", fontSize = 11.sp, color = SettingsDim)
    }
}

internal fun openExternal(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

@Composable
private fun AccountCard(scope: kotlinx.coroutines.CoroutineScope) {
    val accounts = MusicAccountScope.accounts
    val pinned = MusicAccountScope.pinnedAccount
    val busy = MusicAccountScope.busy
    val status = MusicAccountScope.status
    SettingsCard(
        "Music account",
        "Use a separate YouTube profile for Music, with account liked songs",
        Icons.Filled.Person,
    ) {
        if (pinned != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccountAvatar(pinned, 34.dp)
                Column(Modifier.weight(1f)) {
                    Text(pinned.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("Pinned to Music", fontSize = 11.5.sp, color = SettingsAccent)
                }
                TextButton(onClick = MusicAccountScope::unpin) {
                    Text("Unpin", color = SettingsMuted, fontSize = 12.sp)
                }
            }
        } else {
            Text(
                "No Music profile pinned. Music uses the same account as the rest of Nuvio.",
                fontSize = 12.sp,
                color = SettingsDim,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SettingsAccent, strokeWidth = 2.dp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { scope.launch { MusicAccountScope.refreshAccounts() } }, enabled = !busy) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = SettingsMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text("Refresh profiles", color = SettingsMuted, fontSize = 12.sp)
            }
        }
        status?.let { Text(it, color = SettingsDanger, fontSize = 12.sp) }
        accounts.forEach { account ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable {
                    if (!account.selected || MusicAccountScope.pinnedKey != account.key) {
                        scope.launch { MusicAccountScope.pin(account.key) }
                    }
                }.padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                AccountAvatar(account, 28.dp)
                Column(Modifier.weight(1f)) {
                    Text(account.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    account.email?.let { Text(it, fontSize = 11.5.sp, color = SettingsDim, maxLines = 1) }
                }
                if (MusicAccountScope.pinnedKey == account.key) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = SettingsAccent, modifier = Modifier.size(15.dp))
                } else {
                    Text("Use in Music", fontSize = 11.5.sp, color = SettingsAccent)
                }
            }
        }
        Text(
            "Likes made in Music are written to this profile's YouTube liked videos.",
            fontSize = 11.sp,
            color = SettingsDim,
        )
    }
}

@Composable
private fun AccountAvatar(account: YouTubeAccountItem, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(999.dp)).background(SettingsPanelRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (account.avatarUrl != null) {
            AsyncImage(
                model = account.avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = SettingsDim, modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
private fun LastFmCard(scope: kotlinx.coroutines.CoroutineScope) {
    val config = MusicScrobbler.config
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    SettingsCard("Last.fm scrobbling", "Scrobble Music tracks to your Last.fm profile", Icons.Filled.Link) {
        if (!config.available) {
            Text("This build has no Last.fm credentials.", fontSize = 12.sp, color = SettingsDim)
            return@SettingsCard
        }
        if (config.configured) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Signed in as ${config.username}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { MusicScrobbler.setEnabled(!config.enabled) }) {
                    Text(if (config.enabled) "On" else "Off", color = if (config.enabled) SettingsAccent else SettingsDim, fontSize = 12.sp)
                }
                TextButton(onClick = { MusicScrobbler.logout(); message = null }) {
                    Text("Sign out", color = SettingsDanger, fontSize = 12.sp)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MusicFormField(
                    label = "Last.fm username",
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                )
                MusicFormField(
                    label = "Password",
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.weight(1f),
                    password = true,
                    enabled = !busy,
                )
            }
            MusicPrimaryButton(
                label = if (busy) "Signing in…" else "Connect Last.fm",
                enabled = username.isNotBlank() && password.isNotBlank() && !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        message = null
                        MusicScrobbler.login(username, password)
                            .onSuccess {
                                username = ""
                                password = ""
                                message = "Connected as $it."
                            }
                            .onFailure { message = it.message ?: "Could not sign in." }
                        busy = false
                    }
                },
            )
        }
        message?.let { Text(it, fontSize = 12.sp, color = SettingsDim) }
        Text("Tracks scrobble after half their length or four minutes, whichever comes first.", fontSize = 11.sp, color = SettingsDim)
    }
}

@Composable
private fun SpotifyCard(scope: kotlinx.coroutines.CoroutineScope) {
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    SettingsCard("Spotify import", "Copy a public Spotify playlist into a local Music playlist", Icons.Filled.Settings) {
        MusicFormField(
            label = "Spotify playlist link",
            value = url,
            onValueChange = { url = it },
            placeholder = "https://open.spotify.com/playlist/…",
            enabled = !busy,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MusicPrimaryButton(
                label = if (busy) "Importing…" else "Import playlist",
                enabled = url.isNotBlank() && !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        message = null
                        progress = "Reading the playlist…"
                        MusicSpotifyImport.import(url) { done, total ->
                            progress = "Matching $done of $total…"
                        }
                            .onSuccess {
                                message = "Imported ${it.imported} of ${it.total} tracks into \"${it.playlistName}\"."
                            }
                            .onFailure { error = it.message ?: "Could not import that playlist." }
                        busy = false
                        progress = ""
                    }
                },
            )
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SettingsAccent, strokeWidth = 2.dp)
                Text(progress, fontSize = 12.sp, color = SettingsDim)
            }
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(13.dp), color = SettingsAccent, strokeWidth = 2.dp)
            Text(progress, fontSize = 11.5.sp, color = SettingsDim)
        }
        message?.let { Text(it, fontSize = 12.sp, color = SettingsAccent) }
        error?.let { Text(it, fontSize = 12.sp, color = SettingsDanger) }
    }
}

@Composable
private fun PhoneLinkCard(scope: kotlinx.coroutines.CoroutineScope) {
    val state = MusicPhoneLink.state
    SettingsCard("Phone link", "Add WebDAV or Navidrome servers from your phone", Icons.Filled.PhoneAndroid) {
        if (state.running && state.url != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MusicQrCode(state.url, Modifier.size(112.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.url, fontSize = 12.sp, color = SettingsAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Scan with your phone camera, or copy the link.", fontSize = 11.sp, color = SettingsDim)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { copyToClipboard(state.url) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = SettingsMuted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Copy", color = SettingsMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = MusicPhoneLink::stop) {
                            Text("Stop", color = SettingsDanger, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Open this page on a phone on the same Wi-Fi network.", fontSize = 11.5.sp, color = SettingsDim, modifier = Modifier.weight(1f))
                TextButton(onClick = MusicPhoneLink::start) {
                    Text("Start", color = SettingsAccent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }
        state.error?.let { Text(it, fontSize = 12.sp, color = SettingsDanger) }
    }
}

@Composable
private fun PartyCard(onOpenParty: () -> Unit) {
    val state = MusicParty.state
    SettingsCard("Party", "Let guests add songs from their phones", Icons.Filled.Groups) {
        if (state.running && state.url != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MusicQrCode(state.url, Modifier.size(112.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.url, fontSize = 12.sp, color = SettingsAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Scan to open the guest queue.", fontSize = 11.sp, color = SettingsDim)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { copyToClipboard(state.url) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = SettingsMuted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Copy", color = SettingsMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = onOpenParty) {
                            Text("Host view", color = SettingsMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Start a party to show the guest link and approve requests.", fontSize = 11.5.sp, color = SettingsDim, modifier = Modifier.weight(1f))
                TextButton(onClick = MusicParty::start) {
                    Text("Start party", color = SettingsAccent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }
        state.error?.let { Text(it, fontSize = 12.sp, color = SettingsDanger) }
    }
}

@Composable
internal fun SettingsPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (active) SettingsAccentFill else SettingsPanelRaised)
            .border(1.dp, if (active) SettingsAccentFill else Color(0xFF3A3A42), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

internal fun copyToClipboard(value: String?) {
    if (value.isNullOrBlank()) return
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
    }
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1000) "${hz / 1000}k" else hz.toString()
