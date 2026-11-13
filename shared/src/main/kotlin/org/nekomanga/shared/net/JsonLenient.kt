package org.nekomanga.shared.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// Lenient readers for responses whose exact shape isn't guaranteed. Each returns null instead of
// throwing when a field is missing or has another type.

operator fun JsonElement?.get(key: String): JsonElement? =
    (this as? JsonObject)?.get(key)?.takeUnless { it is JsonNull }

fun JsonElement?.items(): List<JsonElement> = (this as? JsonArray).orEmpty()

/** A string field; numbers are returned as their text. */
fun JsonElement?.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }

fun JsonElement?.int(key: String): Int? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.doubleOrNull?.toInt()
}

/** A number field, also accepted when sent as a string such as "8.52". */
fun JsonElement?.double(key: String): Double? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.doubleOrNull ?: primitive.content.toDoubleOrNull()
}

fun JsonElement?.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
