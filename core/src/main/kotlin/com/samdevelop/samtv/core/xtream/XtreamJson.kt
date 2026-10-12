package com.samdevelop.samtv.core.xtream

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Leitura tolerante de JSON Xtream. Servidores diferentes devolvem o mesmo campo
 * como número, string, string vazia, null ou até array — estes helpers aceitam todas
 * as variações sem lançar exceção.
 */

internal fun JsonObject.str(key: String): String? {
    val el = this[key] ?: return null
    if (el is JsonNull) return null
    if (el is JsonPrimitive) return el.content.trim().takeIf { it.isNotEmpty() && it != "null" }
    return null
}

internal fun JsonObject.long(key: String): Long? = str(key)?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }

internal fun JsonObject.int(key: String): Int? = long(key)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

internal fun JsonObject.bool(key: String): Boolean = when (str(key)?.lowercase()) {
    "1", "true", "yes" -> true
    else -> false
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.strList(key: String): List<String> = when (val el = this[key]) {
    is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
    is JsonPrimitive -> listOfNotNull(el.content.trim().takeIf { it.isNotEmpty() && it != "null" })
    else -> emptyList()
}

/** Primeira URL http(s) de um campo que pode ser string ou array (ex.: backdrop_path). */
internal fun JsonObject.firstUrl(vararg keys: String): String? {
    for (k in keys) {
        strList(k).firstOrNull { it.startsWith("http://") || it.startsWith("https://") }?.let { return it }
    }
    return null
}

internal fun JsonElement.objects(): List<JsonObject> = when (this) {
    is JsonArray -> filterIsInstance<JsonObject>()
    else -> emptyList()
}
