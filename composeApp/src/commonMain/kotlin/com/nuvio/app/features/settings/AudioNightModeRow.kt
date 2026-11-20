package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

/** Audio night mode is a desktop player feature, so its settings row is only shown on desktop. */
@Composable
internal expect fun DesktopAudioNightModeRow(isTablet: Boolean)
