package com.nuvio.app.features.cast

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element

/**
 * Finds cast targets on the local network:
 *  - Chromecasts / Google TVs over mDNS (`_googlecast._tcp.local`);
 *  - smart TVs and other DLNA renderers over SSDP (anything offering UPnP AVTransport).
 *
 * Every IPv4 network adapter is asked, since a PC often has several (Ethernet, Wi-Fi, VPN).
 */
internal object CastDiscovery {
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /** One search of about [timeoutMs]; devices appear in [devices] as they answer. */
    suspend fun scan(timeoutMs: Long = 3500) {
        _scanning.value = true
        val found = ConcurrentHashMap<String, CastDevice>()
        fun publish() {
            val merged = (_devices.value.associateBy { it.id } + found).values
            _devices.value = merged.sortedWith(compareBy({ it.kind }, { it.name.lowercase() }))
        }
        try {
            coroutineScope {
                launch(Dispatchers.IO) { runCatching { searchChromecasts(timeoutMs) { found[it.id] = it; publish() } } }
                launch(Dispatchers.IO) { runCatching { searchDlna(timeoutMs) { found[it.id] = it; publish() } } }
            }
            // Keep only what answered this time, so devices that were switched off disappear.
            _devices.value = found.values.sortedWith(compareBy({ it.kind }, { it.name.lowercase() }))
        } finally {
            _scanning.value = false
        }
    }

    // ---------------------------------------------------------------- network adapters

    private fun adapters(): List<Pair<NetworkInterface, Inet4Address>> =
        runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback && it.supportsMulticast() }.getOrDefault(false) }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { ni to it } }

    /** Sends [payload] to [group]:[port] from every adapter and passes each answer to [onAnswer] until [timeoutMs]. */
    private fun multicastAsk(group: String, port: Int, payload: ByteArray, timeoutMs: Long, onAnswer: (DatagramPacket) -> Unit) {
        val target = InetAddress.getByName(group)
        val sockets = adapters().mapNotNull { (ni, address) ->
            runCatching {
                MulticastSocket(InetSocketAddress(address, 0)).apply {
                    networkInterface = ni
                    timeToLive = 4
                    soTimeout = 80
                }
            }.getOrNull()
        }
        try {
            val deadline = System.currentTimeMillis() + timeoutMs
            var nextSend = 0L
            var sends = 0
            val buffer = ByteArray(9000)
            while (System.currentTimeMillis() < deadline) {
                if (sends < 3 && System.currentTimeMillis() >= nextSend) {
                    sockets.forEach { s -> runCatching { s.send(DatagramPacket(payload, payload.size, target, port)) } }
                    sends++
                    nextSend = System.currentTimeMillis() + 1000
                }
                for (socket in sockets) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        runCatching { onAnswer(DatagramPacket(packet.data.copyOf(packet.length), packet.length, packet.address, packet.port)) }
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                    }
                }
            }
        } finally {
            sockets.forEach { runCatching { it.close() } }
        }
    }

    // ---------------------------------------------------------------- Chromecast (mDNS)

    private const val CastService = "_googlecast._tcp.local"

    private fun searchChromecasts(timeoutMs: Long, onFound: (CastDevice) -> Unit) {
        multicastAsk("224.0.0.251", 5353, mdnsQuery(CastService), timeoutMs) { packet ->
            parseMdnsAnswer(packet.data, packet.length, packet.address).forEach(onFound)
        }
    }

    private fun mdnsQuery(name: String): ByteArray = ByteArrayOutputStream().apply {
        write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0)) // id 0, flags 0, one question
        name.split('.').forEach { label ->
            val bytes = label.toByteArray()
            write(bytes.size)
            write(bytes)
        }
        write(0)
        write(byteArrayOf(0, 12, 0, 1)) // PTR, class IN
    }.toByteArray()

    private class DnsReader(val data: ByteArray, val length: Int) {
        var pos = 0
        fun u8(): Int = data[pos++].toInt() and 0xFF
        fun u16(): Int = (u8() shl 8) or u8()
        fun u32(): Long = (u16().toLong() shl 16) or u16().toLong()

        fun name(): String {
            val labels = mutableListOf<String>()
            var p = pos
            var jumped = false
            var guard = 0
            while (guard++ < 64) {
                val len = data[p].toInt() and 0xFF
                when {
                    len == 0 -> { p++; break }
                    len and 0xC0 == 0xC0 -> {
                        val target = ((len and 0x3F) shl 8) or (data[p + 1].toInt() and 0xFF)
                        if (!jumped) pos = p + 2
                        jumped = true
                        p = target
                    }
                    else -> {
                        labels += String(data, p + 1, len, Charsets.UTF_8)
                        p += len + 1
                    }
                }
            }
            if (!jumped) pos = p
            return labels.joinToString(".")
        }
    }

    private fun parseMdnsAnswer(data: ByteArray, length: Int, from: InetAddress): List<CastDevice> {
        val reader = DnsReader(data, length)
        reader.pos = 4
        val questions = reader.u16()
        val records = reader.u16() + reader.u16() + reader.u16()
        repeat(questions) { reader.name(); reader.u16(); reader.u16() }
        val instances = mutableSetOf<String>()
        val srv = mutableMapOf<String, Pair<String, Int>>()
        val txt = mutableMapOf<String, Map<String, String>>()
        val addresses = mutableMapOf<String, String>()
        repeat(records) {
            if (reader.pos >= length) return@repeat
            val name = reader.name()
            val type = reader.u16()
            reader.u16() // class
            reader.u32() // ttl
            val rdLength = reader.u16()
            val end = reader.pos + rdLength
            when (type) {
                12 -> if (name.equals(CastService, true)) instances += reader.name()
                33 -> {
                    reader.u16(); reader.u16()
                    val port = reader.u16()
                    srv[name] = reader.name() to port
                }
                16 -> {
                    val map = mutableMapOf<String, String>()
                    while (reader.pos < end) {
                        val len = reader.u8()
                        if (len == 0 || reader.pos + len > end) { reader.pos += len; continue }
                        val entry = String(data, reader.pos, len, Charsets.UTF_8)
                        reader.pos += len
                        val i = entry.indexOf('=')
                        if (i > 0) map[entry.substring(0, i)] = entry.substring(i + 1)
                    }
                    txt[name] = map
                }
                1 -> if (rdLength == 4) {
                    addresses[name] = (0 until 4).joinToString(".") { (data[reader.pos + it].toInt() and 0xFF).toString() }
                }
            }
            reader.pos = end
        }
        // Some answers carry only the SRV/TXT of an instance; treat those instances as found too.
        instances += srv.keys.filter { it.endsWith(CastService, true) }
        return instances.mapNotNull { instance ->
            val info = txt[instance].orEmpty()
            val (target, port) = srv[instance] ?: (null to 8009)
            val host = target?.let { addresses[it] } ?: from.hostAddress ?: return@mapNotNull null
            val name = info["fn"]?.takeIf { it.isNotBlank() } ?: instance.substringBefore("._googlecast")
            CastDevice(
                id = "cc:" + (info["id"] ?: instance),
                name = name,
                kind = CastKind.CHROMECAST,
                host = host,
                port = port,
                model = info["md"],
            )
        }
    }

    // ---------------------------------------------------------------- DLNA (SSDP)

    private fun searchDlna(timeoutMs: Long, onFound: (CastDevice) -> Unit) {
        val search = (
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n"
            ).toByteArray()
        val seen = ConcurrentHashMap.newKeySet<String>()
        val fetchers = mutableListOf<Thread>()
        multicastAsk("239.255.255.250", 1900, search, timeoutMs) { packet ->
            val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val location = text.lineSequence()
                .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                ?.substringAfter(':')?.trim()
                ?: return@multicastAsk
            if (seen.add(location)) {
                fetchers += Thread { runCatching { describeRenderer(location)?.let(onFound) } }.apply { isDaemon = true; start() }
            }
        }
        fetchers.forEach { it.join(4000) }
    }

    /** Reads a UPnP device description; a [CastDevice] when it offers AVTransport. */
    private fun describeRenderer(location: String): CastDevice? {
        val xml = http.newCall(Request.Builder().url(location).build()).execute().use { it.body?.string() } ?: return null
        val factory = DocumentBuilderFactory.newInstance().apply {
            // Untrusted network input: no DTDs or external entities.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isNamespaceAware = false
        }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        fun text(tag: String): String? = doc.getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        val services = doc.getElementsByTagName("service")
        val avTransport = (0 until services.length).map { services.item(it) as Element }.firstOrNull { service ->
            service.getElementsByTagName("serviceType").item(0)?.textContent?.contains("AVTransport") == true
        } ?: return null
        val control = avTransport.getElementsByTagName("controlURL").item(0)?.textContent?.trim() ?: return null
        val base = text("URLBase") ?: location
        val controlUrl = runCatching { java.net.URI(base).resolve(control).toString() }.getOrNull() ?: return null
        val uri = java.net.URI(location)
        return CastDevice(
            id = "dlna:" + (text("UDN") ?: location),
            name = text("friendlyName") ?: uri.host,
            kind = CastKind.DLNA,
            host = uri.host,
            port = if (uri.port > 0) uri.port else 80,
            controlUrl = controlUrl,
            model = listOfNotNull(text("manufacturer"), text("modelName")).joinToString(" ").ifBlank { null },
        )
    }
}
