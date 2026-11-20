package com.nuvio.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.player.desktop.AnimeUpscalerMode
import com.nuvio.app.features.player.desktop.AnimeUpscalerScope
import com.nuvio.app.features.player.desktop.AnimeUpscalerSettings

@Composable
internal actual fun DesktopAnimeUpscalerRows(isTablet: Boolean) {
    val scope = AnimeUpscalerSettings.scope
    val mode = AnimeUpscalerSettings.mode

    SettingsSwitchRow(
        title = "Anime 4K upscaling",
        description = "Sharpens anime with Anime4K shaders while the video plays.",
        checked = scope != AnimeUpscalerScope.OFF,
        isTablet = isTablet,
        onCheckedChange = { on ->
            AnimeUpscalerSettings.chooseScope(if (on) AnimeUpscalerScope.ANIME else AnimeUpscalerScope.OFF)
        },
    )
    if (scope != AnimeUpscalerScope.OFF) {
        SettingsGroupDivider(isTablet = isTablet)
        CycleSettingRow(
            title = "Applies to",
            value = scope.label,
            description = "Anime only, or every video",
            isTablet = isTablet,
            onClick = {
                val next = if (scope == AnimeUpscalerScope.ANIME) AnimeUpscalerScope.ALL else AnimeUpscalerScope.ANIME
                AnimeUpscalerSettings.chooseScope(next)
            },
        )
        SettingsGroupDivider(isTablet = isTablet)
        CycleSettingRow(
            title = "Upscaler mode",
            value = mode.label,
            description = mode.description,
            isTablet = isTablet,
            onClick = {
                val modes = AnimeUpscalerMode.entries
                AnimeUpscalerSettings.chooseMode(modes[(modes.indexOf(mode) + 1) % modes.size])
            },
        )
    }
}

@Composable
private fun CycleSettingRow(
    title: String,
    value: String,
    description: String,
    isTablet: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (isTablet) 20.dp else 16.dp,
                vertical = if (isTablet) 16.dp else 14.dp,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.75f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
