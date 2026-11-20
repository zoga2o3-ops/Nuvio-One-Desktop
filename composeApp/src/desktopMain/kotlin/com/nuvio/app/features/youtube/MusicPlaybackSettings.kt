package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal enum class MusicAudioQuality(
    val label: String,
    val detail: String,
    val maxKbps: Int,
) {
    LOW("Low", "~96 kbps · saves data", 96),
    MEDIUM("Medium", "~192 kbps", 192),
    HIGH("High", "Best available", 320),
}

@Serializable
private data class MusicPlaybackPayload(
    val audioQuality: String = MusicAudioQuality.HIGH.name,
    val downloadQuality: String = MusicAudioQuality.HIGH.name,
    val autoplay: Boolean = true,
    val skipSilence: Boolean = false,
    val playbackSpeed: Float = 1f,
    val fadeSeconds: Int = 0,
    val preferAddonSources: Boolean = false,
    val personalizedRecommendations: Boolean = true,
)

/**
 * Playback preferences for the Music section. These are separate from the video player's settings
 * and are applied to the desktop mpv player plus the stream/download resolver.
 */
internal object MusicPlaybackSettings {
    private val store = DesktopStorage.store("nuvio_music_playback")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var audioQuality by mutableStateOf(MusicAudioQuality.HIGH)
        private set
    var downloadQuality by mutableStateOf(MusicAudioQuality.HIGH)
        private set
    var autoplay by mutableStateOf(true)
        private set
    var skipSilence by mutableStateOf(false)
        private set
    var playbackSpeed by mutableStateOf(1f)
        private set
    var fadeSeconds by mutableStateOf(0)
        private set
    var preferAddonSources by mutableStateOf(false)
        private set
    var personalizedRecommendations by mutableStateOf(true)
        private set

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val text = store.getString("payload") ?: return@runCatching
            val saved = json.decodeFromString<MusicPlaybackPayload>(text)
            audioQuality = qualityOf(saved.audioQuality, MusicAudioQuality.HIGH)
            downloadQuality = qualityOf(saved.downloadQuality, MusicAudioQuality.HIGH)
            autoplay = saved.autoplay
            skipSilence = saved.skipSilence
            playbackSpeed = saved.playbackSpeed.coerceIn(0.5f, 2f)
            fadeSeconds = saved.fadeSeconds.coerceIn(0, 4)
            preferAddonSources = saved.preferAddonSources
            personalizedRecommendations = saved.personalizedRecommendations
        }
    }

    fun chooseAudioQuality(value: MusicAudioQuality) {
        ensureLoaded()
        audioQuality = value
        save()
    }

    fun chooseDownloadQuality(value: MusicAudioQuality) {
        ensureLoaded()
        downloadQuality = value
        save()
    }

    fun enableAutoplay(value: Boolean) {
        ensureLoaded()
        autoplay = value
        save()
    }

    fun enableSkipSilence(value: Boolean) {
        ensureLoaded()
        skipSilence = value
        save()
    }

    fun choosePlaybackSpeed(value: Float) {
        ensureLoaded()
        playbackSpeed = value.coerceIn(0.5f, 2f)
        save()
    }

    fun chooseFadeSeconds(value: Int) {
        ensureLoaded()
        fadeSeconds = value.coerceIn(0, 4)
        save()
    }

    fun enablePreferAddonSources(value: Boolean) {
        ensureLoaded()
        preferAddonSources = value
        save()
    }

    fun enablePersonalizedRecommendations(value: Boolean) {
        ensureLoaded()
        personalizedRecommendations = value
        save()
    }

    private fun save() {
        runCatching {
            store.putString(
                "payload",
                json.encodeToString(
                    MusicPlaybackPayload(
                        audioQuality = audioQuality.name,
                        downloadQuality = downloadQuality.name,
                        autoplay = autoplay,
                        skipSilence = skipSilence,
                        playbackSpeed = playbackSpeed,
                        fadeSeconds = fadeSeconds,
                        preferAddonSources = preferAddonSources,
                        personalizedRecommendations = personalizedRecommendations,
                    ),
                ),
            )
        }
    }

    private fun qualityOf(name: String, fallback: MusicAudioQuality): MusicAudioQuality =
        runCatching { MusicAudioQuality.valueOf(name) }.getOrDefault(fallback)
}
