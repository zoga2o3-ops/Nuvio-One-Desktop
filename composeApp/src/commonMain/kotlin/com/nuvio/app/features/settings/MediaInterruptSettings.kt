package com.nuvio.app.features.settings

/** Stops Music playback when another kind of media (a movie, series or video) starts. */
internal expect object MediaInterruptSettings {
    /** False where Music and the desktop players don't share a window. */
    val isSupported: Boolean
    val stopMusicOnOtherMedia: Boolean

    fun setStopMusicOnOtherMedia(enabled: Boolean)
}
