package com.nuvio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tv
import com.nuvio.app.core.ui.SectionPalette
import com.nuvio.app.features.iptv.IptvSettingsPage
import com.nuvio.app.features.settings.SectionSettingsEntry
import com.nuvio.app.features.settings.SectionSettingsRegistry
import com.nuvio.app.features.sports.SportsSettingsPage
import com.nuvio.app.features.youtube.MusicSettingsPane
import com.nuvio.app.features.youtube.OneTubeSettingsPage

/**
 * Settings → Sections: each desktop section's own settings, also reachable from its gear menu.
 * The pages read and write the same stores, so a change in either place shows in the other.
 */
internal fun registerSectionSettings() {
    SectionSettingsRegistry.entries = listOf(
        SectionSettingsEntry(
            id = "onetube",
            title = "OneTube",
            description = "Playback, layout, tabs, feeds, SponsorBlock and DeArrow",
            icon = Icons.Filled.SmartDisplay,
        ) { modifier -> SectionPage(modifier, panel = true) { OneTubeSettingsPage(it) } },
        SectionSettingsEntry(
            id = "livetv",
            title = "Live TV",
            description = "Channel rows, guide, playback, parental controls and sync",
            icon = Icons.Filled.Tv,
        ) { modifier -> SectionPage(modifier, panel = true) { IptvSettingsPage(it) } },
        SectionSettingsEntry(
            id = "music",
            title = "Music",
            description = "Account, playback, recommendations, lyrics and downloads",
            icon = Icons.Filled.MusicNote,
        ) { modifier -> SectionPage(modifier) { Box(it) { MusicSettingsPane(onOpenParty = null) } } },
        SectionSettingsEntry(
            id = "sports",
            title = "Sports",
            description = "Scores, auto refresh, my teams and channels",
            icon = Icons.Filled.SportsSoccer,
        ) { modifier -> SectionPage(modifier, panel = true) { SportsSettingsPage(it) } },
    )
}

/**
 * The sections' settings are drawn for their own dark dialogs: a raised panel, with white as the
 * colour of text that sets none. The Settings page gives them the same panel and colour so every
 * option is readable.
 */
@Composable
private fun SectionPage(modifier: Modifier, panel: Boolean = false, content: @Composable (Modifier) -> Unit) {
    // Same look as the other settings pages: the app's theme colours. Pages whose options are
    // plain rows sit on a surface panel, like the Music settings cards.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier.padding(horizontal = 24.dp)) {
            if (panel) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SectionPalette.surface)
                        .padding(16.dp),
                ) {
                    content(Modifier.fillMaxSize())
                }
            } else {
                content(Modifier.fillMaxSize())
            }
        }
    }
}

private val SectionPanel = Color(0xFF1B1C24)
