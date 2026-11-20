package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot

/** Playback snapshot shared by the global player bar, Now Playing and the Music panels. */
internal object MusicPlaybackSession {
    var snapshot by mutableStateOf(PlayerPlaybackSnapshot())
    var controller by mutableStateOf<PlayerEngineController?>(null)

    val isPlaying: Boolean get() = snapshot.isPlaying

    fun toggle() {
        if (snapshot.isPlaying) controller?.pause() else controller?.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.trySeekTo(positionMs.coerceAtLeast(0L))
    }

    fun reset() {
        snapshot = PlayerPlaybackSnapshot()
    }
}
