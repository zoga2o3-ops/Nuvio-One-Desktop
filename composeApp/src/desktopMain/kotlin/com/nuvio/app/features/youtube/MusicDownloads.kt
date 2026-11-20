package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class MusicDownloadEntry(
    val videoId: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val path: String,
    val savedAtMs: Long = System.currentTimeMillis(),
)

@Serializable
private data class MusicDownloadPayload(
    val entries: List<MusicDownloadEntry> = emptyList(),
)

internal object MusicDownloads {
    private const val Key = "payload"
    private const val Agent = "NuvioOneDesktop/0.1"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = DesktopStorage.store("nuvio_music_downloads")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val folder: Path get() = DesktopStorage.rootDir.resolve("music_downloads").also { Files.createDirectories(it) }

    var entries by mutableStateOf<List<MusicDownloadEntry>>(emptyList())
        private set
    var activeId by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val payload = store.getString(Key) ?: return@runCatching
            entries = json.decodeFromString<MusicDownloadPayload>(payload).entries
        }
    }

    fun isDownloaded(videoId: String): Boolean {
        ensureLoaded()
        return entries.any { it.videoId == videoId }
    }

    fun toVideoItem(entry: MusicDownloadEntry): YouTubeVideoItem = YouTubeVideoItem(
        videoId = entry.videoId,
        title = entry.title,
        author = entry.author,
        thumbnailUrl = entry.thumbnailUrl,
        durationMs = entry.durationMs,
        isLive = false,
        kind = YouTubeItemKind.VIDEO,
        isMusic = true,
        localUrl = Path.of(entry.path).toUri().toString(),
    )

    fun download(item: YouTubeVideoItem) {
        ensureLoaded()
        MusicPlaybackSettings.ensureLoaded()
        if (activeId != null || isDownloaded(item.videoId)) return
        activeId = item.videoId
        progress = 0f
        error = null
        scope.launch {
            try {
                val source = when {
                    item.localUrl != null -> Source(item.localUrl, item.playbackHeaders, item.durationMs)
                    else -> {
                        val playback = YouTubeEngine.resolvePlayback(
                            item = item,
                            music = true,
                            maxAudioKbps = MusicPlaybackSettings.downloadQuality.maxKbps,
                        )
                        Source(
                            url = playback.audioUrl ?: playback.videoUrl,
                            headers = playback.headers,
                            durationMs = item.durationMs,
                        )
                    }
                }
                val extension = when {
                    source.url.contains("mime=audio%2Fmp4", true) -> "m4a"
                    source.url.contains("mime=audio/mp4", true) -> "m4a"
                    source.url.substringBefore('?').endsWith(".mp3", true) -> "mp3"
                    source.url.substringBefore('?').endsWith(".flac", true) -> "flac"
                    source.url.substringBefore('?').endsWith(".wav", true) -> "wav"
                    else -> "m4a"
                }
                val safe = (item.title + " - " + (item.author ?: "Unknown"))
                    .replace(Regex("""[^A-Za-z0-9._ -]"""), "_")
                    .trim()
                    .take(80)
                    .ifBlank { "track" }
                val file = folder.resolve("$safe-${item.videoId.hashCode().toUInt().toString(16)}.$extension")
                val temp = file.resolveSibling(file.fileName.toString() + ".part")
                val uri = URI(source.url)
                if (uri.scheme.equals("file", ignoreCase = true)) {
                    Files.copy(Path.of(uri), temp, StandardCopyOption.REPLACE_EXISTING)
                } else {
                    val connection = uri.toURL().openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 20_000
                        connection.readTimeout = 60_000
                        connection.instanceFollowRedirects = true
                        connection.setRequestProperty("User-Agent", Agent)
                        source.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                        require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
                        val total = connection.contentLengthLong
                        BufferedInputStream(connection.inputStream).use { input ->
                            Files.newOutputStream(temp).use { output ->
                                val buffer = ByteArray(256 * 1024)
                                var readTotal = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    readTotal += read
                                    if (total > 0) progress = (readTotal.toFloat() / total).coerceIn(0f, 1f)
                                }
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                val entry = MusicDownloadEntry(
                    videoId = item.videoId,
                    title = item.title,
                    author = item.author,
                    thumbnailUrl = item.thumbnailUrl,
                    durationMs = source.durationMs,
                    path = file.toAbsolutePath().toString(),
                )
                entries = listOf(entry) + entries.filterNot { it.videoId == entry.videoId }
                persist()
                progress = 1f
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                activeId = null
            }
        }
    }

    fun delete(entry: MusicDownloadEntry) {
        runCatching { Files.deleteIfExists(Path.of(entry.path)) }
        entries = entries.filterNot { it.videoId == entry.videoId }
        persist()
    }

    private fun persist() {
        runCatching { store.putString(Key, json.encodeToString(MusicDownloadPayload(entries))) }
    }

    private data class Source(
        val url: String,
        val headers: Map<String, String>,
        val durationMs: Long,
    )
}
