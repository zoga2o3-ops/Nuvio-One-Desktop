package com.nuvio.app.features.iptv.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The TV app's Live TV setup merge (LiveTvDriveSync.merge), ported as-is so a desktop and a TV
 * combine a shared copy the same way.
 *
 * A setup is a JSON object of settings, each `{"t": type, "v": value}`. Two settings are lists of
 * sources ("playlists", "epg_sources", whose "v" is a JSON array string of objects with an "id"),
 * and those are combined source by source; every other setting is taken whole.
 */
internal object LiveTvMerge {
    val SOURCE_LISTS = setOf("playlists", "epg_sources", "music_liked", "music_playlists")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Combines two setups setting by setting: whatever this device changed since the last sync
     * ([base]) is kept, everything else comes from the other device. Without a base,
     * [localWinsWithoutBase] decides, but playlists and guides from both are kept.
     */
    fun merge(
        base: JsonObject?,
        local: JsonObject,
        remote: JsonObject,
        localWinsWithoutBase: Boolean,
        removedHere: Set<String>,
    ): JsonObject {
        if (base == null) {
            val (first, second) = if (localWinsWithoutBase) local to remote else remote to local
            val out = LinkedHashMap(first)
            // A first sync never drops settings only one side has.
            second.forEach { (k, v) -> if (!out.containsKey(k)) out[k] = v }
            // Favourites and hidden channels/groups are personal: keep both sides' entries.
            unionPersonal(out, first, second)
            // The person's own arrangements: this device's group order wins a first sync.
            local.forEach { (k, v) -> if (k.replace(PROFILE_SUFFIX, "") in LOCAL_ORDER_KEYS) out[k] = v }
            for (k in SOURCE_LISTS) {
                val a = first[k] as? JsonObject
                val b = second[k] as? JsonObject ?: continue
                if (a == null) { out[k] = b; continue }
                runCatching { unionSources(a.str("v"), b.str("v"), removedHere) }.getOrNull()
                    ?.let { out[k] = JsonObject(a + ("v" to JsonPrimitive(it))) }
            }
            return JsonObject(out)
        }
        val out = LinkedHashMap(remote)
        val keys = LinkedHashSet<String>().apply { addAll(local.keys); addAll(base.keys) }
        for (k in keys) {
            val l = local[k]?.toString()
            val b = base[k]?.toString()
            if (l == b) continue
            val listMerged = if (k in SOURCE_LISTS && (local.containsKey(k) || remote.containsKey(k))) {
                runCatching {
                    mergeSources(
                        (base[k] as? JsonObject)?.str("v"),
                        (local[k] as? JsonObject)?.str("v") ?: "[]",
                        (remote[k] as? JsonObject)?.str("v") ?: "[]",
                        removedHere,
                    )
                }.getOrNull()
            } else null
            val shell = (local[k] as? JsonObject) ?: (remote[k] as? JsonObject)
            when {
                listMerged != null && shell != null -> out[k] = JsonObject(shell + ("v" to JsonPrimitive(listMerged)))
                local.containsKey(k) -> out[k] = local.getValue(k)
                else -> out.remove(k)
            }
        }
        return JsonObject(out)
    }

    /**
     * Three-way merge of two source lists: sources added or removed on either device are added or
     * removed, and within a source each field changed here since the last sync is kept.
     */
    private fun mergeSources(baseRaw: String?, localRaw: String, remoteRaw: String, removedHere: Set<String>): String {
        val base = readSources(baseRaw)
        val local = readSources(localRaw)
        val remote = readSources(remoteRaw)
        val merged = LinkedHashMap<String, JsonObject>()
        for ((id, r) in remote) {
            val l = local[id]
            val b = base[id]
            when {
                l == null && id in removedHere -> continue
                l == null -> merged[id] = r
                b == null -> merged[id] = r
                else -> {
                    val m = LinkedHashMap(r)
                    for ((f, value) in l) {
                        if (value.toString() != b[f]?.toString()) m[f] = value
                    }
                    merged[id] = JsonObject(m)
                }
            }
        }
        for ((id, l) in local) if (id !in remote && id !in base) merged[id] = l
        // Playlist order is part of the setup: this device's order when it was changed since the
        // last sync, otherwise the other device's, with anything only this one has appended.
        val common = base.keys.filter { it in local && it in remote }
        val localOrder = local.keys.filter { it in common }
        val baseOrder = base.keys.filter { it in common }
        val localReordered = localOrder.size >= 2 && localOrder != baseOrder
        val order = if (localReordered) {
            local.keys + remote.keys.filter { it !in local }
        } else {
            remote.keys + local.keys.filter { it !in remote }
        }
        return JsonArray(order.mapNotNull { merged[it] }).toString()
    }

    /** First sync: keep both sides' favourites and hidden channels/groups. */
    private fun unionPersonal(out: MutableMap<String, JsonElement>, first: JsonObject, second: JsonObject) {
        for (k in first.keys + second.keys) {
            if (k.replace(PROFILE_SUFFIX, "") !in PERSONAL_UNION_KEYS) continue
            val a = first[k] as? JsonObject ?: continue
            val b = second[k] as? JsonObject ?: continue
            val ta = a.str("t")
            val tb = b.str("t")
            if (ta == "s" && tb == "s") {
                val values = LinkedHashSet<String>()
                (a["v"] as? JsonArray)?.forEach { (it as? JsonPrimitive)?.contentOrNull?.let(values::add) }
                (b["v"] as? JsonArray)?.forEach { (it as? JsonPrimitive)?.contentOrNull?.let(values::add) }
                out[k] = JsonObject(a + ("v" to JsonArray(values.map(::JsonPrimitive))))
            } else if (ta == "str" && tb == "str") {
                val la = runCatching { json.parseToJsonElement(a.str("v").orEmpty()).jsonArray }.getOrNull() ?: continue
                val lb = runCatching { json.parseToJsonElement(b.str("v").orEmpty()).jsonArray }.getOrNull() ?: continue
                val values = LinkedHashSet<String>()
                la.forEach { (it as? JsonPrimitive)?.contentOrNull?.let(values::add) }
                lb.forEach { (it as? JsonPrimitive)?.contentOrNull?.let(values::add) }
                out[k] = JsonObject(a + ("v" to JsonPrimitive(JsonArray(values.map(::JsonPrimitive)).toString())))
            }
        }
    }

    private val PROFILE_SUFFIX = Regex("""_p\d+$""")
    private val LOCAL_ORDER_KEYS = setOf("group_order", "vod_group_order", "side_menu_order")
    private val PERSONAL_UNION_KEYS = setOf("favorites", "hidden_channels", "hidden_groups", "vod_hidden_categories")

    private fun unionSources(firstRaw: String?, secondRaw: String?, removedHere: Set<String>): String {
        val first = readSources(firstRaw)
        val result = ArrayList<JsonElement>(first.values)
        for ((id, o) in readSources(secondRaw)) {
            if (id !in first && id !in removedHere) result += o
        }
        return JsonArray(result).toString()
    }

    fun readSources(raw: String?): LinkedHashMap<String, JsonObject> {
        val out = LinkedHashMap<String, JsonObject>()
        if (raw.isNullOrBlank()) return out
        val arr = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return out
        for (e in arr) {
            val o = e as? JsonObject ?: continue
            val id = o.str("id")
            if (!id.isNullOrBlank()) out[id] = o
        }
        return out
    }

    fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
