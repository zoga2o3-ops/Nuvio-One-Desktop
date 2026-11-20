package com.nuvio.app.features.player.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage

/**
 * Audio night mode: a compressor that lifts quiet dialogue and evens out loud peaks, so late-night viewing doesn't
 * need the volume riding up and down. It applies to every native player on desktop (movies, Live TV, OneTube, Music).
 */
internal object AudioNightMode {
    private val store = DesktopStorage.store("nuvio_audio_night")
    private const val Key = "enabled"

    var enabled by mutableStateOf(false)
        private set

    init {
        enabled = runCatching { store.getString(Key) == "true" }.getOrDefault(false)
    }

    fun set(value: Boolean) {
        enabled = value
        runCatching { store.putString(Key, value.toString()) }
    }

    /** The mpv filter for night mode, or null while it's off. */
    fun filter(): String? =
        if (enabled) "acompressor=threshold=-22dB:ratio=3:attack=10:release=200:makeup=4" else null
}
