package com.nuvio.app.features.iptv

import com.nuvio.app.features.iptv.sync.LiveTvSyncLocal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Guides (XMLTV) that aren't tied to one playlist, like the TV app's "EPG sources". Mostly they
 * arrive through Live TV sync from a TV; each enabled one is loaded with the playlists.
 */
internal data class IptvGuideSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
)

internal object IptvGuideSourceStore {
    private val json = Json { ignoreUnknownKeys = true }
    private fun path() = IptvSourceStore.dataDir().resolve("guides.json")

    fun load(): List<IptvGuideSource> = runCatching {
        val file = path()
        if (!Files.isRegularFile(file)) return emptyList()
        json.parseToJsonElement(Files.readString(file)).jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            IptvGuideSource(
                id = id,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: "Guide",
                url = url,
                enabled = o["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
            )
        }
    }.getOrDefault(emptyList())

    fun save(guides: List<IptvGuideSource>) {
        val before = load()
        val file = path()
        Files.createDirectories(file.parent)
        val payload = buildJsonArray {
            guides.forEach { g ->
                add(buildJsonObject {
                    put("id", JsonPrimitive(g.id))
                    put("name", JsonPrimitive(g.name))
                    put("url", JsonPrimitive(g.url))
                    put("enabled", JsonPrimitive(g.enabled))
                })
            }
        }
        val temporary = Files.createTempFile(file.parent, "guides-", ".json")
        try {
            IptvSourceStore.setOwnerOnly(temporary)
            Files.writeString(temporary, payload.toString())
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
        if (before != guides) IptvSession.invalidate()
        LiveTvSyncLocal.noteRemoved(before.map { it.id }.toSet() - guides.map { it.id }.toSet())
        LiveTvSyncLocal.noteChanged()
    }
}
