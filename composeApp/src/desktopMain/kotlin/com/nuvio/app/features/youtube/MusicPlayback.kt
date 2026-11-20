package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class MusicRepeatMode { OFF, ALL, ONE }

/** Shared YouTube Music queue, kept across sections by the root player host. */
internal object MusicPlaybackQueue {
    var items by mutableStateOf<List<YouTubeVideoItem>>(emptyList())
        private set
    var index by mutableStateOf(-1)
        private set
    var expanded by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(MusicRepeatMode.OFF)
        private set

    private var unshuffledItems: List<YouTubeVideoItem>? = null
    private var unshuffledIndex = -1

    val current: YouTubeVideoItem? get() = items.getOrNull(index)

    fun play(queue: List<YouTubeVideoItem>, startIndex: Int) {
        val videos = queue.filter { MusicTrackFilter.isMusic(it) }
        if (videos.isEmpty()) return
        val start = startIndex.coerceIn(0, videos.lastIndex)
        if (shuffle) {
            val chosen = videos[start]
            items = listOf(chosen) + videos.filterIndexed { i, _ -> i != start }.shuffled()
            index = 0
            unshuffledItems = videos
            unshuffledIndex = start
        } else {
            items = videos
            index = start
            unshuffledItems = null
            unshuffledIndex = -1
        }
        expanded = false
        recordCurrent()
    }

    fun jumpTo(target: Int) {
        if (items.isEmpty()) return
        index = target.coerceIn(0, items.lastIndex)
        recordCurrent()
    }

    fun next() {
        if (items.isEmpty()) return
        if (index < items.lastIndex) {
            index += 1
        } else if (repeatMode == MusicRepeatMode.ALL) {
            index = 0
        } else {
            return
        }
        recordCurrent()
    }

    fun previous() {
        if (items.isEmpty()) return
        if (index > 0) {
            index -= 1
        } else if (repeatMode == MusicRepeatMode.ALL) {
            index = items.lastIndex
        } else {
            return
        }
        recordCurrent()
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            MusicRepeatMode.OFF -> MusicRepeatMode.ALL
            MusicRepeatMode.ALL -> MusicRepeatMode.ONE
            MusicRepeatMode.ONE -> MusicRepeatMode.OFF
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        if (shuffle == enabled) return
        shuffle = enabled
        if (items.isEmpty()) return
        if (enabled) {
            val playing = current ?: return
            unshuffledItems = items
            unshuffledIndex = index
            items = listOf(playing) + items.filterIndexed { i, _ -> i != index }.shuffled()
            index = 0
        } else {
            val playing = current
            val original = unshuffledItems
            unshuffledItems = null
            unshuffledIndex = -1
            if (original != null) {
                items = original
                index = original.indexOfFirst { it.videoId == playing?.videoId }
                    .takeIf { it >= 0 } ?: index.coerceIn(0, original.lastIndex)
            }
        }
    }

    fun removeAt(target: Int) {
        if (target !in items.indices) return
        val removed = items[target]
        items = items.filterIndexed { i, _ -> i != target }
        unshuffledItems = unshuffledItems?.filterNot { it.videoId == removed.videoId }
        if (items.isEmpty()) {
            clear()
            return
        }
        index = when {
            target < index -> index - 1
            target == index -> index.coerceAtMost(items.lastIndex)
            else -> index
        }
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val mutable = items.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        items = mutable
        index = when {
            index == from -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
    }

    fun playNext(item: YouTubeVideoItem) {
        if (item.kind != YouTubeItemKind.VIDEO) return
        if (items.isEmpty()) {
            play(listOf(item), 0)
            return
        }
        val existing = items.indexOfFirst { it.videoId == item.videoId }
        if (existing >= 0) {
            jumpTo(existing)
            return
        }
        val insertAt = (index + 1).coerceIn(0, items.size)
        items = items.toMutableList().apply { add(insertAt, item) }
        if (insertAt <= index) index += 1
        unshuffledItems = unshuffledItems?.toMutableList()?.apply { add(insertAt.coerceIn(0, size), item) }
    }

    /**
     * Radio: keeps the playing track ([videoId]) and replaces only what is up next with [tracks].
     * Does nothing if [videoId] is no longer the current track (the user moved on meanwhile).
     */
    fun replaceUpNext(videoId: String, tracks: List<YouTubeVideoItem>) {
        if (current?.videoId != videoId) return
        val upNext = tracks.filter { MusicTrackFilter.isMusic(it) && it.videoId != videoId }
            .distinctBy { it.videoId }
        items = items.take(index + 1) + upNext
        unshuffledItems = unshuffledItems?.let { list ->
            val at = list.indexOfFirst { it.videoId == videoId }
            if (at >= 0) list.take(at + 1) + upNext else null
        }
    }

    fun append(tracks: List<YouTubeVideoItem>) {
        val videos = tracks.filter { MusicTrackFilter.isMusic(it) }
        if (videos.isEmpty()) return
        if (items.isEmpty()) {
            play(videos, 0)
            return
        }
        val known = items.mapTo(HashSet()) { it.videoId }
        val additions = videos.filter { it.videoId !in known }
        if (additions.isEmpty()) return
        items = items + additions
        unshuffledItems = unshuffledItems?.plus(additions)
    }

    fun clear() {
        items = emptyList()
        index = -1
        expanded = false
        unshuffledItems = null
        unshuffledIndex = -1
    }

    private fun recordCurrent() {
        current?.let(MusicLibraryStore::recordPlayed)
    }
}
