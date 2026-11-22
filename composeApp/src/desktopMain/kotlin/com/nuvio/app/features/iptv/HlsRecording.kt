package com.nuvio.app.features.iptv

import java.net.URI
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The small part of the HLS playlist language the recorder needs: choose a variant, list the
 * media segments, and decrypt AES-128 segments. Segments are appended to one `.ts` file.
 */
internal object HlsRecording {
    data class Key(val url: String, val iv: ByteArray?)

    data class Segment(
        val url: String,
        val durationSec: Double,
        val key: Key?,
        val sequence: Long,
    )

    data class Playlist(
        val segments: List<Segment>,
        val targetDurationMs: Long,
        val ended: Boolean,
        val unsupported: String? = null,
    )

    fun isMaster(text: String): Boolean =
        text.lineSequence().any { it.trim().startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) }

    /** Picks the highest-bandwidth variant of a master playlist, if this is one. */
    fun pickVariant(text: String, baseUrl: String): String? {
        val lines = text.lineSequence().map { it.trim() }.toList()
        var bestBandwidth = -1L
        var best: String? = null
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) continue
            val bandwidth = parseAttributes(line.substringAfter(':'))["BANDWIDTH"]?.toLongOrNull() ?: 0L
            val link = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: continue
            if (bandwidth > bestBandwidth) {
                bestBandwidth = bandwidth
                best = resolve(baseUrl, link)
            }
        }
        return best
    }

    fun parse(text: String, playlistUrl: String): Playlist {
        var targetSec = 6.0
        var ended = false
        var mediaSequence = 0L
        var key: Key? = null
        var unsupported: String? = null
        var nextDuration = 0.0
        val segments = ArrayList<Segment>()

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-TARGETDURATION:", ignoreCase = true) ->
                    targetSec = line.substringAfter(':').trim().toDoubleOrNull() ?: targetSec

                line.startsWith("#EXT-X-ENDLIST", ignoreCase = true) -> ended = true

                line.startsWith("#EXT-X-MEDIA-SEQUENCE:", ignoreCase = true) ->
                    mediaSequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L

                line.startsWith("#EXT-X-MAP:", ignoreCase = true) ->
                    unsupported = "This channel uses fragmented-MP4 HLS, which this version can't record yet."

                line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    val attributes = parseAttributes(line.substringAfter(':'))
                    when (val method = attributes["METHOD"]?.uppercase(Locale.ROOT)) {
                        null, "NONE" -> key = null
                        "AES-128" -> {
                            val uri = attributes["URI"]
                            if (uri.isNullOrBlank()) {
                                unsupported = "This channel's HLS recording is encrypted but has no key link."
                            } else {
                                key = Key(resolve(playlistUrl, uri), attributes["IV"]?.let(::parseIv))
                            }
                        }
                        else -> unsupported =
                            "This channel's HLS recording is encrypted ($method), which this version can't record."
                    }
                }

                line.startsWith("#EXTINF:", ignoreCase = true) ->
                    nextDuration = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0

                line.startsWith("#") -> Unit

                else -> {
                    segments += Segment(
                        url = resolve(playlistUrl, line),
                        durationSec = nextDuration,
                        key = key,
                        sequence = mediaSequence + segments.size,
                    )
                    nextDuration = 0.0
                }
            }
        }
        return Playlist(
            segments = segments,
            targetDurationMs = (targetSec * 1000).toLong(),
            ended = ended || text.contains("#EXT-X-ENDLIST", ignoreCase = true),
            unsupported = unsupported,
        )
    }

    /**
     * A live channel can publish its whole look-back window. Start with roughly the newest
     * [seconds], not hours of backlog, the way the TV app starts a live recording.
     */
    fun newestWindow(segments: List<Segment>, seconds: Double): List<Segment> {
        if (segments.size <= 1 || seconds <= 0) return segments
        var total = 0.0
        var start = segments.size
        while (start > 0 && total < seconds) {
            start--
            total += segments[start].durationSec.coerceAtLeast(1.0)
        }
        return segments.subList(start, segments.size)
    }

    fun decrypt(data: ByteArray, key: Key, keyBytes: ByteArray, sequence: Long): ByteArray {
        val iv = key.iv ?: sequenceIv(sequence)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    fun resolve(baseUrl: String, reference: String): String =
        runCatching { URI(baseUrl).resolve(reference.trim()).toString() }.getOrDefault(reference.trim())

    private fun parseAttributes(value: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        while (i < value.length) {
            while (i < value.length && (value[i] == ',' || value[i].isWhitespace())) i++
            if (i >= value.length) break
            val equals = value.indexOf('=', i)
            if (equals < 0) break
            val name = value.substring(i, equals).trim().uppercase(Locale.ROOT)
            var j = equals + 1
            if (j >= value.length) break
            val attribute = if (value[j] == '"') {
                val end = value.indexOf('"', j + 1)
                if (end < 0) break
                value.substring(j + 1, end).also { j = end + 1 }
            } else {
                val end = value.indexOf(',', j).let { if (it < 0) value.length else it }
                value.substring(j, end).trim().also { j = end }
            }
            out[name] = attribute
            i = j
        }
        return out
    }

    private fun parseIv(raw: String): ByteArray? {
        val hex = raw.trim().removePrefix("0x").removePrefix("0X")
        if (hex.isEmpty() || hex.length % 2 != 0) return null
        val decoded = runCatching {
            ByteArray(hex.length / 2) { index ->
                hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull() ?: return null
        if (decoded.size > 16) return null
        return ByteArray(16).also { decoded.copyInto(it, 16 - decoded.size) }
    }

    private fun sequenceIv(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var value = sequence
        for (index in 15 downTo 8) {
            iv[index] = (value and 0xFF).toByte()
            value = value ushr 8
        }
        return iv
    }
}
