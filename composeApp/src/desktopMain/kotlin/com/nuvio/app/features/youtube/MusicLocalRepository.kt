package com.nuvio.app.features.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal data class LocalMusicTrack(
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

/** Local audio folders, scanned on this device and played through mpv. */
internal object MusicLocalRepository {
    private const val FoldersKey = "folders"
    private const val MaxTracks = 30_000
    private val audioExtensions = setOf("mp3", "m4a", "m4b", "flac", "wav", "ogg", "opus", "aac", "webm")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = DesktopStorage.store("nuvio_music_local")

    var folders by mutableStateOf<List<String>>(emptyList())
        private set
    var tracks by mutableStateOf<List<LocalMusicTrack>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        folders = store.getString(FoldersKey)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    fun addFolder(path: Path) {
        ensureLoaded()
        val value = path.toAbsolutePath().toString()
        if (value !in folders) {
            folders = folders + value
            persist()
            scan()
        }
    }

    fun removeFolder(path: String) {
        ensureLoaded()
        folders = folders.filterNot { it == path }
        persist()
        if (folders.isEmpty()) {
            tracks = emptyList()
            scanning = false
            return
        }
        scan()
    }

    fun scan() {
        ensureLoaded()
        if (scanning || folders.isEmpty()) return
        scanning = true
        error = null
        scope.launch {
            runCatching {
                val result = ArrayList<LocalMusicTrack>()
                folders.forEach { folder ->
                    val root = runCatching { Path.of(folder) }.getOrNull() ?: return@forEach
                    if (!Files.isDirectory(root)) return@forEach
                    runCatching {
                        Files.walk(root, 10).use { stream ->
                            stream.filter { file ->
                                Files.isRegularFile(file) &&
                                    file.fileName.toString().substringAfterLast('.', "")
                                        .lowercase(Locale.ROOT) in audioExtensions &&
                                    file.none { it.toString().startsWith(".") }
                            }.forEach { file ->
                                if (result.size < MaxTracks) {
                                    runCatching { readTrack(file) }.getOrNull()?.let(result::add)
                                }
                            }
                        }
                    }
                }
                result.sortedWith(compareBy({ it.artist.lowercase(Locale.ROOT) }, { it.album.lowercase(Locale.ROOT) }, { it.title.lowercase(Locale.ROOT) }))
            }.onSuccess {
                tracks = it
                error = null
            }.onFailure {
                error = it.message ?: "The local music scan failed."
            }
            scanning = false
        }
    }

    fun toVideoItem(track: LocalMusicTrack): YouTubeVideoItem = YouTubeVideoItem(
        videoId = "local:" + stableId(track.path),
        title = track.title,
        author = track.artist,
        thumbnailUrl = null,
        durationMs = track.durationMs,
        isLive = false,
        kind = YouTubeItemKind.VIDEO,
        isMusic = true,
        localUrl = Path.of(track.path).toUri().toString(),
    )

    private fun persist() {
        runCatching { store.putString(FoldersKey, folders.joinToString("\n")) }
    }

    private fun readTrack(file: Path): LocalMusicTrack {
        val name = file.fileName.toString().substringBeforeLast('.')
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        val extension = file.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)
        when (extension) {
            "mp3" -> {
                val tags = readId3(file) ?: Id3Tags()
                title = tags.title
                artist = tags.artist
                album = tags.album
                durationMs = estimateMp3Duration(file, tags.audioStart)
            }
            "flac" -> {
                val info = readFlac(file) ?: FlacInfo()
                title = info.title
                artist = info.artist
                album = info.album
                durationMs = info.durationMs
            }
            "m4a", "m4b", "mp4" -> {
                val info = readMp4(file) ?: Mp4Info()
                title = info.title
                artist = info.artist
                album = info.album
                durationMs = info.durationMs
            }
            "ogg", "opus" -> {
                val info = readOgg(file) ?: OggInfo()
                title = info.title
                artist = info.artist
                album = info.album
                durationMs = info.durationMs
            }
            "wav" -> durationMs = readWavDuration(file)
        }
        return LocalMusicTrack(
            path = file.toAbsolutePath().toString(),
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: name.ifBlank { "Unknown track" },
            artist = artist?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown Artist",
            album = album?.trim()?.takeIf { it.isNotEmpty() } ?: file.parent?.fileName?.toString().orEmpty(),
            durationMs = durationMs,
        )
    }

    private data class Id3Tags(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val audioStart: Long = 0L,
    )

    private fun readId3(file: Path): Id3Tags? = runCatching {
        RandomAccessFile(file.toFile(), "r").use { raf ->
            val length = raf.length()
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var audioStart = 0L
            if (length >= 10) {
                val header = ByteArray(10)
                raf.readFully(header)
                if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    val major = header[3].toInt() and 0xff
                    val tagSize = syncSafe(header, 6)
                    audioStart = (10 + tagSize).toLong().coerceAtMost(length)
                    val end = audioStart
                    raf.seek(10)
                    while (raf.filePointer + 10 <= end) {
                        val frameHeader = ByteArray(10)
                        raf.readFully(frameHeader)
                        val id = String(frameHeader, 0, 4, Charsets.ISO_8859_1)
                        if (id[0] == '\u0000') break
                        val size = if (major >= 4) syncSafe(frameHeader, 4) else beInt(frameHeader, 4)
                        if (size <= 0 || raf.filePointer + size > end) break
                        if (id == "TIT2" || id == "TPE1" || id == "TALB") {
                            val data = ByteArray(size.coerceAtMost(2 * 1024 * 1024))
                            raf.readFully(data)
                            val value = id3Text(data)
                            when (id) {
                                "TIT2" -> title = value
                                "TPE1" -> artist = value
                                "TALB" -> album = value
                            }
                        } else {
                            raf.seek(raf.filePointer + size)
                        }
                    }
                }
            }
            if (title == null || artist == null || album == null) {
                if (length >= 128) {
                    raf.seek(length - 128)
                    val tail = ByteArray(128)
                    raf.readFully(tail)
                    if (tail[0] == 'T'.code.toByte() && tail[1] == 'A'.code.toByte() && tail[2] == 'G'.code.toByte()) {
                        title = title ?: id3v1(tail, 3)
                        artist = artist ?: id3v1(tail, 33)
                        album = album ?: id3v1(tail, 63)
                    }
                }
            }
            Id3Tags(title, artist, album, audioStart)
        }
    }.getOrNull()

    private data class FlacInfo(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long = 0L,
    )

    private fun readFlac(file: Path): FlacInfo? = runCatching {
        RandomAccessFile(file.toFile(), "r").use { raf ->
            val signature = ByteArray(4)
            raf.readFully(signature)
            if (String(signature, Charsets.ISO_8859_1) != "fLaC") return@use null
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var durationMs = 0L
            var last = false
            while (!last) {
                val header = raf.read()
                if (header < 0) break
                last = header and 0x80 != 0
                val type = header and 0x7f
                val size = (raf.read() shl 16) or (raf.read() shl 8) or raf.read()
                if (size < 0) break
                when (type) {
                    0 -> {
                        val info = ByteArray(size.coerceAtMost(34))
                        raf.readFully(info)
                        if (info.size >= 18) {
                            val sampleRate = ((info[10].toInt() and 0x0f) shl 12) or
                                ((info[11].toInt() and 0xff) shl 4) or
                                ((info[12].toInt() and 0xf0) ushr 4)
                            val totalSamples = ((info[13].toLong() and 0x0f) shl 32) or
                                ((info[14].toLong() and 0xff) shl 24) or
                                ((info[15].toLong() and 0xff) shl 16) or
                                ((info[16].toLong() and 0xff) shl 8) or
                                (info[17].toLong() and 0xff)
                            if (sampleRate > 0) durationMs = totalSamples * 1000L / sampleRate
                        }
                        if (info.size < size) raf.seek(raf.filePointer + (size - info.size))
                    }
                    4 -> {
                        val data = ByteArray(size.coerceAtMost(4 * 1024 * 1024))
                        raf.readFully(data)
                        var offset = 0
                        fun leInt(): Int {
                            if (offset + 4 > data.size) return 0
                            val value = (data[offset].toInt() and 0xff) or
                                ((data[offset + 1].toInt() and 0xff) shl 8) or
                                ((data[offset + 2].toInt() and 0xff) shl 16) or
                                ((data[offset + 3].toInt() and 0xff) shl 24)
                            offset += 4
                            return value
                        }
                        val vendorLength = leInt()
                        offset = (offset + vendorLength).coerceAtMost(data.size)
                        val count = leInt()
                        repeat(count) {
                            val length = leInt()
                            if (length < 0 || offset + length > data.size) return@repeat
                            val entry = String(data, offset, length, Charsets.UTF_8)
                            offset += length
                            val key = entry.substringBefore('=').uppercase(Locale.ROOT)
                            val value = entry.substringAfter('=', "").trim()
                            if (value.isEmpty()) return@repeat
                            when (key) {
                                "TITLE" -> title = value
                                "ARTIST" -> artist = value
                                "ALBUM" -> album = value
                            }
                        }
                        if (data.size < size) raf.seek(raf.filePointer + (size - data.size))
                    }
                    else -> raf.seek(raf.filePointer + size)
                }
            }
            FlacInfo(title, artist, album, durationMs)
        }
    }.getOrNull()

    private data class Mp4Info(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long = 0L,
    )

    private fun readMp4(file: Path): Mp4Info? = runCatching {
        RandomAccessFile(file.toFile(), "r").use { raf ->
            val moov = findMp4Box(raf, "moov") ?: return@use null
            val bytes = ByteArray(moov.second)
            raf.seek(moov.first)
            raf.readFully(bytes)
            val values = HashMap<String, String>()
            walkMp4Boxes(bytes, 0, bytes.size, values)
            Mp4Info(
                title = values["title"],
                artist = values["artist"],
                album = values["album"],
                durationMs = readMvhdDuration(bytes),
            )
        }
    }.getOrNull()

    private fun findMp4Box(raf: RandomAccessFile, wanted: String): Pair<Long, Int>? {
        val length = raf.length()
        var position = 0L
        while (position + 8 <= length) {
            raf.seek(position)
            val size32 = raf.readInt().toLong() and 0xffffffffL
            val type = ByteArray(4)
            raf.readFully(type)
            val typeText = String(type, Charsets.ISO_8859_1)
            var header = 8L
            val size = when (size32) {
                1L -> {
                    header = 16L
                    raf.readLong()
                }
                0L -> length - position
                else -> size32
            }
            if (size < header) return null
            if (typeText == wanted) {
                return (position + header) to (size - header).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            position += size
        }
        return null
    }

    private fun walkMp4Boxes(data: ByteArray, start: Int, end: Int, values: MutableMap<String, String>) {
        var offset = start
        while (offset + 8 <= end) {
            val size = beInt(data, offset).toLong() and 0xffffffffL
            if (size < 8 || offset + size > end) return
            val type = String(data, offset + 4, 4, Charsets.ISO_8859_1)
            val contentStart = offset + 8
            val contentEnd = (offset + size).toInt()
            when (type) {
                "udta", "ilst" -> walkMp4Boxes(data, contentStart, contentEnd, values)
                "meta" -> walkMp4Boxes(data, contentStart + 4, contentEnd, values)
                else -> if (type.startsWith("\u00A9") || type == "aART") {
                    readIlstValue(data, contentStart, contentEnd)?.let { value ->
                        when (type) {
                            "\u00A9nam" -> values["title"] = value
                            "\u00A9ART", "aART" -> values["artist"] = value
                            "\u00A9alb" -> values["album"] = value
                        }
                    }
                }
            }
            offset = contentEnd
        }
    }

    private fun readIlstValue(data: ByteArray, start: Int, end: Int): String? {
        var offset = start
        while (offset + 8 <= end) {
            val size = beInt(data, offset).toLong() and 0xffffffffL
            if (size < 8 || offset + size > end) return null
            if (String(data, offset + 4, 4, Charsets.ISO_8859_1) == "data") {
                val payload = offset + 16
                val payloadEnd = (offset + size).toInt()
                if (payload > payloadEnd) return null
                return String(data, payload, payloadEnd - payload, Charsets.UTF_8)
                    .trim('\u0000', ' ', '\n', '\r')
                    .ifBlank { null }
            }
            offset += size.toInt()
        }
        return null
    }

    private fun readMvhdDuration(data: ByteArray): Long {
        var offset = 0
        while (offset + 8 <= data.size) {
            val size = beInt(data, offset).toLong() and 0xffffffffL
            if (size < 8 || offset + size > data.size) break
            if (String(data, offset + 4, 4, Charsets.ISO_8859_1) == "mvhd") {
                val version = data[offset + 8].toInt() and 0xff
                val timescale: Long
                val duration: Long
                if (version == 1) {
                    timescale = beInt(data, offset + 28).toLong() and 0xffffffffL
                    duration = beLong(data, offset + 32)
                } else {
                    timescale = beInt(data, offset + 20).toLong() and 0xffffffffL
                    duration = beInt(data, offset + 24).toLong() and 0xffffffffL
                }
                if (timescale > 0 && duration > 0) return duration * 1000 / timescale
            }
            offset += size.toInt()
        }
        return 0
    }

    private data class OggInfo(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long = 0L,
    )

    private fun readOgg(file: Path): OggInfo? = runCatching {
        RandomAccessFile(file.toFile(), "r").use { raf ->
            val length = raf.length()
            val headLength = minOf(length, 256L * 1024L).toInt()
            val head = ByteArray(headLength)
            raf.seek(0)
            raf.readFully(head)
            var sampleRate = indexOfBytes(head, byteArrayOf(1, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte()))
                ?.let { leInt(head, it + 12) }
                ?: 0
            var comments: Map<String, String>? = null
            indexOfBytes(head, "\u0003vorbis".toByteArray(Charsets.ISO_8859_1))?.let { marker ->
                comments = parseVorbisComments(head, marker + 7)
            }
            if (comments == null) {
                indexOfBytes(head, "OpusTags".toByteArray(Charsets.ISO_8859_1))?.let { marker ->
                    comments = parseVorbisComments(head, marker + 8)
                    sampleRate = 48_000
                }
            }
            if (sampleRate <= 0) sampleRate = 48_000
            val granule = readLastGranule(raf)
            val duration = if (granule > 0) granule * 1000 / sampleRate else 0L
            OggInfo(
                title = comments?.get("TITLE"),
                artist = comments?.get("ARTIST"),
                album = comments?.get("ALBUM"),
                durationMs = duration,
            )
        }
    }.getOrNull()

    private fun parseVorbisComments(data: ByteArray, start: Int): Map<String, String> {
        val comments = HashMap<String, String>()
        var offset = start
        if (offset + 4 > data.size) return comments
        val vendorLength = leInt(data, offset)
        offset += 4
        if (vendorLength < 0 || offset + vendorLength + 4 > data.size) return comments
        offset += vendorLength
        val count = leInt(data, offset)
        offset += 4
        repeat(count.coerceAtMost(512)) {
            if (offset + 4 > data.size) return comments
            val length = leInt(data, offset)
            offset += 4
            if (length < 0 || offset + length > data.size) return comments
            val entry = String(data, offset, length, Charsets.UTF_8)
            offset += length
            val key = entry.substringBefore('=').uppercase(Locale.ROOT)
            val value = entry.substringAfter('=', "").trim()
            if (value.isNotEmpty()) comments[key] = value
        }
        return comments
    }

    private fun readLastGranule(raf: RandomAccessFile): Long {
        val length = raf.length()
        val window = minOf(length, 128L * 1024L).toInt()
        if (window < 14) return 0L
        val bytes = ByteArray(window)
        raf.seek(length - window)
        raf.readFully(bytes)
        for (index in window - 14 downTo 0) {
            if (bytes[index] == 'O'.code.toByte() &&
                bytes[index + 1] == 'g'.code.toByte() &&
                bytes[index + 2] == 'g'.code.toByte() &&
                bytes[index + 3] == 'S'.code.toByte() &&
                bytes[index + 4].toInt() == 0
            ) {
                val granule = leLong(bytes, index + 6)
                if (granule > 0) return granule
            }
        }
        return 0L
    }

    private fun indexOfBytes(data: ByteArray, marker: ByteArray): Int? {
        if (marker.isEmpty() || marker.size > data.size) return null
        outer@ for (index in 0..data.size - marker.size) {
            for (offset in marker.indices) {
                if (data[index + offset] != marker[offset]) continue@outer
            }
            return index
        }
        return null
    }

    private fun readWavDuration(file: Path): Long = runCatching {
        RandomAccessFile(file.toFile(), "r").use { raf ->
            val riff = ByteArray(12)
            raf.readFully(riff)
            if (String(riff, 0, 4, Charsets.ISO_8859_1) != "RIFF" || String(riff, 8, 4, Charsets.ISO_8859_1) != "WAVE") return@use 0L
            var byteRate = 0L
            while (raf.filePointer + 8 <= raf.length()) {
                val id = ByteArray(4)
                raf.readFully(id)
                val size = leInt(raf)
                if (String(id, Charsets.ISO_8859_1) == "fmt " && size >= 16) {
                    raf.readUnsignedShort()
                    raf.readUnsignedShort()
                    raf.readInt()
                    byteRate = raf.readInt().toLong() and 0xffffffffL
                    raf.seek(raf.filePointer + (size - 12))
                } else if (String(id, Charsets.ISO_8859_1) == "data") {
                    return@use if (byteRate > 0) size * 1000L / byteRate else 0L
                } else {
                    raf.seek(raf.filePointer + size + (size and 1))
                }
            }
            0L
        }
    }.getOrDefault(0L)

    private fun estimateMp3Duration(file: Path, audioStart: Long): Long = runCatching {
        RandomAccessFile(file.toFile(), "r").use { raf ->
            raf.seek(audioStart.coerceAtMost(raf.length()))
            val header = ByteArray(4)
            raf.readFully(header)
            val b0 = header[0].toInt() and 0xff
            val b1 = header[1].toInt() and 0xff
            val b2 = header[2].toInt() and 0xff
            if (b0 != 0xff || b1 and 0xe0 != 0xe0) return@use 0L
            val version = (b1 ushr 3) and 0x03
            val layer = (b1 ushr 1) and 0x03
            val bitrateIndex = (b2 ushr 4) and 0x0f
            if (layer != 1 || bitrateIndex == 0 || bitrateIndex == 15) return@use 0L
            val bitrates = if (version == 3) {
                intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
            } else {
                intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
            }
            val bitrate = bitrates[bitrateIndex] * 1000L
            if (bitrate <= 0) 0L else (raf.length() - audioStart) * 8L * 1000L / bitrate
        }
    }.getOrDefault(0L)

    private fun id3Text(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val encoding = data[0].toInt() and 0xff
        val bytes = data.copyOfRange(1, data.size).takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            when (encoding) {
                1 -> String(bytes, Charsets.UTF_16).trimEnd('\u0000')
                2 -> String(bytes, Charsets.UTF_16BE).trimEnd('\u0000')
                3 -> String(bytes, Charsets.UTF_8).trimEnd('\u0000')
                else -> String(bytes, Charsets.ISO_8859_1).trimEnd('\u0000')
            }.trim().ifBlank { null }
        }.getOrNull()
    }

    private fun id3v1(bytes: ByteArray, offset: Int): String? {
        val end = (offset until minOf(offset + 30, bytes.size)).firstOrNull { bytes[it] == 0.toByte() } ?: minOf(offset + 30, bytes.size)
        return String(bytes, offset, end - offset, Charsets.ISO_8859_1).trim().ifBlank { null }
    }

    private fun syncSafe(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7f) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7f) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7f) shl 7) or
            (bytes[offset + 3].toInt() and 0x7f)

    private fun beInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun beLong(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 56) or
            ((bytes[offset + 1].toLong() and 0xff) shl 48) or
            ((bytes[offset + 2].toLong() and 0xff) shl 40) or
            ((bytes[offset + 3].toLong() and 0xff) shl 32) or
            ((bytes[offset + 4].toLong() and 0xff) shl 24) or
            ((bytes[offset + 5].toLong() and 0xff) shl 16) or
            ((bytes[offset + 6].toLong() and 0xff) shl 8) or
            (bytes[offset + 7].toLong() and 0xff)

    private fun leInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun leLong(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24) or
            ((bytes[offset + 4].toLong() and 0xff) shl 32) or
            ((bytes[offset + 5].toLong() and 0xff) shl 40) or
            ((bytes[offset + 6].toLong() and 0xff) shl 48) or
            ((bytes[offset + 7].toLong() and 0xff) shl 56)

    private fun leInt(raf: RandomAccessFile): Int {
        val b0 = raf.read()
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { "%02x".format(it) }
}
