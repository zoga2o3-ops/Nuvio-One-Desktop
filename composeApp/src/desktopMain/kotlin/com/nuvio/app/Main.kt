package com.nuvio.app

import com.nuvio.app.features.autosync.AutoSyncPreferencesDesktop
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.configureSwingGlobalsForCompose
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.deeplink.handleAppUrl
import com.nuvio.app.core.diagnostics.SentryInitializer
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.LocalNuvioPlatformDensity
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.core.ui.ProvideDesktopWindowInsets
import com.nuvio.app.features.discordrpc.DiscordPresenceManager
import com.nuvio.app.features.iptv.IptvDesktopScreen
import com.nuvio.app.features.iptv.IptvLoadingPill
import com.nuvio.app.features.sports.SportsDesktopScreen
import com.nuvio.app.features.youtube.MusicDesktopScreen
import com.nuvio.app.features.youtube.MusicPlaybackQueue
import com.nuvio.app.features.youtube.MusicPlayerHost
import com.nuvio.app.features.youtube.YouTubeDesktopScreen
import com.nuvio.app.features.p2p.P2pStreamingEngine
import com.nuvio.app.features.plugins.configureDesktopQuickJsLibrary
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.desktop.DesktopAppFullscreenController
import com.nuvio.app.features.player.desktop.DesktopHostOs
import com.nuvio.app.features.player.desktop.DesktopWindowGeometry
import com.nuvio.app.features.player.desktop.DesktopWindowModeStorage
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import com.nuvio.app.features.player.desktop.applyNativeDesktopWindowChrome
import com.nuvio.app.features.player.desktop.configureMacosWindowBeforePeer
import com.nuvio.app.features.player.desktop.installDesktopAppFullscreenShortcuts
import com.nuvio.app.features.player.desktop.macosTitleBarDoubleClick
import com.nuvio.app.features.player.desktop.MacosAppMenuBar
import com.nuvio.app.features.player.desktop.preloadNativePlayerBridgeAsync
import com.nuvio.app.features.player.desktop.registerDesktopAppFullscreenToggle
import com.nuvio.app.features.player.desktop.trackMaximizedBoundsForCurrentScreen
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.settings.AppIconRepository
import com.nuvio.app.features.settings.applyDesktopRendererPreference
import com.nuvio.app.features.settings.transparentPreviewResource
import java.awt.Desktop
import javax.imageio.ImageIO
import java.awt.Color as AwtColor
import javax.swing.JComponent

private val NuvioDesktopNativeBackground = AwtColor(0x0D, 0x0D, 0x0D)
private const val MacosDarkAquaAppearance = "NSAppearanceNameDarkAqua"

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    // On Linux, initialize GTK BEFORE AWT/Compose/Skia to prevent GdkDisplayManager
    // type registration conflict (Skiko partially loads GDK without full GTK init).
    if (System.getProperty("os.name", "").lowercase().contains("linux")) {
        runCatching { NativePlayerBridge.initGtkEarly() }
    }
    applyDesktopRendererPreference()
    SentryInitializer.start()
    configureDesktopQuickJsLibrary()
    configureDesktopChrome()
    configureLinuxSwingGlobalsBeforeAwt()
    installDesktopOpenUriHandler()
    handleDesktopLaunchArgs(args)
    preloadNativePlayerBridgeAsync()
    // Load cached profile data synchronously so the profile color is available
    // on the very first Compose frame (matching Android's SharedPreferences behavior).
    ProfileRepository.loadCachedProfiles()
    AppIconRepository.ensureLoaded()
    AutoSyncPreferencesDesktop.initialize()
    registerSectionSettings()
    DiscordPresenceManager.start()
    // Playlists kept twice after combining two setups: one copy each (the removal then syncs).
    runCatching { com.nuvio.app.features.iptv.sync.LiveTvSyncLocal.collapseDuplicates() }
    // Live TV sync (Google Drive and Nuvio account), shared with the TV apps.
    com.nuvio.app.features.iptv.sync.LiveTvDriveSync.start()
    com.nuvio.app.features.iptv.sync.LiveTvCloudSync.start()

    application {
        val appIconState by AppIconRepository.state.collectAsState()
        // Lets a packaged build open straight into a section for visual checks:
        // set NUVIO_DESKTOP_START_SECTION=music (or live, sports, youtube).
        val requestedSection = remember {
            when (
                (System.getProperty("nuvio.desktop.startSection")
                    ?: System.getenv("NUVIO_DESKTOP_START_SECTION"))
                    ?.trim()
                    ?.lowercase()
            ) {
                "music" -> DesktopSection.MUSIC
                "youtube" -> DesktopSection.YOUTUBE
                "live", "livetv", "live_tv" -> DesktopSection.LIVE_TV
                "sports" -> DesktopSection.SPORTS
                else -> null
            }
        }
        var activeSection by remember { mutableStateOf<DesktopSection?>(requestedSection) }
        var iptvSearchRequest by remember { mutableStateOf<String?>(null) }
        val desktopTextSize by ThemeSettingsRepository.desktopTextSize.collectAsState()

        // Do not parse the Live TV cache during app startup. The 29 MB gzipped
        // guide expands to roughly 200 MB and was competing with the first
        // frames. It is loaded on demand when Live TV is opened instead.
        LaunchedEffect(Unit) {
            ThemeSettingsRepository.ensureLoaded()
        }
        val smokePlayerUrl = (
            System.getProperty("nuvio.desktop.smokePlayerUrl")
                ?: System.getenv("NUVIO_DESKTOP_SMOKE_PLAYER_URL")
            )
            ?.takeIf { it.isNotBlank() }
        val wasFullscreenOnLastExit = remember { DesktopWindowModeStorage.loadWasFullscreen() }
        val wasMaximizedOnLastExit = remember { DesktopWindowModeStorage.loadWasMaximized() }
        val savedGeometry = remember { DesktopWindowModeStorage.loadWindowedGeometry() }
        val restoresMaximizedWindowPlacement = DesktopHostOs.current != DesktopHostOs.MACOS
        val initialPlacement = when {
            wasFullscreenOnLastExit && DesktopHostOs.current != DesktopHostOs.WINDOWS -> {
                WindowPlacement.Fullscreen
            }
            wasMaximizedOnLastExit == false && savedGeometry != null -> {
                WindowPlacement.Floating
            }
            restoresMaximizedWindowPlacement -> {
                WindowPlacement.Maximized
            }
            else -> WindowPlacement.Floating
        }
        val isStartingMaximizedOrFullscreen =
            initialPlacement == WindowPlacement.Maximized || initialPlacement == WindowPlacement.Fullscreen
        val maxScreenBounds = remember {
            runCatching {
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            }.getOrNull()
        }
        val initialWidth = when {
            isStartingMaximizedOrFullscreen && maxScreenBounds != null -> maxScreenBounds.width.dp
            savedGeometry != null -> savedGeometry.width.dp
            else -> 1280.dp
        }
        val initialHeight = when {
            isStartingMaximizedOrFullscreen && maxScreenBounds != null -> maxScreenBounds.height.dp
            savedGeometry != null -> savedGeometry.height.dp
            else -> 820.dp
        }
        val windowState = rememberWindowState(
            width = initialWidth,
            height = initialHeight,
            position = savedGeometry?.let { WindowPosition.Absolute(x = it.x.dp, y = it.y.dp) }
                ?: WindowPosition.PlatformDefault,
            // Windows fullscreen is emulated natively (see DesktopAppFullscreenController)
            // rather than driven by WindowPlacement, so it's restored separately below.
            placement = initialPlacement,
        )
        val fullscreenController = remember { DesktopAppFullscreenController() }
        val closeApplication = {
            P2pStreamingEngine.shutdown()
            DiscordPresenceManager.shutdown()
            SentryInitializer.close()
            exitApplication()
        }

        SwingWindow(
            onCloseRequest = closeApplication,
            title = if (smokePlayerUrl == null) "Nuvio One" else "Nuvio Player Smoke",
            state = windowState,
            icon = painterResource(appIconState.selected.transparentPreviewResource),
            init = ::configureMacosWindowBeforePeer,
        ) {
            MacosAppMenuBar(
                onCloseRequest = closeApplication,
                onMinimizeRequest = { windowState.isMinimized = true },
            )
            SideEffect {
                window.background = NuvioDesktopNativeBackground
                window.rootPane.background = NuvioDesktopNativeBackground
                window.contentPane.background = NuvioDesktopNativeBackground
                (window.contentPane as? JComponent)?.isOpaque = true
            }
            LaunchedEffect(window, appIconState.selected) {
                val backgroundSuffix = "-transparent"
                // The default ("Original") icon is the Nuvio One logo; the colour
                // variants from the icon picker keep their own artwork.
                val iconPath = if (appIconState.selected == com.nuvio.app.features.settings.AppIconOption.ORIGINAL) {
                    "icons/nuvio-one-icon.png"
                } else {
                    "icons/app-icon-${appIconState.selected.key}$backgroundSuffix.png"
                }
                Thread.currentThread().contextClassLoader.getResourceAsStream(iconPath)?.use { stream ->
                    ImageIO.read(stream)?.let { image ->
                        window.iconImages = listOf(image)
                    }
                }
            }

            LaunchedEffect(window) {
                applyNativeDesktopWindowChrome(window)
                installLinuxExtendedMouseButtons()
                // Windows fullscreen is emulated natively and isn't reflected by
                // WindowPlacement, so it must be re-applied once the window peer exists.
                fullscreenController.applyRestoredFullscreenState(window, windowState, wasFullscreenOnLastExit)
            }
            LaunchedEffect(windowState) {
                // Covers OS-driven placement changes too (e.g. the native macOS
                // green-button fullscreen toggle), not just our own shortcuts.
                if (DesktopHostOs.current != DesktopHostOs.WINDOWS) {
                    snapshotFlow { windowState.placement }
                        .collect { placement ->
                            DesktopWindowModeStorage.saveWasFullscreen(placement == WindowPlacement.Fullscreen)
                        }
                }
            }
            LaunchedEffect(windowState) {
                // Only persist geometry while windowed: fullscreen/native-Windows-fullscreen
                // coordinates aren't a meaningful "windowed position" to restore later.
                snapshotFlow {
                    windowState.isMinimized to Triple(windowState.placement, windowState.position, windowState.size)
                }
                    .collect { (minimized, geometry) ->
                        // A minimized window's geometry is the off-screen minimize rect; keep the
                        // last real windowed position instead.
                        if (minimized) return@collect
                        val (placement, position, size) = geometry
                        val isFullscreen = fullscreenController.isFullscreen(window, windowState)
                        if (!isFullscreen && restoresMaximizedWindowPlacement) {
                            DesktopWindowModeStorage.saveWasMaximized(placement == WindowPlacement.Maximized)
                        }
                        val isWindowed = placement == WindowPlacement.Floating && !isFullscreen
                        if (isWindowed && position.isSpecified) {
                            DesktopWindowModeStorage.saveWindowedGeometry(
                                DesktopWindowGeometry(
                                    x = position.x.value,
                                    y = position.y.value,
                                    width = size.width.value,
                                    height = size.height.value,
                                ),
                            )
                        }
                    }
            }
            DisposableEffect(window, windowState) {
                val unregisterFullscreenToggle = registerDesktopAppFullscreenToggle(
                    handler = { targetWindow ->
                        if (targetWindow == null || targetWindow === window) {
                            fullscreenController.toggle(window, windowState)
                            DesktopWindowModeStorage.saveWasFullscreen(
                                fullscreenController.isFullscreen(window, windowState),
                            )
                        }
                    },
                    isFullscreen = { targetWindow ->
                        (targetWindow == null || targetWindow === window) &&
                            fullscreenController.isFullscreen(window, windowState)
                    },
                )
                val uninstallFullscreenShortcuts = installDesktopAppFullscreenShortcuts(window)
                val untrackMaximizedBounds = window.trackMaximizedBoundsForCurrentScreen()
                onDispose {
                    fullscreenController.dispose(window)
                    uninstallFullscreenShortcuts()
                    untrackMaximizedBounds()
                    unregisterFullscreenToggle()
                }
            }

            if (smokePlayerUrl == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // DIAGNOSTIC (0.1.214): logs every press before children see it, and whether it was consumed.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press) {
                                        val p = event.changes.first().position
                                        com.nuvio.app.features.youtube.OneTubeDebugLog.line("click-initial at ${p.x.toInt()},${p.y.toInt()}")
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    if (event.type == PointerEventType.Press) {
                                        val p = event.changes.first().position
                                        val consumed = event.changes.any { it.isConsumed }
                                        com.nuvio.app.features.youtube.OneTubeDebugLog.line("click-final at ${p.x.toInt()},${p.y.toInt()} consumed=$consumed")
                                    }
                                }
                            }
                        }
                        .macosTitleBarDoubleClick(window = window, windowState = windowState),
                ) {
                    ProvideDesktopWindowInsets(
                        isFullscreen = windowState.placement == WindowPlacement.Fullscreen,
                    ) {
                        MiddleClickAutoscroll {
                            CompositionLocalProvider(
                                LocalDesktopSectionNavigation provides DesktopSectionNavigation(
                                    active = activeSection,
                                    open = { activeSection = it },
                                    close = { activeSection = null },
                                    content = { section ->
                                        when (section) {
                                            DesktopSection.LIVE_TV ->
                                                IptvDesktopScreen(
                                                    onBack = { activeSection = null },
                                                    initialSearch = iptvSearchRequest,
                                                )
                                            DesktopSection.SPORTS ->
                                                SportsDesktopScreen(
                                                    onBack = { activeSection = null },
                                                    onWatch = { query ->
                                                        iptvSearchRequest = query
                                                        activeSection = DesktopSection.LIVE_TV
                                                    },
                                                )
                                            DesktopSection.YOUTUBE ->
                                                YouTubeDesktopScreen(onBack = { activeSection = null })
                                            DesktopSection.MUSIC -> {
                                                // Render Music at the same scale as the full player /
                                                // queue (MusicPlayerHost below): platform density plus
                                                // the text-size setting, without the theme's
                                                // window-size UI scale, so it doesn't grow with the
                                                // window (Spotify-like sizing).
                                                val themed = LocalDensity.current
                                                val platform = LocalNuvioPlatformDensity.current
                                                val musicDensity = remember(themed, platform) {
                                                    Density(platform.density, themed.fontScale)
                                                }
                                                CompositionLocalProvider(LocalDensity provides musicDensity) {
                                                    MusicDesktopScreen(onBack = { activeSection = null })
                                                }
                                            }
                                        }
                                    },
                                ),
                            ) {
                                App()
                            }
                        }
                    }
                    val baseDensity = LocalDensity.current
                    val overlayDensity = remember(baseDensity, desktopTextSize) {
                        Density(baseDensity.density, desktopTextSize.scale)
                    }
                    CompositionLocalProvider(
                        LocalDensity provides overlayDensity,
                        LocalNuvioPlatformDensity provides baseDensity,
                    ) {
                        MusicPlayerHost(Modifier.fillMaxSize())
                        // A cast whose player was closed: still controllable here.
                        com.nuvio.app.features.cast.CastMiniBar(
                            Modifier.align(Alignment.BottomCenter).padding(
                                bottom = if (MusicPlaybackQueue.current != null) 88.dp else 18.dp,
                            ),
                        )
                        IptvLoadingPill(
                            Modifier.align(Alignment.BottomEnd).padding(
                                end = 18.dp,
                                bottom = if (MusicPlaybackQueue.current != null) 88.dp else 18.dp,
                            ),
                        )
                    }
                }
            } else {
                // The player surface reads LocalNuvioPlatformDensity, which only
                // NuvioTheme provides — the bare smoke harness must supply it too.
                NuvioTheme {
                    PlatformPlayerSurface(
                        sourceUrl = smokePlayerUrl,
                        modifier = Modifier.fillMaxSize(),
                        onControllerReady = {},
                        onSnapshot = {},
                        onError = {},
                    )
                }
            }
        }
    }
}

private fun configureDesktopChrome() {
    if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
        System.setProperty("apple.awt.application.appearance", MacosDarkAquaAppearance)
        // AWT only installs a window's JMenuBar in the macOS screen menu bar when this is set
        // before AWT starts; otherwise it would be drawn inside our full-window-content window.
        System.setProperty("apple.laf.useScreenMenuBar", "true")
    }
}

// application {} applies Compose's Swing globals, which on Linux include Skiko's
// display-scale detection (it sets sun.java2d.uiScale). AWT reads that property
// once, when its graphics environment starts, and installDesktopOpenUriHandler()
// starts it before application {} runs, which left the UI at 1x on HiDPI Linux
// desktops (#514). Apply the globals before anything touches AWT.
@OptIn(ExperimentalComposeUiApi::class)
private fun configureLinuxSwingGlobalsBeforeAwt() {
    if (DesktopHostOs.current != DesktopHostOs.LINUX) return
    if (System.getProperty("compose.application.configure.swing.globals") != "true") return
    // Skiko's detection overwrites sun.java2d.uiScale, so keep an explicitly
    // set scale (the documented #514 workaround) working by skipping it.
    configureSwingGlobalsForCompose(
        useAutoDpiOnLinux = System.getProperty("sun.java2d.uiScale") == null &&
            System.getProperty("skiko.linux.autodpi", "true") == "true",
    )
}

private fun installDesktopOpenUriHandler() {
    if (!Desktop.isDesktopSupported()) return
    val desktop = runCatching { Desktop.getDesktop() }.getOrNull() ?: return
    if (!desktop.isSupported(Desktop.Action.APP_OPEN_URI)) return

    runCatching {
        desktop.setOpenURIHandler { event ->
            event.uri
                ?.toString()
                ?.trim()
                ?.takeIf(::isDesktopAppUrl)
                ?.let(::handleAppUrl)
        }
    }
}

private fun handleDesktopLaunchArgs(args: Array<String>) {
    args.asSequence()
        .map(String::trim)
        .filter(::isDesktopAppUrl)
        .forEach(::handleAppUrl)
}

private fun isDesktopAppUrl(value: String): Boolean =
    value.startsWith("nuvio://", ignoreCase = true) ||
        value.startsWith("stremio://", ignoreCase = true)
