package com.nuvio.app.features.sports

import com.nuvio.app.core.storage.DesktopStorage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Small on-disk cache for sports responses (ESPN, NuvioOne files). File
 * modification time is the fetch time, so expired entries are simply ignored.
 */
internal object SportsCache {
    private val directory: Path by lazy {
        DesktopStorage.cacheDir.resolve("sports").also { Files.createDirectories(it) }
    }

    fun get(url: String, ttlMs: Long): String? {
        val file = fileFor(url)
        if (!Files.isRegularFile(file)) return null
        val ageMs = System.currentTimeMillis() - runCatching {
            Files.getLastModifiedTime(file).toMillis()
        }.getOrDefault(0L)
        if (ageMs > ttlMs) return null
        return runCatching { Files.readString(file) }.getOrNull()
    }

    fun put(url: String, body: String) {
        runCatching {
            Files.createDirectories(directory)
            val file = fileFor(url)
            val temporary = Files.createTempFile(directory, "sports-", ".json")
            try {
                Files.writeString(temporary, body)
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }

    fun clear() {
        runCatching {
            if (!Files.isDirectory(directory)) return
            Files.list(directory).use { stream ->
                stream.forEach { path -> runCatching { Files.deleteIfExists(path) } }
            }
        }
    }

    private fun fileFor(url: String): Path {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return directory.resolve("$hash.json")
    }
}
