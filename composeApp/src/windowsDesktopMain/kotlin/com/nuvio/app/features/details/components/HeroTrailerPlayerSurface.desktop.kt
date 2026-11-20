package com.nuvio.app.features.details.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.LocalNuvioPlatformDensity
import com.nuvio.app.core.ui.NativeVideoCover
import com.nuvio.app.core.ui.NativeVideoHover
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.desktop.NativePlayerController
import com.nuvio.app.features.player.desktop.NativePlayerHost
import com.nuvio.app.features.youtube.YouTubeStreamProxy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Trailer previews (poster hover card, details header) on Windows, played by the app's own mpv
 * player. The Compose video library the other desktop builds use isn't in the Windows build.
 *
 * mpv draws in a native window, which can't fade or be clipped, so it is kept out of sight (one
 * pixel) until the trailer is actually playing, and whenever a menu or dialog covers it; the
 * card's image shows meanwhile. YouTube's streams go through [YouTubeStreamProxy] so the start
 * isn't held back by YouTube's per-request speed cap.
 */
@Composable
actual fun HeroTrailerPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    playWhenReady: Boolean,
    muted: Boolean,
    startPositionMillis: Long,
    fillFrame: Boolean,
    modifier: Modifier,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
) {
    key(sourceUrl, sourceAudioUrl, startPositionMillis) {
        WindowsTrailerSession(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            playWhenReady = playWhenReady,
            muted = muted,
            startPositionMillis = startPositionMillis,
            fillFrame = fillFrame,
            modifier = modifier,
            onReady = onReady,
            onEnded = onEnded,
            onError = onError,
        )
    }
}

private const val TrailerStartTimeoutMs = 15_000L

@Composable
private fun WindowsTrailerSession(
    sourceUrl: String,
    sourceAudioUrl: String?,
    playWhenReady: Boolean,
    muted: Boolean,
    startPositionMillis: Long,
    fillFrame: Boolean,
    modifier: Modifier,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
) {
    val platformDensity = LocalNuvioPlatformDensity.current
    val playerSettings by PlayerSettingsRepository.uiState.collectAsState()
    val host = remember { NativePlayerHost() }
    val controller = remember(host) { NativePlayerController(host) }
    val latestOnReady = rememberUpdatedState(onReady)
    val latestOnEnded = rememberUpdatedState(onEnded)
    val latestOnError = rememberUpdatedState(onError)
    var ready by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    DisposableEffect(host) {
        onDispose {
            NativeVideoHover.set(host, false)
            controller.dispose()
        }
    }

    // The pointer over the video still counts as over the card (see NativeVideoHover). Checked
    // against the real pointer position: enter/exit events from a native window that is resized
    // under the pointer don't arrive reliably, and a missed "exit" would keep the card open.
    LaunchedEffect(host) {
        while (isActive) {
            delay(100)
            val over = runCatching {
                if (!host.isShowing || host.width < 4 || host.height < 4) {
                    false
                } else {
                    val pointer = java.awt.MouseInfo.getPointerInfo()?.location
                    val origin = host.locationOnScreen
                    pointer != null && java.awt.Rectangle(origin.x, origin.y, host.width, host.height).contains(pointer)
                }
            }.getOrDefault(false)
            NativeVideoHover.set(host, over)
        }
    }

    LaunchedEffect(controller) {
        controller.updateControls(PlayerControlsState(hideControls = true, controlsVisible = false))
        controller.setMutedPreview(muted)
        // Zoom crops the black bars many trailers have baked in (the other desktop builds scale by 1.35 for this).
        controller.setPreviewResizeMode(if (fillFrame) PlayerResizeMode.Zoom else PlayerResizeMode.Fit)
        controller.attach(
            sourceUrl = YouTubeStreamProxy.wrap(sourceUrl, emptyMap()) ?: sourceUrl,
            sourceHeaders = emptyMap(),
            sourceAudioUrl = sourceAudioUrl?.let { YouTubeStreamProxy.wrap(it, emptyMap()) ?: it },
            playWhenReady = playWhenReady,
            initialPositionMs = startPositionMillis,
            decoderPriority = playerSettings.decoderPriority,
            nvidiaRtxSuperResolutionEnabled = false,
            onError = {
                if (!finished) {
                    finished = true
                    latestOnError.value()
                }
            },
        )
        // Ready once it is really playing; ended or failed when mpv says so (or it never starts).
        val startedAt = System.currentTimeMillis()
        while (isActive && !finished) {
            delay(200)
            val snapshot = controller.snapshot()
            if (!ready && !snapshot.isLoading && snapshot.positionMs > 0) {
                ready = true
                latestOnReady.value()
            }
            if (snapshot.isEnded) {
                finished = true
                latestOnEnded.value()
            } else if (!ready && System.currentTimeMillis() - startedAt > TrailerStartTimeoutMs) {
                finished = true
                latestOnError.value()
            }
        }
    }

    LaunchedEffect(controller, playWhenReady) {
        if (playWhenReady) controller.play() else controller.pause()
    }

    LaunchedEffect(controller, muted) {
        controller.setMutedPreview(muted)
    }

    val visible = ready && !finished && !NativeVideoCover.active
    Box(modifier.clipToBounds()) {
        CompositionLocalProvider(LocalDensity provides platformDensity) {
            SwingPanel(
                factory = { host },
                modifier = if (visible) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.align(Alignment.BottomEnd).requiredSize(1.dp)
                },
                background = Color.Black,
            )
        }
    }
}
