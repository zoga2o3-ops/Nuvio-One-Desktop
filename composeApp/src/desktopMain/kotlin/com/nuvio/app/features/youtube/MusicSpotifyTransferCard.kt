package com.nuvio.app.features.youtube

import com.nuvio.app.core.ui.SectionPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private val SpotifyAccent = Color(0xFF1DB954)
private val SpotifyPanel: Color get() = SectionPalette.surface
private val SpotifyRaised: Color get() = SectionPalette.raised
private val SpotifyDim: Color get() = SectionPalette.muted
private val SpotifyDanger = Color(0xFFFF8A9B)

@Composable
internal fun SpotifyTransferCard() {
    LaunchedEffect(Unit) {
        MusicSpotifySession.ensureLoaded()
        MusicSpotifyTransfer.ensureLoaded()
    }
    val cookie = MusicSpotifySession.cookie
    val accountName = MusicSpotifySession.accountName
    val state = MusicSpotifyTransfer.state
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var showCookieField by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var showReview by remember { mutableStateOf(false) }
    var captureBusy by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    val busy = state.phase in setOf(
        SpotifyPhase.LOADING,
        SpotifyPhase.MATCHING,
        SpotifyPhase.IMPORTING,
    )

    SettingsCard(
        "Spotify transfer",
        "Copy playlists and Liked Songs into Nuvio, with match review",
        Icons.Filled.Sync,
    ) {
        if (cookie.isBlank()) {
            Text(
                "Sign in once to read Liked Songs and private playlists. A Spotify window opens and Nuvio saves the session automatically. Public playlists work without signing in.",
                fontSize = 11.5.sp,
                color = SpotifyDim,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MusicPrimaryButton(
                    label = if (captureBusy) "Waiting for Spotify…" else "Sign in automatically",
                    enabled = SpotifyCookieBrowser.isSupported && !captureBusy,
                    onClick = {
                        scope.launch {
                            captureBusy = true
                            captureError = null
                            SpotifyCookieBrowser.capture()
                                .onSuccess { MusicSpotifyTransfer.loadAccount() }
                                .onFailure {
                                    captureError = it.message ?: "Could not read the Spotify session."
                                }
                            captureBusy = false
                        }
                    },
                )
                TextButton(onClick = { showCookieField = !showCookieField }) {
                    Text("Paste cookie instead", color = SpotifyDim, fontSize = 11.5.sp)
                }
            }
            if (!SpotifyCookieBrowser.isSupported) {
                Text(
                    "Automatic sign-in is available on Windows. Paste the sp_dc cookie instead.",
                    fontSize = 10.5.sp,
                    color = SpotifyDim,
                )
            }
            if (captureBusy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = SpotifyAccent,
                        strokeWidth = 2.dp,
                    )
                    Text("Complete the sign-in in the Spotify window.", fontSize = 11.sp, color = SpotifyDim)
                }
            }
            captureError?.let { message ->
                Text(message, fontSize = 11.sp, color = SpotifyDanger)
            }
            if (showCookieField) {
                MusicFormField(
                    label = "sp_dc cookie",
                    value = cookieInput,
                    onValueChange = { cookieInput = it },
                    placeholder = "Paste the sp_dc value from open.spotify.com",
                    password = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MusicPrimaryButton(
                        label = "Save Spotify session",
                        enabled = cookieInput.isNotBlank(),
                        onClick = {
                            MusicSpotifySession.saveCookie(cookieInput)
                            cookieInput = ""
                            showCookieField = false
                            MusicSpotifyTransfer.loadAccount()
                        },
                    )
                    TextButton(onClick = { showCookieField = false }) {
                        Text("Cancel", color = SpotifyDim, fontSize = 11.5.sp)
                    }
                }
                Text(
                    "In a browser signed in to Spotify, open developer tools → Application → Cookies → open.spotify.com and copy sp_dc.",
                    fontSize = 10.5.sp,
                    color = SpotifyDim,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (accountName.isNullOrBlank()) "Spotify connected" else "Connected as $accountName",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SpotifyAccent,
                    )
                    Text("Liked Songs and private playlists are available.", fontSize = 11.sp, color = SpotifyDim)
                }
                TextButton(onClick = MusicSpotifyTransfer::loadAccount, enabled = !busy) {
                    Text("Load library", color = SpotifyDim, fontSize = 11.5.sp)
                }
                TextButton(onClick = MusicSpotifySession::clear) {
                    Text("Sign out", color = SpotifyDanger, fontSize = 11.5.sp)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        MusicFormField(
            label = "Spotify playlist link",
            value = link,
            onValueChange = { link = it },
            placeholder = "https://open.spotify.com/playlist/…",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MusicPrimaryButton(
                label = if (state.phase == SpotifyPhase.LOADING) "Reading…" else "Import playlist",
                enabled = link.isNotBlank() && !busy,
                onClick = { MusicSpotifyTransfer.importPlaylist(link) },
            )
            if (cookie.isNotBlank()) {
                MusicPrimaryButton(
                    label = "Liked Songs",
                    enabled = !busy,
                    onClick = MusicSpotifyTransfer::importLiked,
                )
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SpotifyAccent, strokeWidth = 2.dp)
                TextButton(onClick = MusicSpotifyTransfer::cancel) {
                    Text("Cancel", color = SpotifyDim, fontSize = 11.5.sp)
                }
            }
        }

        if (state.library.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Your Spotify library", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.library.take(8).forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !busy) {
                                if (entry.source is SpotifySource.Liked) {
                                    MusicSpotifyTransfer.importLiked()
                                } else {
                                    MusicSpotifyTransfer.importPlaylist("spotify:playlist:${entry.source.key}")
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, tint = SpotifyDim, modifier = Modifier.size(15.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(entry.owner.takeIf { it.isNotBlank() }, entry.count?.let { "$it tracks" })
                                    .joinToString(" · "),
                                fontSize = 10.5.sp,
                                color = SpotifyDim,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        SpotifyTransferStatus(state, onReview = { showReview = true })
    }

    if (showReview) {
        SpotifyReviewDialog(onClose = { showReview = false })
    }
}

@Composable
private fun SpotifyTransferStatus(state: SpotifyTransferState, onReview: () -> Unit) {
    if (state.phase == SpotifyPhase.IDLE && state.message == null && state.error == null) return
    Spacer(Modifier.height(6.dp))
    when (state.phase) {
        SpotifyPhase.LOADING -> {
            Text("Reading Spotify: ${state.loaded}/${state.total}", fontSize = 11.5.sp, color = SpotifyDim)
            TransferProgress(state.loaded, state.total)
        }
        SpotifyPhase.MATCHING -> {
            Text(
                "Matching ${state.searched}/${state.total} · ${state.found} found · ${state.unsure} to review · ${state.missing} missing",
                fontSize = 11.5.sp,
                color = SpotifyDim,
            )
            TransferProgress(state.searched, state.total)
        }
        SpotifyPhase.REVIEW -> {
            Text(
                if (state.total == 0) "No tracks found." else
                    "${state.found} matched · ${state.unsure} to review · ${state.missing} missing",
                fontSize = 12.sp,
                color = SpotifyDim,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.unsure > 0) {
                    MusicPrimaryButton("Review ${state.unsure}", onClick = onReview)
                    TextButton(onClick = MusicSpotifyTransfer::useBestGuesses) {
                        Text("Use best guesses", color = SpotifyDim, fontSize = 11.5.sp)
                    }
                }
                MusicPrimaryButton(
                    label = "Import ${state.found} matched",
                    enabled = state.found > 0,
                    onClick = MusicSpotifyTransfer::importMatches,
                )
            }
            if (state.resumed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resumed from a previous transfer.", fontSize = 10.5.sp, color = SpotifyDim)
                    TextButton(onClick = MusicSpotifyTransfer::clear) {
                        Text("Discard", color = SpotifyDanger, fontSize = 11.sp)
                    }
                }
            }
        }
        SpotifyPhase.IMPORTING -> {
            Text("Importing matched tracks…", fontSize = 11.5.sp, color = SpotifyDim)
            CircularProgressIndicator(modifier = Modifier.size(15.dp), color = SpotifyAccent, strokeWidth = 2.dp)
        }
        SpotifyPhase.DONE -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = SpotifyAccent, modifier = Modifier.size(16.dp))
                Text(state.message ?: "Import finished.", fontSize = 12.sp, color = SpotifyAccent, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = MusicSpotifyTransfer::clear) {
                Text("Start another transfer", color = SpotifyDim, fontSize = 11.5.sp)
            }
        }
        SpotifyPhase.ERROR -> {
            Text(state.error ?: "The transfer failed.", fontSize = 11.5.sp, color = SpotifyDanger)
            TextButton(onClick = MusicSpotifyTransfer::clear) {
                Text("Dismiss", color = SpotifyDim, fontSize = 11.5.sp)
            }
        }
        SpotifyPhase.IDLE -> {
            state.message?.let { Text(it, fontSize = 11.5.sp, color = SpotifyDim) }
        }
    }
    state.message?.takeIf { state.phase != SpotifyPhase.DONE }?.let {
        Text(it, fontSize = 11.5.sp, color = SpotifyDim)
    }
}

@Composable
private fun TransferProgress(done: Int, total: Int) {
    if (total <= 0) return
    Box(
        Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(SpotifyRaised),
    ) {
        Box(
            Modifier.fillMaxWidth((done.toFloat() / total).coerceIn(0f, 1f))
                .height(4.dp)
                .background(SpotifyAccent),
        )
    }
}

@Composable
private fun SpotifyReviewDialog(onClose: () -> Unit) {
    val state = MusicSpotifyTransfer.state
    val unsure = state.matches.withIndex().filter { it.value.state == SpotifyMatchState.UNSURE }
    var position by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val current = unsure.getOrNull(position.coerceIn(0, (unsure.size - 1).coerceAtLeast(0)))
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Review Spotify matches", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.widthIn(min = 440.dp, max = 620.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current == null) {
                    Text("All doubtful matches are reviewed.", fontSize = 12.sp, color = SpotifyDim)
                } else {
                    Text(
                        "${current.index + 1} of ${unsure.size}",
                        fontSize = 10.5.sp,
                        color = SpotifyDim,
                    )
                    Text(current.value.track.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        current.value.track.artistLine.ifBlank { "Unknown artist" },
                        fontSize = 12.sp,
                        color = SpotifyDim,
                    )
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(current.value.alternatives, key = { it.videoId }) { candidate ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        MusicSpotifyTransfer.choose(current.index, candidate)
                                        position = position.coerceAtMost((unsure.size - 2).coerceAtLeast(0))
                                    }
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SpotifyCandidateArt(candidate)
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        candidate.author ?: "YouTube",
                                        fontSize = 10.5.sp,
                                        color = SpotifyDim,
                                        maxLines = 1,
                                    )
                                }
                                Text(formatSpotifyDuration(candidate.durationMs), fontSize = 10.5.sp, color = SpotifyDim)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MusicFormField(
                            label = "Search another version",
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                MusicSpotifyTransfer.searchAlternatives(current.index, query)
                            },
                            enabled = query.isNotBlank(),
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = SpotifyDim, modifier = Modifier.size(15.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { MusicSpotifyTransfer.useBestGuesses() },
                        ) {
                            Text("Use best for all", color = SpotifyAccent, fontSize = 11.5.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                MusicSpotifyTransfer.skip(current.index)
                                position = position.coerceAtMost((unsure.size - 2).coerceAtLeast(0))
                            },
                        ) {
                            Text("Skip", color = SpotifyDim, fontSize = 11.5.sp)
                        }
                        TextButton(
                            onClick = { position = (position - 1).coerceAtLeast(0) },
                            enabled = position > 0,
                        ) {
                            Text("Previous", color = SpotifyDim, fontSize = 11.5.sp)
                        }
                        TextButton(
                            onClick = { position = (position + 1).coerceAtMost(unsure.lastIndex) },
                            enabled = position < unsure.lastIndex,
                        ) {
                            Text("Next", color = SpotifyDim, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Done", color = SpotifyAccent, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun SpotifyCandidateArt(item: YouTubeVideoItem) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(7.dp)).background(SpotifyPanel),
        contentAlignment = Alignment.Center,
    ) {
        if (item.thumbnailUrl != null) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Filled.Sync, contentDescription = null, tint = SpotifyDim, modifier = Modifier.size(16.dp))
        }
    }
}

private fun formatSpotifyDuration(ms: Long): String {
    if (ms <= 0) return ""
    val total = ms / 1000
    return String.format(java.util.Locale.US, "%d:%02d", total / 60, total % 60)
}
