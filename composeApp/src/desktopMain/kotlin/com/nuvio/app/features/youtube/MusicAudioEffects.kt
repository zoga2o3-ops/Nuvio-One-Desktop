package com.nuvio.app.features.youtube

import com.nuvio.app.features.player.PlayerEngineController

/**
 * Builds the Music player's FFmpeg audio filter chain from the sound settings. mpv applies the
 * whole chain through its `af`/`lavfi` property, so the equalizer, silence skipping and fade share
 * one update instead of overwriting each other.
 */
internal object MusicAudioEffects {
    fun apply(controller: PlayerEngineController?) {
        if (controller == null || !controller.supportsAudioFilters) return
        MusicPlaybackSettings.ensureLoaded()
        MusicEqualizer.ensureLoaded()
        val filters = buildList {
            if (MusicPlaybackSettings.skipSilence) {
                add(
                    "silenceremove=start_periods=1:start_duration=0.15:" +
                        "start_threshold=-50dB:stop_periods=-1:" +
                        "stop_duration=0.8:stop_threshold=-50dB",
                )
            }
            addAll(MusicEqualizer.bandFilters())
            val fade = MusicPlaybackSettings.fadeSeconds
            if (fade > 0) add("afade=t=in:st=0:d=$fade")
        }
        controller.setAudioFilters(filters)
    }
}
