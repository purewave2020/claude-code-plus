package com.asakii.plugin.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun JsonObject.getString(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.content
}

internal fun JsonObject.getInt(key: String): Int? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.content.toIntOrNull() ?: primitive.content.toDoubleOrNull()?.toInt()
}

internal fun JsonObject.getLong(key: String): Long? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.content.toLongOrNull() ?: primitive.content.toDoubleOrNull()?.toLong()
}

internal fun JsonObject.getBoolean(key: String): Boolean? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return when (primitive.content.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

internal fun JsonObject.getStringList(key: String): List<String>? {
    val array = this[key] as? JsonArray ?: return null
    return array.mapNotNull { it.asString() }
}

private fun JsonElement.asString(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content
}
