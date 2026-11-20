package com.nuvio.app.features.iptv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Basic DVR: copies an HTTP MPEG-TS/MP4 stream to the configured recordings
 * folder (%APPDATA%\Nuvio\recordings by default) while it is playing.
 */
internal object IptvRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var connection: HttpURLConnection? = null

    var activeFile by mutableStateOf<Path?>(null)
        private set
    var activeName by mutableStateOf("")
        private set
    var startedAtMs by mutableStateOf(0L)
        private set
    var recording by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val isRecording: Boolean get() = recording

    val directory: Path
        get() = IptvRecordingStore.folder().also { Files.createDirectories(it) }

    fun recordings(): List<Path> = runCatching {
        Files.list(directory).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .sorted(Comparator.comparing<Path, Long> { Files.getLastModifiedTime(it).toMillis() }.reversed())
                .toList()
        }
    }.getOrDefault(emptyList())

    fun delete(path: Path) {
        runCatching { Files.deleteIfExists(path) }
    }

    fun start(name: String, url: String, headers: Map<String, String>) {
        if (isRecording) return
        error = null
        if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            error = "This stream is an HLS playlist. Recording works with direct MPEG-TS or MP4 streams."
            return
        }
        val safeName = name
            .replace(Regex("""[^A-Za-z0-9._ -]"""), "_")
            .trim()
            .take(60)
            .ifBlank { "recording" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val extension = if (url.substringBefore('?').endsWith(".mp4", ignoreCase = true)) "mp4" else "ts"
        val file = directory.resolve("$safeName-$stamp.$extension")
        activeFile = file
        activeName = name
        startedAtMs = System.currentTimeMillis()
        recording = true
        job = scope.launch {
            try {
                val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
                connection = conn
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                headers.forEach { (key, value) -> if (key.isNotBlank()) conn.setRequestProperty(key, value) }
                if (headers.keys.none { it.equals("User-Agent", true) }) {
                    conn.setRequestProperty("User-Agent", "NuvioOneDesktop/0.1")
                }
                val status = conn.responseCode
                if (status !in 200..299) error("HTTP $status")
                else {
                    BufferedInputStream(conn.inputStream).use { input ->
                        Files.newOutputStream(file).use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                connection = null
                recording = false
                if (error != null && runCatching { Files.size(file) }.getOrDefault(0L) == 0L) {
                    runCatching { Files.deleteIfExists(file) }
                    if (activeFile == file) activeFile = null
                }
            }
        }
    }

    fun stop() {
        connection?.disconnect()
        connection = null
        job?.cancel()
        job = null
        recording = false
    }
}
