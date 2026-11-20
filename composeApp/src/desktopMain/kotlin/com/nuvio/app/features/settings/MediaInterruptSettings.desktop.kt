package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.DesktopStorage

internal actual object MediaInterruptSettings {
    private const val StopMusicKey = "stop_music_on_other_media"
    private val store by lazy { DesktopStorage.store("nuvio_desktop_media") }

    actual val isSupported: Boolean = true

    // On by default: starting another video stops the Music audio.
    actual val stopMusicOnOtherMedia: Boolean
        get() = store.getBoolean(StopMusicKey) ?: true

    actual fun setStopMusicOnOtherMedia(enabled: Boolean) {
        store.putBoolean(StopMusicKey, enabled)
    }
}
