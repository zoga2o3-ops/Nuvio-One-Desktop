package com.nuvio.app.features.settings

internal actual object MediaInterruptSettings {
    actual val isSupported: Boolean = false
    actual val stopMusicOnOtherMedia: Boolean = false

    actual fun setStopMusicOnOtherMedia(enabled: Boolean) = Unit
}
