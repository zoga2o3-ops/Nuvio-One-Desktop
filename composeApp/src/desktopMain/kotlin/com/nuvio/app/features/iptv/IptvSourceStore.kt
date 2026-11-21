package com.nuvio.app.features.iptv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.nuvio.app.features.youtube.OneTubeDebugLog
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

/** Private per-user source storage. IPTV URLs and Xtream logins may contain credentials. */
internal object IptvSourceStore {
    private val json = Json { ignoreUnknownKeys = true }

    internal fun dataDir(): Path {
        val override = System.getProperty("nuvio.iptv.storageDir")
        return if (override != null) {
            Path.of(override)
        } else {
            val osName = System.getProperty("os.name").orEmpty().lowercase()
            val userHome = Path.of(System.getProperty("user.home").orEmpty())
            when {
                osName.contains("win") -> {
                    val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                    (appData?.let { Path.of(it) } ?: userHome.resolve("AppData/Roaming"))
                        .resolve("Nuvio IPTV")
                }
                osName.contains("mac") -> userHome.resolve("Library/Application Support/Nuvio IPTV")
                else -> {
                    val config = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                    (config?.let { Path.of(it) } ?: userHome.resolve(".config")).resolve("nuvio-iptv")
                }
            }
        }
    }

    private fun path(): Path = dataDir().resolve("sources.json")

    @Volatile
    private var lastLoadError: String? = null

    @Volatile
    private var lastSaveError: String? = null

    fun load(): List<IptvSource> {
        lastLoadError = null
        return try {
            val file = path()
            if (!Files.isRegularFile(file)) return emptyList()
            json.parseToJsonElement(Files.readString(file)).jsonArray.mapNotNull { entry ->
                val o = entry.jsonObject
                val id = o.value("id") ?: return@mapNotNull null
                val url = o.value("url") ?: return@mapNotNull null
                IptvSource(
                    id = id,
                    name = o.value("name") ?: "Playlist",
                    url = url,
                    epgUrl = o.value("epgUrl").orEmpty(),
                    kind = o.value("kind") ?: "m3u",
                    username = o.value("username").orEmpty(),
                    password = o.value("password").orEmpty(),
                )
            }
        } catch (error: Throwable) {
            lastLoadError = "${error.javaClass.simpleName}: ${error.message.orEmpty()}".trim()
            OneTubeDebugLog.line("iptv-source-load-error $lastLoadError")
            emptyList()
        }
    }

    /** A safe summary for the debug report: no URLs, logins or names. */
    fun diagnostics(): String {
        val file = path()
        val exists = runCatching { Files.isRegularFile(file) }.getOrDefault(false)
        val bytes = if (exists) runCatching { Files.size(file) }.getOrDefault(-1L) else 0L
        val sources = load()
        val kinds = sources.groupingBy { it.kind.lowercase() }.eachCount()
            .entries.joinToString(",") { "${it.key}=${it.value}" }
        return "dir=${dataDir()} fileExists=$exists bytes=$bytes count=${sources.size} " +
            "kinds=${kinds.ifBlank { "none" }} " +
            "loadError=${lastLoadError ?: "none"} saveError=${lastSaveError ?: "none"}"
    }

    fun save(sources: List<IptvSource>) {
        lastSaveError = null
        try {
            val before = load()
            val file = path()
            Files.createDirectories(file.parent)
            val payload = buildJsonArray {
                sources.forEach { source ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(source.id))
                        put("name", JsonPrimitive(source.name))
                        put("url", JsonPrimitive(source.url))
                        put("epgUrl", JsonPrimitive(source.epgUrl))
                        put("kind", JsonPrimitive(source.kind))
                        put("username", JsonPrimitive(source.username))
                        put("password", JsonPrimitive(source.password))
                    })
                }
            }
            val text = payload.toString()
            val temporary = Files.createTempFile(file.parent, "sources-", ".json")
            try {
                setOwnerOnly(temporary)
                Files.writeString(temporary, text)
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (error: AtomicMoveNotSupportedException) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
                } catch (error: java.nio.file.FileSystemException) {
                    // A virus scanner can briefly lock the target on Windows; try a plain replace.
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
                }
                setOwnerOnly(file)
            } finally {
                Files.deleteIfExists(temporary)
            }
            // The playlist behind the sources changed: the next entry reloads.
            IptvSession.invalidate()
            StalkerLinks.configure(sources)
            // Removed here (by you): Live TV sync takes them off your other devices too.
            val now = sources.associateBy { it.id }
            val removed = HashSet<String>()
            before.forEach { old ->
                val kept = now[old.id]
                if (kept == null) {
                    removed += old.id
                    removed += com.nuvio.app.features.iptv.sync.LiveTvSyncLocal.sourceGuideId(old.id)
                } else if (old.epgUrl.isNotBlank() && kept.epgUrl.isBlank()) {
                    removed += com.nuvio.app.features.iptv.sync.LiveTvSyncLocal.sourceGuideId(old.id)
                }
            }
            com.nuvio.app.features.iptv.sync.LiveTvSyncLocal.noteRemoved(removed)
            com.nuvio.app.features.iptv.sync.LiveTvSyncLocal.noteChanged()
            OneTubeDebugLog.line("iptv-source-save count=${sources.size} bytes=${text.length}")
        } catch (error: Throwable) {
            lastSaveError = "${error.javaClass.simpleName}: ${error.message.orEmpty()}".trim()
            OneTubeDebugLog.line("iptv-source-save-error count=${sources.size} $lastSaveError")
            throw error
        }
    }

    internal fun setOwnerOnly(file: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                file,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }

    private fun JsonObject.value(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
