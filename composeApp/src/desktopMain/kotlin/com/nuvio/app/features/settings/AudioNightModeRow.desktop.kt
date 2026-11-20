package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import com.nuvio.app.features.player.desktop.AudioNightMode

@Composable
internal actual fun DesktopAudioNightModeRow(isTablet: Boolean) {
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = "Audio night mode",
        description = "Lifts quiet dialogue and evens out loud peaks in every player.",
        checked = AudioNightMode.enabled,
        enabled = true,
        isTablet = isTablet,
        onCheckedChange = { AudioNightMode.set(it) },
    )
}
