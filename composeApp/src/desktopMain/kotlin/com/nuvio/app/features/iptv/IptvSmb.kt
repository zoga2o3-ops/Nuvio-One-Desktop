package com.nuvio.app.features.iptv

import com.nuvio.app.core.storage.DesktopSecretStore
import com.nuvio.app.core.storage.DesktopStorage
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A network share holding Live TV recordings. Credentials never leave this computer. */
internal data class IptvSmbConfig(
    val host: String,
    val port: Int = 445,
    val share: String,
    val folder: String = "",
    val domain: String = "",
    val username: String = "",
    val password: String = "",
) {
    val label: String
        get() = buildString {
            append(host)
            if (port != 445) append(':').append(port)
            append('/').append(share)
            folder.trim('/').takeIf { it.isNotEmpty() }?.let { append('/').append(it) }
        }

    val baseUrl: String
        get() {
            val cleanHost = host.trim().removePrefix("smb://").trimEnd('/')
            val hostPart = if (port in 1..65535 && port != 445) "$cleanHost:$port" else cleanHost
            return buildString {
                append("smb://").append(hostPart).append('/').append(encode(share.trim('/'))).append('/')
                val parts = folder.trim('/').split('/').filter { it.isNotBlank() }
                if (parts.isNotEmpty()) append(parts.joinToString("/") { encode(it) }).append('/')
            }
        }

    internal fun key(): String =
        "$host|$port|$share|$folder|$domain|$username|${password.hashCode()}"

    companion object {
        fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}

/** The one SMB recording share, saved in the IPTV preferences (the password with DPAPI). */
internal object IptvSmbStore {
    private val store = DesktopStorage.store("nuvio_iptv_smb")
    private const val Host = "host"
    private const val Port = "port"
    private const val Share = "share"
    private const val Folder = "folder"
    private const val Domain = "domain"
    private const val Username = "username"
    private const val Password = "password"

    fun load(): IptvSmbConfig? {
        val host = store.getString(Host).orEmpty().trim()
        val share = store.getString(Share).orEmpty().trim()
        if (host.isBlank() || share.isBlank()) return null
        return IptvSmbConfig(
            host = host,
            port = store.getInt(Port) ?: 445,
            share = share,
            folder = store.getString(Folder).orEmpty(),
            domain = store.getString(Domain).orEmpty(),
            username = store.getString(Username).orEmpty(),
            password = DesktopSecretStore.unprotect(store.getString(Password).orEmpty()),
        )
    }

    fun save(config: IptvSmbConfig) {
        store.putString(Host, config.host.trim())
        store.putInt(Port, config.port.coerceIn(1, 65535))
        store.putString(Share, config.share.trim())
        store.putString(Folder, config.folder.trim('/'))
        store.putString(Domain, config.domain.trim())
        store.putString(Username, config.username)
        store.putString(Password, DesktopSecretStore.protect(config.password))
    }

    fun clear() {
        listOf(Host, Port, Share, Folder, Domain, Username, Password)
            .forEach { store.putString(it, null) }
    }
}

/** One cached SMB context per share, so a scan or a playback doesn't reconnect for every file. */
internal object IptvSmbClient {
    private val contexts = ConcurrentHashMap<String, CIFSContext>()

    private fun context(config: IptvSmbConfig): CIFSContext =
        contexts.getOrPut(config.key()) {
            val props = Properties().apply {
                setProperty("jcifs.smb.client.enableSMB2", "true")
                setProperty("jcifs.smb.client.disableSMB1", "true")
                setProperty("jcifs.smb.client.signingPreferred", "false")
                setProperty("jcifs.smb.client.signingEnforced", "false")
                setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
                setProperty("jcifs.smb.useRawNTLM", "true")
                setProperty("jcifs.smb.client.responseTimeout", "30000")
                setProperty("jcifs.smb.client.soTimeout", "35000")
            }
            val base = BaseContext(PropertyConfiguration(props))
            base.withCredentials(
                NtlmPasswordAuthenticator(
                    config.domain.ifBlank { null },
                    config.username.ifBlank { null },
                    config.password,
                )
            )
        }

    fun file(config: IptvSmbConfig, relative: String): SmbFile {
        val clean = relative.trim('/')
        val base = SmbFile(config.baseUrl, context(config))
        return if (clean.isEmpty()) base else SmbFile(base, clean)
    }

    fun test(config: IptvSmbConfig): Result<Long> = runCatching {
        val dir = file(config, "")
        if (!dir.exists()) error("The share or folder wasn't found")
        val probe = file(config, ".nuvio-write-test-${System.nanoTime()}.tmp")
        try {
            probe.openOutputStream().use { it.write("Nuvio SMB test".toByteArray()) }
        } finally {
            runCatching { probe.delete() }
        }
        runCatching { dir.getDiskFreeSpace() }.getOrDefault(0L)
    }

    fun listDirs(config: IptvSmbConfig, relative: String): List<SmbFile> =
        runCatching { file(config, relative).listFiles()?.filter { it.isDirectory }.orEmpty() }
            .getOrDefault(emptyList())

    /**
     * Small files (info.json, playlists). Random access is used rather than a stream because
     * some servers handle the stream's large reads poorly; a playlist is tiny either way.
     */
    fun readText(config: IptvSmbConfig, relative: String): String? = runCatching {
        val target = file(config, relative)
        val length = target.length()
        if (length > 8L * 1024 * 1024) return@runCatching null
        target.openRandomAccess("r").use { raf ->
            val bytes = ByteArray(length.toInt())
            raf.readFully(bytes)
            // A BOM (some editors add one) would stop the JSON/playlist parser at the first byte.
            String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        }
    }.getOrNull()

    /**
     * Copies a file from the share to [output], reporting progress. [cancelled] is checked
     * between chunks so a long combine can be stopped.
     */
    fun copyTo(
        config: IptvSmbConfig,
        relative: String,
        output: OutputStream,
        cancelled: () -> Boolean = { false },
        onBytes: (Long) -> Unit = {},
    ): Long {
        val target = file(config, relative)
        val length = target.length()
        val raf = target.openRandomAccess("r") ?: error("Couldn't open $relative on the share")
        raf.use {
            val buffer = ByteArray(256 * 1024)
            var done = 0L
            while (done < length) {
                if (cancelled()) throw kotlinx.coroutines.CancellationException()
                val read = it.read(buffer, 0, minOf(buffer.size.toLong(), length - done).toInt())
                if (read < 0) break
                output.write(buffer, 0, read)
                done += read
                onBytes(done)
            }
            return done
        }
    }

    fun length(config: IptvSmbConfig, relative: String): Long =
        runCatching { file(config, relative).length() }.getOrDefault(-1L)

    fun exists(config: IptvSmbConfig, relative: String): Boolean =
        runCatching { file(config, relative).exists() }.getOrDefault(false)

    fun openInputStream(config: IptvSmbConfig, relative: String): InputStream? =
        runCatching { file(config, relative).openInputStream() }.getOrNull()

    fun openOutputStream(config: IptvSmbConfig, relative: String): OutputStream? = runCatching {
        val target = file(config, relative)
        target.parent?.takeIf { it.isNotBlank() }?.let { parent ->
            val dir = SmbFile(parent, context(config))
            // Some NAS firmware dislikes mkdir on an existing folder, so only create it when needed.
            if (!runCatching { dir.exists() }.getOrDefault(false)) runCatching { dir.mkdirs() }
        }
        target.openOutputStream()
    }.getOrNull()

    fun randomAccess(config: IptvSmbConfig, relative: String): SmbRandomAccessFile? =
        runCatching { file(config, relative).openRandomAccess("r") }.getOrNull()

    fun deleteRecursively(config: IptvSmbConfig, relative: String): Boolean {
        val clean = relative.trim('/')
        if (clean.isEmpty()) return false
        return runCatching {
            val root = SmbFile(
                config.baseUrl + clean.split('/').joinToString("/") { IptvSmbConfig.encode(it) } + "/",
                context(config),
            )
            if (!root.exists()) return@runCatching true
            fun delete(resource: SmbFile): Boolean {
                var success = true
                if (runCatching { resource.isDirectory }.getOrDefault(false)) {
                    runCatching { resource.listFiles() }.getOrDefault(emptyArray())
                        .forEach { child -> if (!delete(child)) success = false }
                }
                if (!runCatching { resource.delete(); true }.getOrDefault(false)) success = false
                return success
            }
            delete(root)
        }.getOrDefault(false)
    }
}

/** One recording folder found on the share, made by any of the Nuvio apps. */
internal data class SharedRecording(
    val id: String,
    val title: String,
    val episode: String?,
    val channelName: String,
    val startedAtMs: Long,
    val stopAtMs: Long,
    val status: String,
    val durationSec: Double,
    val sizeBytes: Long,
    val problem: String?,
    /** Folder on the share (for example `recordings/abc`). */
    val shareDir: String,
) {
    val isRecording: Boolean get() = status == "RECORDING"

    /** A missing info.json's fields fall back to sensible defaults. */
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String, id: String, shareDir: String): SharedRecording? = runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            fun str(key: String): String? = o[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            fun long(key: String): Long = o[key]?.jsonPrimitive?.longOrNull ?: 0L
            fun double(key: String): Double = o[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
            SharedRecording(
                id = str("id") ?: id,
                title = str("title") ?: "Recording",
                episode = str("episode"),
                channelName = str("channelName").orEmpty(),
                startedAtMs = long("startedAtMs"),
                stopAtMs = long("stopAtMs"),
                status = str("status") ?: "DONE",
                durationSec = double("durationSec"),
                sizeBytes = long("sizeBytes"),
                problem = str("problem"),
                shareDir = str("smbDir")?.trim('/') ?: shareDir,
            )
        }.getOrNull()
    }
}

/** Reads recordings made by the TV or phone from the share (both folder layouts). */
internal object IptvSharedRecordings {
    fun scan(config: IptvSmbConfig): List<SharedRecording> =
        listOf("", "recordings").flatMap { root ->
            IptvSmbClient.listDirs(config, root).mapNotNull { dir ->
                // The path's last segment is the folder name; some servers return odd `name`s
                // for a subfolder listing (for example `recordingsphone-test-rec`).
                val id = dir.path.trimEnd('/').substringAfterLast('/')
                if (id.isBlank() || id.startsWith(".") || id == "Exports") return@mapNotNull null
                val shareDir = if (root.isEmpty()) id else "$root/$id"
                val info = IptvSmbClient.readText(config, "$shareDir/info.json") ?: return@mapNotNull null
                SharedRecording.parse(info, id, shareDir)
            }
        }.distinctBy { it.id }.sortedByDescending { it.startedAtMs }
}
