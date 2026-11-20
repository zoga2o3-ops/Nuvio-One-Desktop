package com.nuvio.app.features.youtube

import com.nuvio.app.core.storage.DesktopStorage
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Keeps a music video's sound in a local file while the video plays. The picture still streams from
 * YouTube; the sound comes from disk, so a seek in the picture can't cut it off. The download goes
 * through the stream proxy, so it is fast (YouTube throttles a single direct download).
 */
internal object MusicAudioCache {
    private const val MaxFiles = 40
    private val dir by lazy { DesktopStorage.rootDir.resolve("music-audio-cache").also { Files.createDirectories(it) } }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * [playback] with its audio read from a local file, downloading it first if it isn't there yet.
     * If the download fails, [playback] is returned unchanged and plays from the stream.
     */
    suspend fun localAudio(playback: YouTubePlayback): YouTubePlayback {
        val url = playback.audioUrl ?: return playback
        val file = dir.resolve(sha1(url) + ".audio")
        if (Files.exists(file) && Files.size(file) > 0L) return playback.copy(audioUrl = file.toString())
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                val source = YouTubeStreamProxy.wrap(url, playback.headers) ?: return@runCatching false
                val part = dir.resolve(file.fileName.toString() + ".part")
                val request = Request.Builder().url(source).apply {
                    playback.headers.forEach { (key, value) -> header(key, value) }
                }.build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching false
                    val body = response.body ?: return@runCatching false
                    body.byteStream().use { input -> Files.newOutputStream(part).use { input.copyTo(it) } }
                }
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING)
                true
            }.getOrDefault(false)
        }
        trim()
        return if (saved) playback.copy(audioUrl = file.toString()) else playback
    }

    private fun trim() {
        runCatching {
            Files.list(dir).use { files ->
                files.filter { it.fileName.toString().endsWith(".audio") }
                    .sorted(compareByDescending<java.nio.file.Path> { Files.getLastModifiedTime(it).toMillis() })
                    .skip(MaxFiles.toLong())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
