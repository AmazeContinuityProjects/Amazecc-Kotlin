package com.amazecc.app.shared.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

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
