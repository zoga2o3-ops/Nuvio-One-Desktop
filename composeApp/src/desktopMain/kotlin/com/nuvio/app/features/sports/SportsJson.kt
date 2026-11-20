package com.nuvio.app.features.sports

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Shared JSON parser for the sports feeds. */
internal val sportsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.toIntOrNull()

internal fun JsonObject.obj(key: String): JsonObject? =
    this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? =
    this[key] as? JsonArray
