package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

/** Anime 4K upscaling is a desktop player feature, so its settings rows are only shown on desktop. */
@Composable
internal expect fun DesktopAnimeUpscalerRows(isTablet: Boolean)
