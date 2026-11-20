package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DeArrowEntry(
    val title: String?,
    val thumbnailUrl: String?,
)

/**
 * Shared DeArrow cache. Feed screens request the visible IDs; answers are
 * swapped in as they arrive, matching the TV app's behaviour.
 */
internal object DeArrowDesktop {
    private const val MAX_IDS_PER_REQUEST = 60
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = mutableStateMapOf<String, DeArrowEntry>()
    private val requested = mutableSetOf<String>()
    private val store = DesktopStorage.store("youtube_settings")
    private var enabledState by mutableStateOf(store.getBoolean("dearrow") ?: true)

    var enabled: Boolean
        get() = enabledState
        set(value) {
            enabledState = value
            store.putBoolean("dearrow", value)
        }

    private var titlesState by mutableStateOf(store.getBoolean("dearrow_titles") ?: true)
    /** Use community titles (when DeArrow is on). */
    var titles: Boolean
        get() = titlesState
        set(value) {
            titlesState = value
            store.putBoolean("dearrow_titles", value)
        }

    private var thumbnailsState by mutableStateOf(store.getBoolean("dearrow_thumbnails") ?: true)
    /** Use community thumbnails (when DeArrow is on). */
    var thumbnails: Boolean
        get() = thumbnailsState
        set(value) {
            thumbnailsState = value
            store.putBoolean("dearrow_thumbnails", value)
        }

    fun requestItems(items: List<YouTubeVideoItem>) {
        requestIds(items.mapNotNull { item -> item.videoId.takeIf { item.kind == YouTubeItemKind.VIDEO } })
    }

    fun requestIds(videoIds: List<String>) {
        if (!enabled) return
        val newIds = videoIds
            .asSequence()
            .filter { it.isNotBlank() }
            .filter { it !in requested }
            .distinct()
            .take(MAX_IDS_PER_REQUEST)
            .toList()
        if (newIds.isEmpty()) return
        requested += newIds
        scope.launch {
            val data = runCatching { YouTubeEngine.deArrowData(newIds) }.getOrDefault(emptyList())
            if (data.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                data.forEach { entry ->
                    val id = entry.videoId ?: return@forEach
                    val title = entry.title?.replace(">", "")?.takeIf { it.isNotBlank() }
                    val thumbnail = entry.thumbnailUrl?.takeIf { it.isNotBlank() }
                    if (title != null || thumbnail != null) {
                        cache[id] = DeArrowEntry(title, thumbnail)
                    }
                }
            }
        }
    }

    fun title(item: YouTubeVideoItem): String =
        if (enabled && titles) cache[item.videoId]?.title ?: item.title else item.title

    fun thumbnailUrl(item: YouTubeVideoItem): String? =
        if (enabled && thumbnails) cache[item.videoId]?.thumbnailUrl ?: item.thumbnailUrl else item.thumbnailUrl
}

