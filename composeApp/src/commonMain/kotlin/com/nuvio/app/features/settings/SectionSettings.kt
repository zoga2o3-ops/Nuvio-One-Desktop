package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** A section of the app (OneTube, Live TV, …) whose own settings also show in Settings → Sections. */
class SectionSettingsEntry(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val content: @Composable (Modifier) -> Unit,
)

/**
 * The sections a platform adds to the main Settings menu. Empty on platforms without them (the
 * Sections category is then hidden); the desktop app fills it at startup.
 */
object SectionSettingsRegistry {
    var entries by mutableStateOf<List<SectionSettingsEntry>>(emptyList())
    internal var selectedId by mutableStateOf<String?>(null)

    internal val selected: SectionSettingsEntry?
        get() = entries.firstOrNull { it.id == selectedId }
}

internal fun SettingsCategory.isAvailable(): Boolean =
    this != SettingsCategory.Sections || SectionSettingsRegistry.entries.isNotEmpty()
