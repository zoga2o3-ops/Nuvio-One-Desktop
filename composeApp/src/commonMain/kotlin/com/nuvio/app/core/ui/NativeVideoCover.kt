package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

/**
 * Whether app UI is drawn over an embedded video right now (the expanded side menu, a dialog, a
 * drop-down menu).
 *
 * On desktop the video is a native window inside the app window, and a native window always sits
 * on top of everything Compose draws, so a menu or dialog over it would be hidden behind the video.
 * While this is [active], embedded players shrink their video to a single pixel (sound keeps
 * playing) and show black in its place, the same way the Picture-in-picture placeholder works.
 */
object NativeVideoCover {
    private val covers = mutableStateListOf<Any>()

    val active: Boolean get() = covers.isNotEmpty()

    fun set(owner: Any, covering: Boolean) {
        if (covering) {
            if (owner !in covers) covers.add(owner)
        } else {
            covers.remove(owner)
        }
    }
}

/** Marks embedded videos as covered while [covering] is true (and while this stays in composition). */
@Composable
fun CoverNativeVideo(covering: Boolean) {
    val owner = remember { Any() }
    DisposableEffect(owner, covering) {
        NativeVideoCover.set(owner, covering)
        onDispose { NativeVideoCover.set(owner, false) }
    }
}
