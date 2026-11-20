package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

internal enum class MusicEqualizerPreset(val label: String) {
    OFF("Off"),
    FLAT("Flat"),
    BASS("Bass"),
    VOCAL("Vocal"),
    ROCK("Rock"),
    CUSTOM("Custom"),
}

@Serializable
private data class MusicEqualizerPayload(
    val preset: String = MusicEqualizerPreset.OFF.name,
    val gains: List<Float> = emptyList(),
)

/**
 * The Music section's graphic equalizer.
 *
 * The desktop player is mpv, so the curve is applied through FFmpeg's `equalizer` audio filter
 * rather than an Android audio session. The chosen curve is remembered across tracks and replayed
 * whenever a new player instance is created.
 */
internal object MusicEqualizer {
    val bandsHz: List<Int> = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
    const val MinGainDb = -12f
    const val MaxGainDb = 12f

    private val store = DesktopStorage.store("nuvio_music_equalizer")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var preset by mutableStateOf(MusicEqualizerPreset.OFF)
        private set
    var gains by mutableStateOf(presetGains(MusicEqualizerPreset.OFF))
        private set

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val payload = store.getString("payload") ?: return@runCatching
            val decoded = json.decodeFromString<MusicEqualizerPayload>(payload)
            val savedPreset = runCatching { MusicEqualizerPreset.valueOf(decoded.preset) }
                .getOrDefault(MusicEqualizerPreset.OFF)
            preset = savedPreset
            gains = if (savedPreset == MusicEqualizerPreset.CUSTOM && decoded.gains.size == bandsHz.size) {
                decoded.gains.map { it.coerceIn(MinGainDb, MaxGainDb) }
            } else {
                presetGains(savedPreset)
            }
        }
    }

    val active: Boolean get() = preset != MusicEqualizerPreset.OFF

    fun selectPreset(next: MusicEqualizerPreset) {
        ensureLoaded()
        preset = next
        if (next != MusicEqualizerPreset.CUSTOM) gains = presetGains(next)
        save()
    }

    fun setBandGain(index: Int, gainDb: Float) {
        ensureLoaded()
        if (index !in bandsHz.indices) return
        val updated = gains.toMutableList()
        updated[index] = gainDb.coerceIn(MinGainDb, MaxGainDb)
        gains = updated
        preset = MusicEqualizerPreset.CUSTOM
        save()
    }

    fun reset() {
        ensureLoaded()
        preset = MusicEqualizerPreset.OFF
        gains = presetGains(MusicEqualizerPreset.OFF)
        save()
    }

    /** The equalizer bands as FFmpeg filter strings; empty when the curve is flat. */
    fun bandFilters(): List<String> {
        ensureLoaded()
        if (!active || gains.all { abs(it) < 0.05f }) return emptyList()
        return bandsHz.zip(gains).map { (frequency, gain) ->
            "equalizer=f=$frequency:width_type=o:width=1:g=" +
                String.format(java.util.Locale.US, "%.1f", gain)
        }
    }

    private fun save() {
        runCatching {
            store.putString(
                "payload",
                json.encodeToString(MusicEqualizerPayload(preset.name, gains)),
            )
        }
    }

    private fun presetGains(value: MusicEqualizerPreset): List<Float> = when (value) {
        MusicEqualizerPreset.OFF, MusicEqualizerPreset.FLAT -> List(bandsHz.size) { 0f }
        MusicEqualizerPreset.BASS -> listOf(9f, 9f, 9f, 3.8f, 0f, 0f, 0f, 0f, 0f, 0f)
        MusicEqualizerPreset.VOCAL -> listOf(0f, 0f, 0f, 6f, 6f, 6f, 6f, 6f, 0f, 0f)
        MusicEqualizerPreset.ROCK -> listOf(6.8f, 6.8f, 6.8f, -2.3f, -2.3f, -2.3f, -2.3f, 6.8f, 6.8f, 6.8f)
        MusicEqualizerPreset.CUSTOM -> gains
    }
}
