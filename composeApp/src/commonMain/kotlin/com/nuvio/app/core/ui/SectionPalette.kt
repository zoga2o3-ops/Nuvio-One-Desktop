package com.nuvio.app.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Colours the desktop sections (OneTube, Live TV, Music, Sports) and their settings draw with.
 * They follow the active app theme: [syncSectionPalette] copies the theme's colour scheme in
 * whenever the theme composes, so the section pages match the Settings pages.
 * Accent colours stay fixed; only surfaces, borders and text follow the theme.
 */
internal object SectionPalette {
    var background by mutableStateOf(Color(0xFF08080A))
    var surface by mutableStateOf(Color(0xFF15151B))
    var raised by mutableStateOf(Color(0xFF1C1C24))
    var border by mutableStateOf(Color(0xFF272731))
    var text by mutableStateOf(Color(0xFFF4F4F8))
    var muted by mutableStateOf(Color(0xFF9C9CAB))
}

internal fun syncSectionPalette(scheme: ColorScheme) {
    SectionPalette.background = scheme.background
    SectionPalette.surface = scheme.surface
    SectionPalette.raised = scheme.surfaceVariant
    SectionPalette.border = scheme.outlineVariant
    SectionPalette.text = scheme.onSurface
    SectionPalette.muted = scheme.onSurfaceVariant
}
