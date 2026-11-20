package com.nuvio.app.features.cast

import java.io.Closeable

internal enum class CastKind { CHROMECAST, DLNA }

/** A Chromecast / Google TV, or a smart TV that plays media over DLNA (UPnP AVTransport). */
internal data class CastDevice(
    val id: String,
    val name: String,
    val kind: CastKind,
    val host: String,
    val port: Int,
    /** DLNA only: the AVTransport control address. */
    val controlUrl: String? = null,
    val model: String? = null,
)

/** What to play: the player's own source address, the headers it needs, and how to describe it. */
internal data class CastMedia(
    val url: String,
    val headers: Map<String, String>,
    val title: String,
    val contentType: String,
)

internal data class CastStatus(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val buffering: Boolean,
    val ended: Boolean,
)

/** One connection to a device that is playing (or about to play) [CastMedia]. Blocking; call off the UI thread. */
internal interface CastPlayer : Closeable {
    fun load(media: CastMedia, playUrl: String, startMs: Long)
    fun play()
    fun pause()
    fun seek(positionMs: Long)
    fun stop()
    fun status(): CastStatus?
}

internal object CastFormats {
    /** The content type a receiver expects, from the address (receivers refuse a missing or wrong one). */
    fun contentTypeOf(url: String): String {
        val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty().lowercase()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".avi") -> "video/x-msvideo"
            path.endsWith(".mov") -> "video/quicktime"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".m4a") -> "audio/mp4"
            else -> "video/mp4"
        }
    }

    /** "H:MM:SS" as DLNA wants it. */
    fun dlnaTime(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
    }

    /** "H:MM:SS" or "H:MM:SS.mmm" from DLNA, in ms; null when the TV reports none ("NOT_IMPLEMENTED"). */
    fun parseDlnaTime(text: String?): Long? {
        val parts = text?.trim()?.split(':') ?: return null
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val s = parts[2].toDoubleOrNull() ?: return null
        return ((h * 3600 + m * 60) * 1000) + (s * 1000).toLong()
    }
}
