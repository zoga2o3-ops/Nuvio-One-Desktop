package com.nuvio.app.features.iptv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

/** Favourite channel keys, stored next to the playlist sources. */
internal object IptvFavoritesStore {
    private val json = Json { ignoreUnknownKeys = true }

    private fun path(): Path {
        val override = System.getProperty("nuvio.iptv.storageDir")
        val dir = if (override != null) {
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
        return dir.resolve("favorites.json")
    }

    fun load(): Set<String> = runCatching {
        val file = path()
        if (!Files.isRegularFile(file)) return emptySet()
        json.parseToJsonElement(Files.readString(file)).jsonArray
            .mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
            .filter { it.isNotBlank() }
            .toSet()
    }.getOrDefault(emptySet())

    /** Written in the given order (the order they were starred), so load() keeps it. */
    fun save(keys: Set<String>) {
        runCatching {
            val file = path()
            Files.createDirectories(file.parent)
            val payload = buildJsonArray {
                keys.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            }
            val temporary = Files.createTempFile(file.parent, "favorites-", ".json")
            try {
                setOwnerOnly(temporary)
                Files.writeString(temporary, payload.toString())
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                setOwnerOnly(file)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        com.nuvio.app.features.iptv.sync.LiveTvSyncLocal.noteChanged()
    }

    private fun setOwnerOnly(file: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                file,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }
}
