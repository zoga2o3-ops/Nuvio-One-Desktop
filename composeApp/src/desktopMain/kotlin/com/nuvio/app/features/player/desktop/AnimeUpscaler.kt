package com.nuvio.app.features.player.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopCache
import com.nuvio.app.core.storage.DesktopStorage
import java.io.File

/** The Anime4K shader that the anime 4K upscaler runs, chosen in the playback settings. */
enum class AnimeUpscalerMode(
    val storageKey: String,
    val label: String,
    val description: String,
    val shaderFile: String,
) {
    FAST("fast", "Fast", "Low-end PCs & laptops", "anime4k_fast.glsl"),
    LINE_RECOVERY("line_recovery", "Soft & Clear", "Smooth lines & balanced detail", "anime4k_line_recovery.glsl"),
    SHARP_DETAIL("sharp_detail", "Sharp & Detailed", "Maximum clarity & fine lines", "anime4k_sharp_hq.glsl");

    companion object {
        fun fromStorageKey(key: String?): AnimeUpscalerMode =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: FAST
    }
}

/** Which content the upscaler runs on. */
enum class AnimeUpscalerScope(
    val storageKey: String,
    val label: String,
) {
    OFF("off", "Off"),
    ANIME("anime", "Anime only"),
    ALL("all", "All media");

    companion object {
        fun fromStorageKey(key: String?): AnimeUpscalerScope =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: OFF
    }
}

/** Anime 4K upscaling settings, kept on this desktop install. */
internal object AnimeUpscalerSettings {
    private val store = DesktopStorage.store("nuvio_anime_upscaler")
    private const val ScopeKey = "scope"
    private const val ModeKey = "mode"

    var scope by mutableStateOf(AnimeUpscalerScope.OFF)
        private set

    var mode by mutableStateOf(AnimeUpscalerMode.FAST)
        private set

    init {
        scope = AnimeUpscalerScope.fromStorageKey(runCatching { store.getString(ScopeKey) }.getOrNull())
        mode = AnimeUpscalerMode.fromStorageKey(runCatching { store.getString(ModeKey) }.getOrNull())
    }

    fun chooseScope(value: AnimeUpscalerScope) {
        scope = value
        runCatching { store.putString(ScopeKey, value.storageKey) }
    }

    fun chooseMode(value: AnimeUpscalerMode) {
        mode = value
        runCatching { store.putString(ModeKey, value.storageKey) }
    }

    /** Whether the upscaler should run for this content. */
    fun activeFor(isAnime: Boolean): Boolean = when (scope) {
        AnimeUpscalerScope.OFF -> false
        AnimeUpscalerScope.ANIME -> isAnime
        AnimeUpscalerScope.ALL -> true
    }
}

/** Copies the bundled Anime4K shaders to a versioned cache folder so mpv can load them from disk. */
internal object AnimeShaderProvider {
    private val installedDir: File? by lazy {
        val files = buildMap {
            AnimeUpscalerMode.entries.map { it.shaderFile }.distinct().forEach { fileName ->
                val stream = AnimeShaderProvider::class.java.getResourceAsStream("/shaders/$fileName")
                if (stream != null) {
                    put(fileName, stream.use { it.readBytes() })
                }
            }
        }
        if (files.isNotEmpty()) {
            DesktopCache.installVersionedFiles("anime-shaders", files).toFile()
        } else {
            null
        }
    }

    fun getShaderPath(mode: AnimeUpscalerMode): String? {
        val dir = installedDir ?: return null
        val file = dir.resolve(mode.shaderFile)
        return if (file.exists()) file.absolutePath.replace('\\', '/') else null
    }
}
