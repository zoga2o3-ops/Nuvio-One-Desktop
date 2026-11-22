package com.nuvio.app.features.iptv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.features.autosync.AutoSyncMpvEngineController
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.desktop.NativePlayerController
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Basic DVR: copies an HTTP MPEG-TS/HLS/MP4 stream to the configured recordings folder
 * (%APPDATA%\Nuvio\recordings by default) until the user stops it.
 *
 * If the channel is already playing in the built-in player, recording starts from that player's
 * own connection. That avoids a second connection, which providers with a one-connection account
 * close after a few seconds. Otherwise the stream is downloaded directly, and a connection the
 * provider drops is reopened and appended to the same file.
 */
internal object IptvRecorder {
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val MAX_REDIRECTS = 8
    private const val MAX_BACKOFF_MS = 15_000L
    private const val LIVE_HLS_LEAD_SECONDS = 30.0
    private const val USER_AGENT = "NuvioOneDesktop/0.1"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val hlsKeys = HashMap<String, ByteArray>()

    private var job: Job? = null
    /** The stream being recorded, kept when the player hands the recording over to the downloader. */
    private var sourceUrl = ""
    private var sourceHeaders: Map<String, String> = emptyMap()
    /** Set while [activeFile] is being written by mpv. */
    private var playerRecorder: NativePlayerController? = null

    @Volatile private var generation = 0
    @Volatile private var stopped = true
    @Volatile private var connection: HttpURLConnection? = null

    var activeFile by mutableStateOf<Path?>(null)
        private set
    var activeName by mutableStateOf("")
        private set
    var startedAtMs by mutableStateOf(0L)
        private set
    var recording by mutableStateOf(false)
        private set
    /** A fatal recording problem; the recording has ended. */
    var error by mutableStateOf<String?>(null)
        private set
    /** A temporary message while the recorder is reconnecting or switching sources. */
    var notice by mutableStateOf<String?>(null)
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

    /**
     * Starts recording from the player that is already showing [url]. Uses mpv's own stream
     * recording, so no second provider connection is opened. Returns false when the player can't
     * record (HLS, no current player handle), and the caller should use [start] instead.
     */
    fun startFromPlayer(
        name: String,
        url: String,
        headers: Map<String, String>,
        controller: PlayerEngineController?,
    ): Boolean {
        if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) return false
        val native = unwrap(controller) ?: return false
        synchronized(lock) {
            if (recording) return false
            val file = newFile(name, url)
            if (!native.setStreamRecord(file.toAbsolutePath().toString())) return false
            runCatching { Files.newOutputStream(file, StandardOpenOption.CREATE).close() }
            error = null
            notice = null
            activeFile = file
            activeName = name
            startedAtMs = System.currentTimeMillis()
            sourceUrl = url
            sourceHeaders = headers
            stopped = false
            playerRecorder = native
            recording = true
            generation++
            val token = generation
            job = scope.launch {
                // mpv can accept the option but a provider/format may not support recording it.
                // After a few seconds of an empty file, carry on through the network instead.
                delay(6_000)
                if (token == generation && recording && playerRecorder === native) {
                    val size = runCatching { Files.size(file) }.getOrDefault(0L)
                    if (size < 16 * 1024L) {
                        synchronized(lock) {
                            if (token == generation && recording && playerRecorder === native) {
                                detachPlayerToHttp("The player didn't record; recording from the stream instead.")
                            }
                        }
                    }
                }
            }
            return true
        }
    }

    fun start(name: String, url: String, headers: Map<String, String>) {
        synchronized(lock) {
            if (recording) return
            val file = newFile(name, url)
            error = null
            notice = null
            activeFile = file
            activeName = name
            startedAtMs = System.currentTimeMillis()
            sourceUrl = url
            sourceHeaders = headers
            stopped = false
            playerRecorder = null
            recording = true
            val token = ++generation
            job = scope.launch { runHttp(file, url, headers, token, append = false) }
        }
    }

    /**
     * The player showing [playingUrl] is gone (channel changed, playback failed, or the Live TV
     * screen was closed). Keep the recording going from the network and append to the same file.
     */
    fun onPlaybackChanged(playingUrl: String?) {
        if (playingUrl != null && playingUrl == sourceUrl) return
        synchronized(lock) {
            if (!recording || playerRecorder == null) return
            detachPlayerToHttp("Playback ended; recording continues in the background.")
        }
    }

    /**
     * The player surface was rebuilt (for example when entering full screen or retrying). The old
     * mpv handle stopped its stream recording, so continue through the network on the same file.
     */
    fun onPlayerControllerChanged(controller: PlayerEngineController?) {
        val native = unwrap(controller) ?: return
        synchronized(lock) {
            if (!recording || playerRecorder == null || playerRecorder === native) return
            detachPlayerToHttp("Playback moved; recording continues in the background.")
        }
    }

    /** Called when the Live TV screen leaves composition. */
    fun onScreenClosed() {
        onPlaybackChanged(null)
    }

    private fun detachPlayerToHttp(reason: String?) {
        val native = playerRecorder ?: return
        native.setStreamRecord(null)
        playerRecorder = null
        val file = activeFile ?: return
        val url = sourceUrl
        val headers = sourceHeaders
        val token = ++generation
        notice = reason
        job?.cancel()
        job = scope.launch {
            // Give mpv a moment to close its file, then continue the same recording.
            delay(2_000)
            runHttp(file, url, headers, token, append = true)
        }
    }

    fun stop() {
        synchronized(lock) {
            stopped = true
            generation++
            connection?.let { runCatching { it.disconnect() } }
            connection = null
            playerRecorder?.let { it.setStreamRecord(null) }
            playerRecorder = null
            job?.cancel()
            job = null
            recording = false
            notice = null
        }
    }

    // ------------------------------------------------------------------ network recording

    private suspend fun runHttp(
        file: Path,
        startUrl: String,
        headers: Map<String, String>,
        token: Int,
        append: Boolean,
    ) {
        var reconnects = 0
        try {
            openOutput(file, append).use { output ->
                if (startUrl.looksLikeHls()) {
                    recordHls(startUrl, headers, output, token)
                    return
                }
                var lastFlush = 0L
                while (active(token)) {
                    var opened: HttpURLConnection? = null
                    try {
                        val conn = openConnection(startUrl, headers)
                        opened = conn
                        val finalUrl = conn.url?.toString().orEmpty()
                        val contentType = conn.contentType.orEmpty().lowercase(Locale.ROOT)
                        if (finalUrl.looksLikeHls() ||
                            contentType.contains("mpegurl") ||
                            contentType.contains("x-mpegurl")
                        ) {
                            runCatching { conn.inputStream.close() }
                            recordHls(finalUrl.ifBlank { startUrl }, headers, output, token)
                            return
                        }
                        if (contentType.contains("text/html") || contentType.contains("application/json")) {
                            error = "The provider returned a web page instead of the channel. " +
                                "Check that the channel plays and that your account allows another connection."
                            return
                        }

                        val input = BufferedInputStream(conn.inputStream)
                        try {
                            val buffer = ByteArray(256 * 1024)
                            var read = input.read(buffer)
                            if (read > 0 && looksLikePlaylist(buffer, read)) {
                                val text = buildString {
                                    append(String(buffer, 0, read, StandardCharsets.UTF_8))
                                    append(input.readBytes().toString(StandardCharsets.UTF_8))
                                }
                                recordHls(finalUrl.ifBlank { startUrl }, headers, output, token, text)
                                return
                            }
                            if (read > 0 && looksLikeWebPage(buffer, read)) {
                                error = "The provider returned a web page instead of the channel. " +
                                    "Check that the channel plays and that your account allows another connection."
                                return
                            }

                            var received = 0L
                            while (read >= 0 && active(token)) {
                                if (read > 0) {
                                    output.write(buffer, 0, read)
                                    received += read
                                    if (notice != null) notice = null
                                    val now = System.currentTimeMillis()
                                    if (now - lastFlush >= 1_000L) {
                                        output.flush()
                                        lastFlush = now
                                    }
                                }
                                read = input.read(buffer)
                            }
                            output.flush()
                            if (!active(token)) return
                            if (received == 0L) {
                                reconnects++
                                notice = "The provider sent nothing; reconnecting…"
                                delay(backoff(reconnects))
                            } else {
                                // Live streams end only when the provider closes the connection.
                                // Reopen and append, so a short drop doesn't end the recording.
                                reconnects = 0
                                notice = "The provider closed the stream; reconnecting…"
                                delay(800)
                            }
                        } finally {
                            runCatching { input.close() }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (!active(token)) return
                        reconnects++
                        notice = "Connection lost (${friendly(e)}); reconnecting…"
                        delay(backoff(reconnects))
                    } finally {
                        opened?.let { closeConnection(it) }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (active(token)) {
                notice = null
                error = friendly(e)
            }
        } finally {
            finish(token, file)
        }
    }

    private suspend fun recordHls(
        startUrl: String,
        headers: Map<String, String>,
        output: OutputStream,
        token: Int,
        firstText: String? = null,
    ) {
        var playlistUrl = startUrl
        var text = firstText

        while (active(token) && text == null) {
            notice = "Waiting for the channel's HLS playlist…"
            text = fetchTextSafely(playlistUrl, headers)
            if (text == null) delay(3_000)
        }
        if (text == null) return

        if (HlsRecording.isMaster(text)) {
            val variant = HlsRecording.pickVariant(text, playlistUrl) ?: run {
                error = "This channel's HLS playlist has no playable stream."
                return
            }
            playlistUrl = variant
            var variantText: String? = null
            while (active(token) && variantText == null) {
                notice = "Waiting for the channel's HLS playlist…"
                variantText = fetchTextSafely(playlistUrl, headers)
                if (variantText == null) delay(3_000)
            }
            text = variantText ?: return
        }

        var firstLoad = true
        val seen = HashSet<String>()
        while (active(token)) {
            val playlist = runCatching { HlsRecording.parse(text.orEmpty(), playlistUrl) }.getOrElse { failure ->
                notice = "Couldn't read the HLS playlist (${friendly(failure)}); retrying…"
                delay(3_000)
                text = fetchTextSafely(playlistUrl, headers)
                continue
            }
            playlist.unsupported?.let {
                error = it
                return
            }
            val segments = if (firstLoad && !playlist.ended) {
                HlsRecording.newestWindow(playlist.segments, LIVE_HLS_LEAD_SECONDS)
            } else {
                playlist.segments
            }
            firstLoad = false

            for (segment in segments) {
                if (!active(token)) return
                if (segment.url in seen) continue
                val data = fetchSegment(segment.url, headers) ?: continue
                val plain = if (segment.key != null) {
                    val keyBytes = hlsKey(segment.key.url, headers) ?: continue
                    runCatching { HlsRecording.decrypt(data, segment.key, keyBytes, segment.sequence) }.getOrElse {
                        error = "Couldn't decrypt this channel's HLS recording: ${friendly(it)}"
                        return
                    }
                } else {
                    data
                }
                output.write(plain)
                output.flush()
                seen += segment.url
                if (notice != null) notice = null
            }

            if (playlist.ended) return

            delay((playlist.targetDurationMs / 2).coerceIn(1_000L, 6_000L))
            var refreshed: String? = null
            while (active(token) && refreshed == null) {
                refreshed = fetchTextSafely(playlistUrl, headers)
                if (refreshed == null) {
                    notice = "Recording is waiting for the provider's HLS playlist; retrying…"
                    delay(3_000)
                }
            }
            text = refreshed ?: return
        }
    }

    private fun fetchSegment(url: String, headers: Map<String, String>): ByteArray? {
        for (attempt in 0 until 3) {
            try {
                return fetchBytes(url, headers)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt < 2) Thread.sleep(1_000L * (attempt + 1))
            }
        }
        return null
    }

    private fun hlsKey(url: String, headers: Map<String, String>): ByteArray? {
        synchronized(hlsKeys) { hlsKeys[url] }?.let { return it }
        return runCatching { fetchBytes(url, headers) }
            .getOrNull()
            ?.also { synchronized(hlsKeys) { hlsKeys[url] = it } }
    }

    private fun fetchTextSafely(url: String, headers: Map<String, String>): String? =
        runCatching { fetchText(url, headers) }.getOrNull()

    private fun fetchText(url: String, headers: Map<String, String>): String {
        val conn = openConnection(url, headers)
        return try {
            conn.inputStream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
        } finally {
            closeConnection(conn)
        }
    }

    private fun fetchBytes(url: String, headers: Map<String, String>): ByteArray {
        val conn = openConnection(url, headers)
        return try {
            conn.inputStream.use { it.readBytes() }
        } finally {
            closeConnection(conn)
        }
    }

    private fun openConnection(rawUrl: String, headers: Map<String, String>): HttpURLConnection {
        var current = URI.create(rawUrl)
        repeat(MAX_REDIRECTS + 1) {
            val conn = current.toURL().openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            headers.forEach { (key, value) -> if (key.isNotBlank()) conn.setRequestProperty(key, value) }
            if (headers.keys.none { it.equals("User-Agent", true) }) {
                conn.setRequestProperty("User-Agent", USER_AGENT)
            }
            val status = conn.responseCode
            if (status in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("HTTP $status without a Location header")
                current = runCatching { current.resolve(location.trim()) }
                    .getOrElse { throw IOException("HTTP $status with an invalid Location header") }
                return@repeat
            }
            if (status !in 200..299) {
                conn.disconnect()
                throw HttpStatusException(status)
            }
            connection = conn
            return conn
        }
        throw IOException("Too many redirects")
    }

    private fun closeConnection(conn: HttpURLConnection) {
        runCatching { conn.disconnect() }
        if (connection === conn) connection = null
    }

    private fun openOutput(file: Path, append: Boolean): OutputStream {
        val options = if (append) {
            arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)
        } else {
            arrayOf(
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }
        var last: Exception? = null
        repeat(6) {
            try {
                return BufferedOutputStream(Files.newOutputStream(file, *options), 256 * 1024)
            } catch (e: Exception) {
                last = e
                // On Windows mpv can still hold the file for a moment after stream-record is cleared.
                Thread.sleep(500)
            }
        }
        throw last ?: IOException("Couldn't open the recording file")
    }

    private fun finish(token: Int, file: Path) {
        synchronized(lock) {
            if (token != generation) return
            recording = false
            playerRecorder = null
            notice = null
            if (error != null && runCatching { Files.size(file) }.getOrDefault(0L) == 0L) {
                runCatching { Files.deleteIfExists(file) }
                if (activeFile == file) activeFile = null
            }
        }
    }

    private fun active(token: Int): Boolean = !stopped && token == generation

    private fun newFile(name: String, url: String): Path {
        val safeName = name
            .replace(Regex("""[^A-Za-z0-9._ -]"""), "_")
            .trim()
            .take(60)
            .ifBlank { "recording" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val extension = if (url.substringBefore('?').endsWith(".mp4", ignoreCase = true)) "mp4" else "ts"
        var file = directory.resolve("$safeName-$stamp.$extension")
        var number = 2
        while (Files.exists(file)) {
            file = directory.resolve("$safeName-$stamp-$number.$extension")
            number++
        }
        return file
    }

    private fun String.looksLikeHls(): Boolean =
        substringBefore('?').endsWith(".m3u8", ignoreCase = true) || contains(".m3u8", ignoreCase = true)

    private fun looksLikePlaylist(buffer: ByteArray, count: Int): Boolean {
        val text = String(buffer, 0, count.coerceAtMost(4 * 1024), StandardCharsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return text.startsWith("#EXTM3U")
    }

    private fun looksLikeWebPage(buffer: ByteArray, count: Int): Boolean {
        val text = String(buffer, 0, count.coerceAtMost(4 * 1024), StandardCharsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            .lowercase(Locale.ROOT)
        return text.startsWith("<!doctype") || text.startsWith("<html") ||
            (text.startsWith("{") && ("\"error\"" in text || "\"message\"" in text))
    }

    private fun unwrap(controller: PlayerEngineController?): NativePlayerController? {
        val raw = (controller as? AutoSyncMpvEngineController)?.base ?: controller
        return raw as? NativePlayerController
    }

    private fun backoff(attempt: Int): Long =
        (1_000L shl attempt.coerceIn(0, 4)).coerceAtMost(MAX_BACKOFF_MS)

    private fun friendly(error: Throwable): String = when (error) {
        is HttpStatusException -> when (error.status) {
            401, 403 -> "The provider refused the recording (HTTP ${error.status})."
            else -> "The provider answered HTTP ${error.status}."
        }
        is java.net.SocketTimeoutException -> "The provider stopped sending data."
        else -> error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    }

    private class HttpStatusException(val status: Int) : IOException("HTTP $status")
}
