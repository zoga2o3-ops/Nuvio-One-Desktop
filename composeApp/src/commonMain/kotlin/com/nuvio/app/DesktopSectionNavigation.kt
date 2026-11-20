package com.nuvio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** Desktop-only sections that live outside the phone/tablet tab bar. */
internal enum class DesktopSection {
    LIVE_TV,
    SPORTS,
    YOUTUBE,
    MUSIC,
}

internal class DesktopSectionNavigation(
    val active: DesktopSection?,
    val open: (DesktopSection) -> Unit,
    val close: () -> Unit,
    val content: @Composable (DesktopSection) -> Unit,
)

internal val LocalDesktopSectionNavigation = compositionLocalOf<DesktopSectionNavigation?> { null }
