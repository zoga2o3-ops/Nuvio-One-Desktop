package com.nuvio.app.core.ui

import androidx.compose.runtime.mutableStateListOf

/**
 * Whether the pointer is over an embedded native video (desktop trailer previews).
 *
 * A native video window takes the mouse from Compose, so a card holding one would think the
 * pointer had left it and close. The card counts this as still hovered.
 */
object NativeVideoHover {
    private val owners = mutableStateListOf<Any>()

    val hovered: Boolean get() = owners.isNotEmpty()

    fun set(owner: Any, over: Boolean) {
        if (over) {
            if (owner !in owners) owners.add(owner)
        } else {
            owners.remove(owner)
        }
    }
}
