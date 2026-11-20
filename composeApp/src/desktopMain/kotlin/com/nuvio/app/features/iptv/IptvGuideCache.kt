package com.nuvio.app.features.iptv

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A guide loaded from disk, with the time it was originally written. */
internal data class CachedIptvGuide(
    val guide: IptvGuide,
    val savedAtMs: Long,
)

/**
 * Persistent copy of the parsed playlist/EPG guide. The cache is gzipped JSON
 * next to the IPTV source file, tied to a fingerprint of the current sources.
 */
internal object IptvGuideCache {
    private const val CacheVersion = 2
    private const val FileName = "guide-cache.json.gz"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun path(): Path = IptvSourceStore.dataDir().resolve(FileName)

    suspend fun load(sources: List<IptvSource>, windowKey: String): CachedIptvGuide? = withContext(Dispatchers.IO) {
        runCatching {
            val file = path()
            if (!Files.isRegularFile(file)) return@runCatching null
            val text = GZIPInputStream(Files.newInputStream(file))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val cached = json.decodeFromString<CachedGuide>(text)
            if (cached.version != CacheVersion) return@runCatching null
            if (cached.windowKey != windowKey) return@runCatching null
            if (cached.sourceFingerprint != fingerprint(sources)) return@runCatching null
            CachedIptvGuide(cached.guide, cached.savedAtMs)
        }.getOrNull()
    }

    suspend fun save(
        sources: List<IptvSource>,
        windowKey: String,
        guide: IptvGuide,
        savedAtMs: Long = System.currentTimeMillis(),
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = path()
            Files.createDirectories(file.parent)
            val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
            IptvSourceStore.setOwnerOnly(temp)
            val payload = json.encodeToString(
                CachedGuide(
                    version = CacheVersion,
                    sourceFingerprint = fingerprint(sources),
                    windowKey = windowKey,
                    savedAtMs = savedAtMs,
                    guide = guide,
                ),
            )
            GZIPOutputStream(Files.newOutputStream(temp)).bufferedWriter(Charsets.UTF_8).use {
                it.write(payload)
            }
            runCatching {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
            IptvSourceStore.setOwnerOnly(file)
            true
        }.getOrDefault(false)
    }

    fun clear() {
        runCatching { Files.deleteIfExists(path()) }
    }

    private fun fingerprint(sources: List<IptvSource>): String {
        // What actually loads: duplicate playlists load once ("v2" retires caches built with
        // every duplicate loaded, and their HTTP 429 errors).
        val material = "v2\n" + IptvLoader.distinctSources(sources)
            .sortedBy { it.id }
            .joinToString("\n") { source ->
                listOf(source.id, source.url, source.epgUrl, source.kind, source.username)
                    .joinToString("|")
            }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    @Serializable
    private data class CachedGuide(
        val version: Int,
        val sourceFingerprint: String,
        val windowKey: String = "",
        val savedAtMs: Long,
        val guide: IptvGuide,
    )
}
