package com.nuvio.app.features.cast

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Plays media on a smart TV (or other DLNA renderer) through UPnP AVTransport SOAP calls. */
internal class DlnaPlayer(private val device: CastDevice) : CastPlayer {
    private companion object {
        const val Service = "urn:schemas-upnp-org:service:AVTransport:1"
    }

    private val control = device.controlUrl ?: throw IOException("This TV has no media control address.")
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    @Volatile private var lastStatus: CastStatus? = null

    override fun load(media: CastMedia, playUrl: String, startMs: Long) {
        // Some TVs refuse a new address while something is playing.
        runCatching { soap("Stop", "") }
        soap(
            "SetAVTransportURI",
            "<CurrentURI>${xml(playUrl)}</CurrentURI><CurrentURIMetaData>${xml(didl(media, playUrl))}</CurrentURIMetaData>",
        )
        soap("Play", "<Speed>1</Speed>")
        if (startMs > 5_000) {
            // Seeking only works once the TV has opened the video.
            repeat(30) {
                if (transportState() == "PLAYING") {
                    runCatching { seek(startMs) }
                    return
                }
                Thread.sleep(500)
            }
        }
    }

    override fun play() { soap("Play", "<Speed>1</Speed>") }
    override fun pause() { soap("Pause", "") }

    override fun seek(positionMs: Long) {
        soap("Seek", "<Unit>REL_TIME</Unit><Target>${CastFormats.dlnaTime(positionMs)}</Target>")
    }

    override fun stop() { runCatching { soap("Stop", "") } }

    override fun status(): CastStatus? {
        val position = soap("GetPositionInfo", "")
        val state = transportState()
        val positionMs = CastFormats.parseDlnaTime(tag(position, "RelTime")) ?: lastStatus?.positionMs ?: 0L
        val durationMs = CastFormats.parseDlnaTime(tag(position, "TrackDuration"))?.takeIf { it > 0 } ?: lastStatus?.durationMs ?: 0L
        val previous = lastStatus
        lastStatus = CastStatus(
            positionMs = positionMs,
            durationMs = durationMs,
            playing = state == "PLAYING",
            buffering = state == "TRANSITIONING",
            // Stopped after it had been playing near the end: the video finished.
            ended = state == "STOPPED" && previous != null && previous.durationMs > 0 && previous.positionMs >= previous.durationMs - 3_000,
        )
        return lastStatus
    }

    private fun transportState(): String? = tag(soap("GetTransportInfo", ""), "CurrentTransportState")

    private fun soap(action: String, arguments: String): String {
        val body = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:$action xmlns:u="$Service"><InstanceID>0</InstanceID>$arguments</u:$action></s:Body></s:Envelope>"""
        val request = Request.Builder()
            .url(control)
            .header("SOAPACTION", "\"$Service#$action\"")
            .post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reason = tag(text, "errorDescription") ?: "error ${response.code}"
                throw IOException("The TV refused $action: $reason")
            }
            return text
        }
    }

    /** DIDL-Lite description: many TVs (Samsung, LG) refuse a video without one. */
    private fun didl(media: CastMedia, playUrl: String): String {
        val upnpClass = if (media.contentType.startsWith("audio/")) "object.item.audioItem.musicTrack" else "object.item.videoItem"
        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """ +
            """xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
            """<item id="0" parentID="-1" restricted="1"><dc:title>${xml(media.title)}</dc:title>""" +
            """<upnp:class>$upnpClass</upnp:class>""" +
            """<res protocolInfo="http-get:*:${media.contentType}:*">${xml(playUrl)}</res></item></DIDL-Lite>"""
    }

    private fun xml(text: String) = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun tag(xml: String, name: String): String? =
        Regex("<(?:\\w+:)?$name>(.*?)</(?:\\w+:)?$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()

    override fun close() = Unit
}
