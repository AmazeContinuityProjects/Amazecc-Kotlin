package com.amazecc.app.shared.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * The TS contracts in ../amazecc-api-hono use `string | null` and `number | null` for fields that
 * the Kotlin UI models as non-null with defaults. Plain kotlinx.serialization throws on an explicit
 * `null` in a non-null field, so one VTOP null was enough to fail an entire module decode.
 *
 * These serializers keep the non-null Kotlin contract the UI relies on while accepting the real
 * wire shapes. They are used only on DTO fields whose TS declaration is nullable — app-side
 * derived fields keep normal serialization.
 */

object NullableStringAsEmptySerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableStringAsEmpty", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val element = JsonElement.serializer().deserialize(decoder)
        return if (element is JsonPrimitive && element !is JsonNull) element.content else ""
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

object NullableIntAsZeroSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableIntAsZero", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val element = JsonElement.serializer().deserialize(decoder)
        if (element !is JsonPrimitive || element is JsonNull) return 0
        return element.content.toIntOrNull()
            ?: element.content.toDoubleOrNull()?.toInt()
            ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** `credits` is `number` in marks.ts but `String?` in the Kotlin model. Accept both. */
object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val element = JsonElement.serializer().deserialize(decoder)
        return if (element is JsonPrimitive && element !is JsonNull) element.content else null
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/**
 * `/api/ffcs section=qcm` returns `semesters` as `[{value, text}]` option objects while the
 * on-device scraper and older caches store plain `["CH20262701", ...]` ids. Keep the public
 * `List<String>` contract by accepting both shapes (D-098).
 */
object FlexibleStringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleStringList", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): List<String> {
        val element = JsonElement.serializer().deserialize(decoder)
        val array = element as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return array.mapNotNull { entry ->
            when (entry) {
                is JsonPrimitive -> entry.content.takeIf { it.isNotBlank() }
                is JsonObject -> entry["value"]?.jsonPrimitiveOrNull()?.content?.takeIf { it.isNotBlank() }
                    ?: entry["text"]?.jsonPrimitiveOrNull()?.content?.takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeSerializableValue(
            kotlinx.serialization.builtins.ListSerializer(JsonElement.serializer()),
            value.map { JsonPrimitive(it) }
        )
    }

    private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive
}

/**
 * `POST /api/attendance` puts marks in `marksRes`, which is a `MarksRes` object on success but a
 * **plain error string** when the upstream marks fetch fails. A non-null DTO field would make the
 * whole attendance response throw; this serializer keeps the `MarksRes?` contract and turns any
 * non-object value into `null` (D-107).
 */
object MarksResOrStringSerializer : KSerializer<MarksRes?> {
    private val tolerantJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override val descriptor: SerialDescriptor = MarksRes.serializer().descriptor

    override fun deserialize(decoder: Decoder): MarksRes? {
        val element = JsonElement.serializer().deserialize(decoder)
        if (element !is JsonObject) return null
        return runCatching { tolerantJson.decodeFromJsonElement(MarksRes.serializer(), element) }.getOrNull()
    }

    override fun serialize(encoder: Encoder, value: MarksRes?) {
        if (value == null) encoder.encodeNull()
        else encoder.encodeSerializableValue(MarksRes.serializer(), value)
    }
}
