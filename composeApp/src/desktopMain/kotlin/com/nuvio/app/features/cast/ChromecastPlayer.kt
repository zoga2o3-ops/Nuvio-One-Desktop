package com.nuvio.app.features.cast

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Plays media on a Chromecast / Google TV through its built-in Default Media Receiver.
 *
 * Google Cast ("CASTV2") is a TLS connection to port 8009 carrying length-prefixed protobuf
 * `CastMessage`s whose payload is JSON. The message has six fields, so it is encoded here by hand.
 * The device uses a self-signed certificate (every Cast sender accepts it), so the certificate is not checked.
 */
internal class ChromecastPlayer(private val device: CastDevice) : CastPlayer {
    private companion object {
        const val Sender = "sender-nuvio"
        const val Receiver = "receiver-0"
        const val NsConnection = "urn:x-cast:com.google.cast.tp.connection"
        const val NsHeartbeat = "urn:x-cast:com.google.cast.tp.heartbeat"
        const val NsReceiver = "urn:x-cast:com.google.cast.receiver"
        const val NsMedia = "urn:x-cast:com.google.cast.media"
        const val DefaultMediaReceiver = "CC1AD845"
    }

    private val socket: SSLSocket
    private val output: DataOutputStream
    private val requestIds = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()
    private val heartbeat: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "cast-heartbeat").apply { isDaemon = true }
    }
    @Volatile private var closed = false
    @Volatile private var transportId: String? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var mediaSessionId: Int? = null
    @Volatile private var lastStatus: CastStatus? = null
    @Volatile private var lastError: String? = null

    init {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val context = SSLContext.getInstance("TLS").apply { init(null, trustAll, java.security.SecureRandom()) }
        socket = (context.socketFactory.createSocket() as SSLSocket).apply {
            connect(InetSocketAddress(device.host, device.port), 5000)
            startHandshake()
        }
        output = DataOutputStream(socket.getOutputStream())
        send(NsConnection, Receiver, buildJsonObject { put("type", "CONNECT") })
        Thread({ readLoop() }, "cast-reader").apply { isDaemon = true; start() }
        heartbeat.scheduleAtFixedRate({
            runCatching { send(NsHeartbeat, Receiver, buildJsonObject { put("type", "PING") }) }
        }, 5, 5, TimeUnit.SECONDS)
    }

    override fun load(media: CastMedia, playUrl: String, startMs: Long) {
        val app = launchReceiver()
        transportId = app.first
        sessionId = app.second
        send(NsConnection, app.first, buildJsonObject { put("type", "CONNECT") })
        val reply = request(NsMedia, app.first, buildJsonObject {
            put("type", "LOAD")
            put("sessionId", app.second)
            put("autoplay", true)
            put("currentTime", startMs / 1000.0)
            putJsonObject("media") {
                put("contentId", playUrl)
                put("streamType", "BUFFERED")
                put("contentType", media.contentType)
                putJsonObject("metadata") {
                    put("metadataType", 0)
                    put("title", media.title)
                }
            }
        }, timeoutSeconds = 25)
        when (reply.string("type")) {
            "MEDIA_STATUS" -> readMediaStatus(reply)
            else -> throw IOException(
                "The Chromecast could not play this (${reply.string("type") ?: "no answer"}). " +
                    "It may not support this video format."
            )
        }
    }

    /** Starts the Default Media Receiver (or finds it running); its transport and session ids. */
    private fun launchReceiver(): Pair<String, String> {
        var status = request(NsReceiver, Receiver, buildJsonObject {
            put("type", "LAUNCH")
            put("appId", DefaultMediaReceiver)
        }, timeoutSeconds = 20)
        repeat(20) {
            receiverApp(status)?.let { return it }
            Thread.sleep(500)
            status = request(NsReceiver, Receiver, buildJsonObject { put("type", "GET_STATUS") })
        }
        throw IOException("The Chromecast did not start its media player.")
    }

    private fun receiverApp(status: JsonObject): Pair<String, String>? {
        val apps = runCatching { status["status"]!!.jsonObject["applications"]!!.jsonArray }.getOrNull() ?: return null
        val app = apps.map { it.jsonObject }.firstOrNull { it.string("appId") == DefaultMediaReceiver } ?: return null
        val transport = app.string("transportId") ?: return null
        val session = app.string("sessionId") ?: return null
        return transport to session
    }

    override fun play() = mediaCommand("PLAY")
    override fun pause() = mediaCommand("PAUSE")

    override fun seek(positionMs: Long) = mediaCommand("SEEK") { put("currentTime", positionMs / 1000.0) }

    override fun stop() {
        runCatching { mediaCommand("STOP") }
        sessionId?.let { session ->
            runCatching { send(NsReceiver, Receiver, buildJsonObject { put("type", "STOP"); put("sessionId", session); put("requestId", requestIds.getAndIncrement()) }) }
        }
    }

    override fun status(): CastStatus? {
        lastError?.let { throw IOException(it) }
        val transport = transportId ?: return lastStatus
        runCatching {
            val reply = request(NsMedia, transport, buildJsonObject {
                put("type", "GET_STATUS")
                mediaSessionId?.let { put("mediaSessionId", it) }
            }, timeoutSeconds = 5)
            readMediaStatus(reply)
        }
        if (closed) throw IOException("The Chromecast closed the connection.")
        return lastStatus
    }

    private fun mediaCommand(type: String, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) {
        val transport = transportId ?: return
        val session = mediaSessionId ?: return
        send(NsMedia, transport, buildJsonObject {
            put("type", type)
            put("mediaSessionId", session)
            put("requestId", requestIds.getAndIncrement())
            extra()
        })
    }

    private fun readMediaStatus(message: JsonObject) {
        val entry = runCatching { message["status"]!!.jsonArray.firstOrNull()?.jsonObject }.getOrNull() ?: return
        (entry["mediaSessionId"] as? JsonPrimitive)?.intOrNull?.let { mediaSessionId = it }
        val state = entry.string("playerState")
        val idleReason = entry.string("idleReason")
        if (state == "IDLE" && idleReason == "ERROR") lastError = "The Chromecast stopped with a playback error."
        val durationMs = runCatching { entry["media"]?.jsonObject?.get("duration")?.jsonPrimitive?.doubleOrNull }
            .getOrNull()?.let { (it * 1000).toLong() } ?: lastStatus?.durationMs ?: 0L
        val positionMs = (entry["currentTime"] as? JsonPrimitive)?.doubleOrNull?.let { (it * 1000).toLong() } ?: lastStatus?.positionMs ?: 0L
        lastStatus = CastStatus(
            positionMs = positionMs,
            durationMs = durationMs,
            playing = state == "PLAYING",
            buffering = state == "BUFFERING" || state == "LOADING",
            ended = state == "IDLE" && idleReason == "FINISHED",
        )
    }

    // ---------------------------------------------------------------- messages

    private fun request(namespace: String, destination: String, body: JsonObject, timeoutSeconds: Long = 10): JsonObject {
        val id = requestIds.getAndIncrement()
        val future = CompletableFuture<JsonObject>()
        pending[id] = future
        try {
            send(namespace, destination, JsonObject(body + ("requestId" to JsonPrimitive(id))))
            return future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            throw IOException("The Chromecast did not answer.")
        } finally {
            pending.remove(id)
        }
    }

    @Synchronized
    private fun send(namespace: String, destination: String, body: JsonObject) {
        if (closed) throw IOException("Not connected to the Chromecast.")
        val frame = encode(Sender, destination, namespace, body.toString())
        output.writeInt(frame.size)
        output.write(frame)
        output.flush()
    }

    private fun readLoop() {
        val input = DataInputStream(socket.getInputStream())
        try {
            while (!closed) {
                val size = input.readInt()
                if (size <= 0 || size > 1_048_576) throw IOException("bad frame")
                val frame = ByteArray(size)
                input.readFully(frame)
                val message = decode(frame) ?: continue
                val payload = runCatching { Json.parseToJsonElement(message.payload).jsonObject }.getOrNull() ?: continue
                when {
                    message.namespace == NsHeartbeat && payload.string("type") == "PING" ->
                        runCatching { send(NsHeartbeat, message.source, buildJsonObject { put("type", "PONG") }) }
                    message.namespace == NsConnection && payload.string("type") == "CLOSE" && message.source == transportId -> {
                        lastError = "The Chromecast ended the session."
                    }
                    else -> {
                        if (message.namespace == NsMedia && payload.string("type") == "MEDIA_STATUS") readMediaStatus(payload)
                        val requestId = (payload["requestId"] as? JsonPrimitive)?.intOrNull
                        if (requestId != null && requestId != 0) pending[requestId]?.complete(payload)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            closed = true
            pending.values.forEach { it.completeExceptionally(IOException("closed")) }
        }
    }

    override fun close() {
        closed = true
        heartbeat.shutdownNow()
        runCatching { socket.close() }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    // ---------------------------------------------------------------- CastMessage protobuf

    private class Message(val source: String, val destination: String, val namespace: String, val payload: String)

    private fun encode(source: String, destination: String, namespace: String, payload: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun varint(value: Long) {
            var v = value
            while (true) {
                if (v and 0x7FL.inv() == 0L) { out.write(v.toInt()); return }
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }
        fun string(field: Int, text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            varint(((field shl 3) or 2).toLong())
            varint(bytes.size.toLong())
            out.write(bytes)
        }
        varint((1 shl 3).toLong()); varint(0) // protocol_version = CASTV2_1_0
        string(2, source)
        string(3, destination)
        string(4, namespace)
        varint((5 shl 3).toLong()); varint(0) // payload_type = STRING
        string(6, payload)
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): Message? {
        var pos = 0
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (pos < bytes.size) {
                val b = bytes[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            return result
        }
        val strings = mutableMapOf<Int, String>()
        while (pos < bytes.size) {
            val key = varint().toInt()
            val field = key ushr 3
            when (key and 7) {
                0 -> varint()
                2 -> {
                    val len = varint().toInt()
                    if (len < 0 || pos + len > bytes.size) return null
                    if (field in 2..6) strings[field] = String(bytes, pos, len, Charsets.UTF_8)
                    pos += len
                }
                else -> return null
            }
        }
        return Message(strings[2].orEmpty(), strings[3].orEmpty(), strings[4].orEmpty(), strings[6] ?: return null)
    }
}
