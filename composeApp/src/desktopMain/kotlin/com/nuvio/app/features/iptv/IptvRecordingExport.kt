package com.nuvio.app.features.iptv

import com.nuvio.app.core.storage.DesktopStorage
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Where the MP4 exporter looks for ffmpeg. */
internal object IptvExportSettings {
    private val store = DesktopStorage.store("nuvio_iptv_export")
    private const val Ffmpeg = "ffmpegPath"

    fun ffmpegPath(): String? = store.getString(Ffmpeg)?.takeIf { it.isNotBlank() }

    fun setFfmpegPath(path: String?) =
        store.putString(Ffmpeg, path?.takeIf { it.isNotBlank() })

    /** A saved path, then PATH, then the app's own folder. */
    fun findFfmpeg(): Path? {
        ffmpegPath()?.let { saved ->
            val path = runCatching { Path.of(saved) }.getOrNull()
            if (path != null && Files.isRegularFile(path)) return path
        }
        val names = if (System.getProperty("os.name").orEmpty().startsWith("Windows", true)) {
            listOf("ffmpeg.exe")
        } else {
            listOf("ffmpeg")
        }
        val folders = buildList {
            System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
                .filter { it.isNotBlank() }
                .forEach { add(Path.of(it)) }
            add(DesktopStorage.rootDir)
            runCatching { add(Path.of(System.getProperty("user.dir"))) }
        }
        for (folder in folders) {
            for (name in names) {
                val candidate = folder.resolve(name)
                if (Files.isRegularFile(candidate)) return candidate
            }
        }
        return null
    }
}

/**
 * Makes finished recordings portable: joins an HLS folder into one `.ts`, or remuxes it to MP4
 * with ffmpeg (no re-encode). Inputs and outputs can be on the SMB share; the share is only
 * storage, all reading, writing and muxing happens on this computer.
 */
internal object IptvRecordingExport {
    data class Info(val destination: String, val bytes: Long)
    data class Progress(val stage: String, val doneBytes: Long, val totalBytes: Long)

    private const val EXPORTS = "Exports"
    private val invalidName = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    /** Joins a shared recording's segments and writes one `.ts` (share by default). */
    suspend fun combineShared(
        config: IptvSmbConfig,
        rec: SharedRecording,
        toShare: Boolean,
        onProgress: (Progress) -> Unit,
    ): Result<Info> = withContext(Dispatchers.IO) {
        runCatching {
            if (rec.isRecording) error("Stop the recording first.")
            val playlist = IptvSmbClient.readText(config, "${rec.shareDir}/index.m3u8")
                ?: error("This recording has no segments to combine.")
            if (isMaster(playlist)) {
                error("This recording has separate picture and sound. Use Export MP4.")
            }
            if (playlist.contains("#EXT-X-MAP")) error("This recording isn't MPEG-TS.")
            val pieces = pieces(playlist)
            if (pieces.isEmpty()) error("This recording has no segments to combine.")
            val lengths = pieces.map { IptvSmbClient.length(config, "${rec.shareDir}/$it").coerceAtLeast(0L) }
            val total = lengths.sum()
            if (total <= 0L) error("This recording has no segments to combine.")

            val name = exportName(rec.title, rec.startedAtMs, "ts")
            var output: OutputStream? = null
            var local: Path? = null
            var remote: String? = null
            try {
                if (toShare) {
                    remote = uniqueRemote(config, "$EXPORTS/$name")
                    output = IptvSmbClient.openOutputStream(config, remote)
                        ?: error("Couldn't create the file on the share.")
                } else {
                    val dir = IptvRecorder.directory.resolve(EXPORTS).also { Files.createDirectories(it) }
                    local = uniqueLocal(dir, name)
                    output = Files.newOutputStream(local)
                }
                onProgress(Progress("Combining", 0L, total))
                var done = 0L
                var last = 0L
                val context = currentCoroutineContext()
                for (piece in pieces) {
                    context.ensureActive()
                    val base = done
                    done += IptvSmbClient.copyTo(
                        config,
                        "${rec.shareDir}/$piece",
                        output,
                        cancelled = { !context.isActive },
                    ) { fileDone ->
                        done = base + fileDone
                        val now = System.currentTimeMillis()
                        if (now - last >= 500L) {
                            last = now
                            onProgress(Progress("Combining", done, total))
                        }
                    }
                }
                output.flush()
                output.close()
                output = null
                onProgress(Progress("Combining", done, total))
                val destination = if (toShare) "${config.label}/$remote" else local.toString()
                Info(destination, done)
            } catch (failure: Exception) {
                runCatching { output?.close() }
                local?.let { runCatching { Files.deleteIfExists(it) } }
                remote?.let { runCatching { IptvSmbClient.deleteRecursively(config, it) } }
                throw failure
            }
        }
    }

    /**
     * Remuxes a shared recording to MP4. Single-playlist recordings are combined into a temp
     * file first; separate picture/sound recordings are copied to a temp folder for ffmpeg.
     */
    suspend fun exportShared(
        config: IptvSmbConfig,
        rec: SharedRecording,
        toShare: Boolean,
        onProgress: (Progress) -> Unit,
    ): Result<Info> = withContext(Dispatchers.IO) {
        runCatching {
            if (rec.isRecording) error("Stop the recording first.")
            val ffmpeg = IptvExportSettings.findFfmpeg()
                ?: error("ffmpeg wasn't found. Install it or set its path in Recordings settings.")
            val playlist = IptvSmbClient.readText(config, "${rec.shareDir}/index.m3u8")
                ?: error("This recording has no segments to export.")
            val work = Files.createTempDirectory(DesktopStorage.cacheDir, "recording-export-")
            try {
                // Separate sound and fragmented MP4 need the whole folder; plain TS can be joined.
                val input = if (isMaster(playlist) || playlist.contains("#EXT-X-MAP")) {
                    val nodes = collectNodes(config, rec.shareDir, "index.m3u8")
                    val total = nodes.sumOf { it.length }
                    downloadNodes(config, rec.shareDir, nodes, work, total, onProgress)
                    work.resolve("index.m3u8")
                } else {
                    val pieces = pieces(playlist)
                    val lengths = pieces.map { IptvSmbClient.length(config, "${rec.shareDir}/$it").coerceAtLeast(0L) }
                    val total = lengths.sum()
                    val combined = work.resolve("recording.ts")
                    Files.newOutputStream(combined).use { output ->
                        copyPieces(config, rec.shareDir, pieces, total, output, onProgress)
                    }
                    combined
                }
                val mp4 = work.resolve("recording.mp4")
                val expected = runCatching { Files.size(input) }.getOrDefault(0L)
                runFfmpeg(ffmpeg, input, mp4, expected, onProgress)
                val size = Files.size(mp4)
                val name = exportName(rec.title, rec.startedAtMs, "mp4")
                val destination = if (toShare) {
                    val remote = uniqueRemote(config, "$EXPORTS/$name")
                    onProgress(Progress("Uploading", 0L, size))
                    val target = IptvSmbClient.openOutputStream(config, remote)
                        ?: error("Couldn't create the file on the share.")
                    target.use { out ->
                        Files.newInputStream(mp4).use { source ->
                            val buffer = ByteArray(256 * 1024)
                            var done = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = source.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                done += read
                                onProgress(Progress("Uploading", done, size))
                            }
                        }
                    }
                    "${config.label}/$remote"
                } else {
                    val dir = IptvRecorder.directory.resolve(EXPORTS).also { Files.createDirectories(it) }
                    val target = uniqueLocal(dir, name)
                    Files.copy(mp4, target)
                    target.toString()
                }
                Info(destination, size)
            } finally {
                runCatching { work.toFile().deleteRecursively() }
            }
        }
    }

    /** Remuxes one of this computer's own single-file recordings (TS/MP4) to MP4. */
    suspend fun exportLocal(input: Path, onProgress: (Progress) -> Unit): Result<Info> =
        withContext(Dispatchers.IO) {
            runCatching {
                val ffmpeg = IptvExportSettings.findFfmpeg()
                    ?: error("ffmpeg wasn't found. Install it or set its path in Recordings settings.")
                val total = runCatching { Files.size(input) }.getOrDefault(0L)
                val work = Files.createTempDirectory(DesktopStorage.cacheDir, "recording-export-")
                try {
                    val name = exportName(
                        input.fileName.toString().substringBeforeLast('.'),
                        runCatching { Files.getLastModifiedTime(input).toMillis() }.getOrDefault(System.currentTimeMillis()),
                        "mp4",
                    )
                    val mp4 = work.resolve("recording.mp4")
                    runFfmpeg(ffmpeg, input, mp4, total, onProgress)
                    val dir = IptvRecorder.directory.resolve(EXPORTS).also { Files.createDirectories(it) }
                    val target = uniqueLocal(dir, name)
                    Files.copy(mp4, target)
                    Info(target.toString(), Files.size(mp4))
                } finally {
                    runCatching { work.toFile().deleteRecursively() }
                }
            }
        }

    private data class Node(val relative: String, val length: Long)

    /** Every file a playback of [relative] needs, with sizes for a progress bar. */
    private fun collectNodes(
        config: IptvSmbConfig,
        shareDir: String,
        relative: String,
        seen: MutableSet<String> = HashSet(),
    ): List<Node> {
        if (!seen.add(relative)) return emptyList()
        val text = IptvSmbClient.readText(config, "$shareDir/$relative") ?: return emptyList()
        val nodes = mutableListOf(Node(relative, text.toByteArray().size.toLong()))
        val refs = if (isMaster(text)) masterRefs(text) else mediaRefs(text)
        for (ref in refs) {
            val resolved = resolveRelative(relative, ref)
            if (resolved.endsWith(".m3u8", true)) {
                nodes += collectNodes(config, shareDir, resolved, seen)
            } else {
                val length = IptvSmbClient.length(config, "$shareDir/$resolved").coerceAtLeast(0L)
                nodes += Node(resolved, length)
            }
        }
        return nodes
    }

    private suspend fun downloadNodes(
        config: IptvSmbConfig,
        shareDir: String,
        nodes: List<Node>,
        dest: Path,
        total: Long,
        onProgress: (Progress) -> Unit,
    ) {
        var done = 0L
        var last = 0L
        onProgress(Progress("Preparing", 0L, total))
        for (node in nodes) {
            currentCoroutineContext().ensureActive()
            val target = dest.resolve(node.relative).normalize()
            if (!target.startsWith(dest)) error("A segment has an unsafe name.")
            target.parent?.let { Files.createDirectories(it) }
            if (node.relative.endsWith(".m3u8", true)) {
                val text = IptvSmbClient.readText(config, "$shareDir/${node.relative}")
                    ?: error("A playlist is missing from the share.")
                Files.writeString(target, text)
            } else {
                val context = currentCoroutineContext()
                Files.newOutputStream(target).use { output ->
                    val base = done
                    done += IptvSmbClient.copyTo(
                        config,
                        "$shareDir/${node.relative}",
                        output,
                        cancelled = { !context.isActive },
                    ) { fileDone ->
                        done = base + fileDone
                        val now = System.currentTimeMillis()
                        if (now - last >= 500L) {
                            last = now
                            onProgress(Progress("Preparing", done, total))
                        }
                    }
                }
            }
        }
        onProgress(Progress("Preparing", total, total))
    }

    private suspend fun copyPieces(
        config: IptvSmbConfig,
        shareDir: String,
        pieces: List<String>,
        total: Long,
        output: OutputStream,
        onProgress: (Progress) -> Unit,
    ) {
        var done = 0L
        var last = 0L
        onProgress(Progress("Preparing", 0L, total))
        val context = currentCoroutineContext()
        for (piece in pieces) {
            context.ensureActive()
            val base = done
            done += IptvSmbClient.copyTo(
                config,
                "$shareDir/$piece",
                output,
                cancelled = { !context.isActive },
            ) { fileDone ->
                done = base + fileDone
                val now = System.currentTimeMillis()
                if (now - last >= 500L) {
                    last = now
                    onProgress(Progress("Preparing", done, total))
                }
            }
        }
        onProgress(Progress("Preparing", total, total))
    }

    private suspend fun runFfmpeg(
        ffmpeg: Path,
        input: Path,
        output: Path,
        expected: Long,
        onProgress: (Progress) -> Unit,
    ) {
        onProgress(Progress("Exporting", 0L, expected))
        val process = ProcessBuilder(
            ffmpeg.toString(),
            "-nostdin",
            "-y",
            "-i", input.toString(),
            "-c", "copy",
            "-movflags", "+faststart",
            "-f", "mp4",
            output.toString(),
        ).redirectErrorStream(true).start()
        val log = StringBuilder()
        val reader = thread(name = "ffmpeg-log", isDaemon = true) {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (log.length < 20_000) log.appendLine(line)
                }
            }
        }
        try {
            while (process.isAlive) {
                currentCoroutineContext().ensureActive()
                val size = runCatching { Files.size(output) }.getOrDefault(0L)
                onProgress(Progress("Exporting", size.coerceAtMost(expected), expected))
                Thread.sleep(500L)
            }
            if (process.exitValue() != 0) {
                val tail = log.lines().filter { it.isNotBlank() }.takeLast(4).joinToString(" ")
                error("ffmpeg couldn't make an MP4${tail.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."}")
            }
            if (!Files.isRegularFile(output) || Files.size(output) <= 0L) {
                error("ffmpeg produced an empty file.")
            }
            onProgress(Progress("Exporting", expected, expected))
        } catch (failure: Exception) {
            runCatching { process.destroyForcibly() }
            if (failure is CancellationException) throw failure
            throw failure
        } finally {
            reader.join(2_000L)
        }
    }

    private fun isMaster(text: String): Boolean = text.contains("#EXT-X-STREAM-INF")

    /** The media pieces in a single recording playlist, in order. */
    private fun pieces(text: String): List<String> = text.lines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) null else line
    }

    private fun mediaRefs(text: String): List<String> = buildList {
        addAll(pieces(text))
        Regex("""#EXT-X-MAP:.*?URI="([^"]+)"""")
            .findAll(text)
            .forEach { add(it.groupValues[1]) }
    }

    private fun masterRefs(text: String): List<String> = buildList {
        val lines = text.lines().map { it.trim() }
        lines.forEachIndexed { index, line ->
            when {
                line.startsWith("#EXT-X-STREAM-INF") ->
                    lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                        ?.let { add(it) }
                line.startsWith("#EXT-X-MEDIA:") ->
                    Regex("""URI="([^"]+)"""").find(line)?.let { add(it.groupValues[1]) }
            }
        }
    }

    private fun resolveRelative(parent: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("smb://")) return ref
        val base = parent.substringBeforeLast('/', "")
        return Path.of(base, ref).normalize().toString().replace('\\', '/')
    }

    private fun exportName(title: String, startedAtMs: Long, extension: String): String {
        val clean = title.trim()
            .replace(invalidName, "_")
            .trim('_', ' ', '.')
            .take(80)
            .ifBlank { "Recording" }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US)
            .format(Date(if (startedAtMs > 0) startedAtMs else System.currentTimeMillis()))
        return "$clean $stamp.$extension"
    }

    private fun uniqueLocal(dir: Path, name: String): Path {
        var file = dir.resolve(name)
        var n = 2
        while (Files.exists(file)) {
            file = dir.resolve("${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.')}")
            n++
        }
        return file
    }

    private fun uniqueRemote(config: IptvSmbConfig, path: String): String {
        var candidate = path
        var n = 2
        while (IptvSmbClient.exists(config, candidate)) {
            candidate = "${path.substringBeforeLast('.')} ($n).${path.substringAfterLast('.')}"
            n++
        }
        return candidate
    }
}
