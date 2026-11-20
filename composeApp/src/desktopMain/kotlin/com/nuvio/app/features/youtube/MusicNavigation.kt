package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class MusicArtistRequest(
    /** Null when only the name is known; the Music screen finds the channel. */
    val channelId: String?,
    val name: String,
)

/** Cross-screen Music navigation requests from the Now Playing overlay. */
internal object MusicNavigation {
    var artistRequest by mutableStateOf<MusicArtistRequest?>(null)
}
